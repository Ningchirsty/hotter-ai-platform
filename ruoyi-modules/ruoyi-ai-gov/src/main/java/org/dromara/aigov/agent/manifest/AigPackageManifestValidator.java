package org.dromara.aigov.agent.manifest;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigAgentCategoryEnum;
import org.dromara.aigov.agent.enums.AigNetworkAccessEnum;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.dromara.aigov.agent.enums.AigPackageTypeEnum;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Package Manifest 校验器（设计 §6.1 最小字段集、§6.2 五类拒绝规则）。
 *
 * <p><b>这是纯判定，不碰数据库</b>：输入是一串 Manifest 原文（可附带包记录侧的身份声明），
 * 输出是「通过 / 拒绝 + 命中哪几条规则 + 为什么」。落库与「只允许在 DRAFT 阶段落库」
 * 属于流程约束，在 {@code IAigAgentRegistryService#scanStoredManifest} 里。</p>
 *
 * <h3>三条刻意口径</h3>
 * <ol>
 *     <li><b>未声明的字段一律拒绝（白名单）</b>。Manifest 只允许出现 §6.1 的那批声明式字段
 *         （加少量允许的可选声明）。任何其它字段都命中 §6.2-3。理由：一个平台看不懂的字段
 *         今天被忽略，明天可能被新版本执行——而「能不能限制」这个问题，在字段未被平台定义之前
 *         答案永远是「不能」。宁可拒绝一个无害的扩展字段，也不接受一个不可界定的字段。</li>
 *     <li><b>不做「字符串里像不像代码」的启发式</b>。既会漏（base64 一段脚本）又会误伤
 *         （正常 prompt 里出现 {@code exec(}）。真正的边界是两条硬约束：平台不执行 Manifest 里
 *         的任何东西，以及未声明字段/工具一律拒绝。启发式只会制造一层可以绕过的心理安慰。</li>
 *     <li><b>「未声明」不等于「没有」</b>。{@code network_access}、{@code data_level} 这类字段
 *         缺失时按「未声明」拒绝，而不是按缺省值放行：缺省即放行会让「忘了写」和「故意不写」
 *         享有同一个宽松结果。</li>
 * </ol>
 *
 * <p><b>数据等级为 STRICT 的包声明外网访问，也命中 §6.2-1</b>。严格说这不在 §6.2-1 的字面列举里
 * （那句话讲的是 DB 直连/Shell/SSH/Docker Socket/生产密钥/管理员权限），但它是同一类东西：
 * <b>向平台索取一条平台已被硬禁止的通路</b>。这个判断写在 {@code scan_detail} 里带依据，
 * 评审若认为该归别的规则，改这里的映射即可，不影响结论本身（它照样会被拒绝）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigPackageManifestValidator {

    /**
     * 扫描说明的最大长度，与 {@code aig_package_version.scan_detail varchar(1000)} 对齐。
     */
    public static final int DETAIL_MAX_LENGTH = 1000;

    /**
     * 拒绝原因里最多列出的字段名个数（超出的用「等 N 项」概括，避免说明被少数几项占满）
     */
    private static final int MAX_LISTED_FIELDS = 8;

    /**
     * SHA-256 十六进制形态
     */
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    /**
     * §6.1 最小字段集（必填）。有序：报错时按此顺序列出，便于逐条对齐设计文档。
     */
    private static final Map<String, Kind> REQUIRED_FIELDS = new LinkedHashMap<>();

    /**
     * 允许出现的可选声明字段（出现了必须类型正确，但不要求出现）。
     */
    private static final Map<String, Kind> OPTIONAL_FIELDS = new LinkedHashMap<>();

    /**
     * {@code agents[]} 条目内部允许的字段
     */
    private static final Map<String, Kind> AGENT_SUB_FIELDS = new LinkedHashMap<>();

    /**
     * {@code skills[]} 条目内部允许的字段
     */
    private static final Map<String, Kind> SKILL_SUB_FIELDS = new LinkedHashMap<>();

    /**
     * {@code agents[]} 条目的必填子字段（category 决定这个 Agent 的语义类别）
     */
    private static final List<String> AGENT_REQUIRED = List.of("code", "name", "category");

    /**
     * {@code skills[]} 条目的必填子字段
     */
    private static final List<String> SKILL_REQUIRED = List.of("code", "name");

    /**
     * 平台禁止的工具（§6.2-1）。取值按小写、分隔符统一为「-」后比对。
     *
     * <p>与 {@code aig_agent_registry_seed.sql} 里四个内置 Agent 的 {@code forbidden_tools}
     * 保持同一批名字：让「拒绝项」在数据里可见，而不是只存在于代码里。</p>
     */
    private static final Set<String> FORBIDDEN_TOOLS = Set.of(
        "shell", "ssh", "db-direct", "database", "docker-socket", "docker",
        "prod-secret", "production-secret", "admin", "root", "sudo", "exec", "code-run");

    static {
        // §6.1 身份
        REQUIRED_FIELDS.put("package_code", Kind.TEXT);
        REQUIRED_FIELDS.put("name", Kind.TEXT);
        REQUIRED_FIELDS.put("publisher", Kind.TEXT);
        REQUIRED_FIELDS.put("version", Kind.TEXT);
        REQUIRED_FIELDS.put("license", Kind.TEXT);
        REQUIRED_FIELDS.put("checksum", Kind.TEXT);
        // §6.1 能力
        REQUIRED_FIELDS.put("package_type", Kind.TEXT);
        REQUIRED_FIELDS.put("capabilities", Kind.ARRAY);
        REQUIRED_FIELDS.put("scenario_codes", Kind.ARRAY);
        REQUIRED_FIELDS.put("input_schema", Kind.OBJECT);
        REQUIRED_FIELDS.put("output_schema", Kind.OBJECT);
        // §6.1 依赖
        REQUIRED_FIELDS.put("min_platform_version", Kind.TEXT);
        REQUIRED_FIELDS.put("dependencies", Kind.ARRAY);
        // §6.1 权限
        REQUIRED_FIELDS.put("required_tools", Kind.ARRAY);
        REQUIRED_FIELDS.put("forbidden_tools", Kind.ARRAY);
        REQUIRED_FIELDS.put("roles", Kind.ARRAY);
        REQUIRED_FIELDS.put("data_level", Kind.TEXT);
        REQUIRED_FIELDS.put("network_access", Kind.TEXT);
        // §6.1 质量
        REQUIRED_FIELDS.put("golden_cases", Kind.ARRAY);
        REQUIRED_FIELDS.put("version_notes", Kind.TEXT);
        REQUIRED_FIELDS.put("upgrade_policy", Kind.TEXT);
        REQUIRED_FIELDS.put("rollback_policy", Kind.TEXT);

        // 可选的声明式字段：声明式内容物本身（prompt / workflow / 知识范围）与说明性字段
        OPTIONAL_FIELDS.put("description", Kind.TEXT);
        OPTIONAL_FIELDS.put("prompt_template", Kind.TEXT);
        OPTIONAL_FIELDS.put("workflow", Kind.STRUCTURE);
        OPTIONAL_FIELDS.put("knowledge_scope", Kind.ARRAY);
        OPTIONAL_FIELDS.put("network_hosts", Kind.ARRAY);
        // 内容物声明（安装时据此建 Agent/Skill）——见 AigManifestAgentSpec 的注释：
        // 没有这两项，「Package 安装」在实现时会发现无物可建
        OPTIONAL_FIELDS.put("agents", Kind.ARRAY);
        OPTIONAL_FIELDS.put("skills", Kind.ARRAY);

        // agents[] / skills[] 条目内部的允许字段（同样白名单）
        AGENT_SUB_FIELDS.put("code", Kind.TEXT);
        AGENT_SUB_FIELDS.put("name", Kind.TEXT);
        AGENT_SUB_FIELDS.put("category", Kind.TEXT);
        AGENT_SUB_FIELDS.put("scenario_code", Kind.TEXT);
        AGENT_SUB_FIELDS.put("prompt_template", Kind.TEXT);
        AGENT_SUB_FIELDS.put("input_schema", Kind.OBJECT);
        AGENT_SUB_FIELDS.put("output_schema", Kind.OBJECT);
        AGENT_SUB_FIELDS.put("provider_capability", Kind.TEXT);
        AGENT_SUB_FIELDS.put("allow_external", Kind.TEXT);
        AGENT_SUB_FIELDS.put("golden_cases", Kind.ARRAY);

        SKILL_SUB_FIELDS.put("code", Kind.TEXT);
        SKILL_SUB_FIELDS.put("name", Kind.TEXT);
        SKILL_SUB_FIELDS.put("capabilities", Kind.ARRAY);
        SKILL_SUB_FIELDS.put("input_schema", Kind.OBJECT);
        SKILL_SUB_FIELDS.put("output_schema", Kind.OBJECT);
        SKILL_SUB_FIELDS.put("tool_policy_json", Kind.OBJECT);
        SKILL_SUB_FIELDS.put("provider_capability", Kind.TEXT);
        SKILL_SUB_FIELDS.put("allow_external", Kind.TEXT);
    }

    private final JsonMapper jsonMapper;

    /**
     * 纯 Manifest 扫描（无包记录可比对）。
     *
     * @param rawManifestJson Manifest 原文
     * @return 扫描结论
     */
    public AigManifestScanResult scan(String rawManifestJson) {
        return scan(rawManifestJson, null);
    }

    /**
     * Manifest 扫描 + 与包记录交叉核对。
     *
     * @param rawManifestJson Manifest 原文
     * @param identity        包记录侧的身份声明；为 {@code null} 时跳过交叉核对
     * @return 扫描结论
     */
    public AigManifestScanResult scan(String rawManifestJson, AigPackageIdentity identity) {
        Accumulator acc = new Accumulator(manifestHash(rawManifestJson));
        if (StringUtils.isBlank(rawManifestJson)) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "Manifest 为空：§6.1 的全部最小字段都视为缺失");
            return acc.build();
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(rawManifestJson);
        } catch (Exception e) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "Manifest 不是合法 JSON（" + brief(e) + "）：读不出任何声明，"
                    + "§6.1 的全部最小字段都视为缺失");
            return acc.build();
        }
        if (root == null || !root.isObject()) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "Manifest 顶层必须是 JSON 对象（实际是" + nodeType(root) + "）："
                    + "§6.1 的全部最小字段都视为缺失");
            return acc.build();
        }
        acc.manifest = AigPackageManifest.from(root);

        checkUndeclaredFields(root, acc);
        checkRequiredFields(root, acc);
        checkProvenance(root, acc);
        checkValueDomains(root, acc);
        checkOptionalTypes(root, acc);
        checkDeclaredContent(root, acc);
        checkNetwork(root, acc);
        checkTools(root, acc);
        checkQuality(root, acc);
        checkIdentity(root, identity, acc);
        return acc.build();
    }

    /**
     * 计算 Manifest 原文的 SHA-256（UTF-8 编码的那串字节）。
     *
     * <p>唯一的实现点：入库时算一次、复扫时算一次，两处必须用同一个函数，
     * 否则「原文有没有被改过」这个判断会时好时坏。</p>
     *
     * @param rawManifestJson Manifest 原文
     * @return 十六进制小写 SHA-256；入参为空时仍返回其哈希（空串的哈希），不做特殊处理
     */
    public static String manifestHash(String rawManifestJson) {
        return DigestUtil.sha256Hex(
            (rawManifestJson == null ? "" : rawManifestJson).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析 Manifest 视图（不做判定）。
     *
     * <p>给「已经知道自己在读哪份 Manifest」的调用方用（例如安装时按声明建 Agent/Skill）。
     * 判定归 {@link #scan}，这里只做形状检查。</p>
     *
     * @param rawManifestJson Manifest 原文
     * @return 视图；无法解析为 JSON 对象时返回 null
     */
    public AigPackageManifest parse(String rawManifestJson) {
        if (StringUtils.isBlank(rawManifestJson)) {
            return null;
        }
        try {
            JsonNode root = jsonMapper.readTree(rawManifestJson);
            return root != null && root.isObject() ? AigPackageManifest.from(root) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 未声明字段检查（§6.2-3）。
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkUndeclaredFields(JsonNode root, Accumulator acc) {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String key = entry.getKey();
            if (!REQUIRED_FIELDS.containsKey(key) && !OPTIONAL_FIELDS.containsKey(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "出现未声明的字段：" + listing(unknown) + "。"
                    + "本平台只接受 §6.1 的声明式字段，未声明的字段一律按「无法界定的可执行内容」处理"
                    + "（今天被忽略的东西，明天可能被执行）");
        }
    }

    /**
     * 必填声明检查（§6.2-2）。
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkRequiredFields(JsonNode root, Accumulator acc) {
        List<String> missing = new ArrayList<>();
        List<String> wrongType = new ArrayList<>();
        for (Map.Entry<String, Kind> entry : REQUIRED_FIELDS.entrySet()) {
            JsonNode node = root.get(entry.getKey());
            if (node == null || node.isNull() || node.isMissingNode()) {
                missing.add(entry.getKey());
            } else if (!kindOk(entry.getValue(), node)) {
                wrongType.add(entry.getKey() + "（应为" + entry.getValue().getDesc()
                    + "，实际是" + nodeType(node) + "）");
            }
        }
        if (!missing.isEmpty() || !wrongType.isEmpty()) {
            StringBuilder note = new StringBuilder();
            if (!missing.isEmpty()) {
                note.append("未声明或为空：").append(listing(missing));
            }
            if (!wrongType.isEmpty()) {
                if (note.length() > 0) {
                    note.append("；");
                }
                note.append("类型不符：").append(listing(wrongType));
            }
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD, note.toString());
        }
    }

    /**
     * 来源/版权/校验声明检查（§6.2-4）。
     *
     * <p>只看 Manifest 自身：{@code publisher}、{@code license}、{@code checksum} 任一为空，
     * 「来源、校验和或版权信息不明确」当场成立，<b>不需要有包记录可比对</b>。
     * 与包记录交叉核对（{@link #checkIdentity}）是叠加的第二步——它管的是「说法与登记不一致」。</p>
     *
     * <p>其中 {@code license} 同时也在 §6.2-2 的「未声明许可证」里，因此为空时两条规则都会命中。
     * 这是设计文档自己的重叠，不做去重——去重会让评审以为只有一条规则管它。</p>
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkProvenance(JsonNode root, Accumulator acc) {
        for (String field : List.of("publisher", "license", "checksum")) {
            if (StringUtils.isBlank(text(root, field))) {
                acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                    "Manifest 未声明 " + field + "（或声明为空）：来源/版权/校验信息不明确");
            }
        }
    }

    /**
     * 取值域检查（§6.2-2）：枚举字段必须落在平台认识的取值里。
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkValueDomains(JsonNode root, Accumulator acc) {
        String packageType = text(root, "package_type");
        if (StringUtils.isNotBlank(packageType) && AigPackageTypeEnum.find(packageType) == null) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "package_type 取值未知：" + packageType + "（可选 AGENT/SKILL/MIXED）");
        }
        String dataLevel = text(root, "data_level");
        if (StringUtils.isNotBlank(dataLevel) && AigDataLevelEnum.find(StringUtils.trim(dataLevel)) == null) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "data_level 取值未知：" + dataLevel
                    + "（可选 PUBLIC/INTERNAL/RESTRICTED/STRICT）");
        }
        String networkAccess = text(root, "network_access");
        if (StringUtils.isNotBlank(networkAccess)
            && AigNetworkAccessEnum.find(StringUtils.trim(networkAccess)) == null) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "network_access 取值未知：" + networkAccess + "（可选 NONE/EXTERNAL）");
        }
        // checksum 属 §6.2-4（校验和不明确），不属「未声明」
        String checksum = text(root, "checksum");
        if (StringUtils.isNotBlank(checksum) && !SHA256_HEX.matcher(checksum.trim()).matches()) {
            acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                "checksum 不是 64 位十六进制 SHA-256："
                    + StringUtils.substring(checksum.trim(), 0, 32));
        }
    }

    /**
     * 可选声明字段的类型检查（§6.2-3）：声明式内容物不得是「一坨无法界定的东西」。
     *
     * <p>例：{@code workflow} 必须是结构（对象/数组），不能是一段字符串——字符串形式的流程
     * 平台无法界定其行为，与任意代码没有区别。这里不深挖结构内部（不做启发式），
     * 只保证「形态是可声明的」。</p>
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkOptionalTypes(JsonNode root, Accumulator acc) {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Kind> entry : OPTIONAL_FIELDS.entrySet()) {
            JsonNode node = root.get(entry.getKey());
            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }
            if (!kindOk(entry.getValue(), node, true)) {
                bad.add(entry.getKey() + "（应为" + entry.getValue().getDesc()
                    + "，实际是" + nodeType(node) + "）");
            }
        }
        if (!bad.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "可选声明字段的类型不符合声明式要求：" + listing(bad));
        }
    }

    /**
     * 声明的 Agent/Skill 内容检查（§5.3、§6.1）。
     *
     * <p>三条判据：①条目内部同样是白名单（未声明子字段即拒绝）；②必填子字段、类型与取值；
     * ③与 {@code package_type} 的一致性（AGENT 包不能带 Skill、SKILL 包不能带 Agent、MIXED 两者都要有）。</p>
     *
     * <p><b>只在真的声明了内容时才做一致性检查</b>：§6.1 的最小白名单里本来没有 agents/skills，
     * 「没声明内容」的包依然能通过 Manifest 校验（校验管的是拒绝规则）；但**安装**会明确报
     * 「这个 Manifest 没有声明任何 Agent/Skill，无法安装」——把「内容缺失」放在安装那一步，
     * 而不是让扫描去替安装做判断。</p>
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkDeclaredContent(JsonNode root, Accumulator acc) {
        List<String> unknownSub = new ArrayList<>();
        List<String> missingSub = new ArrayList<>();
        List<String> badSub = new ArrayList<>();
        List<String> agentCodes = new ArrayList<>();
        List<String> skillCodes = new ArrayList<>();

        boolean declared = collectContent(root.get("agents"), "agents", AGENT_SUB_FIELDS, AGENT_REQUIRED,
            agentCodes, unknownSub, missingSub, badSub);
        declared |= collectContent(root.get("skills"), "skills", SKILL_SUB_FIELDS, SKILL_REQUIRED,
            skillCodes, unknownSub, missingSub, badSub);

        if (declared) {
            AigPackageTypeEnum type = AigPackageTypeEnum.find(text(root, "package_type"));
            if (type != null) {
                boolean hasAgents = !agentCodes.isEmpty();
                boolean hasSkills = !skillCodes.isEmpty();
                switch (type) {
                    case AGENT -> {
                        if (!hasAgents) {
                            missingSub.add("package_type=AGENT 但 agents 为空");
                        }
                        if (hasSkills) {
                            badSub.add("package_type=AGENT 却声明了 skills（包类型与内容物不一致）");
                        }
                    }
                    case SKILL -> {
                        if (!hasSkills) {
                            missingSub.add("package_type=SKILL 但 skills 为空");
                        }
                        if (hasAgents) {
                            badSub.add("package_type=SKILL 却声明了 agents（包类型与内容物不一致）");
                        }
                    }
                    case MIXED -> {
                        if (!hasAgents || !hasSkills) {
                            missingSub.add("package_type=MIXED 需要 agents 与 skills 都非空");
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        if (!unknownSub.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "内容物声明里出现未声明的字段：" + listing(unknownSub)
                    + "。agents[]/skills[] 的条目同样只接受平台认识的声明式字段");
        }
        if (!missingSub.isEmpty() || !badSub.isEmpty()) {
            StringBuilder note = new StringBuilder();
            if (!missingSub.isEmpty()) {
                note.append("缺失或为空：").append(listing(missingSub));
            }
            if (!badSub.isEmpty()) {
                if (note.length() > 0) {
                    note.append("；");
                }
                note.append("不符合要求：").append(listing(badSub));
            }
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD, note.toString());
        }
    }

    /**
     * 收集一类内容声明的检查结果。
     *
     * @param array      声明数组
     * @param label      字段名（agents / skills）
     * @param allowed    允许的子字段
     * @param required   必填子字段
     * @param codes      编码收集器（用于查重与一致性判断）
     * @param unknownSub 未声明子字段收集器
     * @param missingSub 缺失/为空收集器
     * @param badSub     类型与取值不符收集器
     * @return 该字段存在（哪怕是空数组）返回 true
     */
    private boolean collectContent(JsonNode array, String label, Map<String, Kind> allowed,
                                   List<String> required, List<String> codes, List<String> unknownSub,
                                   List<String> missingSub, List<String> badSub) {
        if (array == null || array.isNull() || array.isMissingNode()) {
            return false;
        }
        if (!array.isArray()) {
            badSub.add(label + "（应为数组，实际是" + nodeType(array) + "）");
            return true;
        }
        int index = 0;
        for (JsonNode item : array) {
            String where = label + "[" + index + "]";
            index++;
            if (!item.isObject()) {
                badSub.add(where + "（应为对象，实际是" + nodeType(item) + "）");
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : item.properties()) {
                Kind kind = allowed.get(entry.getKey());
                if (kind == null) {
                    unknownSub.add(where + "." + entry.getKey());
                } else if (!kindOk(kind, entry.getValue(), true)) {
                    badSub.add(where + "." + entry.getKey() + "（应为" + kind.getDesc() + "，实际是"
                        + nodeType(entry.getValue()) + "）");
                }
            }
            for (String field : required) {
                JsonNode node = item.get(field);
                if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
                    missingSub.add(where + "." + field);
                }
            }
            String code = text(item, "code");
            if (code != null && !code.isBlank()) {
                if (codes.contains(code.trim())) {
                    badSub.add(where + ".code 重复：" + code.trim());
                } else {
                    codes.add(code.trim());
                }
            }
            if ("agents".equals(label)) {
                String category = text(item, "category");
                if (category != null && !category.isBlank()
                    && AigAgentCategoryEnum.find(category) == null) {
                    badSub.add(where + ".category 取值未知：" + category);
                }
            }
            String allowExternal = text(item, "allow_external");
            if (allowExternal != null && !allowExternal.isBlank()
                && !"Y".equalsIgnoreCase(allowExternal.trim())
                && !"N".equalsIgnoreCase(allowExternal.trim())) {
                badSub.add(where + ".allow_external 只能是 Y 或 N，实际 " + allowExternal);
            }
        }
        return true;
    }

    /**
     * 外网访问声明检查（§6.2-2，兼 §6.2-1 的 STRICT 情况）。
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkNetwork(JsonNode root, Accumulator acc) {
        AigNetworkAccessEnum access = AigNetworkAccessEnum.find(
            StringUtils.trim(text(root, "network_access")));
        if (access != AigNetworkAccessEnum.EXTERNAL) {
            return;
        }
        JsonNode hosts = root.get("network_hosts");
        if (hosts == null || !hosts.isArray() || hosts.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
                "声明 network_access=EXTERNAL 却未给出 network_hosts：说不出要连哪里，"
                    + "平台无法评估外发风险；若确实不需要外网，应显式声明 NONE");
        }
        AigDataLevelEnum level = AigDataLevelEnum.find(StringUtils.trim(text(root, "data_level")));
        if (level != null && level.externalForbidden()) {
            acc.hit(AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                "声明外网访问但数据等级为 " + level.getCode() + "（" + level.getDesc()
                    + "）：STRICT 一律禁止外发，是平台不受策略影响的硬规则，"
                    + "该包等于要求一条平台不能给的通路");
        }
    }

    /**
     * 工具声明检查（§6.2-1、§6.2-3）。
     *
     * <p>首期是声明式 Package（Q9：不含可执行代码），因此 {@code required_tools} 正常就是空数组。
     * 里面出现平台禁止的工具 → §6.2-1；出现其它工具 → §6.2-3：平台不认识它，
     * 也就无法界定它会做什么。真需要受控工具时应当由平台先定义工具、再开放声明，
     * 而不是让包自己写一个名字就当作已授权。</p>
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkTools(JsonNode root, Accumulator acc) {
        JsonNode tools = root.get("required_tools");
        if (tools == null || !tools.isArray() || tools.isEmpty()) {
            return;
        }
        List<String> forbidden = new ArrayList<>();
        List<String> unbounded = new ArrayList<>();
        for (JsonNode item : tools) {
            if (!item.isString()) {
                unbounded.add("<非字符串元素：" + nodeType(item) + ">");
                continue;
            }
            // 匹配用归一化名（写法不同也要认出来），回显用作者原本的写法（作者才认得出自己写了什么）
            String name = normalizeTool(item.stringValue());
            if (name.isEmpty()) {
                continue;
            }
            if (FORBIDDEN_TOOLS.contains(name)) {
                forbidden.add(item.stringValue().trim());
            } else {
                unbounded.add(item.stringValue().trim());
            }
        }
        if (!forbidden.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                "required_tools 含平台禁止的工具：" + listing(forbidden)
                    + "（§6.2-1：数据库直连、Shell/SSH、Docker Socket、生产密钥、管理员权限一律拒绝）");
        }
        if (!unbounded.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "required_tools 声明了平台未定义的工具：" + listing(unbounded)
                    + "。首期只支持声明式 Package（不含可执行代码），正常不需要任何工具；"
                    + "包自己写一个工具名不等于平台已授权该行为");
        }
    }

    /**
     * 质量声明检查（§6.2-5）。
     *
     * <p>把「黄金用例、输出 Schema、升级与回滚策略」的缺失归到「试图绕过平台链路」，
     * 是 §6.1 与 §6.2-5 合起来的读法：§6.1 要求它们存在，§6.2-5 拒绝绕过评测/审核/回滚链路。
     * 一个没有黄金用例、也说不清怎么回滚的包，平台放行它就只能靠人工信任——
     * 那正是「绕过链路」的定义。</p>
     *
     * @param root 根节点
     * @param acc  累积器
     */
    private void checkQuality(JsonNode root, Accumulator acc) {
        List<String> reasons = new ArrayList<>();
        JsonNode golden = root.get("golden_cases");
        if (golden == null || golden.isNull() || golden.isMissingNode()
            || (golden.isArray() && golden.isEmpty())) {
            reasons.add("golden_cases 为空：没有黄金用例就无法进入 §13.2 的评测链路");
        }
        JsonNode output = root.get("output_schema");
        if (output == null || output.isNull() || output.isMissingNode()
            || (output.isObject() && output.isEmpty())) {
            reasons.add("output_schema 为空：平台无法校验产物，只能原样信任包的输出");
        }
        if (StringUtils.isBlank(text(root, "upgrade_policy"))) {
            reasons.add("未声明 upgrade_policy：升级行为不确定");
        }
        if (StringUtils.isBlank(text(root, "rollback_policy"))) {
            reasons.add("未声明 rollback_policy：出了问题没有回退路径（§6.3-7）");
        }
        if (!reasons.isEmpty()) {
            acc.hit(AigPackageRejectRuleEnum.BYPASSES_PLATFORM_CHAIN, String.join("；", reasons));
        }
    }

    /**
     * 与包记录交叉核对（§6.2-4）。
     *
     * @param root     根节点
     * @param identity 包记录侧身份；为 {@code null} 时跳过
     * @param acc      累积器
     */
    private void checkIdentity(JsonNode root, AigPackageIdentity identity, Accumulator acc) {
        if (identity == null) {
            return;
        }
        compare(acc, "package_code", text(root, "package_code"), identity.packageCode());
        compare(acc, "publisher", text(root, "publisher"), identity.publisher());
        compare(acc, "license", text(root, "license"), identity.licenseCode());
        compare(acc, "checksum", text(root, "checksum"), identity.checksum());
        compare(acc, "package_type", text(root, "package_type"), identity.packageType());
        compare(acc, "version", text(root, "version"), identity.version());

        if (StringUtils.isBlank(identity.storedManifestHash())) {
            acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                "包记录的 manifest_hash 为空：无法判断 Manifest 原文是否被改动过");
        } else if (!identity.storedManifestHash().equalsIgnoreCase(acc.manifestHash)) {
            acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                "库中 manifest_hash 与重算值不一致（库=" + identity.storedManifestHash()
                    + "，重算=" + acc.manifestHash + "）：Manifest 原文在入库后被改动过。"
                    + "本平台不覆盖库中的 manifest_hash 来「修复」这个差异");
        }
    }

    /**
     * 单字段交叉比对：包记录缺该值、或与 Manifest 声明不一致，都命中 §6.2-4。
     *
     * @param acc      累积器
     * @param field    字段名
     * @param declared Manifest 声明值（可能为空，此时由「未声明」检查负责）
     * @param recorded 包记录值
     */
    private void compare(Accumulator acc, String field, String declared, String recorded) {
        if (StringUtils.isBlank(recorded)) {
            acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                "包记录缺少 " + field + "：来源与校验信息不完整");
            return;
        }
        if (StringUtils.isBlank(declared)) {
            // Manifest 侧缺失已由 §6.2-2 报告，这里不重复计数
            return;
        }
        if (!declared.trim().equalsIgnoreCase(recorded.trim())) {
            acc.hit(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
                field + " 不一致（Manifest=" + declared.trim() + "，包记录=" + recorded.trim()
                    + "）：这份 Manifest 描述的不是包记录里的那个包");
        }
    }

    /**
     * 字段形态是否可接受。
     *
     * @param kind        期望形态
     * @param node        实际节点
     * @return 可接受返回 true
     */
    private static boolean kindOk(Kind kind, JsonNode node) {
        return kindOk(kind, node, false);
    }

    /**
     * 字段形态是否可接受。
     *
     * @param kind        期望形态
     * @param node        实际节点
     * @param allowEmpty  文本是否允许为空（可选字段允许；必填字段不允许——空串与未声明在平台侧
     *                    的实际效果相同，都不能当作「已声明」）
     * @return 可接受返回 true
     */
    private static boolean kindOk(Kind kind, JsonNode node, boolean allowEmpty) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        return switch (kind) {
            case TEXT -> node.isString() && (allowEmpty || StringUtils.isNotBlank(node.stringValue()));
            case OBJECT -> node.isObject() && (allowEmpty || !node.isEmpty());
            case ARRAY -> node.isArray();
            case STRUCTURE -> node.isObject() || node.isArray();
        };
    }

    /**
     * 取字符串字段值。
     *
     * @param root 根节点
     * @param key  字段名
     * @return 字符串值；缺失或类型不符返回 null
     */
    private static String text(JsonNode root, String key) {
        JsonNode node = root.get(key);
        return node != null && node.isString() ? node.stringValue() : null;
    }

    /**
     * 工具名归一：小写、去空白、分隔符统一为「-」。
     *
     * <p>否则 {@code DOCKER_SOCKET} 与 {@code docker-socket} 会被当成两个工具，
     * 拒绝名单只要漏一个写法就等于漏掉一整类。</p>
     *
     * @param raw 原始工具名
     * @return 归一后的名字
     */
    private static String normalizeTool(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * 节点类型的中文描述（错误信息里出现 {@code TextNode} 对使用者没有帮助）。
     *
     * @param node 节点
     * @return 描述
     */
    private static String nodeType(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "缺失";
        }
        if (node.isNull()) {
            return "空值";
        }
        if (node.isString()) {
            return "字符串";
        }
        if (node.isObject()) {
            return "对象";
        }
        if (node.isArray()) {
            return "数组";
        }
        if (node.isNumber()) {
            return "数字";
        }
        if (node.isBoolean()) {
            return "布尔值";
        }
        return "未知类型";
    }

    /**
     * 列出字段名清单（过长则概括）。
     *
     * @param items 名称清单
     * @return 可读文本
     */
    private static String listing(List<String> items) {
        if (items.size() <= MAX_LISTED_FIELDS) {
            return String.join("、", items);
        }
        return String.join("、", items.subList(0, MAX_LISTED_FIELDS))
            + " 等 " + items.size() + " 项";
    }

    /**
     * 异常摘要（只留类型与一小段消息，避免把 Jackson 的长消息塞进 scan_detail）。
     *
     * @param e 异常
     * @return 摘要
     */
    private static String brief(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (StringUtils.isBlank(message)
            ? "" : ": " + StringUtils.substring(message.replace('\n', ' ').replace('\r', ' '), 0, 120));
    }

    /**
     * 字段期望形态。
     */
    private enum Kind {

        /**
         * 非空字符串
         */
        TEXT("字符串"),

        /**
         * 非空对象
         */
        OBJECT("对象"),

        /**
         * 数组
         */
        ARRAY("数组"),

        /**
         * 对象或数组（用于 workflow：声明式结构，不能是字符串）
         */
        STRUCTURE("对象或数组");

        private final String desc;

        Kind(String desc) {
            this.desc = desc;
        }

        /**
         * 取描述。
         *
         * @return 描述
         */
        public String getDesc() {
            return desc;
        }
    }

    /**
     * 扫描累积器：按规则归拢理由，最后统一成结果与说明。
     */
    private static final class Accumulator {

        /**
         * Manifest 原文哈希
         */
        private final String manifestHash;

        /**
         * 命中规则 → 理由清单（EnumMap 保证输出顺序稳定，便于测试与比对）
         */
        private final EnumMap<AigPackageRejectRuleEnum, List<String>> hits =
            new EnumMap<>(AigPackageRejectRuleEnum.class);

        /**
         * 解析出的 Manifest
         */
        private AigPackageManifest manifest;

        Accumulator(String manifestHash) {
            this.manifestHash = manifestHash;
        }

        /**
         * 记录一条命中理由。
         *
         * @param rule 规则
         * @param note 理由
         */
        void hit(AigPackageRejectRuleEnum rule, String note) {
            hits.computeIfAbsent(rule, key -> new ArrayList<>()).add(note);
        }

        /**
         * 产出结论（说明超长则截断，保证写库不会因长度失败）。
         *
         * @return 扫描结论
         */
        AigManifestScanResult build() {
            AigManifestScanResult result = new AigManifestScanResult();
            result.setManifestHash(manifestHash);
            result.setManifest(manifest);
            if (hits.isEmpty()) {
                result.setPass(true);
                result.setHitRules(List.of());
                result.setDetail("未命中 §6.2 五类拒绝规则（§6.1 最小字段集齐全，"
                    + "与包记录身份一致）");
                return result;
            }
            result.setPass(false);
            result.setHitRules(new ArrayList<>(hits.keySet()));

            StringBuilder sb = new StringBuilder();
            sb.append("命中 ").append(hits.size()).append(" 类拒绝规则（§6.2）：");
            for (AigPackageRejectRuleEnum rule : hits.keySet()) {
                sb.append(rule.getCode()).append('（').append(rule.getDesc()).append("）、");
            }
            sb.setLength(sb.length() - 1);
            for (Map.Entry<AigPackageRejectRuleEnum, List<String>> entry : hits.entrySet()) {
                sb.append("\n- ").append(entry.getKey().getCode())
                    .append(" [").append(entry.getKey().getBasis()).append("]：")
                    .append(String.join("；", entry.getValue()));
            }
            result.setDetail(truncate(sb.toString()));
            return result;
        }

        /**
         * 截断说明至列宽（截断本身是异常情况，记 warn 便于运维发现有人在试探列宽）。
         *
         * @param detail 原始说明
         * @return 截断后的说明
         */
        private static String truncate(String detail) {
            if (detail.length() <= DETAIL_MAX_LENGTH) {
                return detail;
            }
            String marker = "…（说明已截断）";
            log.warn("Manifest 扫描说明超过列宽，已截断: length={}, max={}",
                detail.length(), DETAIL_MAX_LENGTH);
            return detail.substring(0, DETAIL_MAX_LENGTH - marker.length()) + marker;
        }

    }

}
