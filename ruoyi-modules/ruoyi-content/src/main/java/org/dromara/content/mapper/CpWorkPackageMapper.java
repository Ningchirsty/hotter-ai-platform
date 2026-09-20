package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpWorkPackage;
import org.dromara.content.domain.vo.CpWorkPackageVo;

/**
 * 设计开工包 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpWorkPackageMapper extends BaseMapperPlus<CpWorkPackage, CpWorkPackageVo> {

}
