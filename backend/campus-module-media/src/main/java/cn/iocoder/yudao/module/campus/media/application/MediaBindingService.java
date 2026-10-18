package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.domain.MediaAssetStatus;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The cross-domain caller owns the transaction so resource creation and every asset binding commit
 * or roll back together. Asset rows are locked in ascending order to avoid lock-order inversions.
 */
@Service
public class MediaBindingService {

    private final MediaAssetRepository assets;
    private final MediaBindingRepository bindings;

    public MediaBindingService(MediaAssetRepository assets, MediaBindingRepository bindings) {
        this.assets = Objects.requireNonNull(assets);
        this.bindings = Objects.requireNonNull(bindings);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MediaBindingBatchResult bind(long ownerId, MediaBindingCommand command) {
        if (ownerId <= 0) {
            throw new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
        }
        ValidatedCommand input = validate(command);
        List<MediaBindingAsset> ordered = input.assets().stream()
                .sorted(Comparator.comparingLong(MediaBindingAsset::assetId)).toList();
        List<MediaBindingResult> result = ordered.stream()
                .map(asset -> bindOne(ownerId, asset, input))
                .toList();
        return new MediaBindingBatchResult(result);
    }

    private MediaBindingResult bindOne(long ownerId, MediaBindingAsset reference, ValidatedCommand input) {
        MediaAssetDO asset = assets.lock(reference.assetId());
        assertBindable(asset, ownerId, reference, input.purpose());
        MediaBindingDO existing = bindings.findByAssetId(reference.assetId());
        if (existing != null) {
            if (!sameBinding(existing, ownerId, reference.generation(), input)) {
                throw new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND);
            }
            return result(asset, existing);
        }
        MediaBindingDO binding = new MediaBindingDO();
        binding.setAssetId(asset.getId());
        binding.setAssetGeneration(reference.generation());
        binding.setOwnerId(ownerId);
        binding.setPurpose(input.purpose());
        binding.setResourceType(input.resourceType());
        binding.setResourceId(input.resourceId());
        binding.setContextHash(input.contextHash());
        binding.setValidFrom(local(input.validFrom()));
        binding.setExpiresAt(local(input.expiresAt()));
        binding.setVersion(1L);
        try {
            bindings.insert(binding);
        } catch (DuplicateKeyException exception) {
            MediaBindingDO concurrent = bindings.findByAssetId(reference.assetId());
            if (concurrent != null && sameBinding(concurrent, ownerId, reference.generation(), input)) {
                return result(asset, concurrent);
            }
            throw new CampusApiException(CampusErrorCodes.MEDIA_ALREADY_BOUND);
        }
        return result(asset, binding);
    }

    private static void assertBindable(MediaAssetDO asset, long ownerId, MediaBindingAsset reference, String purpose) {
        if (asset == null || !Objects.equals(asset.getOwnerId(), ownerId)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_OWNED);
        }
        if (!Objects.equals(asset.getPurpose(), purpose)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_PURPOSE_MISMATCH);
        }
        if (!Objects.equals(asset.getGeneration(), reference.generation())
                || !Objects.equals(asset.getValidatedGeneration(), reference.generation())) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        if (reference.expectedVersion() > 0 && !Objects.equals(asset.getVersion(), reference.expectedVersion())) {
            throw new CampusApiException(CampusErrorCodes.VERSION_CONFLICT);
        }
        if (MediaAssetStatus.REJECTED.name().equals(asset.getStatus())) {
            throw new CampusApiException(CampusErrorCodes.FILE_INVALID);
        }
        if (!MediaAssetStatus.VALID.name().equals(asset.getStatus())) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
    }

    private static boolean sameBinding(MediaBindingDO row, long ownerId, long generation, ValidatedCommand input) {
        return Objects.equals(row.getAssetGeneration(), generation)
                && Objects.equals(row.getOwnerId(), ownerId)
                && Objects.equals(row.getPurpose(), input.purpose())
                && Objects.equals(row.getResourceType(), input.resourceType())
                && Objects.equals(row.getResourceId(), input.resourceId())
                && Objects.equals(row.getContextHash(), input.contextHash());
    }

    private static MediaBindingResult result(MediaAssetDO asset, MediaBindingDO binding) {
        return new MediaBindingResult(String.valueOf(asset.getId()), String.valueOf(binding.getId()),
                binding.getAssetGeneration(), asset.getVersion(), instant(binding.getValidFrom()),
                instant(binding.getExpiresAt()));
    }

    private static ValidatedCommand validate(MediaBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String purpose = required(command.purpose(), 48, "purpose");
        String resourceType = required(command.resourceType(), 64, "resourceType");
        String resourceId = required(command.resourceId(), 128, "resourceId");
        String context = required(command.context(), 4096, "context");
        Instant validFrom = Objects.requireNonNull(command.validFrom(), "validFrom");
        Instant expiresAt = Objects.requireNonNull(command.expiresAt(), "expiresAt");
        if (!expiresAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("expiresAt must be after validFrom");
        }
        List<MediaBindingAsset> refs = Objects.requireNonNull(command.assets(), "assets");
        if (refs.isEmpty() || refs.size() > 10 || refs.stream().anyMatch(ref -> ref == null
                || ref.assetId() <= 0 || ref.generation() <= 0 || ref.expectedVersion() < 0)
                || refs.stream().map(MediaBindingAsset::assetId).distinct().count() != refs.size()) {
            throw new IllegalArgumentException("assets must contain 1..10 unique positive references");
        }
        return new ValidatedCommand(purpose, resourceType, resourceId, MediaHashing.sha256(context), validFrom,
                expiresAt, List.copyOf(refs));
    }

    private static String required(String raw, int maxLength, String field) {
        if (raw == null || raw.isBlank() || raw.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1.." + maxLength + " characters");
        }
        return raw.trim();
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private record ValidatedCommand(String purpose, String resourceType, String resourceId, String contextHash,
                                    Instant validFrom, Instant expiresAt, List<MediaBindingAsset> assets) {
    }
}
