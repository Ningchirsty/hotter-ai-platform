package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioChangeProposal;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 训练草稿变更提案 Mapper（增量 21／附件 07 增量 6）。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioChangeProposalMapper
    extends BaseMapperPlus<AigStudioChangeProposal, AigStudioChangeProposalVo> {

}
