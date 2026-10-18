package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Public directory representation; no operator contact, membership, or private profile fields. */
@Data
@AllArgsConstructor
public class SchoolPublicResponse {

    @CampusIdString
    private String schoolId;
    private String name;
    private String timezone;
    private String status;
    private String configVersion;
    private List<CampusPublicResponse> campuses;
}
