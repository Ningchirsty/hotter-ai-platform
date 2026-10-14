package org.dromara.aigov.knowledge.retrieval;

import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识检索授权判据测试（设计 §7.4、§8.4）。
 *
 * <p>它守的是"越权片段会不会成为检索结果"这条底线。判据出错不会报错，只会安静地多返回几段
 * 不该看到的资料——所以这里逐条钉住：状态、有效期、集合授权、版本限制。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeAccessTest {

    private static final Set<String> CODES = Set.of("product_docs");

    private static AigKnowledgeDocumentVersion version(String status, LocalDateTime from, LocalDateTime to) {
        AigKnowledgeDocumentVersion version = new AigKnowledgeDocumentVersion();
        version.setVersionId(100L);
        version.setStatus(status);
        version.setEffectiveFrom(from);
        version.setEffectiveTo(to);
        return version;
    }

    @Test
    @DisplayName("就绪 + 在有效期内 + 集合已授权 → 可检索")
    void readyAndInEffectAndAuthorized() {
        assertTrue(AigKnowledgeAccess.retrievable(
            version("READY", null, null), Set.of(), CODES, "product_docs", LocalDateTime.now()));
    }

    @Test
    @DisplayName("非就绪版本不可检索（未处理完的资料不能进结果）")
    void notReadyIsNotRetrievable() {
        for (String status : new String[] {"UPLOADED", "SCANNING", "PARSING", "CHUNKING", "INDEXING",
            "FAILED", "RETIRED"}) {
            assertFalse(AigKnowledgeAccess.retrievable(
                    version(status, null, null), Set.of(), CODES, "product_docs", LocalDateTime.now()),
                status + " 不应可检索");
        }
    }

    @Test
    @DisplayName("未生效 / 已过期不可检索；截止时刻本身仍有效（闭区间）")
    void effectivityWindow() {
        LocalDateTime now = LocalDateTime.parse("2026-10-14T10:00:00");
        assertFalse(AigKnowledgeAccess.retrievable(
            version("READY", now.plusDays(1), null), Set.of(), CODES, "product_docs", now),
            "未到生效时间不应可检索");
        assertFalse(AigKnowledgeAccess.retrievable(
            version("READY", null, now.minusDays(1)), Set.of(), CODES, "product_docs", now),
            "已过截止时间不应可检索");
        assertTrue(AigKnowledgeAccess.retrievable(
            version("READY", now.minusDays(1), now), Set.of(), CODES, "product_docs", now),
            "截止时刻本身仍有效（闭区间口径）");
    }

    @Test
    @DisplayName("集合未授权 → 不可检索（授权是检索的前置过滤）")
    void collectionMustBeAuthorized() {
        assertFalse(AigKnowledgeAccess.retrievable(
            version("READY", null, null), Set.of(), Set.of("other_docs"), "product_docs",
            LocalDateTime.now()));
        assertFalse(AigKnowledgeAccess.retrievable(
            version("READY", null, null), Set.of(), null, "product_docs", LocalDateTime.now()));
    }

    @Test
    @DisplayName("指定了允许版本时，未命中的版本不可检索")
    void allowedVersionMustMatch() {
        assertTrue(AigKnowledgeAccess.retrievable(
            version("READY", null, null), Set.of(100L), CODES, "product_docs", LocalDateTime.now()));
        assertFalse(AigKnowledgeAccess.retrievable(
            version("READY", null, null), Set.of(999L), CODES, "product_docs", LocalDateTime.now()));
    }

    @Test
    @DisplayName("★ 组织范围：不限则放行；有限则按身份等值匹配；无身份不做该层收窄")
    void orgScopeJudge() {
        assertTrue(AigKnowledgeAccess.orgAllowed(null, 5L), "不限组织");
        assertTrue(AigKnowledgeAccess.orgAllowed("  ", 5L), "空白视为不限");
        assertTrue(AigKnowledgeAccess.orgAllowed("5", 5L));
        assertTrue(AigKnowledgeAccess.orgAllowed(" 5 ", 5L));
        assertFalse(AigKnowledgeAccess.orgAllowed("999", 5L), "组织不匹配应排除");
        assertTrue(AigKnowledgeAccess.orgAllowed("999", null), "无身份上下文时不做该层收窄");
    }

}
