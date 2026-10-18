package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("campus_verification")
@Data
@EqualsAndHashCode(callSuper = true)
public class VerificationDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private Long schoolId;
    private Long campusId;
    private String method;
    private String evidenceRefsJson;
    private String proofReference;
    private String status;
    private Long version;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String reviewReason;
    private LocalDateTime validFrom;
    private LocalDateTime expiresAt;
    @TableField(exist = false)
    private Integer pendingSlot;
}
