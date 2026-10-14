package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.knowledge.domain.AigKnowledgeProcessingJob;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识处理任务 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeProcessingJobMapper
    extends BaseMapperPlus<AigKnowledgeProcessingJob, AigKnowledgeProcessingJob> {
}
