package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigSandboxArtifactDownload;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactDownloadVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 沙箱产物下载账本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigSandboxArtifactDownloadMapper
    extends BaseMapperPlus<AigSandboxArtifactDownload, AigSandboxArtifactDownloadVo> {
}
