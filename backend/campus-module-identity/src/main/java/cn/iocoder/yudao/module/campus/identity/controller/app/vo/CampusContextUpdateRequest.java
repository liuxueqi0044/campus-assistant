package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** A display choice only. It deliberately has no tenant, role, or membership field. */
@Data
public class CampusContextUpdateRequest {

    @NotBlank(message = "schoolId 不能为空")
    @CampusIdString
    private String schoolId;

    @NotBlank(message = "campusId 不能为空")
    @CampusIdString
    private String campusId;
}
