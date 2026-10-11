package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话式 Copilot 提示词测试（附件 07 增量 6）。
 *
 * <p><b>要挡住的是"提示词过期"</b>：白名单只在代码里改、提示词里还是旧的一份，模型于是持续输出
 * 一个必然被拒绝的操作——而那条错误看起来像"模型不行"。所以这里断言提示词里的路径
 * <b>就是从规划器来的那一份</b>（不是抄的）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioCopilotPromptsTest {

    private static final String CONTENT = "{\"agentName\":\"详情页文案助手\","
        + "\"providerCapability\":\"text_generation\",\"promptSections\":{\"role\":\"策划\"}}";

    @Test
    @DisplayName("★ 提示词里的白名单必须与规划器一致（提示词由代码生成，不手工抄一份）")
    void whitelistComesFromPlanner() {
        String prompt = AigStudioCopilotPrompts.build(CONTENT, "把目标改一下");

        for (String path : AigStudioProposalPlanner.allowedPaths()) {
            assertTrue(prompt.contains("- " + path + "\n"),
                "提示词缺少白名单路径 " + path + "（代码改了提示词没改，模型会一直输出必然被拒绝的操作）");
        }
        // 八个 Prompt 分节都要展开出现（不是只给一个前缀）
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            assertTrue(prompt.contains("/promptSections/" + key), "缺分节 " + key);
        }
    }

    @Test
    @DisplayName("★ 提示词必须把「不能改什么」也写出来（只靠代码兜底等于明知会踩还让它踩）")
    void reservedPathsAreStated() {
        String prompt = AigStudioCopilotPrompts.build(CONTENT, "换个类别");

        for (String path : AigStudioProposalPlanner.RESERVED_PATHS) {
            assertTrue(prompt.contains(path), "提示词没有说明不允许改 " + path);
        }
        assertTrue(prompt.contains("明确不允许改的路径"), prompt);
    }

    @Test
    @DisplayName("提示词带上当前内容与用户要求，并要求严格 JSON 输出")
    void carriesContentAndInstruction() {
        String prompt = AigStudioCopilotPrompts.build(CONTENT, "把主要责任改成出分镜");

        assertTrue(prompt.contains(CONTENT), "要带上当前草稿内容，否则模型无法基于现状改");
        assertTrue(prompt.contains("把主要责任改成出分镜"));
        assertTrue(prompt.contains("operations"));
        assertTrue(prompt.contains("严格 JSON"));
        assertTrue(prompt.contains("提案"), "要说清它只提案、不改草稿");
        // 表达不了时允许空 operations（否则模型会拿自然语言糊过去，人看到的原因变成"格式不对"）
        assertTrue(prompt.contains("\"operations\":[]"));
    }

    @Test
    @DisplayName("内容为空时也给出合法的 JSON 占位（不拼出半个 JSON 让模型猜）")
    void handlesBlankInputs() {
        String prompt = AigStudioCopilotPrompts.build(null, null);

        assertTrue(prompt.contains("{}"), prompt);
        assertTrue(prompt.contains("（空）"), prompt);
        assertFalse(prompt.contains("null"), "不要把 Java 的 null 拼进去：" + prompt);
    }

}
