package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 训练草稿 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioDraftMapper extends BaseMapperPlus<AigStudioDraft, AigStudioDraftVo> {

}
