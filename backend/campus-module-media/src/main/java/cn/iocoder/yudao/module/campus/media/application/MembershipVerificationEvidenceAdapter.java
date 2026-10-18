package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationEvidencePort;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationProof;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Claims verified membership evidence inside the M01.03 command transaction. A proof is a durable,
 * exclusive media binding, never a read-only validation result.
 */
@Component
public class MembershipVerificationEvidenceAdapter implements VerificationEvidencePort {

    static final String PURPOSE = "MEMBERSHIP_VERIFICATION";
    static final String RESOURCE_TYPE = "MEMBERSHIP_VERIFICATION_PROOF";

    private final MediaBindingService bindingService;
    private final MediaAssetRepository assets;
    private final MediaBindingRepository bindings;
    private final Clock clock;
    private final Duration proofTtl;

    public MembershipVerificationEvidenceAdapter(MediaBindingService bindingService,
                                                  MediaAssetRepository assets,
                                                  MediaBindingRepository bindings,
                                                  Clock clock,
                                                  @Value("${campus.media.membership-proof-ttl:365d}")
                                                  Duration proofTtl) {
        this.bindingService = Objects.requireNonNull(bindingService);
        this.assets = Objects.requireNonNull(assets);
        this.bindings = Objects.requireNonNull(bindings);
        this.clock = Objects.requireNonNull(clock);
        this.proofTtl = Objects.requireNonNull(proofTtl);
        if (proofTtl.isZero() || proofTtl.isNegative()) {
            throw new IllegalArgumentException("membership proof ttl must be positive");
        }
    }

    @Override
    public VerificationProof verify(long userId, long schoolId, long campusId, String method,
                                    List<String> evidenceRefs) {
        requireTransaction();
        if (userId <= 0 || schoolId <= 0 || campusId <= 0) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        String normalizedMethod = Objects.requireNonNullElse(method, "").trim();
        if (!"STUDENT_CARD".equals(normalizedMethod)) {
            throw new CampusApiException(CampusErrorCodes.VERIFICATION_METHOD_DISABLED);
        }
        List<Long> assetIds = parseAssetIds(evidenceRefs);
        String context = canonicalContext(userId, schoolId, campusId, normalizedMethod, assetIds);
        String contextHash = MediaHashing.sha256(context);
        List<MediaBindingDO> existing = bindings.findByAssetIds(assetIds);

        String resourceId;
        Instant validFrom;
        Instant expiresAt;
        if (existing.isEmpty()) {
            resourceId = UUID.randomUUID().toString();
            validFrom = clock.instant();
            expiresAt = validFrom.plus(proofTtl);
        } else {
            MediaBindingDO first = requireExistingProof(existing, assetIds, userId, contextHash);
            resourceId = first.getResourceId();
            validFrom = first.getValidFrom().toInstant(ZoneOffset.UTC);
            expiresAt = first.getExpiresAt().toInstant(ZoneOffset.UTC);
        }

        bindingService.bind(userId, new MediaBindingCommand(PURPOSE, RESOURCE_TYPE, resourceId, context,
                validFrom, expiresAt, assetIds.stream().map(id -> requiredReference(id, existing)).toList()));

        List<MediaBindingDO> completeProof = bindings.findByResource(RESOURCE_TYPE, resourceId);
        requireExistingProof(completeProof, assetIds, userId, contextHash);
        return new VerificationProof(resourceId, validFrom, expiresAt);
    }

    private MediaBindingAsset requiredReference(long assetId, List<MediaBindingDO> existing) {
        long generation = 0;
        if (!existing.isEmpty()) {
            generation = existing.stream().filter(row -> Objects.equals(row.getAssetId(), assetId)).findFirst()
                    .map(MediaBindingDO::getAssetGeneration)
                    .orElseThrow(() -> new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND));
        }
        MediaAssetDO asset = assets.find(assetId);
        if (asset == null || asset.getGeneration() == null || asset.getGeneration() <= 0) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        // This read only supplies the expected generation. MediaBindingService locks the row and
        // checks it again, so a renewal race fails closed instead of validating stale bytes.
        if (generation == 0) {
            generation = asset.getGeneration();
        }
        return new MediaBindingAsset(assetId, generation, asset.getVersion());
    }

    private static MediaBindingDO requireExistingProof(List<MediaBindingDO> rows, List<Long> expectedAssetIds,
                                                       long userId, String contextHash) {
        if (rows.size() != expectedAssetIds.size()) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND);
        }
        List<Long> actualAssetIds = rows.stream().map(MediaBindingDO::getAssetId).sorted().toList();
        if (!actualAssetIds.equals(expectedAssetIds)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND);
        }
        MediaBindingDO first = rows.get(0);
        boolean consistent = rows.stream().allMatch(row -> Objects.equals(row.getOwnerId(), userId)
                && PURPOSE.equals(row.getPurpose())
                && RESOURCE_TYPE.equals(row.getResourceType())
                && Objects.equals(row.getResourceId(), first.getResourceId())
                && Objects.equals(row.getContextHash(), contextHash)
                && Objects.equals(row.getValidFrom(), first.getValidFrom())
                && Objects.equals(row.getExpiresAt(), first.getExpiresAt()));
        if (!consistent) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND);
        }
        return first;
    }

    private static List<Long> parseAssetIds(List<String> evidenceRefs) {
        List<String> refs = Objects.requireNonNull(evidenceRefs, "evidenceRefs");
        if (refs.isEmpty() || refs.size() > 10) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        try {
            List<Long> result = refs.stream().map(ref -> {
                if (ref == null || !ref.matches("[1-9][0-9]{0,18}")) {
                    throw new NumberFormatException("not a canonical asset id");
                }
                return Long.parseLong(ref);
            }).sorted(Comparator.naturalOrder()).toList();
            if (result.stream().distinct().count() != result.size()) {
                throw new NumberFormatException("duplicate asset id");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
    }

    private static String canonicalContext(long userId, long schoolId, long campusId, String method,
                                           List<Long> assetIds) {
        return "v1|" + userId + '|' + schoolId + '|' + campusId + '|' + method + '|'
                + assetIds.stream().map(String::valueOf).reduce((left, right) -> left + ',' + right).orElseThrow();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("membership evidence binding requires an active transaction");
        }
    }
}
