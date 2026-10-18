package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorAuthorizationVersionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OperatorAuthorizationVersionMapper extends BaseMapperX<OperatorAuthorizationVersionDO> {

    @Insert("""
            INSERT INTO campus_operator_authorization_version(operator_id, version)
            VALUES (#{operatorId}, 1)
            ON DUPLICATE KEY UPDATE version = version + 1
            """)
    int bump(@Param("operatorId") long operatorId);

    @Select("SELECT version FROM campus_operator_authorization_version WHERE operator_id = #{operatorId}")
    Long selectVersion(@Param("operatorId") long operatorId);
}
