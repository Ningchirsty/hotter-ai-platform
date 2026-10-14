package org.dromara.aigov.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.AigKnowledgeProcessingJob;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeCollectionBo;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeDocumentBo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeDocumentRegisterVo;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeProcessingJobMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识中心服务测试（设计 §6.4、§8.2）。
 *
 * <p>要钉死的：集合唯一与默认严格级、格式识别、重复文件只提示不拒绝、处理管道的幂等与跳步防护。</p>
 *
 * @author author
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeServiceImplTest {

    private static final long COLLECTION_ID = 5001L;
    private static final long DOCUMENT_ID = 6001L;
    private static final long VERSION_ID = 7001L;
    private static final String SHA = "a".repeat(64);

    private AigKnowledgeCollectionMapper collectionMapper;
    private AigKnowledgeDocumentMapper documentMapper;
    private AigKnowledgeDocumentVersionMapper versionMapper;
    private AigKnowledgeProcessingJobMapper jobMapper;
    private org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper chunkMapper;
    private AigKnowledgeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeCollection.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeDocumentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeProcessingJob.class);
        TableInfoHelper.initTableInfo(assistant, org.dromara.aigov.knowledge.domain.AigKnowledgeChunk.class);
    }

    @BeforeEach
    void setUp() {
        collectionMapper = mock(AigKnowledgeCollectionMapper.class);
        documentMapper = mock(AigKnowledgeDocumentMapper.class);
        versionMapper = mock(AigKnowledgeDocumentVersionMapper.class);
        jobMapper = mock(AigKnowledgeProcessingJobMapper.class);
        chunkMapper = mock(org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper.class);
        service = new AigKnowledgeServiceImpl(collectionMapper, documentMapper, versionMapper, jobMapper,
            chunkMapper);
        when(collectionMapper.insert(any(AigKnowledgeCollection.class))).thenAnswer(invocation -> {
            invocation.<AigKnowledgeCollection>getArgument(0).setCollectionId(COLLECTION_ID);
            return 1;
        });
        when(documentMapper.insert(any(AigKnowledgeDocument.class))).thenAnswer(invocation -> {
            invocation.<AigKnowledgeDocument>getArgument(0).setDocumentId(DOCUMENT_ID);
            return 1;
        });
        when(versionMapper.insert(any(AigKnowledgeDocumentVersion.class))).thenAnswer(invocation -> {
            invocation.<AigKnowledgeDocumentVersion>getArgument(0).setVersionId(VERSION_ID);
            return 1;
        });
        when(versionMapper.update(any(), any())).thenReturn(1);
    }

    private static AigKnowledgeCollectionBo collectionBo() {
        AigKnowledgeCollectionBo bo = new AigKnowledgeCollectionBo();
        bo.setCollectionCode("product_docs");
        bo.setCollectionName("产品资料");
        return bo;
    }

    private static AigKnowledgeDocumentBo documentBo() {
        AigKnowledgeDocumentBo bo = new AigKnowledgeDocumentBo();
        bo.setCollectionId(COLLECTION_ID);
        bo.setSourceName("产品手册.pdf");
        bo.setSha256(SHA);
        return bo;
    }

    private static AigKnowledgeDocumentVersion version(Long id, String status) {
        AigKnowledgeDocumentVersion version = new AigKnowledgeDocumentVersion();
        version.setVersionId(id);
        version.setStatus(status);
        return version;
    }

    private static org.dromara.aigov.knowledge.domain.AigKnowledgeChunk chunk(long id, int ordinal,
                                                                             String excerpt,
                                                                             String textRef,
                                                                             String indexRef) {
        org.dromara.aigov.knowledge.domain.AigKnowledgeChunk chunk =
            new org.dromara.aigov.knowledge.domain.AigKnowledgeChunk();
        chunk.setChunkId(id);
        chunk.setDocumentVersionId(VERSION_ID);
        chunk.setOrdinal(ordinal);
        chunk.setLocator("p." + ordinal);
        chunk.setTextExcerpt(excerpt);
        chunk.setTextRef(textRef);
        chunk.setIndexRef(indexRef);
        chunk.setDataLevel("STRICT");
        return chunk;
    }

    @Test
    @DisplayName("★ 分段预览：只给受限预览 + 如实告诉正文在哪里、有没有进索引")
    void listChunksReturnsPreviewOnly() {
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version(VERSION_ID, "READY"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
            chunk(1L, 1, "第一段预览", "oss://knowledge/v1/chunk-1", "idx-1"),
            chunk(2L, 2, "第二段预览", null, null)));

        List<org.dromara.aigov.knowledge.domain.vo.AigKnowledgeChunkVo> chunks =
            service.listChunks(VERSION_ID, null);

        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).getOrdinal());
        assertEquals("第一段预览", chunks.get(0).getTextExcerpt());
        assertEquals(Boolean.TRUE, chunks.get(0).getHasFullText());
        assertEquals(Boolean.TRUE, chunks.get(0).getIndexed());
        assertEquals(Boolean.FALSE, chunks.get(1).getHasFullText(), "没有正文引用就如实说没有");
        assertEquals(Boolean.FALSE, chunks.get(1).getIndexed());
        // 不含正文字段：VO 里根本没有正文（正文在受控存储引用里，见 text_ref）
        assertFalse(chunks.get(0).toString().contains("oss://knowledge/v1/chunk-1"));
    }

    @Test
    @DisplayName("★ 分段预览条数归一化：空/非正数回落到默认，超过上限按上限（一行的事，最容易改错）")
    void chunkLimitIsNormalized() {
        int def = org.dromara.aigov.knowledge.service.IAigKnowledgeService.DEFAULT_CHUNK_LIMIT;
        int max = org.dromara.aigov.knowledge.service.IAigKnowledgeService.MAX_CHUNK_LIMIT;

        assertEquals(def, AigKnowledgeServiceImpl.resolveChunkLimit(null));
        assertEquals(def, AigKnowledgeServiceImpl.resolveChunkLimit(0));
        assertEquals(def, AigKnowledgeServiceImpl.resolveChunkLimit(-5));
        assertEquals(10, AigKnowledgeServiceImpl.resolveChunkLimit(10));
        assertEquals(max, AigKnowledgeServiceImpl.resolveChunkLimit(max + 1));
        assertTrue(def < max, "默认值应当小于上限，否则上限形同虚设");
    }

    @Test
    @DisplayName("★ 分段预览：版本不存在时拒绝（否则会被读成「这一版没有分段」）")
    void listChunksRejectsMissingVersion() {
        when(versionMapper.selectById(VERSION_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.listChunks(VERSION_ID, 10));
        assertThrows(ServiceException.class, () -> service.listChunks(null, 10));
        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("集合：编码重复拒绝；默认数据等级为 STRICT（知识资料按禁区处理）")
    void createCollectionDefaultsAndUniqueness() {
        when(collectionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.createCollection(collectionBo(), 9L));

        when(collectionMapper.selectCount(any())).thenReturn(0L);
        assertEquals(COLLECTION_ID, service.createCollection(collectionBo(), 9L));
        ArgumentCaptor<AigKnowledgeCollection> captor = ArgumentCaptor.forClass(AigKnowledgeCollection.class);
        verify(collectionMapper).insert(captor.capture());
        assertEquals("STRICT", captor.getValue().getDataLevel());
        assertEquals(9L, captor.getValue().getOwnerId());
    }

    @Test
    @DisplayName("集合：非法数据等级拒绝")
    void createCollectionRejectsIllegalLevel() {
        when(collectionMapper.selectCount(any())).thenReturn(0L);
        AigKnowledgeCollectionBo bo = collectionBo();
        bo.setDataLevel("NOT_A_LEVEL");
        assertThrows(ServiceException.class, () -> service.createCollection(bo, 9L));
    }

    @Test
    @DisplayName("文档：不支持的格式（XLSX）在登记时拒绝，而不是等到异步解析才失败")
    void registerRejectsUnsupportedFormat() {
        when(collectionMapper.selectById(COLLECTION_ID))
            .thenReturn(collection(COLLECTION_ID, "product_docs"));
        AigKnowledgeDocumentBo bo = documentBo();
        bo.setSourceName("报价表.xlsx");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.registerDocument(bo, 9L));
        assertTrue(ex.getMessage().contains("不支持"), ex.getMessage());
    }

    @Test
    @DisplayName("★ 重复文件只提示不拒绝：不同业务范围不能共享授权")
    void duplicateShaIsHintNotRejection() {
        when(collectionMapper.selectById(COLLECTION_ID))
            .thenReturn(collection(COLLECTION_ID, "product_docs"));
        when(versionMapper.selectOne(any())).thenReturn(version(8888L, "READY"));
        when(versionMapper.selectCount(any())).thenReturn(0L);

        AigKnowledgeDocumentRegisterVo vo = service.registerDocument(documentBo(), 9L);

        assertTrue(vo.getDuplicateHint());
        assertEquals(8888L, vo.getDuplicateVersionId());
        assertEquals(AigKnowledgeStatusEnum.UPLOADED.getCode(), vo.getStatus(), "仍照常建版本");
        verify(versionMapper).insert(any(AigKnowledgeDocumentVersion.class));
    }

    @Test
    @DisplayName("文档：同文档重复版本号拒绝（版本号是原文不可变的锚点）")
    void registerRejectsDuplicateVersion() {
        when(collectionMapper.selectById(COLLECTION_ID))
            .thenReturn(collection(COLLECTION_ID, "product_docs"));
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.registerDocument(documentBo(), 9L));
    }

    @Test
    @DisplayName("★ 处理管道：UPLOADED → SCANNING，写一条成功任务并 CAS 更新版本状态")
    void advanceMovesOneStage() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.UPLOADED.getCode()));
        when(jobMapper.selectCount(any())).thenReturn(0L);

        assertEquals(AigKnowledgeStatusEnum.SCANNING,
            service.advanceProcessing(VERSION_ID, "key-1"));
        verify(jobMapper).insert(any(AigKnowledgeProcessingJob.class));
        verify(versionMapper).update(any(), any());
    }

    @Test
    @DisplayName("★ 处理管道幂等：同一键重复调用不再推进（重试不产生重复索引）")
    void advanceIsIdempotentByKey() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.SCANNING.getCode()));
        when(jobMapper.selectCount(any())).thenReturn(1L);

        assertEquals(AigKnowledgeStatusEnum.SCANNING,
            service.advanceProcessing(VERSION_ID, "key-1"));
        verify(jobMapper, never()).insert(any(AigKnowledgeProcessingJob.class));
        verify(versionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("处理管道：READY 幂等返回，RETIRED 拒绝推进")
    void advanceTerminalStates() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.READY.getCode()));
        assertEquals(AigKnowledgeStatusEnum.READY, service.advanceProcessing(VERSION_ID, "key-2"));

        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.RETIRED.getCode()));
        assertThrows(ServiceException.class, () -> service.advanceProcessing(VERSION_ID, "key-3"));
    }

    @Test
    @DisplayName("失败：进入 FAILED 且保留失败阶段；READY 是终态不被一次失败改写")
    void failProcessingSemantics() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.PARSING.getCode()));
        when(jobMapper.selectCount(any())).thenReturn(0L);

        assertEquals(AigKnowledgeStatusEnum.FAILED,
            service.failProcessing(VERSION_ID, "key-f", "PARSE_ERROR", "解析器未接入"));

        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.READY.getCode()));
        assertEquals(AigKnowledgeStatusEnum.READY,
            service.failProcessing(VERSION_ID, "key-g", "X", "不应改写就绪版本"));
        // 只有第一次（PARSING→FAILED）改过状态；READY 那次一次都没改
        verify(versionMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("退役：READY → RETIRED；再退役幂等")
    void retireSemantics() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.READY.getCode()));
        assertEquals(AigKnowledgeStatusEnum.RETIRED, service.retire(VERSION_ID));

        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.RETIRED.getCode()));
        assertEquals(AigKnowledgeStatusEnum.RETIRED, service.retire(VERSION_ID));
    }

    private static AigKnowledgeCollection collection(Long id, String code) {
        AigKnowledgeCollection collection = new AigKnowledgeCollection();
        collection.setCollectionId(id);
        collection.setCollectionCode(code);
        collection.setStatus("0");
        return collection;
    }

    @Test
    @DisplayName("CAS 冲突：状态被并发改过时报可读冲突，而不是静默覆盖")
    void casConflictIsReported() {
        when(versionMapper.selectById(VERSION_ID))
            .thenReturn(version(VERSION_ID, AigKnowledgeStatusEnum.UPLOADED.getCode()));
        when(jobMapper.selectCount(any())).thenReturn(0L);
        when(versionMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.advanceProcessing(VERSION_ID, "key-c"));
    }

    @Test
    @DisplayName("登记结果里保留 effectiveTo 为空表示长期，不伪造一个截止时间")
    void effectiveWindowIsNotFabricated() {
        when(collectionMapper.selectById(COLLECTION_ID))
            .thenReturn(collection(COLLECTION_ID, "product_docs"));
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(0L);
        AigKnowledgeDocumentBo bo = documentBo();
        bo.setEffectiveFrom(LocalDateTime.parse("2026-01-01T00:00:00"));

        service.registerDocument(bo, 9L);

        ArgumentCaptor<AigKnowledgeDocumentVersion> captor =
            ArgumentCaptor.forClass(AigKnowledgeDocumentVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertEquals(LocalDateTime.parse("2026-01-01T00:00:00"), captor.getValue().getEffectiveFrom());
        assertNull(captor.getValue().getEffectiveTo());
        assertFalse(captor.getValue().getSha256().isEmpty());
    }

    @Test
    @DisplayName("集合不存在时登记被拒")
    void registerRequiresExistingCollection() {
        when(collectionMapper.selectById(eq(COLLECTION_ID))).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.registerDocument(documentBo(), 9L));
    }

}
