package org.dromara.creative.service.impl;

import org.dromara.creative.domain.DpGateItem;
import org.dromara.creative.domain.DpGateProfile;
import org.dromara.creative.mapper.DpGateItemMapper;
import org.dromara.creative.mapper.DpGateProfileMapper;
import org.dromara.creative.service.ICreativeGateService.GateItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 创作侧闸门项配置化的单元测试（V0.2 B2，文档 §25）。
 *
 * <p>要钉住三件事——它们都是"改错了也不会报错、只会静默放开门禁"的地方：</p>
 * <ol>
 *   <li><b>默认清单＝当前策略</b>（7 项、编码/展示名/等级/顺序）。它原本是"改造前逐字一致"的
 *       golden list，用于保证重构不改变行为；内测冲突 A 之后它多了一层作用：
 *       <b>把"哪几项是硬拦"钉成需要有意修改的契约</b>——见 {@link #onlySanctionedItemsAreBlocking()}。
 *       改这张表必须是有意识的行为变更，不是顺手改的；</li>
 *   <li><b>配置只决定取舍与展示</b>：判定结果必须仍来自代码检查器（配置改等级要生效，
 *       但不能把"没通过"改写成"通过"）；</li>
 *   <li><b>未知配置项 fail-closed</b>：配置要求检查、而代码没有检查器时按未通过处理，
 *       绝不能因为"没人实现"就显示成绿的。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeGateItemConfigTest {

    @Mock
    private DpGateProfileMapper gateProfileMapper;

    @Mock
    private DpGateItemMapper gateItemMapper;

    @InjectMocks
    private CreativeGateServiceImpl service;

    /**
     * 当前策略下的默认清单（golden list）：改这里必须是"有意的行为变更"。
     *
     * <p>与改造前清单的唯一差异：{@code BRAND_BRIEF_CONFIRMED} 由 {@code CONDITION}
     * 升为 {@code BLOCK}（内测冲突 A：同一产线的同一根因只留一道硬拦）。
     * 这次变更的连带影响（存量项目会被拦）见
     * {@code script/sql/dp_gate_brand_requirement_uniform.sql} 的核对查询。</p>
     */
    private static final List<String[]> LEGACY = List.of(
        new String[]{"DNA_LOCKED", "视觉基因已锁定", "BLOCK"},
        new String[]{"REFERENCE_IMAGE", "产品参考图已上传", "BLOCK"},
        new String[]{"DIRECTION_SELECTED", "视觉方向已选定", "CONDITION"},
        new String[]{"STORYBOARD_LOCKED", "分镜已锁定", "CONDITION"},
        new String[]{"BRAND_TONE_CONFIRMED", "品牌调性已确认", "CONDITION"},
        new String[]{"BRAND_BRIEF_CONFIRMED", "品牌 Brief 已填写并确认", "BLOCK"},
        new String[]{"FORBIDDEN_WORDS_DECLARED", "已声明禁用词与合规红线", "CONDITION"}
    );

    /**
     * 硬拦项白名单（内测冲突 A 的定案）。
     *
     * <p>定案原文：<b>同一条产线的同一个根因，只留一道硬拦</b>。当前这道产线上有两个根因：</p>
     * <ol>
     *   <li><b>设计侧准备工作没做完</b> → {@code DNA_LOCKED} + {@code REFERENCE_IMAGE}
     *       （出图的输入，缺了根本渲不出来，本质是一件事的两半）；</li>
     *   <li><b>品牌部功课没做完</b> → <b>只有</b> {@code BRAND_BRIEF_CONFIRMED} 一道。
     *       {@code FORBIDDEN_WORDS_DECLARED} 与 {@code BRAND_TONE_CONFIRMED} 是它的细项，
     *       再设成 BLOCK 就是对同一根因拦第二次——使用者会在同一个门上被同样的事拒两次。</li>
     * </ol>
     *
     * <p>这条断言的作用是<b>涨价</b>：以后有人想把某一项改成 BLOCK，必须同时改这里、
     * 并回答"它是哪个根因、原来的那道拦为什么不够"。</p>
     */
    private static final Set<String> SANCTIONED_BLOCKING =
        Set.of("DNA_LOCKED", "REFERENCE_IMAGE", "BRAND_BRIEF_CONFIRMED");

    private static GateItem checked(String code, boolean passed) {
        return new GateItem(code, "标签（代码侧）", passed ? "CONDITION" : "BLOCK", passed,
            passed ? "代码说通过了" : "代码说没通过");
    }

    private static Map<String, GateItem> allChecked() {
        Map<String, GateItem> map = new LinkedHashMap<>();
        for (String[] row : LEGACY) {
            map.put(row[0], checked(row[0], true));
        }
        return map;
    }

    @Test
    @DisplayName("默认清单与改造前逐字一致（7 项，编码/展示名/等级/顺序）")
    void defaultItemsMatchLegacyList() {
        assertEquals(LEGACY.size(), CreativeGateServiceImpl.DEFAULT_ITEMS.size());
        for (int i = 0; i < LEGACY.size(); i++) {
            String[] want = LEGACY.get(i);
            CreativeGateServiceImpl.ItemSpec got = CreativeGateServiceImpl.DEFAULT_ITEMS.get(i);
            assertEquals(want[0], got.code(), "第 " + i + " 项编码");
            assertEquals(want[1], got.label(), "第 " + i + " 项展示名");
            assertEquals(want[2], got.level(), "第 " + i + " 项等级");
        }
        // 默认清单产出后也应与旧行为一致：顺序、等级、判定全部来自检查器
        List<GateItem> items = CreativeGateServiceImpl.mergeItems(CreativeGateServiceImpl.DEFAULT_ITEMS, allChecked());
        assertEquals(7, items.size());
        assertEquals("DNA_LOCKED", items.get(0).code());
        assertEquals("BLOCK", items.get(0).level());
        assertEquals("FORBIDDEN_WORDS_DECLARED", items.get(6).code());
        assertTrue(items.stream().allMatch(GateItem::passed));
    }

    @Test
    @DisplayName("硬拦项白名单：同一根因只留一道硬拦，多一道都要先解释")
    void onlySanctionedItemsAreBlocking() {
        Set<String> actual = CreativeGateServiceImpl.DEFAULT_ITEMS.stream()
            .filter(spec -> "BLOCK".equals(spec.level()))
            .map(CreativeGateServiceImpl.ItemSpec::code)
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(SANCTIONED_BLOCKING, actual,
            "硬拦项集合变了。按内测冲突 A 的定案：同一产线的同一根因只留一道硬拦——"
                + "品牌部功课的总闸是 BRAND_BRIEF_CONFIRMED，禁用词/品牌调性是它的细项，"
                + "不应各自再设一道硬拦；确需新增请先回答『它是哪个根因、原来那道拦为什么不够』");
    }

    @Test
    @DisplayName("配置决定取舍/顺序/展示名/等级；判定结果仍来自代码检查器")
    void configControlsSelectionOrderLabelAndLevel() {
        // 配置：只留 3 项、顺序倒过来、改写展示名、把"品牌调性"从 CONDITION 提成 BLOCK
        List<CreativeGateServiceImpl.ItemSpec> specs = List.of(
            new CreativeGateServiceImpl.ItemSpec("FORBIDDEN_WORDS_DECLARED", "禁用词（配置改的名字）", "CONDITION"),
            new CreativeGateServiceImpl.ItemSpec("BRAND_TONE_CONFIRMED", "品牌调性（配置提级）", "BLOCK"),
            new CreativeGateServiceImpl.ItemSpec("DNA_LOCKED", "基因（配置改的名字）", "CONDITION")
        );
        Map<String, GateItem> checked = new LinkedHashMap<>();
        checked.put("FORBIDDEN_WORDS_DECLARED", checked("FORBIDDEN_WORDS_DECLARED", true));
        checked.put("BRAND_TONE_CONFIRMED", checked("BRAND_TONE_CONFIRMED", false));
        checked.put("DNA_LOCKED", checked("DNA_LOCKED", true));

        List<GateItem> items = CreativeGateServiceImpl.mergeItems(specs, checked);
        assertEquals(3, items.size(), "配置只留 3 项就只出 3 项");
        assertEquals(List.of("FORBIDDEN_WORDS_DECLARED", "BRAND_TONE_CONFIRMED", "DNA_LOCKED"),
            items.stream().map(GateItem::code).toList(), "顺序跟配置走");
        assertEquals("禁用词（配置改的名字）", items.get(0).label(), "展示名跟配置走");
        assertEquals("CONDITION", items.get(2).level(), "配置把 BLOCK 降级为 CONDITION 要生效");
        // 关键：配置改等级、改名字，但**不能**改判定结果
        assertFalse(items.get(1).passed(), "代码说没通过，配置不能把它变绿");
        assertTrue(items.get(1).detail().contains("代码说没通过"), "详情仍来自检查器");
        assertEquals("BLOCK", items.get(1).level(), "配置提级为 BLOCK 要生效（这会阻断提交）");
    }

    @Test
    @DisplayName("未知配置项 fail-closed：没有检查器就按未通过，绝不显示成绿的")
    void unknownConfiguredItemIsFailClosed() {
        List<CreativeGateServiceImpl.ItemSpec> specs = List.of(
            new CreativeGateServiceImpl.ItemSpec("DNA_LOCKED", "视觉基因已锁定", "BLOCK"),
            new CreativeGateServiceImpl.ItemSpec("MODULE_PLAN_CONFIRMED", "页面模块已确认", "BLOCK")
        );
        Map<String, GateItem> checked = new LinkedHashMap<>();
        checked.put("DNA_LOCKED", checked("DNA_LOCKED", true));

        List<GateItem> items = CreativeGateServiceImpl.mergeItems(specs, checked);
        assertEquals(2, items.size());
        GateItem unknown = items.get(1);
        assertEquals("MODULE_PLAN_CONFIRMED", unknown.code());
        assertFalse(unknown.passed(), "没有检查逻辑必须按未通过处理");
        assertTrue(unknown.detail().contains("没有对应的检查逻辑"), unknown.detail());
        assertEquals("BLOCK", unknown.level(), "等级照配置（BLOCK 会真的阻断提交）");
        // 也就意味着：把一项还没实现的 BLOCK 项写进配置，会立刻让门变红——这是有意的
        assertTrue(items.stream().filter(i -> !i.passed()).count() == 1);
    }

    @Test
    @DisplayName("没配闸门档案时回落默认 7 项；配了就按配置（按 sort_no）")
    void configuredItemsFallsBackToDefault() {
        when(gateProfileMapper.selectList(any())).thenReturn(List.of());
        assertEquals(7, service.configuredItems("ECOM_DETAIL").size(), "没配置要回落默认清单");
        assertEquals("DNA_LOCKED", service.configuredItems("ECOM_DETAIL").get(0).code());
        assertEquals(7, service.configuredItems("  ").size(), "交付类型为空也回落默认");
        assertEquals(7, service.configuredItems(null).size());

        DpGateProfile profile = new DpGateProfile();
        profile.setId(9L);
        profile.setProfileCode("GATE_X_V1");
        when(gateProfileMapper.selectList(any())).thenReturn(List.of(profile));
        DpGateItem a = new DpGateItem();
        a.setItemCode("REFERENCE_IMAGE");
        a.setItemLabel("参考图");
        a.setLevel("BLOCK");
        a.setSortNo(10);
        DpGateItem b = new DpGateItem();
        b.setItemCode("DNA_LOCKED");
        b.setItemLabel("基因");
        b.setLevel("CONDITION");
        b.setSortNo(20);
        when(gateItemMapper.selectList(any())).thenReturn(List.of(a, b));

        List<CreativeGateServiceImpl.ItemSpec> specs = service.configuredItems("ECOM_DETAIL");
        assertEquals(2, specs.size(), "配了就只出配置里的项");
        assertEquals("REFERENCE_IMAGE", specs.get(0).code());
        assertEquals("参考图", specs.get(0).label());
        assertEquals("CONDITION", specs.get(1).level());

        // 档案存在但项为空：也要回落默认（否则门会变成"零项=全绿"）
        when(gateItemMapper.selectList(any())).thenReturn(List.of());
        assertEquals(7, service.configuredItems("ECOM_DETAIL").size(), "档案里没有项时不能变成 0 项全绿");
    }
}
