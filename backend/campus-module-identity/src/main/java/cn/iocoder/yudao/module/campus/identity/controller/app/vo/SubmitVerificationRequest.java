package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import cn.iocoder.yudao.module.campus.common.api.json.CampusIdStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Request contains opaque media identifiers only; certificate content and URLs never cross this boundary. */
@Data
public class SubmitVerificationRequest {

    @CampusIdString
    @NotBlank(message = "schoolId 不能为空")
    @Pattern(regexp = "[1-9][0-9]*", message = "schoolId 必须是正整数字符串")
    private String schoolId;

    @CampusIdString
    @NotBlank(message = "campusId 不能为空")
    @Pattern(regexp = "[1-9][0-9]*", message = "campusId 必须是正整数字符串")
    private String campusId;

    @NotNull(message = "method 不能为空")
    private VerificationMethod method;

    @NotEmpty(message = "evidenceRefs 不能为空")
    @Size(max = 10, message = "evidenceRefs 最多 10 项")
    @JsonDeserialize(contentUsing = CampusIdStringDeserializer.class)
    private List<@NotBlank(message = "evidenceRefs 不能包含空值")
                 @Size(max = 128, message = "evidenceRefs 单项长度不能超过 128")
                 @Pattern(regexp = "^(?!.*://).+$", message = "evidenceRefs 必须是媒体 ID，不能是 URL") String> evidenceRefs;
}
