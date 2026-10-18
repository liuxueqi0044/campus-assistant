package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import java.util.List;

public record CampusAdminContextResponse(String operatorId, long authorizationVersion,
                                         List<String> permissions, List<SchoolScope> schools,
                                         List<String> menus) {
    public record SchoolScope(String schoolId, String name, List<String> actions,
                               List<CampusScope> campuses) {
    }

    public record CampusScope(String campusId, String name, List<String> actions) {
    }
}
