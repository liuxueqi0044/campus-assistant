package cn.iocoder.yudao.module.campus.identity.api;

/**
 * 已由服务端目录验证的公开浏览范围。
 *
 * <p>它不携带 membership、角色或任何授权结论；业务域仍须自行进行私有资源授权。</p>
 */
public record CampusPublicScope(Long schoolId, Long campusId, Long schoolConfigVersion, Long campusConfigVersion) {
}
