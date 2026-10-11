package org.dromara.aigov.agent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 类别的封闭集合守卫（2026-10-11 扩值随行加入）。
 *
 * <h3>它防的是什么</h3>
 * <p>类别是"按类统计质量/成本/采纳率"的分组键，也是"我要找哪类 Agent"的筛选维度。
 * 扩值本身没问题，但**悄悄扩值**会同时造成两种静默失效：</p>
 * <ul>
 *     <li>新类别有了实现、界面下拉里却没有它 → 那个人永远配不出来（配不出也只会表现为"没人用"）；</li>
 *     <li>旧类别被改名/删掉 → 历史数据的统计分组凭空少一栏，而没有任何一处会报错。</li>
 * </ul>
 * <p>所以把取值集合钉成构建期断言：扩值必须同时改这里——那一刻人必须回答
 * "这个新类别谁来定义、按什么统计"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentCategoryContractTest {

    /**
     * 当前允许的取值集合（**双向**断言：多一个少一个都红）
     */
    private static final Set<String> EXPECTED = Set.of(
        "PLANNING", "VISUAL_DNA", "GENERATION", "QA", "ANALYSIS");

    @Test
    @DisplayName("★类别取值集合封闭：扩值必须同时改这条用例")
    void categoryCodesAreClosedSet() {
        Set<String> actual = new LinkedHashSet<>();
        for (AigAgentCategoryEnum item : AigAgentCategoryEnum.values()) {
            actual.add(item.getCode());
        }
        assertEquals(EXPECTED, actual, "Agent 类别集合被改动了；扩值/改名都要同时更新界面下拉与统计口径");
        assertTrue(actual.size() >= 5, "类别数少于 5，可能读错了枚举：" + actual);
    }

    @Test
    @DisplayName("编码形如大写常量（入库/筛选口径），且描述不为空")
    void codesAreUppercaseAndDescribed() {
        for (AigAgentCategoryEnum item : AigAgentCategoryEnum.values()) {
            assertTrue(item.getCode().matches("[A-Z][A-Z0-9_]*"),
                item.name() + " 的编码不是大写常量形态：" + item.getCode());
            assertTrue(item.getDesc() != null && !item.getDesc().isBlank(),
                item.name() + " 缺描述：界面下拉要靠它显示");
        }
    }

    @Test
    @DisplayName("查找大小写不敏感、两侧空白可容忍；认不出就返回 null（调用方据此拒绝）")
    void findIsTolerantOnShapeButStrictOnUnknown() {
        assertSame(AigAgentCategoryEnum.QA, AigAgentCategoryEnum.find("qa"));
        assertSame(AigAgentCategoryEnum.PLANNING, AigAgentCategoryEnum.find("  PLANNING  "));
        assertSame(AigAgentCategoryEnum.ANALYSIS, AigAgentCategoryEnum.find("analysis"));
        assertNull(AigAgentCategoryEnum.find("NOT_A_CATEGORY"));
        assertNull(AigAgentCategoryEnum.find(""));
        assertNull(AigAgentCategoryEnum.find(null));
    }

}
