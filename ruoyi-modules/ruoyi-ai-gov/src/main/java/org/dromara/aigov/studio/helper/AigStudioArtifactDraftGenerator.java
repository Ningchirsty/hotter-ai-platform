package org.dromara.aigov.studio.helper;

import org.dromara.aigov.agent.enums.AigAgentCategoryEnum;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;
import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 草案生成器：把三类草案映射成<b>一份可提交的训练台草稿内容</b>
 * （{@code AigStudioDraftContent}；附件 07 增量 7／ST-005/006/007）。
 *
 * <h3>它保证什么</h3>
 * <ol>
 *     <li><b>只产内容，不产版本</b>：结果是一份"能过预检、能被提交"的草稿内容；
 *         提交之后走的是既有五道门槛（附件 07 §286：只产草案，不等同可执行）；</li>
 *     <li><b>八个 Prompt 分节一律齐全</b>：预检按 {@code containsKey} 判"缺节"，
 *         少一个键就直接不通过。所以哪怕没有内容也要把键摆出来（值为空串），
 *         否则生成物连预检都过不了；</li>
 *     <li><b>缺了必需的东西就拒绝，不猜</b>：类别（{@code agentCategory}）与能力
 *         （{@code providerCapability}）是必填——替它猜一个会猜出一个与内容毫不相干的实现绑定，
 *         而且要到提交时才暴露；</li>
 *     <li><b>没写进内容的字段要说出来</b>：草案里有几项（父 Agent、启动方式与启动目标）
 *         在草稿内容模型里<b>根本没有槽位</b>。静默丢掉比不写更糟，因此它们进 {@code notes}
 *         让页面明确告诉人"这项要你去别处配"。</li>
 * </ol>
 *
 * <h3>为什么是纯静态函数</h3>
 * <p>生成规则是"错了不报错"的那类：少一个分节键、能力取错字段、把空值当成"没问题"，
 * 结果都只是"生成了一份看起来正常的草稿"，直到提交或上线才炸。纯函数才能把每条规则钉死。</p>
 *
 * @author ai-gov
 */
public final class AigStudioArtifactDraftGenerator {

    /**
     * 预检用的默认 Schema（合法 JSON：预检会校验它能不能解析）
     */
    private static final String DEFAULT_SCHEMA = "{\"type\":\"object\"}";

    /**
     * 解析 child-agent 草案里的八分节 JSON
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AigStudioArtifactDraftGenerator() {
    }

    /**
     * 生成结果。
     *
     * @param content 可提交的草稿内容（八分节齐全、能力与类别已校验）
     * @param notes   人读说明：没进内容的字段、留空待补的分节（可为空但不为 null）
     */
    public record Result(AigStudioDraftContent content, List<String> notes) {
    }

    /**
     * 技能草案 → 草稿内容。
     *
     * @param draft 技能草案
     * @return 生成结果
     * @throws IllegalArgumentException 必填缺失（技能编码 / 类别 / 能力）或字段非法
     */
    public static Result forSkill(AigStudioSkillDraft draft) {
        require(draft != null, "技能草案不能为空");
        String skillCode = requireText(draft.getSkillCode(), "技能编码（skillCode）");
        String category = requireCategory(draft.getAgentCategory());
        String capability = requireCapability(draft.getProviderCapability());
        String skillName = StringUtils.blankToDefault(draft.getSkillName(), skillCode);

        List<String> notes = new ArrayList<>();
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("role", "负责使用技能「" + skillName + "」完成相关任务");
        sections.put("objective", StringUtils.blankToDefault(draft.getDescription(),
            "使用技能「" + skillName + "」产出该技能声明范围内的结果"));
        sections.put("tools_and_skills", buildSkillSection(skillName, skillCode, draft.getDescription()));
        sections.put("workflow_rules", draft.getUsageNotes());

        AigStudioDraftContent content = baseContent(
            StringUtils.blankToDefault(draft.getDraftName(), skillName + " · 技能助手"), sections);
        content.setAgentCategory(category);
        content.setProviderCapability(capability);
        content.setAllowedSkills(new ArrayList<>(List.of(skillCode)));
        List<String> tools = splitCodes(draft.getAllowedTools());
        if (!tools.isEmpty()) {
            content.setAllowedTools(tools);
        }
        List<String> capabilities = splitCodes(draft.getCapabilities());
        if (!capabilities.isEmpty()) {
            content.setCoreCapabilities(capabilities);
        }

        notes.add("技能编码「" + skillCode + "」写进了 allowedSkills（该 Agent 可用的技能声明）。");
        if (!capabilities.isEmpty()) {
            notes.add("草案声明的 capabilities 写进了 coreCapabilities（人读清单），不参与路由；"
                + "路由只认 providerCapability。");
        }
        addEmptySectionNote(sections, notes);
        return new Result(content, notes);
    }

    /**
     * 子 Agent 草案 → 草稿内容。
     *
     * @param draft 子 Agent 草案
     * @return 生成结果
     * @throws IllegalArgumentException 必填缺失（类别 / 能力）或八分节 JSON 非法
     */
    public static Result forChildAgent(AigStudioChildAgentDraft draft) {
        require(draft != null, "子 Agent 草案不能为空");
        String category = requireCategory(draft.getAgentCategory());
        String capability = requireCapability(draft.getProviderCapability());
        Map<String, String> provided = parseSections(draft.getPromptSectionsJson());

        List<String> notes = new ArrayList<>();
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("role", StringUtils.blankToDefault(provided.get("role"), draft.getRoleDescription()));
        sections.put("objective", StringUtils.blankToDefault(provided.get("objective"), draft.getObjective()));
        sections.put("constraints",
            StringUtils.blankToDefault(provided.get("constraints"), draft.getProhibitions()));
        sections.put("output_contract",
            StringUtils.blankToDefault(provided.get("output_contract"), draft.getOutputSummary()));
        // 草案没填的分节仍然照抄（哪怕是空串），保证"八个键齐全"
        for (Map.Entry<String, String> entry : provided.entrySet()) {
            sections.putIfAbsent(entry.getKey(), entry.getValue());
        }

        AigStudioDraftContent content = baseContent(
            StringUtils.blankToDefault(draft.getAgentName(),
                StringUtils.blankToDefault(draft.getDraftName(), draft.getAgentCode())),
            sections);
        content.setAgentCategory(category);
        content.setProviderCapability(capability);
        content.setRoleDescription(draft.getRoleDescription());
        content.setObjective(draft.getObjective());
        content.setProhibitions(draft.getProhibitions());
        content.setOutputSummary(draft.getOutputSummary());
        // 缺省不允许外发：更安全的那一侧，且与提交时的缺省口径一致
        content.setAllowExternal(StringUtils.blankToDefault(
            StringUtils.trimToNull(draft.getAllowExternal()), "N"));
        List<String> tools = splitCodes(draft.getAllowedTools());
        if (!tools.isEmpty()) {
            content.setAllowedTools(tools);
        }
        List<String> knowledge = splitCodes(draft.getKnowledgeScope());
        if (!knowledge.isEmpty()) {
            content.setKnowledgeScope(knowledge);
        }

        if (StringUtils.isNotBlank(draft.getParentAgentCode())) {
            notes.add("父 Agent 编码「" + draft.getParentAgentCode()
                + "」是 Agent 定义上的归属关系，草稿内容里没有这个槽位——生成不会替你建立父子关系，"
                + "请在 Agent 定义/岗位包侧维护。");
        }
        if (StringUtils.isNotBlank(draft.getAgentCode())) {
            notes.add("Agent 编码「" + draft.getAgentCode()
                + "」不在草稿内容里：提交时用的是训练台草稿自身的编码，请确保目标草稿用这个编码。");
        }
        addEmptySectionNote(sections, notes);
        return new Result(content, notes);
    }

    /**
     * 快捷指令草案 → 草稿内容。
     *
     * @param draft 快捷指令草案
     * @return 生成结果
     * @throws IllegalArgumentException 必填缺失（类别 / 能力）或字段非法
     */
    public static Result forAction(AigStudioActionDraft draft) {
        require(draft != null, "快捷指令草案不能为空");
        String category = requireCategory(draft.getAgentCategory());
        String capability = requireCapability(draft.getProviderCapability());
        String actionName = StringUtils.blankToDefault(draft.getActionName(), draft.getActionCode());

        List<String> notes = new ArrayList<>();
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("role", "执行岗位快捷指令「" + actionName + "」");
        sections.put("objective", StringUtils.blankToDefault(draft.getObjective(),
            "完成快捷指令「" + actionName + "」"));
        sections.put("output_contract", draft.getOutputContract());

        AigStudioDraftContent content = baseContent(
            StringUtils.blankToDefault(draft.getDraftName(), actionName), sections);
        content.setAgentCategory(category);
        content.setProviderCapability(capability);
        content.setObjective(StringUtils.blankToDefault(draft.getObjective(),
            "完成快捷指令「" + actionName + "」"));
        content.setOutputSummary(draft.getOutputContract());
        List<String> tools = splitCodes(draft.getAllowedTools());
        if (!tools.isEmpty()) {
            content.setAllowedTools(tools);
        }

        notes.add("启动方式（launchMode=" + StringUtils.blankToDefault(draft.getLaunchMode(), "未填")
            + "）与启动目标（targetType=" + StringUtils.blankToDefault(draft.getTargetType(), "未填")
            + "，targetRef=" + StringUtils.blankToDefault(draft.getTargetRef(), "未填")
            + "）属于岗位卡片（aig_role_action）配置，不在草稿内容里——生成不会替你写岗位卡，"
            + "请到岗位包里配。");
        addEmptySectionNote(sections, notes);
        return new Result(content, notes);
    }

    /* ------------------------------------------------------------------ 内部 */

    /**
     * 造一份基础内容：八分节齐全（缺的填空串）、合法 Schema。
     *
     * @param agentName 展示名
     * @param sections  已知分节（其余补空串，保证八个键都在）
     * @return 内容
     */
    private static AigStudioDraftContent baseContent(String agentName, Map<String, String> sections) {
        AigStudioDraftContent content = new AigStudioDraftContent();
        content.setAgentName(requireText(agentName, "Agent 名称"));
        Map<String, String> filled = new LinkedHashMap<>();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            String text = sections.get(key);
            filled.put(key, text == null ? "" : text);
        }
        content.setPromptSections(filled);
        content.setInputSchema(DEFAULT_SCHEMA);
        content.setOutputSchema(DEFAULT_SCHEMA);
        return content;
    }

    /**
     * 组装 {@code tools_and_skills} 分节。
     *
     * @param skillName   技能名
     * @param skillCode   技能编码
     * @param description 技能说明
     * @return 分节正文
     */
    private static String buildSkillSection(String skillName, String skillCode, String description) {
        StringBuilder text = new StringBuilder();
        text.append("技能：").append(skillName).append("（").append(skillCode).append("）");
        if (StringUtils.isNotBlank(description)) {
            text.append('\n').append(description.trim());
        }
        return text.toString();
    }

    /**
     * 解析八分节 JSON（非对象/非法 JSON 直接拒绝：静默当成"没填"会生成一份空内容）。
     *
     * @param json JSON 文本（可空）
     * @return 分节表（键 → 正文）
     * @throws IllegalArgumentException 不是合法 JSON 对象
     */
    private static Map<String, String> parseSections(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("promptSectionsJson 不是合法 JSON：" + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("promptSectionsJson 必须是 JSON 对象（分节键 → 正文）");
        }
        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            sections.put(key, value == null ? "" : String.valueOf(value));
        }
        return sections;
    }

    /**
     * 把"留空待补"的分节列进说明（不替人编造内容，但要说清还差什么）。
     *
     * @param sections 分节表
     * @param notes    说明（就地追加）
     */
    private static void addEmptySectionNote(Map<String, String> sections, List<String> notes) {
        List<String> empty = new ArrayList<>();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            if (StringUtils.isBlank(sections.get(key))) {
                empty.add(key);
            }
        }
        if (!empty.isEmpty()) {
            notes.add("以下分节留空待补充（生成器不替你编内容）：" + String.join("、", empty));
        }
    }

    /**
     * 校验类别（必填且必须是已登记的类别）。
     *
     * @param category 类别编码
     * @return 规范化后的类别编码
     * @throws IllegalArgumentException 缺失或未登记
     */
    private static String requireCategory(String category) {
        if (StringUtils.isBlank(category)) {
            throw new IllegalArgumentException("未声明 Agent 类别（agentCategory）："
                + "生成物是一份 Agent 内容，类别决定它绑定哪个实现，平台不替草案猜一个");
        }
        AigAgentCategoryEnum found = AigAgentCategoryEnum.find(category);
        if (found == null) {
            throw new IllegalArgumentException("未登记的 Agent 类别：" + category.trim() + "；可选："
                + describeCategories());
        }
        return found.getCode();
    }

    /**
     * 校验能力编码（必填：没有它提交后无法路由）。
     *
     * @param capability 能力编码
     * @return 规范化后的能力编码
     * @throws IllegalArgumentException 缺失
     */
    private static String requireCapability(String capability) {
        if (StringUtils.isBlank(capability)) {
            throw new IllegalArgumentException("未声明所需能力（providerCapability）："
                + "没有它无法路由，提交后也调不通");
        }
        return capability.trim();
    }

    /**
     * 校验文本必填。
     *
     * @param value 值
     * @param label 字段中文名
     * @return 规范化后的文本
     * @throws IllegalArgumentException 缺失
     */
    private static String requireText(String value, String label) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    /**
     * 断言。
     *
     * @param condition 条件
     * @param message   失败信息
     * @throws IllegalArgumentException 条件不成立
     */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 拆分逗号分隔的编码串（去空白、丢空项）。
     *
     * @param csv 逗号分隔串
     * @return 编码清单（可能为空，不为 null）
     */
    private static List<String> splitCodes(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * 已登记类别（用于报错文案）。
     *
     * @return 形如 {@code PLANNING/VISUAL_DNA/...}
     */
    private static String describeCategories() {
        StringBuilder codes = new StringBuilder();
        for (AigAgentCategoryEnum item : AigAgentCategoryEnum.values()) {
            if (codes.length() > 0) {
                codes.append('/');
            }
            codes.append(item.getCode());
        }
        return codes.toString();
    }

}
