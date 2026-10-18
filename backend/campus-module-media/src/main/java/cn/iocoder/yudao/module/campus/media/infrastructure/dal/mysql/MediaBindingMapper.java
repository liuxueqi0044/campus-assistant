package cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaBindingMapper extends BaseMapperX<MediaBindingDO> {

    @Select("SELECT * FROM campus_media_binding WHERE asset_id = #{assetId} AND deleted = b'0'")
    MediaBindingDO selectByAssetId(@Param("assetId") long assetId);

    @Select("""
            <script>
            SELECT * FROM campus_media_binding
            WHERE asset_id IN
            <foreach collection="assetIds" item="assetId" open="(" separator="," close=")">
                #{assetId}
            </foreach>
              AND deleted = b'0'
            ORDER BY asset_id
            </script>
            """)
    List<MediaBindingDO> selectByAssetIds(@Param("assetIds") List<Long> assetIds);

    @Select("""
            SELECT * FROM campus_media_binding
            WHERE resource_type = #{resourceType} AND resource_id = #{resourceId} AND deleted = b'0'
            ORDER BY asset_id
            """)
    List<MediaBindingDO> selectByResource(@Param("resourceType") String resourceType,
                                          @Param("resourceId") String resourceId);

    @Insert("""
            INSERT INTO campus_media_binding
                (asset_id, asset_generation, owner_id, purpose, resource_type, resource_id, context_hash,
                 valid_from, expires_at, version, creator, updater)
            VALUES
                (#{assetId}, #{assetGeneration}, #{ownerId}, #{purpose}, #{resourceType}, #{resourceId}, #{contextHash},
                 #{validFrom}, #{expiresAt}, #{version}, CAST(#{ownerId} AS CHAR), CAST(#{ownerId} AS CHAR))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBinding(MediaBindingDO binding);
}
