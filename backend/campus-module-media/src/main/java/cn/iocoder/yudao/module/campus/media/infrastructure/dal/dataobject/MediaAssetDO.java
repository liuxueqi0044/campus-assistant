package cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("campus_media_asset")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaAssetDO extends BaseDO {

    @TableId
    private Long id;
    private Long ownerId;
    private String purpose;
    private String originalFilename;
    private String declaredMime;
    private Long declaredSize;
    private String objectKey;
    private Long generation;
    private String status;
    private String idempotencyKeyHash;
    private String requestHash;
    private String ticketNonce;
    private LocalDateTime ticketExpiresAt;
    private String actualMime;
    private Long actualSize;
    private String contentSha256;
    private Long validatedGeneration;
    private LocalDateTime validatedAt;
    private String validationFailure;
    private Long version;
}
