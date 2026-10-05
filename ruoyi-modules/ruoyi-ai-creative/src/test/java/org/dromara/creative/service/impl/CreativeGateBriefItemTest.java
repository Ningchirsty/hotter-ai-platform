package org.dromara.creative.service.impl;

import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.creative.service.ICreativeGateService.GateItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「品牌 Brief 这一道闸门怎么说」的单元测试（v1 人工测试反馈：品牌 brief 确认点了没反应）。
 *
 * <p><b>真机上发生的事</b>：本地有一条 Brief 记录但 8 项全空（`configured=true`、状态 DRAFT）。
 * 闸门原来只说「Brief <b>已填写</b>但状态是『草稿』」，品牌部照这话去点「品牌方确认」，
 * 后端以「品牌要求 8 项全空：至少填一项再确认」拒绝（实测确认接口 500、状态仍 DRAFT）——
 * 于是主观感受就是"点了没反应"。三态（没记录 / 有记录但空 / 填了没确认）必须分开说。</p>
 *
 * @author creative
 */
class CreativeGateBriefItemTest {

    /** 一条 8 项全空、状态草稿的记录（真机上的那一份） */
    private static CpBrandBriefVo emptyDraft() {
        CpBrandBriefVo vo = new CpBrandBriefVo();
        vo.setTaskId(1L);
        vo.setConfigured(true);
        vo.setStatus("DRAFT");
        return vo;
    }

    /** 一条真的填了内容的草稿 */
    private static CpBrandBriefVo filledDraft() {
        CpBrandBriefVo vo = emptyDraft();
        vo.setMustShow("品牌名/logo");
        return vo;
    }

    @Test
    @DisplayName("没有 Brief 记录：说「还没有填」，不暗示任何东西已经填过")
    void noRecord() {
        GateItem item = CreativeGateServiceImpl.brandBriefItem(null);
        assertFalse(item.passed());
        assertTrue(item.detail().contains("还没有填品牌 Brief"), item.detail());

        CpBrandBriefVo blank = new CpBrandBriefVo();
        blank.setConfigured(false);
        assertTrue(CreativeGateServiceImpl.brandBriefItem(blank).detail().contains("还没有填品牌 Brief"));
    }

    @Test
    @DisplayName("有记录但 8 项全空：说清是空草稿，并说明「现在点确认不会有任何变化」")
    void emptyDraftIsNotCalledFilled() {
        GateItem item = CreativeGateServiceImpl.brandBriefItem(emptyDraft());
        assertFalse(item.passed());
        assertTrue(item.detail().contains("空草稿"), item.detail());
        // 关键回归：不许再说"已填写"——那句话是品牌部点那个必然被拒的按钮的原因
        assertFalse(item.detail().contains("Brief 已填写"), item.detail());
        assertTrue(item.detail().contains("确认会被直接拒绝"), item.detail());
    }

    @Test
    @DisplayName("只有参考风格图片也算填过（与内容域的确认校验同一口径）")
    void styleImagesCountAsContent() {
        CpBrandBriefVo vo = emptyDraft();
        vo.setStyleRefFiles("1001,1002");
        GateItem item = CreativeGateServiceImpl.brandBriefItem(vo);
        assertTrue(item.detail().contains("Brief 已填写但还没确认"), item.detail());
    }

    @Test
    @DisplayName("填了内容但没确认：说「已填写但还没确认」，并给出当前状态")
    void filledDraftPointsAtBrandSide() {
        GateItem item = CreativeGateServiceImpl.brandBriefItem(filledDraft());
        assertFalse(item.passed());
        assertTrue(item.detail().contains("Brief 已填写但还没确认"), item.detail());
        assertTrue(item.detail().contains("品牌方确认"), item.detail());
        // 等级不写进文案（等级只有配置一处来源）
        assertFalse(item.detail().contains("BLOCK"), item.detail());
        assertFalse(item.detail().contains("建议级"), item.detail());
    }

    @Test
    @DisplayName("已确认：通过，并带上确认时间")
    void confirmedPasses() {
        CpBrandBriefVo vo = filledDraft();
        vo.setStatus("CONFIRMED");
        vo.setConfirmedAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0));
        GateItem item = CreativeGateServiceImpl.brandBriefItem(vo);
        assertTrue(item.passed());
        assertTrue(item.detail().contains("2026-10-01"), item.detail());
    }
}
