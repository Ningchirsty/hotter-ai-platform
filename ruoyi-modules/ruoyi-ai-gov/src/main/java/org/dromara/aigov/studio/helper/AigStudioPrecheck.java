package org.dromara.aigov.studio.helper;

import org.dromara.aigov.agent.enums.AigAgentCategoryEnum;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.vo.AigStudioPrecheckItem;
import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 草稿分级预检（设计 D07「分级预检，结构与执行检查前移」、§9.3）。
 *
 * <p><b>为什么要有这一处、而不是继续写在服务里</b>：预检与提交必须用<b>同一套</b>判据——
 * 若预检说"通过"而提交却拒绝，用户会认为预检在骗人；若提交悄悄放宽，不合格内容就进了发布流程。
 * 把规则收敛成一个纯函数后，两个入口共用它，判据不可能分叉。</p>
 *
 * <p><b>分级（本方法的输出）</b>：</p>
 * <ul>
 *     <li><b>blockers（阻断）</b>：内容结构或执行前提缺失（非法 JSON、缺分节、无能力、类别非法、
 *         Schema 非法、夹带脚本）。提交必须全部清空。</li>
 *     <li><b>warnings（提醒）</b>：不影响提交，但责任人应当看见——例如未声明输出 Schema、
 *         工具/知识「已登记待接入」（设计 §4.1 要求如实展示，不得显示为可用）。</li>
 * </ul>
 *
 * @author ai-gov
 */
public final class AigStudioPrecheck {

    /**
     * 只用于"能否解析成 JSON"这一层判断的序列化器（读取是无副作用的，可共享）。
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AigStudioPrecheck() {
    }

    /**
     * 预检结论。
     *
     * @param blockers 阻断项
     * @param warnings 提醒项
     */
    public record Result(List<AigStudioPrecheckItem> blockers, List<AigStudioPrecheckItem> warnings) {

        /**
         * 是否通过（阻断为空即通过；提醒不拦提交）。
         *
         * @return 通过返回 true
         */
        public boolean passed() {
            return blockers.isEmpty();
        }

        /**
         * 阻断项的人话清单（用于旧调用方与提交拒绝文案）。
         *
         * @return 说明列表
         */
        public List<String> blockerMessages() {
            List<String> messages = new ArrayList<>();
            for (AigStudioPrecheckItem item : blockers) {
                messages.add(item.getMessage());
            }
            return messages;
        }
    }

    /**
     * 从内容 JSON 原文预检（含"是否合法 JSON"这一层）。
     *
     * @param mapper      序列化器
     * @param contentJson 内容原文
     * @return 结论
     */
    public static Result evaluate(JsonMapper mapper, String contentJson) {
        AigStudioDraftContent content;
        try {
            content = mapper.readValue(contentJson, AigStudioDraftContent.class);
        } catch (Exception e) {
            return new Result(List.of(AigStudioPrecheckItem.blocker("CONTENT_NOT_JSON", "",
                "内容不是合法 JSON：" + e.getMessage(), "修正内容格式后重试", null)), List.of());
        }
        if (content == null) {
            return new Result(List.of(AigStudioPrecheckItem.blocker("CONTENT_EMPTY", "",
                "内容为空", "填写至少一个 Prompt 分节", null)), List.of());
        }
        return of(content);
    }

    /**
     * 对已解析的内容预检。
     *
     * @param content 草稿内容
     * @return 结论
     */
    public static Result of(AigStudioDraftContent content) {
        List<AigStudioPrecheckItem> blockers = new ArrayList<>();
        List<AigStudioPrecheckItem> warnings = new ArrayList<>();

        // 1) 八个分节：键齐 + 至少一节非空
        Map<String, String> sections = content.getPromptSections() == null
            ? Map.of() : content.getPromptSections();
        List<String> missing = AigStudioDraftContent.PROMPT_SECTION_KEYS.stream()
            .filter(key -> !sections.containsKey(key))
            .toList();
        if (!missing.isEmpty()) {
            blockers.add(AigStudioPrecheckItem.blocker("SECTION_MISSING", "/promptSections",
                "缺少 Prompt 分节：" + String.join("、", missing)
                    + "（八个分节是 Diff 与缺节校验的唯一口径，不能少）",
                "补齐后重新预检", String.join(",", missing)));
        }
        boolean allBlank = sections.values().stream().allMatch(StringUtils::isBlank);
        if (allBlank) {
            blockers.add(AigStudioPrecheckItem.blocker("SECTIONS_ALL_BLANK", "/promptSections",
                "至少填写一个 Prompt 分节（当前全部为空）",
                "至少填写角色定位/任务目标等一节正文", null));
        }

        // 2) 执行前提：能力（没有它无法路由）
        if (StringUtils.isBlank(content.getProviderCapability())) {
            blockers.add(AigStudioPrecheckItem.blocker("CAPABILITY_MISSING", "/providerCapability",
                "未声明所需能力（providerCapability）：没有它无法路由，提交后也调不通",
                "选择一个能力编码（如 text_generation）", null));
        }

        // 3) 类别：D07 把"合法类别"从提交阶段前移到预检（执行前提，必须显式声明）
        AigAgentCategoryEnum category = AigAgentCategoryEnum.find(content.getAgentCategory());
        if (category == null) {
            blockers.add(AigStudioPrecheckItem.blocker("CATEGORY_MISSING", "/agentCategory",
                "草稿未声明合法的 Agent 类别（agentCategory=" + content.getAgentCategory()
                    + "）：当前只接受 " + categoryCodes(),
                "选择上述类别之一；非创作类 Agent 需要的新类别属独立变更", null));
        }

        // 4) 外发许可取值
        String allowExternal = content.getAllowExternal();
        if (StringUtils.isNotBlank(allowExternal)
            && !"Y".equalsIgnoreCase(allowExternal) && !"N".equalsIgnoreCase(allowExternal)) {
            blockers.add(AigStudioPrecheckItem.blocker("ALLOW_EXTERNAL_INVALID", "/allowExternal",
                "allowExternal 只能是 Y 或 N，当前=" + allowExternal,
                "改为 Y 或 N（内部/敏感资料应为 N）", null));
        }

        // 5) Schema 合法性
        checkJsonField(blockers, "输入 Schema", "/inputSchema", content.getInputSchema());
        checkJsonField(blockers, "输出 Schema", "/outputSchema", content.getOutputSchema());
        if (StringUtils.isBlank(content.getOutputSchema())) {
            warnings.add(AigStudioPrecheckItem.warning("OUTPUT_SCHEMA_MISSING", "/outputSchema",
                "未声明输出 Schema：非结构化输出无法做 Schema 结论判定",
                "如该 Agent 输出结构化结果，建议补上输出 Schema", null));
        }

        // 6) 页面定制不得夹带脚本
        String page = content.getPageCustomizationJson();
        if (StringUtils.isNotBlank(page)) {
            String lower = page.toLowerCase(Locale.ROOT);
            if (lower.contains("<script") || lower.contains("javascript:")) {
                blockers.add(AigStudioPrecheckItem.blocker("PAGE_SCRIPT_FORBIDDEN", "/pageCustomizationJson",
                    "页面定制里出现了可执行脚本（<script / javascript:）：门户只接受安全组件白名单，不接受任意脚本",
                    "移除脚本，改用允许的组件配置", null));
            }
        }

        // 7) 工具/知识：如实提示「已登记、待接入」（设计 §4.1）
        if ((content.getAllowedTools() != null && !content.getAllowedTools().isEmpty())
            || (content.getKnowledgeScope() != null && !content.getKnowledgeScope().isEmpty())) {
            warnings.add(AigStudioPrecheckItem.warning("RESOURCE_REGISTERED_ONLY", "/allowedTools",
                "已声明工具/知识范围，但当前平台<只登记、不执行>：它们不会真正参与运行",
                "如需要真实执行，请等待知识/工具接入后再声明为可用", null));
        }
        return new Result(blockers, warnings);
    }

    /**
     * 可用的类别编码串（报错文案用）。
     *
     * @return 形如 {@code PLANNING/VISUAL_DNA/GENERATION/QA/ANALYSIS}
     */
    public static String categoryCodes() {
        StringBuilder codes = new StringBuilder();
        for (AigAgentCategoryEnum item : AigAgentCategoryEnum.values()) {
            if (codes.length() > 0) {
                codes.append('/');
            }
            codes.append(item.getCode());
        }
        return codes.toString();
    }

    private static void checkJsonField(List<AigStudioPrecheckItem> blockers, String label,
                                       String fieldPath, String json) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            // 只判"能否解析成 JSON"，不判 Schema 合法性——两者是不同层次的结论
            JSON.readValue(json, Object.class);
        } catch (Exception e) {
            blockers.add(AigStudioPrecheckItem.blocker("SCHEMA_INVALID", fieldPath,
                label + "不是合法 JSON", "修正 JSON 语法", null));
        }
    }

}
