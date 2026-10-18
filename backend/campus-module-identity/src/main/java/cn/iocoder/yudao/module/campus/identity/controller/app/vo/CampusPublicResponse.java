package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Public, non-sensitive representation of a campus. */
@Data
@AllArgsConstructor
public class CampusPublicResponse {

    @CampusIdString
    private String schoolId;
    @CampusIdString
    private String campusId;
    private String name;
    private String timezone;
    private String status;
    private String configVersion;
}
