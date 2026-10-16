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
 *     <li><b>blockers（阻断）</b>：内容结构或执行前提缺失（非法 JSON、缺分节、无能力、**能力不存在或已停用**、
 *         类别非法、Schema 非法、夹带脚本）。提交必须全部清空。</li>
 *     <li><b>warnings（提醒）</b>：不影响提交，但责任人应当看见——例如未声明输出 Schema、
 *         工具/知识「已登记待接入」（设计 §4.1 要求如实展示，不得显示为可用）、
 *         **该能力目前缺模型候选或缺路由策略**（草稿允许先存半成品，所以只能提醒，不能拦）。</li>
 * </ul>
 *
 * <p><b>能力事实从哪来</b>：本类是纯函数，不能查库；调用方先查出
 * {@link AigStudioCapabilityFacts} 再传进来。传 {@code null} 表示"本次不判能力事实"——
 * 那是<b>有意的</b>：如果把"没查"默认当成"不存在"，任何一处忘了传事实的调用都会突然报
 * "能力不存在"，那比不判更糟。</p>
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
        return evaluate(mapper, contentJson, null);
    }

    /**
     * 从内容 JSON 原文预检，并带上能力事实。
     *
     * @param mapper      序列化器
     * @param contentJson 内容原文
     * @param facts       能力事实（可为 null，表示本次不判能力事实）
     * @return 结论
     */
    public static Result evaluate(JsonMapper mapper, String contentJson, AigStudioCapabilityFacts facts) {
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
        return of(content, facts);
    }

    /**
     * 对已解析的内容预检。
     *
     * @param content 草稿内容
     * @return 结论
     */
    public static Result of(AigStudioDraftContent content) {
        return of(content, null);
    }

    /**
     * 对已解析的内容预检（含能力事实判定）。
     *
     * @param content 草稿内容
     * @param facts   能力事实（可为 null，表示本次不判能力事实）
     * @return 结论
     */
    public static Result of(AigStudioDraftContent content, AigStudioCapabilityFacts facts) {
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
        } else {
            // 2.1) 声明的能力在能力清单里的客观状态（facts 为 null 表示调用方没查，不判）
            checkCapabilityFacts(blockers, warnings, content.getProviderCapability(), facts);
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
     * 能力事实判定（阻断项与提醒项分开，规则见本类注释）。
     *
     * <p>分级理由：<b>能力不存在 / 已停用</b>是"编码错了或这个能力根本不能用"，属阻断；
     * <b>缺候选 / 缺策略 / 候选不健康</b>是"配置还没补齐"，训练台按 D07 允许先存半成品，
     * 所以只能是提醒 —— 提醒里也要写清"现在正式调用一定失败"，不能让人误以为能跑。</p>
     *
     * @param blockers 阻断项集合（写出口）
     * @param warnings 提醒项集合（写出口）
     * @param code     草稿声明的能力编码
     * @param facts    能力事实（null 表示本次不判）
     */
    private static void checkCapabilityFacts(List<AigStudioPrecheckItem> blockers,
                                             List<AigStudioPrecheckItem> warnings,
                                             String code, AigStudioCapabilityFacts facts) {
        if (facts == null) {
            return;
        }
        if (!facts.exists()) {
            blockers.add(AigStudioPrecheckItem.blocker("CAPABILITY_NOT_FOUND", "/providerCapability",
                "声明的能力「" + code + "」不在能力清单里：编码可能写错，或这个能力已被删除"
                    + "（引用不存在的能力，提交后一定调不通）",
                "到「平台治理 → 能力」里选一个已登记的能力编码；确需新能力请先登记能力本身", code));
            return;
        }
        if (facts.disabled()) {
            blockers.add(AigStudioPrecheckItem.blocker("CAPABILITY_DISABLED", "/providerCapability",
                "声明的能力「" + code + "」已停用：停用能力的所有调用都会被拒绝",
                "在能力页面启用它，或改用另一个可用能力", code));
            return;
        }
        if (facts.noCandidate()) {
            warnings.add(AigStudioPrecheckItem.warning("CAPABILITY_NO_CANDIDATE", "/providerCapability",
                "能力「" + code + "」现在没有任何启用的模型候选：路由到第 3 步会失败，正式调用一定调不通",
                "到「平台治理 → 能力 → 模型候选」给它挂一个候选（本预检按 D07 不拦草稿保存）", code));
        } else if (facts.candidateUnhealthy()) {
            warnings.add(AigStudioPrecheckItem.warning("CAPABILITY_CANDIDATE_UNHEALTHY", "/providerCapability",
                "能力「" + code + "」的模型候选当前全部不健康：调用会被路由排除，正式调用一定调不通",
                "检查模型可用性（或补一个健康候选）后再正式使用；本预检不拦草稿保存", code));
        } else if (facts.candidateUnknown()) {
            warnings.add(AigStudioPrecheckItem.warning("CAPABILITY_CANDIDATE_UNKNOWN", "/providerCapability",
                "能力「" + code + "」的模型候选尚未探测过健康状态：能否调用要到实际调用才知道",
                "如计划正式使用，建议先探测一次模型健康状态", code));
        }
        if (facts.noPolicy()) {
            warnings.add(AigStudioPrecheckItem.warning("CAPABILITY_NO_ROUTE_POLICY", "/providerCapability",
                "能力「" + code + "」一条路由策略都没配（0/" + facts.policyTotalLevels()
                    + "）：任何数据等级都无法路由，正式调用一定调不通",
                "到「平台治理 → 能力 → 路由策略」补上各数据等级的策略；本预检不拦草稿保存", code));
        } else if (facts.policyIncomplete()) {
            warnings.add(AigStudioPrecheckItem.warning("CAPABILITY_POLICY_INCOMPLETE", "/providerCapability",
                "能力「" + code + "」的路由策略不全（" + facts.policyCoveredLevels() + "/"
                    + facts.policyTotalLevels() + "）：未覆盖的数据等级走默认兜底，可能不是你要的行为",
                "补齐剩余数据等级的策略后重新预检", code));
        }
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
