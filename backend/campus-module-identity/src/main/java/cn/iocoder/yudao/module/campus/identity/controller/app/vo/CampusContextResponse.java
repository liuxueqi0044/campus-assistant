package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Public browsing context. The deliberately small shape is a security boundary: selecting it
 * cannot imply membership, role, verification, or access to a private resource.
 */
@Data
@AllArgsConstructor
public class CampusContextResponse {

    private boolean loggedIn;
    private String browseMode;
    private SchoolPublicResponse school;
    private CampusPublicResponse campus;
}
