package org.dromara.aigov.workspace.launch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTemplate;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 启动任务模板 Mapper（设计 D11）。
 *
 * @author ai-gov
 */
@Mapper
public interface AigLaunchTemplateMapper extends BaseMapperPlus<AigLaunchTemplate, AigLaunchTemplate> {
}
