package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.CampusDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CampusMapper extends BaseMapperX<CampusDO> {
}
