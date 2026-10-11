package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;
import org.dromara.aigov.studio.domain.vo.AigStudioActionDraftVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 快捷指令草案 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioActionDraftMapper
    extends BaseMapperPlus<AigStudioActionDraft, AigStudioActionDraftVo> {
}
