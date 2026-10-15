package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识集合 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeCollectionMapper
    extends BaseMapperPlus<AigKnowledgeCollection, AigKnowledgeCollection> {
}
