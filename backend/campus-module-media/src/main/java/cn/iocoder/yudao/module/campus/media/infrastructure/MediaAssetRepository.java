package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class MediaAssetRepository {

    private final MediaAssetMapper mapper;

    public void reserve(MediaAssetDO asset) {
        mapper.reserve(asset);
    }

    public MediaAssetDO lockByTicket(long ownerId, String keyHash) {
        return mapper.selectByTicketForUpdate(ownerId, keyHash);
    }

    public MediaAssetDO findByTicket(long ownerId, String keyHash) {
        return mapper.selectByTicket(ownerId, keyHash);
    }

    public MediaAssetDO find(long id) {
        return mapper.selectAsset(id);
    }

    public MediaAssetDO lock(long id) {
        return mapper.selectAssetForUpdate(id);
    }

    public long countActiveTickets(long ownerId, String purpose, LocalDateTime now) {
        return mapper.countActiveTickets(ownerId, purpose, now);
    }

    public boolean renew(long id, long ownerId, String objectKey, String nonce,
                         LocalDateTime expiresAt, long expectedVersion) {
        return mapper.renew(id, ownerId, objectKey, nonce, expiresAt, expectedVersion) == 1;
    }

    public boolean beginValidation(long id, long generation, long expectedVersion) {
        return mapper.beginValidation(id, generation, expectedVersion) == 1;
    }

    public boolean submitValidation(long id, long generation, long expectedVersion, String status,
                                    String actualMime, long actualSize, String sha256,
                                    LocalDateTime validatedAt, String validationFailure) {
        return mapper.submitValidation(id, generation, expectedVersion, status, actualMime, actualSize,
                sha256, validatedAt, validationFailure) == 1;
    }
}
