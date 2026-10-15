package org.dromara.aigov.knowledge.service.impl;

import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeIndexingVo;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeProcessingJobMapper;
import org.dromara.aigov.knowledge.pipeline.AigKnowledgePipelineException;
import org.dromara.aigov.knowledge.pipeline.AigKnowledgeProcessor;
import org.dromara.aigov.knowledge.pipeline.KnowledgeVectorIndexPort;
import org.dromara.aigov.knowledge.service.IAigKnowledgeService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识索引服务测试。
 *
 * <p>钉住四件事：状态推进顺序（干完活才记成功）、分段落库内容、向量索引用主键、
 * 失败要同时落库与抛错。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeIndexingServiceImplTest {

    private IAigKnowledgeService knowledgeService;
    private AigKnowledgeCollectionMapper collectionMapper;
    private AigKnowledgeDocumentMapper documentMapper;
    private AigKnowledgeChunkMapper chunkMapper;
    private AigKnowledgeProcessingJobMapper jobMapper;
    private AigKnowledgeProcessor processor;
    private KnowledgeVectorIndexPort vectorIndex;
    private AigKnowledgeIndexingServiceImpl service;

    @BeforeEach
    void setUp() {
        knowledgeService = mock(IAigKnowledgeService.class);
        collectionMapper = mock(AigKnowledgeCollectionMapper.class);
        documentMapper = mock(AigKnowledgeDocumentMapper.class);
        chunkMapper = mock(AigKnowledgeChunkMapper.class);
        jobMapper = mock(AigKnowledgeProcessingJobMapper.class);
        processor = mock(AigKnowledgeProcessor.class);
        vectorIndex = mock(KnowledgeVectorIndexPort.class);
        service = new AigKnowledgeIndexingServiceImpl(knowledgeService, collectionMapper, documentMapper,
            chunkMapper, jobMapper, processor, vectorIndex);
    }

    private void stubReadyToProcess() {
        when(knowledgeService.getVersion(100L)).thenReturn(version("UPLOADED"));
        when(jobMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectById(10L)).thenReturn(document());
        when(collectionMapper.selectById(1L)).thenReturn(collection());
        when(vectorIndex.engineId()).thenReturn("builtin-file-cosine-v1");
        when(knowledgeService.advanceProcessing(any(), any())).thenReturn(AigKnowledgeStatusEnum.SCANNING);
    }

    private static AigKnowledgeDocumentVersion version(String status) {
        AigKnowledgeDocumentVersion version = new AigKnowledgeDocumentVersion();
        version.setVersionId(100L);
        version.setDocumentId(10L);
        version.setStatus(status);
        return version;
    }

    private static AigKnowledgeDocument document() {
        AigKnowledgeDocument document = new AigKnowledgeDocument();
        document.setDocumentId(10L);
        document.setCollectionId(1L);
        document.setSourceName("手册.txt");
        document.setSourceType("TXT");
        return document;
    }

    private static AigKnowledgeCollection collection() {
        AigKnowledgeCollection collection = new AigKnowledgeCollection();
        collection.setCollectionId(1L);
        collection.setCollectionCode("product_docs");
        collection.setDataLevel("RESTRICTED");
        return collection;
    }

    private static AigKnowledgeProcessor.ProcessingResult processed() {
        return new AigKnowledgeProcessor.ProcessingResult("builtin-text-v1", "builtin-hash-ngram-v1",
            "UTF-8", List.of(
            new AigKnowledgeProcessor.ProcessedChunk(1, "第一段正文", "行 1", "RESTRICTED", "第一段正文",
                new float[] {1f, 0f}),
            new AigKnowledgeProcessor.ProcessedChunk(2, "第二段正文", "行 2", "RESTRICTED", "第二段正文",
                new float[] {0f, 1f})));
    }

    @Test
    @DisplayName("★ 正常路径：干完一段才记一段成功，最后 READY；分段写库并带索引引用")
    void indexesHappyPath() {
        stubReadyToProcess();
        when(processor.process(any(), any(), any(), any())).thenReturn(processed());
        doAnswer(invocation -> {
            AigKnowledgeChunk row = invocation.getArgument(0);
            row.setChunkId(row.getOrdinal().longValue());
            return 1;
        }).when(chunkMapper).insert(any(AigKnowledgeChunk.class));
        when(vectorIndex.replace(eq(100L), any())).thenReturn(2);

        AigKnowledgeIndexingVo vo = service.index("文件内容".getBytes(StandardCharsets.UTF_8), 100L, "key-1");

        assertEquals(AigKnowledgeStatusEnum.READY.getCode(), vo.getStatus());
        assertEquals(2, vo.getChunkCount());
        assertEquals("builtin-text-v1", vo.getParserEngineId());
        assertEquals("builtin-hash-ngram-v1", vo.getEmbeddingEngineId());
        assertEquals("builtin-file-cosine-v1", vo.getVectorIndexEngine());

        // 状态推进顺序：扫描 → 解析 → 分段 → 索引 → 就绪（每步一个自己的幂等键）
        verify(knowledgeService).advanceProcessing(100L, "key-1#SCANNING");
        verify(knowledgeService).advanceProcessing(100L, "key-1#PARSING");
        verify(knowledgeService).advanceProcessing(100L, "key-1#CHUNKING");
        verify(knowledgeService).advanceProcessing(100L, "key-1#INDEXING");
        verify(knowledgeService).advanceProcessing(100L, "key-1#READY");
        verify(knowledgeService, times(5)).advanceProcessing(any(), any());

        // 重建前物理清场（唯一键不含 del_flag，逻辑删除会让重建撞键）
        verify(chunkMapper).deletePhysicallyByVersionId(100L);

        ArgumentCaptor<AigKnowledgeChunk> rows = ArgumentCaptor.forClass(AigKnowledgeChunk.class);
        verify(chunkMapper, times(2)).insert(rows.capture());
        AigKnowledgeChunk first = rows.getAllValues().get(0);
        assertEquals(1, first.getOrdinal());
        assertEquals("行 1", first.getLocator());
        assertEquals("RESTRICTED", first.getDataLevel(), "分段等级来自集合");
        assertEquals("第一段正文", first.getTextExcerpt());
        assertNotNull(first.getIndexRef());
        assertTrue(first.getIndexRef().startsWith("builtin-file-cosine-v1#"), first.getIndexRef());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeVectorIndexPort.VectorEntry>> entries =
            ArgumentCaptor.forClass(List.class);
        verify(vectorIndex).replace(eq(100L), entries.capture());
        assertEquals(2, entries.getValue().size());
        assertEquals(1L, entries.getValue().get(0).chunkId(), "向量必须挂在落库后的分段主键上");
        assertEquals(2L, entries.getValue().get(1).chunkId());
    }

    @Test
    @DisplayName("★ 同一幂等键已完成过：直接返回当前状态，不重复解析/嵌入")
    void sameKeyDoesNotReprocess() {
        when(knowledgeService.getVersion(100L)).thenReturn(version("READY"));
        when(jobMapper.selectCount(any())).thenReturn(1L);
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(vectorIndex.engineId()).thenReturn("builtin-file-cosine-v1");

        AigKnowledgeIndexingVo vo = service.index("文件内容".getBytes(StandardCharsets.UTF_8), 100L, "key-1");

        assertEquals("READY", vo.getStatus());
        assertEquals(2, vo.getChunkCount());
        assertTrue(vo.getMessage().contains("未重复处理"), vo.getMessage());
        verify(processor, never()).process(any(), any(), any(), any());
        verify(chunkMapper, never()).insert(any(AigKnowledgeChunk.class));
    }

    @Test
    @DisplayName("★ 处理失败：写 FAILED + 失败分类，然后抛错（调用方要当场看到原因）")
    void pipelineFailureIsRecordedAndThrown() {
        stubReadyToProcess();
        when(processor.process(any(), any(), any(), any()))
            .thenThrow(new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "PDF 一页文本都没取到：手册.pdf"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-1"));

        assertTrue(error.getMessage().contains("一页文本都没取到"), error.getMessage());
        verify(knowledgeService).failProcessing(100L, "key-1#FAILED",
            AigKnowledgePipelineException.PARSE_FAILED, "PDF 一页文本都没取到：手册.pdf");
        verify(chunkMapper, never()).insert(any(AigKnowledgeChunk.class));
    }

    @Test
    @DisplayName("已退役版本 / 已就绪版本 / 内容为空 / 幂等键为空：四种都明确失败")
    void invalidInputsFail() {
        when(knowledgeService.getVersion(100L)).thenReturn(version("RETIRED"));
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-1"))
            .getMessage().contains("已退役"));

        // 已 READY + 新幂等键 = 原地重建请求：拒绝（版本不可变；重建中途失败会让 READY 版本数据不全）
        when(knowledgeService.getVersion(100L)).thenReturn(version("READY"));
        when(jobMapper.selectCount(any())).thenReturn(0L);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-2"))
            .getMessage().contains("不能原地重建"));

        stubReadyToProcess();
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index(new byte[0], 100L, "key-1")).getMessage().contains("内容为空"));
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, " "))
            .getMessage().contains("幂等键"));
    }

    @Test
    @DisplayName("文档/集合/来源类型缺失：说清是哪一环缺，而不是「处理失败」")
    void missingContextFailsClearly() {
        stubReadyToProcess();
        when(documentMapper.selectById(10L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-1"))
            .getMessage().contains("文档不存在"));

        when(documentMapper.selectById(10L)).thenReturn(document());
        when(collectionMapper.selectById(1L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-1"))
            .getMessage().contains("知识集合不存在"));

        AigKnowledgeDocument unknown = document();
        unknown.setSourceType("XLSX");
        when(collectionMapper.selectById(1L)).thenReturn(collection());
        when(documentMapper.selectById(10L)).thenReturn(unknown);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.index("x".getBytes(StandardCharsets.UTF_8), 100L, "key-1"))
            .getMessage().contains("来源类型不可识别"));
    }

}
