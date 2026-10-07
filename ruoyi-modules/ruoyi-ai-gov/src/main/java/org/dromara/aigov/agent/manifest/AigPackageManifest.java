package org.dromara.aigov.agent.manifest;

import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Manifest 的只读解析视图（设计 §6.1 最小字段集）。
 *
 * <p>只做「取值」，不做判断——判断全部在 {@link AigPackageManifestValidator} 里。
 * 这样测试可以单独验证「字段被正确读出」，而规则判定有自己的一组用例，两件事不会互相掩盖。</p>
 *
 * <p><b>两个必须写清楚的字段口径</b>：</p>
 * <ol>
 *     <li>{@code checksum} 指的是<b>包体</b>（Manifest 所依附的那份内容物）的 SHA-256，
 *         <b>不是</b> Manifest 自身的哈希。原因：Manifest 无法声明自己的哈希——那是一个自指，
 *         声明的值与算出来的值永远无法互相印证（往文档里写哈希会改变文档的哈希）。
 *         Manifest 自身的哈希由平台对<b>库中存储的字节</b>计算，落在
 *         {@code aig_package_version.manifest_hash}（见 {@link AigPackageManifestValidator#manifestHash}）。</li>
 *     <li>{@code data_level} 取设计 §12 的四档口径（PUBLIC/INTERNAL/RESTRICTED/STRICT），
 *         与平台既有的 {@code AigDataLevelEnum} 同一套取值，不另立一套「Manifest 专用等级」。</li>
 * </ol>
 *
 * @param raw             原始 JSON 节点（供需要完整结构的调用方使用，如安装时读 dependencies）
 * @param packageCode     包编码
 * @param name            包名称
 * @param publisher       发布方
 * @param version         版本号
 * @param license         许可证
 * @param checksum        包体 SHA-256
 * @param packageType     包类型（AGENT/SKILL/MIXED）
 * @param dataLevel       数据等级
 * @param networkAccess   外网访问声明（NONE/EXTERNAL）
 * @param minPlatformVersion 最低平台版本
 * @param capabilities    能力编码清单
 * @param scenarioCodes   适用场景编码清单
 * @param requiredTools   所需工具
 * @param forbiddenTools  声明禁止的工具
 * @param roles           所需角色
 * @param goldenCases     黄金用例标识清单
 * @param networkHosts    声明需要的外网主机清单
 * @param knowledgeScope  知识范围
 * @param upgradePolicy   升级策略
 * @param rollbackPolicy  回滚策略
 * @param agents          声明的 Agent 清单（安装时据此建 aig_agent + 版本）
 * @param skills          声明的 Skill 清单（安装时据此建 aig_skill + 版本）
 * @author ai-gov
 */
public record AigPackageManifest(
    JsonNode raw,
    String packageCode,
    String name,
    String publisher,
    String version,
    String license,
    String checksum,
    String packageType,
    String dataLevel,
    String networkAccess,
    String minPlatformVersion,
    List<String> capabilities,
    List<String> scenarioCodes,
    List<String> requiredTools,
    List<String> forbiddenTools,
    List<String> roles,
    List<String> goldenCases,
    List<String> networkHosts,
    List<String> knowledgeScope,
    String upgradePolicy,
    String rollbackPolicy,
    List<AigManifestAgentSpec> agents,
    List<AigManifestSkillSpec> skills
) {

    /**
     * 从 JSON 对象读出视图。
     *
     * <p>本方法<b>假定入参已经是对象</b>（调用方即校验器已确认）；字段缺失或类型不符一律读成
     * {@code null} 或空清单，由校验器负责如实报告「哪一项没声明」。</p>
     *
     * @param root Manifest 根节点
     * @return 只读视图
     */
    public static AigPackageManifest from(JsonNode root) {
        return new AigPackageManifest(
            root,
            text(root, "package_code"),
            text(root, "name"),
            text(root, "publisher"),
            text(root, "version"),
            text(root, "license"),
            text(root, "checksum"),
            text(root, "package_type"),
            text(root, "data_level"),
            text(root, "network_access"),
            text(root, "min_platform_version"),
            textList(root, "capabilities"),
            textList(root, "scenario_codes"),
            textList(root, "required_tools"),
            textList(root, "forbidden_tools"),
            textList(root, "roles"),
            textList(root, "golden_cases"),
            textList(root, "network_hosts"),
            textList(root, "knowledge_scope"),
            text(root, "upgrade_policy"),
            text(root, "rollback_policy"),
            readAgents(root),
            readSkills(root)
        );
    }

    /**
     * 读声明的 Agent 清单（元素必须是对象；缺字段读成 null，由校验器负责报告）。
     *
     * @param root 根节点
     * @return Agent 声明清单
     */
    private static List<AigManifestAgentSpec> readAgents(JsonNode root) {
        List<AigManifestAgentSpec> specs = new ArrayList<>();
        JsonNode array = root.get("agents");
        if (array == null || !array.isArray()) {
            return specs;
        }
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            specs.add(new AigManifestAgentSpec(
                text(item, "code"),
                text(item, "name"),
                text(item, "category"),
                text(item, "scenario_code"),
                text(item, "prompt_template"),
                rawText(item, "input_schema"),
                rawText(item, "output_schema"),
                text(item, "provider_capability"),
                text(item, "allow_external"),
                textList(item, "golden_cases")));
        }
        return specs;
    }

    /**
     * 读声明的 Skill 清单。
     *
     * @param root 根节点
     * @return Skill 声明清单
     */
    private static List<AigManifestSkillSpec> readSkills(JsonNode root) {
        List<AigManifestSkillSpec> specs = new ArrayList<>();
        JsonNode array = root.get("skills");
        if (array == null || !array.isArray()) {
            return specs;
        }
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            specs.add(new AigManifestSkillSpec(
                text(item, "code"),
                text(item, "name"),
                textList(item, "capabilities"),
                rawText(item, "input_schema"),
                rawText(item, "output_schema"),
                rawText(item, "tool_policy_json"),
                text(item, "provider_capability"),
                text(item, "allow_external")));
        }
        return specs;
    }

    /**
     * 取节点原文（对象/数组等结构化字段要原样入库，不能拆成字符串清单）。
     *
     * @param node 节点
     * @param key  字段
     * @return 原文；缺失或非结构化返回 null
     */
    private static String rawText(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.toString();
    }

    /**
     * 依赖清单的编码：元素既可以是字符串编码，也可以是 {@code {"type":...,"code":...}} 对象。
     *
     * <p>两种形态都接受，是因为设计 §6.1 只写了「Provider/Skill/Tool 依赖」，没规定写法。
     * 这里如实把能认出来的编码取出来，认不出来的元素<b>不猜</b>（返回的清单短于实际元素数，
     * 差异由 {@link #dependencyCount()} 暴露）。</p>
     *
     * @return 依赖编码清单
     */
    public List<String> dependencyCodes() {
        JsonNode node = raw == null ? null : raw.get("dependencies");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isString() && StringUtils.isNotBlank(item.stringValue())) {
                codes.add(item.stringValue().trim());
            } else if (item.isObject()) {
                String code = text(item, "code");
                if (StringUtils.isNotBlank(code)) {
                    codes.add(code.trim());
                }
            }
        }
        return codes;
    }

    /**
     * 依赖声明的元素个数（含认不出编码的元素）。
     *
     * @return 元素个数；未声明依赖时返回 0
     */
    public int dependencyCount() {
        JsonNode node = raw == null ? null : raw.get("dependencies");
        return node != null && node.isArray() ? node.size() : 0;
    }

    /**
     * 是否声明了 prompt 模板。
     *
     * @return 声明了非空字符串返回 true
     */
    public boolean hasPromptTemplate() {
        return raw != null && StringUtils.isNotBlank(text(raw, "prompt_template"));
    }

    /**
     * 是否声明了 workflow。
     *
     * @return 声明了返回 true
     */
    public boolean hasWorkflow() {
        return raw != null && raw.has("workflow") && !raw.get("workflow").isNull();
    }

    /**
     * 取字符串字段。
     *
     * @param node 节点
     * @param key  字段名
     * @return 字符串值；缺失或类型不符返回 null
     */
    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(key);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    /**
     * 取「字符串数组」字段中的字符串元素（非字符串元素忽略，不猜）。
     *
     * @param node 节点
     * @param key  字段名
     * @return 字符串清单；字段缺失或不是数组时返回空清单
     */
    private static List<String> textList(JsonNode node, String key) {
        if (node == null) {
            return List.of();
        }
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (item.isString() && StringUtils.isNotBlank(item.stringValue())) {
                items.add(item.stringValue().trim());
            }
        }
        return items;
    }

}
