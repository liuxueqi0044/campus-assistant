package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.domain.MediaAssetStatus;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Completion performs storage I/O outside a database transaction and commits only CAS-guarded results. */
@Service
public class MediaCompletionService {

    private final MediaAssetRepository repository;
    private final MediaStoragePort storage;
    private final FileValidationPipeline validation;
    private final Clock clock;

    public MediaCompletionService(MediaAssetRepository repository, MediaStoragePort storage,
                                  FileValidationPipeline validation, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.storage = Objects.requireNonNull(storage);
        this.validation = Objects.requireNonNull(validation);
        this.clock = Objects.requireNonNull(clock);
    }

    public MediaCompletionResult complete(long ownerId, long assetId, long generation) {
        if (ownerId <= 0 || assetId <= 0 || generation <= 0) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        MediaAssetDO asset = ownedGeneration(repository.find(assetId), ownerId, generation);
        MediaCompletionResult terminal = terminal(asset, generation);
        if (terminal != null) {
            return terminal;
        }
        if (MediaAssetStatus.UPLOADING.name().equals(asset.getStatus())) {
            repository.beginValidation(assetId, generation, asset.getVersion());
            asset = ownedGeneration(repository.find(assetId), ownerId, generation);
            terminal = terminal(asset, generation);
            if (terminal != null) {
                return terminal;
            }
        }
        if (!MediaAssetStatus.VALIDATING.name().equals(asset.getStatus())) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }

        FileValidationPipeline.ValidationResult result;
        try {
            result = validation.validate(storage, asset.getObjectKey(), asset.getDeclaredSize(), asset.getDeclaredMime());
        } catch (MediaStorageException exception) {
            if (exception.reason() == MediaStorageException.Reason.NOT_FOUND) {
                throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
            }
            // A transient storage failure must leave the asset retryable, never turn it into REJECTED.
            throw new CampusApiException(CampusErrorCodes.STORAGE_UNAVAILABLE);
        }
        String target = result.valid() ? MediaAssetStatus.VALID.name() : MediaAssetStatus.REJECTED.name();
        boolean committed = repository.submitValidation(asset.getId(), generation, asset.getVersion(), target,
                result.actualMime(), result.actualSize(), result.sha256(), now(), result.failure());
        MediaAssetDO current = repository.find(assetId);
        if (!committed && current == null) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        MediaCompletionResult currentTerminal = terminal(current, generation);
        if (currentTerminal != null) {
            return currentTerminal;
        }
        throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
    }

    private static MediaAssetDO ownedGeneration(MediaAssetDO asset, long ownerId, long generation) {
        if (asset == null || !Objects.equals(asset.getOwnerId(), ownerId)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_OWNED);
        }
        if (!Objects.equals(asset.getGeneration(), generation)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        return asset;
    }

    private static MediaCompletionResult terminal(MediaAssetDO asset, long generation) {
        if (asset == null || !Objects.equals(asset.getValidatedGeneration(), generation)) {
            return null;
        }
        if (MediaAssetStatus.VALID.name().equals(asset.getStatus())
                || MediaAssetStatus.REJECTED.name().equals(asset.getStatus())) {
            return new MediaCompletionResult(String.valueOf(asset.getId()), generation, asset.getStatus(),
                    asset.getVersion());
        }
        return null;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
