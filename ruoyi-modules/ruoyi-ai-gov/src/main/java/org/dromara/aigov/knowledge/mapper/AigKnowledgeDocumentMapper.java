package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识文档 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeDocumentMapper
    extends BaseMapperPlus<AigKnowledgeDocument, AigKnowledgeDocument> {
}
