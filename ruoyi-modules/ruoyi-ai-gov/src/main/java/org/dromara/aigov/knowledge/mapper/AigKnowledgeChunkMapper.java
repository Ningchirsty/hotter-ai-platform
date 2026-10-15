package org.dromara.aigov.knowledge.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识分段 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigKnowledgeChunkMapper extends BaseMapperPlus<AigKnowledgeChunk, AigKnowledgeChunk> {

    /**
     * 物理删除某版本的全部分段（重建索引前清场）。
     *
     * <p><b>为什么是物理删除，而不是走 {@code @TableLogic} 的逻辑删除</b>：
     * {@code uk_aig_knowledge_chunk (document_version_id, ordinal)} 这条唯一键<b>不含 {@code del_flag}</b>，
     * 逻辑删除后行还在表里，重建索引插入同样的 {@code ordinal} 会直接撞唯一键——表现在
     * "删了却重建不了"。要么改唯一键（影响面大、且会放宽"同版本段序唯一"这条不变式），
     * 要么在这里真删。</p>
     *
     * <p>为什么真删是安全的：调用点只在"重建索引"里，紧接着就会写入该版本的新分段；
     * 而且删除条件必须带 {@code document_version_id}（见方法签名），不可能误删别的版本。
     * 版本级的追溯信息在 {@code aig_knowledge_processing_job} 与审计里，不依赖分段行本身。</p>
     *
     * @param versionId 文档版本ID
     * @return 删除行数
     */
    @Delete("delete from aig_knowledge_chunk where document_version_id = #{versionId}")
    int deletePhysicallyByVersionId(@Param("versionId") Long versionId);

}
