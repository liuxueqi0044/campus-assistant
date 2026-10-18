package cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaDeliveryGrantDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface MediaDeliveryGrantMapper extends BaseMapperX<MediaDeliveryGrantDO> {

    @Insert("""
            INSERT INTO campus_media_delivery_grant
                (token_hash, actor_type, actor_id, asset_id, asset_generation, asset_version, binding_id,
                 case_reference, reason, expires_at, version, creator, updater)
            VALUES
                (#{tokenHash}, #{actorType}, #{actorId}, #{assetId}, #{assetGeneration}, #{assetVersion}, #{bindingId},
                 #{caseReference}, #{reason}, #{expiresAt}, #{version}, CAST(#{actorId} AS CHAR), CAST(#{actorId} AS CHAR))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGrant(MediaDeliveryGrantDO grant);

    @Select("""
            SELECT * FROM campus_media_delivery_grant
            WHERE token_hash = #{tokenHash} AND deleted = b'0' AND expires_at > #{now}
            """)
    MediaDeliveryGrantDO selectActiveByTokenHash(@Param("tokenHash") String tokenHash,
                                                 @Param("now") LocalDateTime now);
}
