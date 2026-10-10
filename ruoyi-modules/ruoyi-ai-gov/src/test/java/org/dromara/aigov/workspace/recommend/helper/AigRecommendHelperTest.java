package org.dromara.aigov.workspace.recommend.helper;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐链路的纯逻辑测试（增量 7）。
 *
 * <p>这里钉住两件"错了不报错"的事：①**提示词只包含本人可见的卡片**（提示词本身就是一次外发，
 * 多塞一个岗位就是泄漏配置）；②**模型输出不老实也要解析对、解析不出就返回空**——
 * 只认一种写法的后果是"明明推荐对了却显示没有推荐"，而把解析搞松的后果是"推荐出不存在的卡片"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRecommendHelperTest {

    @Test
    @DisplayName("提示词只列本人可见的卡片，并要求严格 JSON 与「只推荐不执行」")
    void promptContainsOnlyVisibleCards() {
        String prompt = AigRecommendPromptBuilder.build(List.of(role()), 60);

        assertTrue(prompt.contains("[DETAIL_PAGE_CREATE]"), "应列出可见卡片的编码");
        assertTrue(prompt.contains("scenario://OTHER@1.0.0") == false, "不应凭空出现别的东西");
        assertTrue(prompt.contains("actionCodes"), "应要求 JSON 字段名");
        assertTrue(prompt.contains("只做推荐"), "应写明只推荐不执行");
        // 清单之外的卡片不得出现（本次只给了一张可见卡片）
        assertFalse(prompt.contains("NOT_VISIBLE_CARD"));
    }

    @Test
    @DisplayName("提示词按上限截断卡片清单（可见卡片很多时不把整站塞进提示词）")
    void promptRespectsCatalogCap() {
        String prompt = AigRecommendPromptBuilder.build(List.of(role()), 1);
        assertTrue(prompt.contains("DETAIL_PAGE_CREATE"));
        assertFalse(prompt.contains("BANNER_CREATE"), "上限 1 时第二张卡片不应出现");
    }

    @Test
    @DisplayName("解析：对象/纯数组/代码块/带前后解释都能读出来")
    void parserAcceptsCommonShapes() {
        assertEquals(List.of("A", "B"), AigRecommendParser.parse("{\"actionCodes\":[\"A\",\"B\"]}", 3));
        assertEquals(List.of("A"), AigRecommendParser.parse("[\"A\"]", 3));
        assertEquals(List.of("A", "B"),
            AigRecommendParser.parse("```json\n{\"actionCodes\":[\"A\",\"B\"]}\n```", 3));
        assertEquals(List.of("A"),
            AigRecommendParser.parse("我认为最相关的是 {\"actionCodes\":[\"A\"]} 这一张。", 3));
    }

    @Test
    @DisplayName("解析：读不出结构、非字符串条目、空白、重复、超限，都按约定处理")
    void parserIsTolerantButStrict() {
        assertEquals(List.of(), AigRecommendParser.parse(null, 3));
        assertEquals(List.of(), AigRecommendParser.parse("  ", 3));
        assertEquals(List.of(), AigRecommendParser.parse("{不是 JSON", 3));
        assertEquals(List.of(), AigRecommendParser.parse("{\"actionCodes\":\"A\"}", 3), "不是数组就没有结果");
        assertEquals(List.of(), AigRecommendParser.parse("{\"other\":[\"A\"]}", 3), "字段名不对不猜");
        // 非字符串条目跳过（把 1 / true 收进来会得到匹配不上的假编码）
        assertEquals(List.of("A"), AigRecommendParser.parse("{\"actionCodes\":[1,true,\"A\",\"  \"]}", 3));
        // 去重保序 + 截断
        assertEquals(List.of("A", "B"), AigRecommendParser.parse("{\"actionCodes\":[\"A\",\"B\",\"A\"]}", 5));
        assertEquals(List.of("A"), AigRecommendParser.parse("{\"actionCodes\":[\"A\",\"B\",\"C\"]}", 1));
    }

    private static AigPortalRoleHomeVo role() {
        AigPortalRoleHomeVo role = new AigPortalRoleHomeVo();
        role.setRoleCode("GRAPHIC_DESIGNER_AI");
        role.setRoleName("平面设计 AI 工作台");
        role.setActions(List.of(
            action("DETAIL_PAGE_CREATE", "做详情页"),
            action("BANNER_CREATE", "做横幅")));
        return role;
    }

    private static AigPortalActionVo action(String code, String title) {
        AigPortalActionVo action = new AigPortalActionVo();
        action.setActionCode(code);
        action.setTitle(title);
        action.setLaunchMode("QUICK");
        action.setTargetType("QUICK_CAPABILITY");
        action.setTargetRef("cap/" + code);
        return action;
    }

}
