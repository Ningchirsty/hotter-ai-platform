package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.vo.CpTaskFileVo;

/**
 * 任务附件 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpTaskFileMapper extends BaseMapperPlus<CpTaskFile, CpTaskFileVo> {

}
