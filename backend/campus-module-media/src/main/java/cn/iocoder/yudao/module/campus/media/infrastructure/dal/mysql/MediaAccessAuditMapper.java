package cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface MediaAccessAuditMapper extends BaseMapperX<MediaAccessAuditDO> {

    @Insert("""
            INSERT INTO campus_media_access_log
                (actor_type, actor_id, asset_id, asset_generation, asset_version, binding_id, resource_type,
                 resource_id, action, purpose, decision, case_reference, reason, audit_labels_json, trace_id,
                 occurred_at, creator, updater)
            VALUES
                (#{actorType}, #{actorId}, #{assetId}, #{assetGeneration}, #{assetVersion}, #{bindingId}, #{resourceType},
                 #{resourceId}, #{action}, #{purpose}, #{decision}, #{caseReference}, #{reason}, #{auditLabelsJson},
                 #{traceId}, #{occurredAt}, CAST(#{actorId} AS CHAR), CAST(#{actorId} AS CHAR))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAudit(MediaAccessAuditDO audit);
}
