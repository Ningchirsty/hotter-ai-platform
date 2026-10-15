package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识文档版本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeDocumentVersionMapper
    extends BaseMapperPlus<AigKnowledgeDocumentVersion, AigKnowledgeDocumentVersion> {
}
