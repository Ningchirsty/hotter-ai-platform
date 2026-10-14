package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识分段 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeChunkMapper extends BaseMapperPlus<AigKnowledgeChunk, AigKnowledgeChunk> {
}
