package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.api.StorageWriteResult;
import cn.iocoder.yudao.module.campus.media.domain.MediaAssetStatus;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class MediaUploadService {

    private final MediaAssetRepository repository;
    private final MediaStoragePort storage;
    private final UploadTokenCodec tokenCodec;
    private final Clock clock;

    public MediaUploadService(MediaAssetRepository repository, MediaStoragePort storage,
                              UploadTokenCodec tokenCodec, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.storage = Objects.requireNonNull(storage);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.clock = Objects.requireNonNull(clock);
    }

    public UploadContentResult upload(long ownerId, long assetId, String uploadToken, String contentType,
                                      long contentLength, InputStream content) {
        MediaAssetDO asset = repository.find(assetId);
        authorize(asset, ownerId, uploadToken, contentType, contentLength);
        try {
            StorageWriteResult stored = storage.putImmutable(asset.getObjectKey(), content, asset.getDeclaredSize());
            if (stored.bytesWritten() != asset.getDeclaredSize()) {
                storage.delete(asset.getObjectKey());
                throw new CampusApiException(CampusErrorCodes.FILE_INVALID);
            }
            return new UploadContentResult(String.valueOf(asset.getId()), asset.getGeneration(),
                    stored.bytesWritten(), asset.getStatus());
        } catch (MediaStorageException exception) {
            if (exception.reason() == MediaStorageException.Reason.TOO_LARGE) {
                throw new CampusApiException(CampusErrorCodes.FILE_SIZE_EXCEEDED);
            }
            if (exception.reason() == MediaStorageException.Reason.ALREADY_EXISTS) {
                throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
            }
            throw new CampusApiException(CampusErrorCodes.STORAGE_UNAVAILABLE);
        }
    }

    private void authorize(MediaAssetDO asset, long ownerId, String token, String contentType, long contentLength) {
        if (ownerId <= 0 || asset == null || asset.getOwnerId() != ownerId || !tokenCodec.matches(asset, token)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_OWNED);
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!MediaAssetStatus.UPLOADING.name().equals(asset.getStatus())
                || !asset.getTicketExpiresAt().isAfter(now)) {
            throw new CampusApiException(CampusErrorCodes.MEDIA_NOT_READY);
        }
        if (!asset.getDeclaredMime().equalsIgnoreCase(Objects.requireNonNullElse(contentType, ""))) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        if (contentLength < 0 || contentLength > asset.getDeclaredSize()) {
            throw new CampusApiException(CampusErrorCodes.FILE_SIZE_EXCEEDED);
        }
    }
}
