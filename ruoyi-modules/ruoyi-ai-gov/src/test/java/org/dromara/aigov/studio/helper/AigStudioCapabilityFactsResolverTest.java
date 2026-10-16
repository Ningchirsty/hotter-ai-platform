package org.dromara.aigov.studio.helper;

import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;
import org.dromara.aigov.service.IAigCapabilityOverviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 训练台能力事实查询（{@link AigStudioCapabilityFactsResolver}）。
 *
 * <p><b>守的是"精确匹配"</b>：能力概览按编码过滤用的是 LIKE，所以查 {@code text_generation}
 * 可能连 {@code text_generation_v2} 一起返回。若把第一条当答案，预检与下拉提示就会张冠李戴
 * （一个编码齐全的能力被报成"缺候选"）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioCapabilityFactsResolverTest {

    private IAigCapabilityOverviewService overviewService;

    private AigStudioCapabilityFactsResolver resolver;

    @BeforeEach
    void setUp() {
        overviewService = mock(IAigCapabilityOverviewService.class);
        resolver = new AigStudioCapabilityFactsResolver(overviewService);
    }

    /**
     * 造一行概览。
     *
     * @param code       能力编码
     * @param name       能力名
     * @param candidates 候选数
     * @return 概览行
     */
    private static AigCapabilityOverviewVo row(String code, String name, int candidates) {
        AigCapabilityOverviewVo vo = new AigCapabilityOverviewVo();
        vo.setCapabilityCode(code);
        vo.setCapabilityName(name);
        vo.setStatus("0");
        vo.setCandidateCount(candidates);
        vo.setCandidateHealthAgg(candidates > 0 ? "HEALTHY" : "NONE");
        vo.setPolicyCoveredLevels(4);
        vo.setPolicyTotalLevels(4);
        return vo;
    }

    @Test
    @DisplayName("空编码 → 不查库、返回 null（「没声明能力」由预检的 CAPABILITY_MISSING 负责）")
    void blankCodeShortCircuits() {
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve("   "));
        verify(overviewService, never()).overview(any(AigCapabilityBo.class), any(), any(), any());
    }

    @Test
    @DisplayName("★ LIKE 命中多条时按编码精确取（近似的编码不能被当成它）")
    void picksExactCodeAmongLikeResults() {
        when(overviewService.overview(any(AigCapabilityBo.class), any(), any(), any()))
            .thenReturn(List.of(row("text_generation_v2", "文本生成v2", 0), row("text_generation", "文本生成", 3)));

        AigStudioCapabilityFacts facts = resolver.resolve("text_generation");

        assertTrue(facts.exists());
        assertEquals("text_generation", facts.capabilityCode());
        assertEquals("文本生成", facts.capabilityName());
        assertEquals(3, facts.candidateCount());
        assertFalse(facts.noCandidate());
    }

    @Test
    @DisplayName("大小写不同仍算同一个编码（路由侧规范化过大小写，这里不能因为大小写报「不存在」）")
    void caseInsensitiveMatch() {
        when(overviewService.overview(any(AigCapabilityBo.class), any(), any(), any()))
            .thenReturn(List.of(row("Text_Generation", "文本生成", 1)));

        AigStudioCapabilityFacts facts = resolver.resolve("text_generation");

        assertTrue(facts.exists());
        // 回显的是**调用方给的编码**：判定要针对它，而不是库里的写法
        assertEquals("text_generation", facts.capabilityCode());
        assertFalse(facts.disabled());
    }

    @Test
    @DisplayName("一条都没查到 / 只有近似编码 → 视为不存在（exists=false 但编码原样回显）")
    void missingWhenNothingMatchesExactly() {
        when(overviewService.overview(any(AigCapabilityBo.class), any(), any(), any()))
            .thenReturn(List.of());
        AigStudioCapabilityFacts none = resolver.resolve("brief_precheck");
        assertFalse(none.exists());
        assertEquals("brief_precheck", none.capabilityCode());

        when(overviewService.overview(any(AigCapabilityBo.class), any(), any(), any()))
            .thenReturn(List.of(row("text_generation_v2", "文本生成v2", 2)));
        AigStudioCapabilityFacts onlyApprox = resolver.resolve("text_generation");
        assertFalse(onlyApprox.exists());
    }

    @Test
    @DisplayName("状态与策略覆盖原样带进快照（预检据此分级）")
    void carriesStatusAndPolicy() {
        AigCapabilityOverviewVo disabled = row("image_generation", "图片生成", 2);
        disabled.setStatus("1");
        disabled.setPolicyCoveredLevels(2);
        when(overviewService.overview(any(AigCapabilityBo.class), any(), any(), any()))
            .thenReturn(List.of(disabled));

        AigStudioCapabilityFacts facts = resolver.resolve("image_generation");

        assertTrue(facts.disabled());
        assertEquals(2, facts.policyCoveredLevels());
        assertTrue(facts.policyIncomplete());
    }
}
