package cn.iocoder.yudao.module.campus.identity.api;

/** M01.02 提供给其他校园业务模块的范围校验入口。 */
public interface CampusScopeApi {

    /** 校验二者存在、启用且校区确属该学校，失败时抛出稳定校园错误。 */
    void assertCampusBelongsToSchool(Long schoolId, Long campusId);

    /** 返回已校验的公开范围；不能被客户端 tenant/header 替代。 */
    CampusPublicScope resolvePublicScope(Long schoolId, Long campusId);
}
