package org.dromara.aigov.knowledge.retrieval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
import org.dromara.aigov.knowledge.pipeline.KnowledgeEmbeddingPort;
import org.dromara.aigov.knowledge.pipeline.KnowledgeVectorIndexPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识检索实现测试（设计 §7.4）。
 *
 * <p>钉住"只返回已授权片段"与"没有支持资料就明说"两条。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeRetrievalServiceTest {

    private AigKnowledgeCollectionMapper collectionMapper;
    private AigKnowledgeDocumentMapper documentMapper;
    private AigKnowledgeDocumentVersionMapper versionMapper;
    private AigKnowledgeChunkMapper chunkMapper;
    private KnowledgeEmbeddingPort embeddingPort;
    private KnowledgeVectorIndexPort vectorIndex;
    private AigKnowledgePipelineProperties properties;
    private AigKnowledgeRetrievalService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeCollection.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeDocumentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigKnowledgeChunk.class);
    }

    @BeforeEach
    void setUp() {
        collectionMapper = mock(AigKnowledgeCollectionMapper.class);
        documentMapper = mock(AigKnowledgeDocumentMapper.class);
        versionMapper = mock(AigKnowledgeDocumentVersionMapper.class);
        chunkMapper = mock(AigKnowledgeChunkMapper.class);
        embeddingPort = mock(KnowledgeEmbeddingPort.class);
        vectorIndex = mock(KnowledgeVectorIndexPort.class);
        properties = new AigKnowledgePipelineProperties();
        service = new AigKnowledgeRetrievalService(collectionMapper, documentMapper, versionMapper,
            chunkMapper, embeddingPort, vectorIndex, properties);
    }

    private static AigKnowledgeCollection collection() {
        AigKnowledgeCollection c = new AigKnowledgeCollection();
        c.setCollectionId(1L);
        c.setCollectionCode("product_docs");
        return c;
    }

    private static AigKnowledgeDocument document() {
        AigKnowledgeDocument d = new AigKnowledgeDocument();
        d.setDocumentId(10L);
        d.setCollectionId(1L);
        d.setSourceName("产品手册.pdf");
        return d;
    }

    private static AigKnowledgeDocumentVersion version(String status) {
        AigKnowledgeDocumentVersion v = new AigKnowledgeDocumentVersion();
        v.setVersionId(100L);
        v.setDocumentId(10L);
        v.setStatus(status);
        return v;
    }

    private static AigKnowledgeChunk chunk() {
        AigKnowledgeChunk c = new AigKnowledgeChunk();
        c.setChunkId(1000L);
        c.setDocumentVersionId(100L);
        c.setOrdinal(1);
        c.setLocator("第3页");
        c.setTextExcerpt("产品支持 3-6 岁");
        c.setDataLevel("STRICT");
        return c;
    }

    private void stubAuthorizedScenario(String status) {
        when(collectionMapper.selectList(any())).thenReturn(List.of(collection()));
        when(documentMapper.selectList(any())).thenReturn(List.of(document()));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(status)));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk()));
    }

    @Test
    @DisplayName("★ 命中已授权且就绪的片段：返回可核验的引用（chunkId/来源/定位）")
    void returnsCitationForAuthorizedChunk() {
        stubAuthorizedScenario("READY");

        KnowledgePort.RetrievalResult result = service.retrieve(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertFalse(result.noSupport());
        assertEquals(1, result.citations().size());
        KnowledgePort.Citation citation = result.citations().get(0);
        assertEquals(1000L, citation.chunkId());
        assertEquals(100L, citation.versionId());
        assertEquals("产品手册.pdf", citation.sourceName());
        assertEquals("第3页", citation.locator());
    }

    @Test
    @DisplayName("★ 非就绪版本不产生任何片段（未处理完的资料不能进结果）")
    void notReadyVersionYieldsNoSupport() {
        stubAuthorizedScenario("FAILED");

        KnowledgePort.RetrievalResult result = service.retrieve(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertTrue(result.noSupport());
        assertTrue(result.reason().contains("就绪"), result.reason());
    }

    @Test
    @DisplayName("没有授权范围 / 查询为空：明确说明，而不是回一个空列表让人猜")
    void emptyScopeIsExplicit() {
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "x", Set.of(), Set.of(), 5, null)).noSupport());
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "  ", Set.of(), Set.of("product_docs"), 5, null)).noSupport());
    }

    @Test
    @DisplayName("授权范围内没有集合：明确说明")
    void unknownCollectionIsExplicit() {
        when(collectionMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "x", Set.of(), Set.of("product_docs"), 5, null)).noSupport());
    }

    @Test
    @DisplayName("★ 身份层收窄：组织范围不匹配的集合被排除（§7.2）")
    void orgScopeMismatchIsExcluded() {
        AigKnowledgeCollection scoped = collection();
        scoped.setOrgScope("999");
        when(collectionMapper.selectList(any())).thenReturn(List.of(scoped));
        when(documentMapper.selectList(any())).thenReturn(List.of(document()));
        when(versionMapper.selectList(any())).thenReturn(List.of(version("READY")));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk()));

        KnowledgePort.RetrievalResult result = service.retrieve(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, 5L));

        assertTrue(result.noSupport(), "组织不匹配的集合不应进入候选");
    }

    @Test
    @DisplayName("身份为空时不做组织层收窄（维护测试入口按显式集合检索）")
    void nullOrgDoesNotNarrow() {
        AigKnowledgeCollection scoped = collection();
        scoped.setOrgScope("999");
        when(collectionMapper.selectList(any())).thenReturn(List.of(scoped));
        when(documentMapper.selectList(any())).thenReturn(List.of(document()));
        when(versionMapper.selectList(any())).thenReturn(List.of(version("READY")));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk()));

        KnowledgePort.RetrievalResult result = service.retrieve(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertTrue(result.noSupport() == false, "无身份上下文时不因 orgScope 排除");
    }

    @Test
    @DisplayName("★ 向量检索：按索引顺序返回引用，且落点必须回库复查")
    void vectorModeReturnsCitationsByIndexOrder() {
        stubAuthorizedScenario("READY");
        when(embeddingPort.engineId()).thenReturn("builtin-hash-ngram-v1");
        when(embeddingPort.embed("3-6 岁")).thenReturn(new float[] {1f, 0f});
        when(vectorIndex.search(any(), any(), anyInt()))
            .thenReturn(List.of(new KnowledgeVectorIndexPort.Hit(1000L, 0.9)));
        when(chunkMapper.selectByIds(any())).thenReturn(List.of(chunk()));

        KnowledgePort.RetrievalResult result = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertFalse(result.noSupport());
        assertEquals(1, result.citations().size());
        assertEquals(1000L, result.citations().get(0).chunkId());
        assertEquals("第3页", result.citations().get(0).locator());
    }

    @Test
    @DisplayName("★ 向量命中但分段已不在授权范围（或已被删除）：丢掉，不当成引用")
    void vectorHitOutsideAuthorizedScopeIsDropped() {
        stubAuthorizedScenario("READY");
        when(embeddingPort.embed(any())).thenReturn(new float[] {1f, 0f});
        when(vectorIndex.search(any(), any(), anyInt()))
            .thenReturn(List.of(new KnowledgeVectorIndexPort.Hit(1000L, 0.9)));
        AigKnowledgeChunk other = chunk();
        other.setDocumentVersionId(999L);
        when(chunkMapper.selectByIds(any())).thenReturn(List.of(other));

        KnowledgePort.RetrievalResult result = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertTrue(result.noSupport(), "索引命中但落点越权/不存在时必须没有支持资料");
        assertTrue(result.reason().contains("不在授权范围内"), result.reason());
    }

    @Test
    @DisplayName("★ 索引里没有这个版本的向量：原因要指向「还没执行解析索引」，而不是「资料里没写」")
    void vectorIndexEmptyPointsToMissingIndexing() {
        stubAuthorizedScenario("READY");
        when(embeddingPort.engineId()).thenReturn("builtin-hash-ngram-v1");
        when(embeddingPort.embed(any())).thenReturn(new float[] {1f, 0f});
        when(vectorIndex.search(any(), any(), anyInt())).thenReturn(List.of());

        KnowledgePort.RetrievalResult result = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertTrue(result.noSupport());
        assertTrue(result.reason().contains("解析并索引"), result.reason());
    }

    @Test
    @DisplayName("向量检索被配置关闭 / 嵌入不产出向量：都要明说，不许静默退化成关键字")
    void vectorDisabledOrEmptyEmbeddingIsExplicit() {
        properties.setVectorSearchEnabled(false);
        KnowledgePort.RetrievalResult disabled = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));
        assertTrue(disabled.noSupport());
        assertTrue(disabled.reason().contains("vector-search-enabled"), disabled.reason());

        properties.setVectorSearchEnabled(true);
        stubAuthorizedScenario("READY");
        when(embeddingPort.engineId()).thenReturn("builtin-hash-ngram-v1");
        when(embeddingPort.embed(any())).thenReturn(null);
        KnowledgePort.RetrievalResult empty = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));
        assertTrue(empty.reason().contains("空向量"), empty.reason());
    }

    @Test
    @DisplayName("向量检索沿用同一套授权判定：非就绪版本照样没有结果")
    void vectorModeRespectsReadiness() {
        stubAuthorizedScenario("FAILED");
        KnowledgePort.RetrievalResult result = service.retrieveByVector(new KnowledgePort.RetrievalQuery(
            "3-6 岁", Set.of(), Set.of("product_docs"), 5, null));

        assertTrue(result.noSupport());
        assertTrue(result.reason().contains("就绪"), result.reason());
    }

}
