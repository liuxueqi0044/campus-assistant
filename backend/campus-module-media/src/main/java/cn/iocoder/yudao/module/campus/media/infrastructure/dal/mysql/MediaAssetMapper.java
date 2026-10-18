package cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MediaAssetMapper extends BaseMapperX<MediaAssetDO> {

    @Insert("""
            INSERT INTO campus_media_asset
                (owner_id, purpose, original_filename, declared_mime, declared_size, object_key,
                 generation, status, idempotency_key_hash, request_hash, ticket_nonce,
                 ticket_expires_at, version, creator, updater)
            VALUES
                (#{ownerId}, #{purpose}, #{originalFilename}, #{declaredMime}, #{declaredSize}, #{objectKey},
                 #{generation}, #{status}, #{idempotencyKeyHash}, #{requestHash}, #{ticketNonce},
                 #{ticketExpiresAt}, #{version}, CAST(#{ownerId} AS CHAR), CAST(#{ownerId} AS CHAR))
            ON DUPLICATE KEY UPDATE id = id
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int reserve(MediaAssetDO asset);

    @Select("""
            SELECT * FROM campus_media_asset
            WHERE owner_id = #{ownerId} AND idempotency_key_hash = #{keyHash} AND deleted = b'0'
            FOR UPDATE
            """)
    MediaAssetDO selectByTicketForUpdate(@Param("ownerId") long ownerId, @Param("keyHash") String keyHash);

    @Select("""
            SELECT * FROM campus_media_asset
            WHERE owner_id = #{ownerId} AND idempotency_key_hash = #{keyHash} AND deleted = b'0'
            """)
    MediaAssetDO selectByTicket(@Param("ownerId") long ownerId, @Param("keyHash") String keyHash);

    @Select("SELECT * FROM campus_media_asset WHERE id = #{id} AND deleted = b'0'")
    MediaAssetDO selectAsset(@Param("id") long id);

    @Select("SELECT * FROM campus_media_asset WHERE id = #{id} AND deleted = b'0' FOR UPDATE")
    MediaAssetDO selectAssetForUpdate(@Param("id") long id);

    @Select("""
            SELECT COUNT(*) FROM campus_media_asset
            WHERE owner_id = #{ownerId} AND purpose = #{purpose} AND status = 'UPLOADING'
              AND ticket_expires_at > #{now} AND deleted = b'0'
            """)
    long countActiveTickets(@Param("ownerId") long ownerId, @Param("purpose") String purpose,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE campus_media_asset
            SET object_key = #{objectKey}, generation = generation + 1, ticket_nonce = #{nonce},
                ticket_expires_at = #{expiresAt}, version = version + 1, updater = CAST(#{ownerId} AS CHAR)
            WHERE id = #{id} AND owner_id = #{ownerId} AND status = 'UPLOADING'
              AND version = #{expectedVersion} AND deleted = b'0'
            """)
    int renew(@Param("id") long id, @Param("ownerId") long ownerId,
              @Param("objectKey") String objectKey, @Param("nonce") String nonce,
              @Param("expiresAt") LocalDateTime expiresAt, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE campus_media_asset
            SET status = 'VALIDATING', version = version + 1
            WHERE id = #{id} AND generation = #{generation} AND status = 'UPLOADING'
              AND version = #{expectedVersion} AND deleted = b'0'
            """)
    int beginValidation(@Param("id") long id, @Param("generation") long generation,
                        @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE campus_media_asset
            SET status = #{status}, actual_mime = #{actualMime}, actual_size = #{actualSize},
                content_sha256 = #{contentSha256}, validated_generation = #{generation},
                validated_at = #{validatedAt}, validation_failure = #{validationFailure}, version = version + 1
            WHERE id = #{id} AND generation = #{generation} AND status = 'VALIDATING'
              AND version = #{expectedVersion} AND deleted = b'0'
            """)
    int submitValidation(@Param("id") long id, @Param("generation") long generation,
                         @Param("expectedVersion") long expectedVersion, @Param("status") String status,
                         @Param("actualMime") String actualMime, @Param("actualSize") long actualSize,
                         @Param("contentSha256") String contentSha256, @Param("validatedAt") LocalDateTime validatedAt,
                         @Param("validationFailure") String validationFailure);
}
