package org.dromara.aigov.studio.helper;

/**
 * 对话式 Copilot 的提示词构建（附件 07 增量 6「对话式 Copilot」）。
 *
 * <h3>三条刻意的写法</h3>
 * <ol>
 *     <li><b>白名单从代码来</b>：路径清单调用 {@link AigStudioProposalPlanner#allowedPaths()}，
 *         不在这里抄一份。抄一份的结果是"代码改了、提示词没改"，模型于是持续输出一个必然被拒绝的
 *         操作——而那条错误看起来像"模型不行"，实际是提示词过期；</li>
 *     <li><b>把"不能改什么"也写出来</b>：{@link AigStudioProposalPlanner#RESERVED_PATHS}
 *         明确列出类别/能力编码/外发许可/Schema。只靠代码兜底、提示词不提，等于明知模型会踩还让它踩；</li>
 *     <li><b>输出格式写死成一段 JSON</b>：并说明"表达不了就返回空 operations"——
 *         否则模型会用一句自然语言糊过去，而那句自然语言在解析层只能被判成格式错误，
 *         人看到的原因就变成"格式不对"而不是"你的要求做不到"。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigStudioCopilotPrompts {

    private AigStudioCopilotPrompts() {
    }

    /**
     * 构建一次对话的提示词。
     *
     * @param contentJson 当前草稿内容（规范化 JSON；可空）
     * @param userMessage 用户要求（可空）
     * @return 提示词
     */
    public static String build(String contentJson, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是「岗位 Agent 训练台」的培训助手（Copilot）。\n")
            .append("你的职责是**提案**，不是改草稿：你的输出会由人审阅后决定是否采纳。\n")
            .append("你无法改类别、能力编码、外发许可与 Schema——它们不在白名单里。\n\n");

        prompt.append("## 只能使用下列路径（JSON Pointer；值写新值）\n");
        for (String path : AigStudioProposalPlanner.allowedPaths()) {
            prompt.append("- ").append(path).append('\n');
        }
        prompt.append("\n## 明确不允许改的路径（写了会被平台拒绝）\n");
        for (String path : AigStudioProposalPlanner.RESERVED_PATHS) {
            prompt.append("- ").append(path).append('\n');
        }
        prompt.append("\n## 值类型\n")
            .append("文本路径=字符串；清单路径=字符串数组；同一路径在一条提案里只能出现一次。\n");

        prompt.append("\n## 当前草稿内容（规范化 JSON）\n```json\n")
            .append(contentJson == null ? "{}" : contentJson)
            .append("\n```\n");

        prompt.append("\n## 用户要求\n").append(userMessage == null ? "（空）" : userMessage).append('\n');

        prompt.append("\n## 输出格式（严格 JSON；不要解释、不要多余文字）\n")
            .append("{\"summary\":\"一句话说明改什么\",\"operations\":[{\"path\":\"/objective\",\"value\":\"新值\"}]}\n")
            .append("如果用户的要求无法用白名单内的字段表达，就返回空 operations：")
            .append("{\"summary\":\"说明做不到的原因\",\"operations\":[]}\n");
        return prompt.toString();
    }

}
