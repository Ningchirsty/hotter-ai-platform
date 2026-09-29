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
 *   <li><b>默认清单与改造前逐字一致</b>（7 项、编码/展示名/等级/顺序）；</li>
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
     * 改造前的写死清单（golden list）：改这里必须是"有意的行为变更"。
     */
    private static final List<String[]> LEGACY = List.of(
        new String[]{"DNA_LOCKED", "视觉基因已锁定", "BLOCK"},
        new String[]{"REFERENCE_IMAGE", "产品参考图已上传", "BLOCK"},
        new String[]{"DIRECTION_SELECTED", "视觉方向已选定", "CONDITION"},
        new String[]{"STORYBOARD_LOCKED", "分镜已锁定", "CONDITION"},
        new String[]{"BRAND_TONE_CONFIRMED", "品牌调性已确认", "CONDITION"},
        new String[]{"BRAND_BRIEF_CONFIRMED", "品牌 Brief 已填写并确认", "CONDITION"},
        new String[]{"FORBIDDEN_WORDS_DECLARED", "已声明禁用词与合规红线", "CONDITION"}
    );

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
