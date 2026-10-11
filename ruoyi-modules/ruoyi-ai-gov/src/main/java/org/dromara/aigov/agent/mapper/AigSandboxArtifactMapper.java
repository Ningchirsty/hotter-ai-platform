package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigSandboxArtifact;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 沙箱产物账本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigSandboxArtifactMapper
    extends BaseMapperPlus<AigSandboxArtifact, AigSandboxArtifactVo> {
}
