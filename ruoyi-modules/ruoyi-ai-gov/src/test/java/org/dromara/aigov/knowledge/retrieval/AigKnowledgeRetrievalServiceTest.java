package org.dromara.aigov.knowledge.retrieval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
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
        service = new AigKnowledgeRetrievalService(collectionMapper, documentMapper, versionMapper,
            chunkMapper);
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
            "3-6 岁", Set.of(), Set.of("product_docs"), 5));

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
            "3-6 岁", Set.of(), Set.of("product_docs"), 5));

        assertTrue(result.noSupport());
        assertTrue(result.reason().contains("就绪"), result.reason());
    }

    @Test
    @DisplayName("没有授权范围 / 查询为空：明确说明，而不是回一个空列表让人猜")
    void emptyScopeIsExplicit() {
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "x", Set.of(), Set.of(), 5)).noSupport());
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "  ", Set.of(), Set.of("product_docs"), 5)).noSupport());
    }

    @Test
    @DisplayName("授权范围内没有集合：明确说明")
    void unknownCollectionIsExplicit() {
        when(collectionMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.retrieve(new KnowledgePort.RetrievalQuery(
            "x", Set.of(), Set.of("product_docs"), 5)).noSupport());
    }

}
