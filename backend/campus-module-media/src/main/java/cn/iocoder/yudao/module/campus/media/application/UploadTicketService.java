package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotentCommand;
import cn.iocoder.yudao.module.campus.media.domain.MediaAssetStatus;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class UploadTicketService {

    static final int MAX_ACTIVE_TICKETS = 8;
    static final Duration TICKET_LIFETIME = Duration.ofMinutes(10);

    private final MediaAssetRepository repository;
    private final UploadPolicy policy;
    private final UploadTokenCodec tokenCodec;
    private final Clock clock;

    public UploadTicketService(MediaAssetRepository repository, UploadPolicy policy,
                               UploadTokenCodec tokenCodec, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public UploadTicketResult issue(long ownerId, UploadTicketCommand command) {
        if (ownerId <= 0 || command == null) {
            throw new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
        }
        UploadPolicy.PolicyDecision decision = policy.requireAllowed(command.purpose(), command.fileName(),
                command.size(), command.mime());
        String idempotencyKey = requiredKey(command.idempotencyKey());
        String keyHash = MediaHashing.sha256(idempotencyKey);
        String requestHash = requestHash(decision);
        LocalDateTime now = utc(clock.instant());

        MediaAssetDO existing = repository.findByTicket(ownerId, keyHash);
        if (existing == null && repository.countActiveTickets(ownerId, decision.purpose().name(), now)
                >= MAX_ACTIVE_TICKETS) {
            throw new CampusApiException(CampusErrorCodes.UPLOAD_QUOTA_EXCEEDED);
        }
        if (existing == null) {
            repository.reserve(newAsset(ownerId, keyHash, requestHash, decision, now));
        }
        MediaAssetDO asset = repository.lockByTicket(ownerId, keyHash);
        if (asset == null) {
            throw new CampusApiException(CampusErrorCodes.STORAGE_UNAVAILABLE);
        }
        if (!requestHash.equals(asset.getRequestHash())) {
            throw new CampusApiException(CampusErrorCodes.IDEMPOTENCY_CONFLICT);
        }
        if (!MediaAssetStatus.UPLOADING.name().equals(asset.getStatus())) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        if (!asset.getTicketExpiresAt().isAfter(now)) {
            renew(asset, now);
            asset = repository.lockByTicket(ownerId, keyHash);
        }
        return result(asset);
    }

    private void renew(MediaAssetDO asset, LocalDateTime now) {
        String nextKey = objectKey(asset.getPurpose(), asset.getGeneration() + 1);
        boolean renewed = repository.renew(asset.getId(), asset.getOwnerId(), nextKey, nonce(),
                now.plus(TICKET_LIFETIME), asset.getVersion());
        if (!renewed) {
            throw new CampusApiException(CampusErrorCodes.VERSION_CONFLICT);
        }
    }

    private MediaAssetDO newAsset(long ownerId, String keyHash, String requestHash,
                                  UploadPolicy.PolicyDecision decision, LocalDateTime now) {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setOwnerId(ownerId);
        asset.setPurpose(decision.purpose().name());
        asset.setOriginalFilename(decision.fileName());
        asset.setDeclaredMime(decision.mime());
        asset.setDeclaredSize(decision.size());
        asset.setObjectKey(objectKey(decision.purpose().name(), 1));
        asset.setGeneration(1L);
        asset.setStatus(MediaAssetStatus.UPLOADING.name());
        asset.setIdempotencyKeyHash(keyHash);
        asset.setRequestHash(requestHash);
        asset.setTicketNonce(nonce());
        asset.setTicketExpiresAt(now.plus(TICKET_LIFETIME));
        asset.setVersion(1L);
        return asset;
    }

    private UploadTicketResult result(MediaAssetDO asset) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", asset.getDeclaredMime());
        headers.put("X-Upload-Token", tokenCodec.issue(asset));
        return new UploadTicketResult(String.valueOf(asset.getId()), asset.getGeneration(), "PROXY_PUT",
                "/app-api/campus/v1/media/assets/" + asset.getId() + "/content",
                asset.getTicketExpiresAt().toInstant(ZoneOffset.UTC), Map.copyOf(headers));
    }

    private static String requiredKey(String value) {
        if (value == null || value.isBlank() || value.length() > IdempotentCommand.KEY_MAX_LENGTH) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        return value.trim();
    }

    private static String requestHash(UploadPolicy.PolicyDecision decision) {
        return MediaHashing.sha256(decision.purpose() + "\n" + decision.fileName() + "\n"
                + decision.size() + "\n" + decision.mime());
    }

    private static String objectKey(String purpose, long generation) {
        return "private/" + purpose.toLowerCase(java.util.Locale.ROOT) + "/g" + generation + "/"
                + UUID.randomUUID();
    }

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
