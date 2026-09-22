package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpOutputCheck;
import org.dromara.content.domain.vo.CpOutputCheckVo;

/**
 * 成品一致性检查 Mapper 接口
 *
 * <p>无需 XML：字段与列名一一对应，MyBatis-Plus 通用方法即可覆盖；
 * 新增列（如后续的差异定位坐标）也会被自动映射，不必同步改映射文件。</p>
 *
 * @author content
 */
@Mapper
public interface CpOutputCheckMapper extends BaseMapperPlus<CpOutputCheck, CpOutputCheckVo> {

}
