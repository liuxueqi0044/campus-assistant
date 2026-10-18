package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("campus_verification_review")
@Data
public class VerificationReviewDO {

    @TableId
    private Long id;
    private Long verificationId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorId;
    private String reason;
    private Long verificationVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
