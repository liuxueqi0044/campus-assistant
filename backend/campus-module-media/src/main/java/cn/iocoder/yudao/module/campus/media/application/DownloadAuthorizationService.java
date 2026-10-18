package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAction;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourceRef;
import cn.iocoder.yudao.module.campus.media.domain.MediaAssetStatus;
import cn.iocoder.yudao.module.campus.media.domain.MediaPurpose;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAccessAuditRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaDeliveryGrantRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaDeliveryGrantDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authorization core for proxy-only membership-proof downloads. It never opens storage and never
 * returns a permanent object address.
 */
@Service
public class DownloadAuthorizationService {

    static final String PROOF_RESOURCE_TYPE = "MEMBERSHIP_VERIFICATION_PROOF";
    static final String ISSUE_ACTION = "ISSUE_DELIVERY";
    static final String OPEN_ACTION = "OPEN_AUTHORIZED";
    static final String ALLOW = "ALLOW";
    static final String DENY = "DENY";
    static final String REPLAY_CASE_REFERENCE = "DELIVERY_TOKEN_REPLAY";
    static final String REPLAY_REASON = "actor mismatch";
    static final Duration MAX_TTL = Duration.ofSeconds(120);

    private final MediaAssetRepository assets;
    private final MediaBindingRepository bindings;
    private final MediaDeliveryGrantRepository grants;
    private final MediaAccessAuditRepository audits;
    private final MediaAccessAuditWriter deniedAudits;
    private final ResourceAuthorizationApi authorization;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public DownloadAuthorizationService(MediaAssetRepository assets, MediaBindingRepository bindings,
                                        MediaDeliveryGrantRepository grants, MediaAccessAuditRepository audits,
                                        MediaAccessAuditWriter deniedAudits, ResourceAuthorizationApi authorization,
                                        ObjectMapper objectMapper, Clock clock,
                                        @Value("${campus.media.delivery-ttl:60s}") Duration ttl) {
        this.assets = Objects.requireNonNull(assets);
        this.bindings = Objects.requireNonNull(bindings);
        this.grants = Objects.requireNonNull(grants);
        this.audits = Objects.requireNonNull(audits);
        this.deniedAudits = Objects.requireNonNull(deniedAudits);
        this.authorization = Objects.requireNonNull(authorization);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.ttl = Objects.requireNonNull(ttl);
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("campus.media.delivery-ttl must be in 1..120 seconds");
        }
    }

    @Transactional
    public MediaDeliveryIssueResult issue(ActorRef actor, long assetId, String caseReference, String reason) {
        Objects.requireNonNull(actor, "actor");
        if (assetId <= 0) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        AccessContext context = normalizeContext(actor, "MEMBERSHIP_VERIFICATION", caseReference, reason);
        AccessTarget target = findTarget(assetId);
        if (target == null || target.binding() == null || !PROOF_RESOURCE_TYPE.equals(target.binding().getResourceType())) {
            deny(actor, target, ISSUE_ACTION, context, Set.of("RESOURCE_TYPE_MISMATCH"));
            throw inaccessible();
        }
        ResourceAuthorizationDecision decision = authorize(actor, target.binding(), context);
        if (!decision.allowed()) {
            deny(actor, target, ISSUE_ACTION, context, decision.auditLabels());
            throw inaccessible();
        }
        if (!usable(target, now())) {
            deny(actor, target, ISSUE_ACTION, context, Set.of("MEDIA_UNAVAILABLE"));
            throw new CampusApiException(CampusErrorCodes.MEDIA_UNAVAILABLE);
        }

        Instant expiresAt = clock.instant().plus(ttl);
        String token = nextToken();
        MediaDeliveryGrantDO grant = new MediaDeliveryGrantDO();
        grant.setTokenHash(MediaHashing.sha256(token));
        grant.setActorType(actor.type().name());
        grant.setActorId(actor.id());
        grant.setAssetId(target.asset().getId());
        grant.setAssetGeneration(target.asset().getGeneration());
        grant.setAssetVersion(target.asset().getVersion());
        grant.setBindingId(target.binding().getId());
        grant.setCaseReference(context.caseReference());
        grant.setReason(context.reason());
        grant.setExpiresAt(local(expiresAt));
        grant.setVersion(1L);
        // Same transaction as the grant: an audit write failure must prevent token issuance.
        audits.insert(audit(actor, target, ISSUE_ACTION, ALLOW, context, decision.auditLabels()));
        grants.insert(grant);
        return new MediaDeliveryIssueResult(token, expiresAt, target.asset().getGeneration(),
                target.asset().getVersion());
    }

    @Transactional
    public AuthorizedMediaObject authorizeOpen(ActorRef actor, String token) {
        Objects.requireNonNull(actor, "actor");
        rejectUnsupportedActor(actor);
        AccessContext anonymousDenyContext = replayContext(actor);
        if (!validToken(token)) {
            deny(actor, null, OPEN_ACTION, anonymousDenyContext, Set.of("TOKEN_INVALID"));
            throw inaccessible();
        }
        String tokenHash = MediaHashing.sha256(token);
        MediaDeliveryGrantDO grant = grants.findActiveByTokenHash(tokenHash, now());
        if (grant == null) {
            deny(actor, null, OPEN_ACTION, anonymousDenyContext, Set.of("TOKEN_NOT_ACTIVE"));
            throw inaccessible();
        }
        AccessTarget target = findTarget(grant.getAssetId());
        if (!sameActor(grant, actor)) {
            deny(actor, target, OPEN_ACTION, replayContext(actor), Set.of("ACTOR_MISMATCH"));
            throw inaccessible();
        }
        AccessContext context = normalizeContext(actor, "MEMBERSHIP_VERIFICATION", grant.getCaseReference(),
                grant.getReason());
        if (target == null || target.binding() == null || !PROOF_RESOURCE_TYPE.equals(target.binding().getResourceType())) {
            deny(actor, target, OPEN_ACTION, context, Set.of("RESOURCE_TYPE_MISMATCH"));
            throw inaccessible();
        }
        ResourceAuthorizationDecision decision = authorize(actor, target.binding(), context);
        if (!decision.allowed()) {
            deny(actor, target, OPEN_ACTION, context, decision.auditLabels());
            throw inaccessible();
        }
        if (!matchesGrant(grant, target) || !usable(target, now())) {
            deny(actor, target, OPEN_ACTION, context, Set.of("MEDIA_UNAVAILABLE"));
            throw new CampusApiException(CampusErrorCodes.MEDIA_UNAVAILABLE);
        }
        // This write precedes the return; an audit failure cannot accidentally authorize the proxy stream.
        audits.insert(audit(actor, target, OPEN_ACTION, ALLOW, context, decision.auditLabels()));
        MediaAssetDO asset = target.asset();
        return new AuthorizedMediaObject(asset.getObjectKey(), asset.getActualMime(), asset.getActualSize(),
                asset.getOriginalFilename());
    }

    private AccessTarget findTarget(long assetId) {
        MediaAssetDO asset = assets.find(assetId);
        return new AccessTarget(asset, asset == null ? null : bindings.findByAssetId(assetId), assetId);
    }

    private ResourceAuthorizationDecision authorize(ActorRef actor, MediaBindingDO binding,
                                                     AccessContext context) {
        ResourceAuthorizationDecision decision = authorization.authorize(actor,
                new ResourceRef(binding.getResourceType(), binding.getResourceId(), null), ResourceAction.READ,
                new ResourceAccessPurpose(binding.getPurpose(), context.caseReference(), context.reason()));
        return decision == null ? ResourceAuthorizationDecision.deny() : decision;
    }

    private boolean usable(AccessTarget target, LocalDateTime now) {
        MediaAssetDO asset = target.asset();
        MediaBindingDO binding = target.binding();
        MediaPurpose purpose = MediaPurpose.MEMBERSHIP_VERIFICATION;
        return MediaAssetStatus.VALID.name().equals(asset.getStatus())
                && purpose.name().equals(asset.getPurpose()) && purpose.name().equals(binding.getPurpose())
                && Objects.equals(asset.getGeneration(), asset.getValidatedGeneration())
                && Objects.equals(asset.getGeneration(), binding.getAssetGeneration())
                && purpose.accepts(asset.getActualMime())
                && asset.getActualSize() != null && asset.getActualSize() > 0
                && asset.getActualSize() <= purpose.maxBytes()
                && asset.getContentSha256() != null && asset.getContentSha256().matches("[0-9a-f]{64}")
                && asset.getObjectKey() != null && !asset.getObjectKey().isBlank()
                && asset.getOriginalFilename() != null && !asset.getOriginalFilename().isBlank()
                && binding.getValidFrom() != null && binding.getExpiresAt() != null
                && !now.isBefore(binding.getValidFrom()) && now.isBefore(binding.getExpiresAt());
    }

    private static boolean matchesGrant(MediaDeliveryGrantDO grant, AccessTarget target) {
        return Objects.equals(grant.getAssetId(), target.asset().getId())
                && Objects.equals(grant.getAssetGeneration(), target.asset().getGeneration())
                && Objects.equals(grant.getAssetVersion(), target.asset().getVersion())
                && Objects.equals(grant.getBindingId(), target.binding().getId());
    }

    private static boolean sameActor(MediaDeliveryGrantDO grant, ActorRef actor) {
        return Objects.equals(grant.getActorType(), actor.type().name()) && Objects.equals(grant.getActorId(), actor.id());
    }

    private void deny(ActorRef actor, AccessTarget target, String action, AccessContext context, Set<String> labels) {
        deniedAudits.appendDenied(audit(actor, target, action, DENY, context, labels));
    }

    private MediaAccessAuditDO audit(ActorRef actor, AccessTarget target, String action, String decision,
                                     AccessContext context, Set<String> labels) {
        MediaAccessAuditDO audit = new MediaAccessAuditDO();
        audit.setActorType(actor.type().name());
        audit.setActorId(actor.id());
        if (target != null) {
            audit.setAssetId(target.asset() == null ? target.requestedAssetId() : target.asset().getId());
        }
        if (target != null && target.asset() != null) {
            audit.setAssetGeneration(target.asset().getGeneration());
            audit.setAssetVersion(target.asset().getVersion());
            if (target.binding() != null) {
                audit.setBindingId(target.binding().getId());
                audit.setResourceType(target.binding().getResourceType());
                audit.setResourceId(target.binding().getResourceId());
                audit.setPurpose(target.binding().getPurpose());
            }
        }
        audit.setAction(action);
        audit.setDecision(decision);
        audit.setCaseReference(context.caseReference());
        audit.setReason(context.reason());
        audit.setAuditLabelsJson(labelsJson(labels));
        audit.setTraceId(Objects.requireNonNullElse(TracerUtils.getTraceId(), ""));
        audit.setOccurredAt(now());
        return audit;
    }

    private String labelsJson(Set<String> labels) {
        try {
            List<String> sorted = (labels == null ? Set.<String>of() : labels).stream().sorted().toList();
            return objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("audit labels cannot be serialized", exception);
        }
    }

    private String nextToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean validToken(String token) {
        return token != null && token.length() == 43 && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static AccessContext normalizeContext(ActorRef actor, String purpose, String caseReference, String reason) {
        rejectUnsupportedActor(actor);
        ResourceAccessPurpose normalized;
        try {
            normalized = new ResourceAccessPurpose(purpose, caseReference, reason);
        } catch (RuntimeException exception) {
            throw inaccessible();
        }
        if (actor.type() == ActorType.MEMBER
                && (normalized.caseReference() != null || normalized.reason() != null)) {
            throw inaccessible();
        }
        if (actor.type() == ActorType.OPERATOR
                && (normalized.caseReference() == null || normalized.reason() == null)) {
            throw inaccessible();
        }
        return new AccessContext(normalized.caseReference(), normalized.reason());
    }

    private static void rejectUnsupportedActor(ActorRef actor) {
        if (actor.type() == ActorType.SYSTEM) {
            throw inaccessible();
        }
    }

    private static AccessContext replayContext(ActorRef actor) {
        return actor.type() == ActorType.OPERATOR
                ? new AccessContext(REPLAY_CASE_REFERENCE, REPLAY_REASON)
                : new AccessContext(null, null);
    }

    private LocalDateTime now() {
        return local(clock.instant());
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static CampusApiException inaccessible() {
        return new CampusApiException(CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);
    }

    private record AccessTarget(MediaAssetDO asset, MediaBindingDO binding, long requestedAssetId) {
    }

    private record AccessContext(String caseReference, String reason) {
    }
}
