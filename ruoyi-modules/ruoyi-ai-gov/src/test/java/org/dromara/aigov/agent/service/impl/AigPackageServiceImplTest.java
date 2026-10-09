package org.dromara.aigov.agent.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageInstallLog;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigSkill;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.bo.AigPackageUploadBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.domain.vo.AigPackageDisableVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;
import org.dromara.aigov.agent.domain.vo.AigPackageStatusVo;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.config.AigPackageProperties;
import org.dromara.aigov.agent.config.AigPackageSecurityProperties;
import org.dromara.aigov.agent.helper.AigPackageArchiveScanner;
import org.dromara.aigov.agent.helper.AigPackageRejectionRecorder;
import org.dromara.aigov.agent.helper.IAigPackageBodyStore;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageInstallLogMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Package 上传登记与安装的守门测试（设计 §6.1、§6.3）。
 *
 * <p>这里钉住的是「不生效也不会报错」的几类违约：</p>
 * <ol>
 *     <li>包体哈希与 Manifest 声明不一致却照样登记（那等于校验和是调用方说了算）；</li>
 *     <li>同包同版本被覆盖（版本不可变，覆盖会让已发布的结论失去依据）；</li>
 *     <li>Manifest 校验没通过却允许安装（门槛变成声明）；</li>
 *     <li>没有声明内容物却"安装成功"（装了个空）；</li>
 *     <li>重复安装造出两套版本（幂等靠"已存在指向该 Package 版本的版本行"）；</li>
 *     <li>装出来的版本不是 DRAFT（安装跳过了发布门槛）。</li>
 * </ol>
 * <p>纯 Mockito + 真实校验器（判据不能再用假的糊过去）：不加载 Spring、不碰数据库。
 * 真库那一段由 {@code PackageChainCheck} 探针负责。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPackageServiceImplTest {

    private static final long PKG_ID = 8801L;

    private static final long PKG_VERSION_ID = 8802L;

    private static final long AGENT_ID = 8803L;

    private static final long AGENT_VERSION_ID = 8804L;

    private static final long SKILL_ID = 8805L;

    private static final long SKILL_VERSION_ID = 8806L;

    private static final String BODY = "fake package body";

    private static final String BODY_SHA = DigestUtil.sha256Hex(BODY.getBytes(StandardCharsets.UTF_8));

    private AigPackageMapper packageMapper;
    private AigPackageVersionMapper packageVersionMapper;
    private AigPackageInstallLogMapper installLogMapper;
    private AigAgentMapper agentMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigSkillMapper skillMapper;
    private AigSkillVersionMapper skillVersionMapper;
    private IAigAgentRegistryService registryService;
    private IAigPackageBodyStore bodyStore;
    private AigPackageProperties packageProperties;
    private AigPackageRejectionRecorder rejectionRecorder;
    private AigPackageServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigPackage.class);
        TableInfoHelper.initTableInfo(assistant, AigPackageVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigPackageInstallLog.class);
        TableInfoHelper.initTableInfo(assistant, AigAgent.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigSkill.class);
        TableInfoHelper.initTableInfo(assistant, AigSkillVersion.class);
    }

    @BeforeEach
    void setUp() {
        packageMapper = mock(AigPackageMapper.class);
        packageVersionMapper = mock(AigPackageVersionMapper.class);
        installLogMapper = mock(AigPackageInstallLogMapper.class);
        agentMapper = mock(AigAgentMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        skillMapper = mock(AigSkillMapper.class);
        skillVersionMapper = mock(AigSkillVersionMapper.class);
        registryService = mock(IAigAgentRegistryService.class);
        bodyStore = mock(IAigPackageBodyStore.class);
        rejectionRecorder = mock(AigPackageRejectionRecorder.class);
        packageProperties = new AigPackageProperties();
        service = new AigPackageServiceImpl(packageMapper, packageVersionMapper, installLogMapper,
            agentMapper, agentVersionMapper, skillMapper, skillVersionMapper,
            new AigPackageManifestValidator(JsonMapper.builder().build()),
            registryService, packageProperties, bodyStore,
            new AigPackageArchiveScanner(new AigPackageSecurityProperties()), rejectionRecorder,
            JsonMapper.builder().build());

        when(packageMapper.insert(any(AigPackage.class))).thenAnswer(invocation -> {
            invocation.<AigPackage>getArgument(0).setPackageId(PKG_ID);
            return 1;
        });
        when(packageVersionMapper.insert(any(AigPackageVersion.class))).thenAnswer(invocation -> {
            invocation.<AigPackageVersion>getArgument(0).setPackageVersionId(PKG_VERSION_ID);
            return 1;
        });
        when(agentMapper.insert(any(AigAgent.class))).thenAnswer(invocation -> {
            invocation.<AigAgent>getArgument(0).setAgentId(AGENT_ID);
            return 1;
        });
        when(agentVersionMapper.insert(any(AigAgentVersion.class))).thenAnswer(invocation -> {
            invocation.<AigAgentVersion>getArgument(0).setAgentVersionId(AGENT_VERSION_ID);
            return 1;
        });
        when(skillMapper.insert(any(AigSkill.class))).thenAnswer(invocation -> {
            invocation.<AigSkill>getArgument(0).setSkillId(SKILL_ID);
            return 1;
        });
        when(skillVersionMapper.insert(any(AigSkillVersion.class))).thenAnswer(invocation -> {
            invocation.<AigSkillVersion>getArgument(0).setSkillVersionId(SKILL_VERSION_ID);
            return 1;
        });
    }

    /**
     * 造一份 Manifest（默认：SKILL 包 + 一个 skill，checksum 与包体一致）。
     *
     * @param checksum 校验和
     * @param skills   skills 数组
     * @param agents   agents 数组
     * @param type     包类型
     * @param dataLevel 数据等级
     * @return JSON 文本
     */
    private static String manifest(String checksum, String skills, String agents, String type,
                                   String dataLevel) {
        return "{\"package_code\":\"vision-planning-skill\",\"name\":\"视觉规划 Skill\","
            + "\"publisher\":\"design-center\",\"version\":\"1.0.0\",\"license\":\"Apache-2.0\","
            + "\"checksum\":\"" + checksum + "\",\"package_type\":\"" + type + "\","
            + "\"capabilities\":[\"CREATIVE_PLANNING\"],\"scenario_codes\":[\"CREATIVE_DRAFT\"],"
            + "\"input_schema\":{\"type\":\"object\"},\"output_schema\":{\"type\":\"object\"},"
            + "\"min_platform_version\":\"6.0.0\",\"dependencies\":[],\"required_tools\":[],"
            + "\"forbidden_tools\":[\"shell\"],\"roles\":[\"aig_viewer\"],"
            + "\"data_level\":\"" + dataLevel + "\",\"network_access\":\"NONE\","
            + "\"golden_cases\":[\"case-1\"],\"version_notes\":\"v1\","
            + "\"knowledge_scope\":[\"brand-x\"],"
            + "\"upgrade_policy\":\"IN_PLACE\",\"rollback_policy\":\"PREVIOUS_STABLE\""
            + (skills == null ? "" : ",\"skills\":" + skills)
            + (agents == null ? "" : ",\"agents\":" + agents)
            + "}";
    }

    /**
     * 造一份「SKILL 包 + 一个 skill」的合规模板。
     *
     * @return Manifest 文本
     */
    private static String defaultManifest() {
        return manifest(BODY_SHA,
            "[{\"code\":\"image-fitter\",\"name\":\"图像适配\","
                + "\"capabilities\":[\"image_generation\"],\"allow_external\":\"N\","
                + "\"input_schema\":{\"type\":\"object\"}}]",
            null, "SKILL", "INTERNAL");
    }

    /**
     * 造上传入参。
     *
     * @param manifestJson Manifest 文本
     * @return 入参
     */
    private static AigPackageUploadBo bo(String manifestJson) {
        AigPackageUploadBo bo = new AigPackageUploadBo();
        bo.setManifestJson(manifestJson);
        return bo;
    }

    // ---------------------------------------------------------------- 上传登记

    @Test
    @DisplayName("上传必须携包体：空包体直接拒（否则校验和只是调用方的声明）")
    void registerRejectsEmptyBody() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo(defaultManifest()), new byte[0], "pkg.zip", 1L));
        assertTrue(error.getMessage().contains("包体不能为空"), error.getMessage());
        verify(packageMapper, never()).insert(any(AigPackage.class));
    }

    @Test
    @DisplayName("包体哈希与 Manifest 声明不一致：整笔拒绝且不落库")
    void registerRejectsChecksumMismatch() {
        String otherSha = DigestUtil.sha256Hex("other".getBytes(StandardCharsets.UTF_8));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo(manifest(otherSha, "[]", null, "SKILL", "INTERNAL")),
                BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 1L));

        assertTrue(error.getMessage().contains("包体校验和不一致"), error.getMessage());
        assertTrue(error.getMessage().contains(norm(otherSha)), error.getMessage());
        verify(packageMapper, never()).insert(any(AigPackage.class));
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
        // 证据先落（独立事务）：被拒的"对方交了什么"必须留下来
        verify(rejectionRecorder).record(any(), any(), eq("pkg.zip"), any(), any(),
            eq(AigPackageRejectionRecorder.REASON_CHECKSUM_MISMATCH), any(), any(), eq(1L));
    }

    @Test
    @DisplayName("Manifest 解析不出来：不登记任何东西，但**留下证据行**")
    void registerRejectsUnparseableManifest() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo("{oops"), BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 1L));
        assertTrue(error.getMessage().contains("无法解析"), error.getMessage());
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
        // 连包编码都解析不出来时也要留痕：否则"有人传了畸形 Manifest"只剩 oper_log
        verify(rejectionRecorder).record(isNull(), isNull(), eq("pkg.zip"), any(), any(),
            eq(AigPackageRejectionRecorder.REASON_MANIFEST_INVALID), any(), any(), eq(1L));
    }

    @Test
    @DisplayName("★ 包体内容不安全：留证据行（带命中规则）并整笔拒绝——ADR-013 的落地")
    void registerRejectsUnsafeArchiveAndKeepsEvidence() {
        byte[] risky = zipWith("install.sh", "echo hi");
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo(manifest(DigestUtil.sha256Hex(risky), "[]", null, "SKILL", "INTERNAL")),
                risky, "pkg.zip", 9L));

        assertTrue(error.getMessage().contains("包体安全检查未通过"), error.getMessage());
        ArgumentCaptor<String> rulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(rejectionRecorder).record(eq("vision-planning-skill"), eq("1.0.0"), eq("pkg.zip"), any(),
            eq(DigestUtil.sha256Hex(risky)),
            eq(AigPackageRejectionRecorder.REASON_ARCHIVE_UNSAFE), rulesCaptor.capture(), any(), eq(9L));
        assertTrue(rulesCaptor.getValue().contains(
            AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION.getCode()),
            "证据里要写明命中哪条规则：" + rulesCaptor.getValue());
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
    }

    /**
     * 造一个单条目 ZIP（用于包体检查的用例）。
     *
     * @param name    条目名
     * @param content 条目内容
     * @return ZIP 字节
     */
    private static byte[] zipWith(String name, String content) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("主干：登记包与版本（DRAFT、写入 manifest_hash 与本次校验结论），并写 UPLOAD 日志")
    void registerCreatesPackageAndVersion() {
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);

        AigPackageRegisterVo vo = service.register(bo(defaultManifest()),
            BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 7L);

        assertTrue(vo.scanPass(), vo.scanDetail());
        assertEquals("PASS", vo.scanResult());
        assertEquals(norm(BODY_SHA), norm(vo.checksum()));
        assertEquals("vision-planning-skill", vo.packageCode());
        assertEquals("1.0.0", vo.version());
        assertNotNull(vo.manifest());

        ArgumentCaptor<AigPackage> pkg = ArgumentCaptor.forClass(AigPackage.class);
        verify(packageMapper).insert(pkg.capture());
        assertEquals(norm(BODY_SHA), norm(pkg.getValue().getChecksum()));
        assertEquals("UPLOAD", pkg.getValue().getSourceType());

        ArgumentCaptor<AigPackageVersion> version = ArgumentCaptor.forClass(AigPackageVersion.class);
        verify(packageVersionMapper).insert(version.capture());
        assertEquals("DRAFT", version.getValue().getReleaseStatus());
        assertEquals("PASS", version.getValue().getScanResult());
        assertEquals(AigPackageManifestValidator.manifestHash(defaultManifest()),
            version.getValue().getManifestHash());
        assertEquals(defaultManifest(), version.getValue().getManifestJson(),
            "Manifest 必须原样入库（哈希是对这串字节算的）");

        ArgumentCaptor<AigPackageInstallLog> log = ArgumentCaptor.forClass(AigPackageInstallLog.class);
        verify(installLogMapper).insert(log.capture());
        assertEquals("UPLOAD", log.getValue().getAction());
        assertEquals("PASS", log.getValue().getResult());
        assertEquals(7L, log.getValue().getOperatorId());
    }

    @Test
    @DisplayName("被拒绝也要留痕：Manifest 不合规仍登记，但 scan_result=REJECT 且写明命中哪条")
    void registerRecordsRejectedManifest() {
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);
        // 去掉 data_level：命中 §6.2-2
        String rejected = manifest(BODY_SHA, "[]", null, "SKILL", "INTERNAL")
            .replace("\"data_level\":\"INTERNAL\",", "");

        AigPackageRegisterVo vo = service.register(bo(rejected),
            BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 7L);

        assertFalse(vo.scanPass());
        assertEquals("REJECT", vo.scanResult());
        assertTrue(vo.scanDetail().contains("data_level"), vo.scanDetail());
        verify(packageVersionMapper).insert(any(AigPackageVersion.class));
        ArgumentCaptor<AigPackageInstallLog> log = ArgumentCaptor.forClass(AigPackageInstallLog.class);
        verify(installLogMapper).insert(log.capture());
        assertEquals("REJECT", log.getValue().getResult());
    }

    @Test
    @DisplayName("版本不可变：同包同版本再传一次直接拒，且不落任何行")
    void registerRejectsDuplicateVersion() {
        when(packageMapper.selectOne(any())).thenReturn(existingPackage());
        when(packageVersionMapper.selectCount(any())).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo(defaultManifest()), BODY.getBytes(StandardCharsets.UTF_8),
                "pkg.zip", 7L));

        assertTrue(error.getMessage().contains("已存在"), error.getMessage());
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
    }

    @Test
    @DisplayName("已存在的包：只把 checksum 更新为最近一次包体哈希（不新建包）")
    void registerUpdatesChecksumOnExistingPackage() {
        when(packageMapper.selectOne(any())).thenReturn(existingPackage());
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);

        service.register(bo(defaultManifest()), BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 7L);

        verify(packageMapper, never()).insert(any(AigPackage.class));
        ArgumentCaptor<AigPackage> update = ArgumentCaptor.forClass(AigPackage.class);
        verify(packageMapper).updateById(update.capture());
        assertEquals(PKG_ID, update.getValue().getPackageId());
        assertEquals(norm(BODY_SHA), norm(update.getValue().getChecksum()));
    }

    // ---------------------------------------------------------------- 安装

    @Test
    @DisplayName("安装要求 Manifest 校验 PASS：未扫描/被拒都不许装（门槛查库里的证据）")
    void installRequiresScanPass() {
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion(null, defaultManifest()));
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.install(PKG_VERSION_ID, 7L));
        assertTrue(error.getMessage().contains("未扫描"), error.getMessage());
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));

        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("REJECT", defaultManifest()));
        ServiceException rejected = assertThrows(ServiceException.class,
            () -> service.install(PKG_VERSION_ID, 7L));
        assertTrue(rejected.getMessage().contains("REJECT"), rejected.getMessage());
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));
    }

    @Test
    @DisplayName("没有声明内容物的 Manifest：安装明确报「没有内容可安装」，而不是装个空")
    void installRequiresDeclaredContent() {
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", manifest(BODY_SHA, "[]", null, "SKILL", "INTERNAL")));
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.install(PKG_VERSION_ID, 7L));

        assertTrue(error.getMessage().contains("没有声明任何 Agent/Skill"), error.getMessage());
        verify(skillVersionMapper, never()).insert(any(AigSkillVersion.class));
    }

    @Test
    @DisplayName("幂等：已存在指向该 Package 版本的版本行就不再重复建（日志是审计，不是状态）")
    void installIsIdempotent() {
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", defaultManifest()));
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());
        AigSkillVersion installed = new AigSkillVersion();
        installed.setSkillVersionId(SKILL_VERSION_ID);
        installed.setSkillId(SKILL_ID);
        installed.setVersion("1.0.0");
        when(skillVersionMapper.selectList(any())).thenReturn(List.of(installed));
        when(skillMapper.selectById(SKILL_ID)).thenReturn(existingSkill());

        AigPackageInstallVo vo = service.install(PKG_VERSION_ID, 7L);

        assertTrue(vo.alreadyInstalled());
        assertEquals(1, vo.skills().size());
        assertEquals("image-fitter", vo.skills().get(0).code());
        verify(skillVersionMapper, never()).insert(any(AigSkillVersion.class));
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
    }

    @Test
    @DisplayName("主干安装：Agent 与 Skill 各建出 DRAFT 版本，config_json 按口径带无列字段")
    void installCreatesAgentAndSkill() {
        String mixed = manifest(BODY_SHA,
            "[{\"code\":\"image-fitter\",\"name\":\"图像适配\","
                + "\"capabilities\":[\"image_generation\"],\"allow_external\":\"N\"}]",
            "[{\"code\":\"vision-planner\",\"name\":\"视觉规划\",\"category\":\"PLANNING\","
                + "\"golden_cases\":[\"case-a\"],\"provider_capability\":\"vision_plan\","
                + "\"allow_external\":\"N\"}]",
            "MIXED", "INTERNAL");
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", mixed));
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());
        when(agentMapper.selectOne(any())).thenReturn(null);
        when(skillMapper.selectOne(any())).thenReturn(null);
        when(agentVersionMapper.selectOne(any())).thenReturn(null);
        when(skillVersionMapper.selectOne(any())).thenReturn(null);
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());
        when(agentVersionMapper.selectList(any())).thenReturn(List.of());

        AigPackageInstallVo vo = service.install(PKG_VERSION_ID, 7L);

        assertFalse(vo.alreadyInstalled());
        assertEquals("vision-planner", vo.agents().get(0).code());
        assertTrue(vo.agents().get(0).created());
        assertEquals("image-fitter", vo.skills().get(0).code());
        assertTrue(vo.skills().get(0).created());

        ArgumentCaptor<AigAgentVersion> agentVersion = ArgumentCaptor.forClass(AigAgentVersion.class);
        verify(agentVersionMapper).insert(agentVersion.capture());
        AigAgentVersion av = agentVersion.getValue();
        assertEquals("DRAFT", av.getReleaseStatus(), "安装不等于发布：装出来必须是 DRAFT");
        assertEquals("CREATIVE_DRAFT", av.getScenarioCode(),
            "恰好一个场景时同时落单值列（既有按场景过滤可用）");
        assertEquals(PKG_VERSION_ID, av.getPackageVersionId());
        assertEquals("shell", av.getForbiddenTools());
        assertEquals("[\"brand-x\"]", av.getKnowledgeScopeJson());
        assertNotNull(av.getConfigJson());
        assertTrue(av.getConfigJson().contains("\"data_level\":\"INTERNAL\""), av.getConfigJson());
        assertTrue(av.getConfigJson().contains("\"golden_cases\":[\"case-a\"]"),
            "Agent 自己的黄金用例要进它的 config_json（评测从这里读）");
        assertTrue(av.getConfigJson().contains("\"package_code\":\"vision-planning-skill\""),
            av.getConfigJson());

        ArgumentCaptor<AigSkillVersion> skillVersion = ArgumentCaptor.forClass(AigSkillVersion.class);
        verify(skillVersionMapper).insert(skillVersion.capture());
        AigSkillVersion sv = skillVersion.getValue();
        assertEquals("DRAFT", sv.getReleaseStatus());
        assertEquals(PKG_VERSION_ID, sv.getPackageVersionId(),
            "Skill 版本也要能回溯来源包（本轮补的列）");

        ArgumentCaptor<AigSkill> skill = ArgumentCaptor.forClass(AigSkill.class);
        verify(skillMapper).insert(skill.capture());
        assertEquals("N", skill.getValue().getBuiltin(), "第三方带入的不是平台内置");

        ArgumentCaptor<AigPackageInstallLog> log = ArgumentCaptor.forClass(AigPackageInstallLog.class);
        verify(installLogMapper).insert(log.capture());
        assertEquals("INSTALL", log.getValue().getAction());
        assertTrue(log.getValue().getDetail().contains("vision-planner"), log.getValue().getDetail());
    }

    @Test
    @DisplayName("包已停用：不许安装")
    void installRejectsDisabledPackage() {
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", defaultManifest()));
        AigPackage disabled = existingPackage();
        disabled.setStatus("1");
        when(packageMapper.selectById(PKG_ID)).thenReturn(disabled);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.install(PKG_VERSION_ID, 7L));

        assertTrue(error.getMessage().contains("已停用"), error.getMessage());
    }

    @Test
    @DisplayName("入参：空版本ID、版本不存在都报可读错误")
    void installValidatesInput() {
        assertTrue(assertThrows(ServiceException.class, () -> service.install(null, 7L))
            .getMessage().contains("不能为空"));
        when(packageVersionMapper.selectById(99999L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.install(99999L, 7L))
            .getMessage().contains("不存在"));
    }

    // ---------------------------------------------------------------- 包体留存

    @Test
    @DisplayName("默认不留存包体：不碰对象存储，响应如实回报 bodyStored=false")
    void registerDoesNotStoreBodyByDefault() {
        // storeBody 默认 false（见 AigPackageProperties 的取舍说明）
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);

        AigPackageRegisterVo vo = service.register(bo(defaultManifest()),
            BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 7L);

        assertFalse(vo.bodyStored(), "默认关：不留存");
        assertNull(vo.bodyRef());
        verify(bodyStore, never()).put(any(), any());
        verify(bodyStore, never()).buildKey(any(), any(), any());

        // 落库的版本行 body_ref 必须为空（而不是写了别的东西）
        ArgumentCaptor<AigPackageVersion> captor = ArgumentCaptor.forClass(AigPackageVersion.class);
        verify(packageVersionMapper).insert(captor.capture());
        assertNull(captor.getValue().getBodyRef());
    }

    @Test
    @DisplayName("开启留存：先把对象放好再写库，body_ref 与响应一致")
    void registerStoresBodyWhenEnabled() {
        packageProperties.setStoreBody(true);
        when(bodyStore.buildKey("vision-planning-skill", "1.0.0", BODY_SHA))
            .thenReturn("aig-private/package/vision-planning-skill/1.0.0/body-abc123.bin");
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);

        AigPackageRegisterVo vo = service.register(bo(defaultManifest()),
            BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 7L);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(bodyStore).put(eq("aig-private/package/vision-planning-skill/1.0.0/body-abc123.bin"),
            bodyCaptor.capture());
        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), bodyCaptor.getValue(),
            "留存的对象必须就是被核对过哈希的那份字节");

        assertTrue(vo.bodyStored());
        assertEquals("aig-private/package/vision-planning-skill/1.0.0/body-abc123.bin", vo.bodyRef());
        ArgumentCaptor<AigPackageVersion> captor = ArgumentCaptor.forClass(AigPackageVersion.class);
        verify(packageVersionMapper).insert(captor.capture());
        assertEquals(vo.bodyRef(), captor.getValue().getBodyRef());
    }

    @Test
    @DisplayName("开启留存但存储失败：整笔上传失败，且不落任何版本行（不留半截状态）")
    void registerFailsLoudlyWhenStoreFails() {
        packageProperties.setStoreBody(true);
        when(bodyStore.buildKey(any(), any(), any())).thenReturn("aig-private/package/x/1.0.0/body-y.bin");
        // put 是 void：打桩要用 doThrow（when(...) 语法对 void 方法不成立）
        doThrow(new ServiceException("包体留存失败（对象存储不可用或未配置）：S3StorageException"))
            .when(bodyStore).put(any(), any());
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectCount(any())).thenReturn(0L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo(defaultManifest()), BODY.getBytes(StandardCharsets.UTF_8),
                "pkg.zip", 7L));

        assertTrue(error.getMessage().contains("包体留存失败"), error.getMessage());
        // 关键：不能出现「版本登记了、对象存储里没有」这种最难查的半截状态。
        // （包主记录在本步之前就已插入；生产里它随 @Transactional 一起回滚，
        //   单测用 mock 没有事务所以看得见——这里断言的是承载 body_ref 的版本行与账本行。）
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
    }

    // ---------------------------------------------------------------- 停用

    @Test
    @DisplayName("停用：带进来的版本逐个交给发布唯一写入口下线，并如实记下停用前状态")
    void disableDelegatesToReleaseEntry() {
        stubDisableContext();
        when(agentVersionMapper.selectList(any()))
            .thenReturn(List.of(agentVersion(AGENT_VERSION_ID, "DRAFT")));
        when(skillVersionMapper.selectList(any()))
            .thenReturn(List.of(skillVersion(SKILL_VERSION_ID, "STABLE")));

        AigPackageDisableVo vo = service.disable(PKG_VERSION_ID, 7L);

        assertFalse(vo.alreadyDisabled());
        assertEquals(2, vo.disabled().size());
        assertTrue(vo.skipped().isEmpty());

        ArgumentCaptor<AigReleaseAdvanceBo> captor = ArgumentCaptor.forClass(AigReleaseAdvanceBo.class);
        verify(registryService, times(2)).advanceRelease(captor.capture());
        List<AigReleaseAdvanceBo> bos = captor.getAllValues();
        assertEquals("AGENT_VERSION", bos.get(0).getTargetType());
        assertEquals("DRAFT", bos.get(0).getExpectedStatus(), "expectedStatus 必须取库中当前状态（CAS 的前提）");
        assertEquals("DISABLED", bos.get(0).getToStatus());
        assertEquals("SKILL_VERSION", bos.get(1).getTargetType());
        assertEquals("STABLE", bos.get(1).getExpectedStatus(),
            "把一个 STABLE 版本下线时，expectedStatus 也必须是它当前的状态");
        assertEquals(7L, bos.get(0).getOperatorId());
        assertTrue(bos.get(1).getDetail().contains("STABLE"), bos.get(1).getDetail());

        // 停用前状态要如实上报：STABLE 下线与 DRAFT 下线的影响面不是一回事
        assertEquals("DRAFT", vo.disabled().get(0).fromStatus());
        assertEquals("STABLE", vo.disabled().get(1).fromStatus());

        ArgumentCaptor<AigPackageInstallLog> log = ArgumentCaptor.forClass(AigPackageInstallLog.class);
        verify(installLogMapper).insert(log.capture());
        assertEquals("DISABLE", log.getValue().getAction());
        assertEquals("PASS", log.getValue().getResult());
        assertTrue(log.getValue().getDetail().contains("STABLE→DISABLED"), log.getValue().getDetail());
    }

    @Test
    @DisplayName("停用幂等：带进来的版本都已在停用状态时，不改库也不写账本")
    void disableIsIdempotent() {
        stubDisableContext();
        when(agentVersionMapper.selectList(any()))
            .thenReturn(List.of(agentVersion(AGENT_VERSION_ID, "DISABLED")));
        when(skillVersionMapper.selectList(any()))
            .thenReturn(List.of(skillVersion(SKILL_VERSION_ID, "DISABLED")));

        AigPackageDisableVo vo = service.disable(PKG_VERSION_ID, 7L);

        assertTrue(vo.alreadyDisabled());
        assertTrue(vo.disabled().isEmpty());
        assertEquals(2, vo.skipped().size());
        verify(registryService, never()).advanceRelease(any());
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
    }

    @Test
    @DisplayName("从没装过就停用：报「没有安装出任何版本」，而不是静默成功一条账")
    void disableRequiresInstalled() {
        stubDisableContext();
        when(agentVersionMapper.selectList(any())).thenReturn(List.of());
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.disable(PKG_VERSION_ID, 7L));

        assertTrue(error.getMessage().contains("没有安装出任何版本"), error.getMessage());
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
    }

    @Test
    @DisplayName("归档版本跳过并说明原因（不阻断其余版本）；全归档到一条也停不了才报错")
    void disableSkipsArchived() {
        stubDisableContext();
        // 一个 DRAFT + 一个 ARCHIVED：只停 DRAFT，归档的进 skipped 带原因
        when(agentVersionMapper.selectList(any()))
            .thenReturn(List.of(agentVersion(AGENT_VERSION_ID, "ARCHIVED")));
        when(skillVersionMapper.selectList(any()))
            .thenReturn(List.of(skillVersion(SKILL_VERSION_ID, "DRAFT")));

        AigPackageDisableVo vo = service.disable(PKG_VERSION_ID, 7L);

        assertFalse(vo.alreadyDisabled());
        assertEquals(1, vo.disabled().size());
        assertEquals(SKILL_VERSION_ID, vo.disabled().get(0).versionId());
        assertEquals(1, vo.skipped().size());
        assertTrue(vo.skipped().get(0).reason().contains("终态"),
            "跳过必须带可读原因：" + vo.skipped().get(0).reason());
        // 归档的那个不该被交给发布服务（它根本不可迁移）
        verify(registryService, times(1)).advanceRelease(any());
        // 但这次确实停了一个，所以账本要有一行
        verify(installLogMapper, times(1)).insert(any(AigPackageInstallLog.class));
    }

    @Test
    @DisplayName("全部归档：一条也停不了 → 报错，不写账本（不能记一条没做任何事的 DISABLE）")
    void disableFailsWhenAllArchived() {
        stubDisableContext();
        when(agentVersionMapper.selectList(any()))
            .thenReturn(List.of(agentVersion(AGENT_VERSION_ID, "ARCHIVED")));
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.disable(PKG_VERSION_ID, 7L));

        assertTrue(error.getMessage().contains("一条也停不了"), error.getMessage());
        verify(registryService, never()).advanceRelease(any());
        verify(installLogMapper, never()).insert(any(AigPackageInstallLog.class));
    }

    @Test
    @DisplayName("停用入参：空版本ID、版本不存在都报可读错误")
    void disableValidatesInput() {
        assertTrue(assertThrows(ServiceException.class, () -> service.disable(null, 7L))
            .getMessage().contains("不能为空"));
        when(packageVersionMapper.selectById(99999L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.disable(99999L, 7L))
            .getMessage().contains("不存在"));
    }

    // ---------------------------------------------------------------- 包级停用/启用

    @Test
    @DisplayName("包级停用：只改 status 这一列，并说明「既有版本不受影响」")
    void disablePackageSetsStatus() {
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());

        AigPackageStatusVo vo = service.disablePackage(PKG_ID, 7L);

        assertTrue(vo.disabled());
        assertTrue(vo.changed());
        assertEquals("vision-planning-skill", vo.packageCode());
        assertTrue(vo.note().contains("既有版本不受影响"), vo.note());

        ArgumentCaptor<AigPackage> captor = ArgumentCaptor.forClass(AigPackage.class);
        verify(packageMapper).updateById(captor.capture());
        assertEquals(PKG_ID, captor.getValue().getPackageId());
        assertEquals("1", captor.getValue().getStatus(), "包级停用只改 status，不碰任何版本状态");
    }

    @Test
    @DisplayName("包级停用幂等：已是停用状态则不改库，并说清楚「没做任何改动」")
    void disablePackageIsIdempotent() {
        AigPackage pkg = existingPackage();
        pkg.setStatus("1");
        when(packageMapper.selectById(PKG_ID)).thenReturn(pkg);

        AigPackageStatusVo vo = service.disablePackage(PKG_ID, 7L);

        assertTrue(vo.disabled());
        assertFalse(vo.changed());
        assertTrue(vo.note().contains("未做任何改动"), vo.note());
        verify(packageMapper, never()).updateById(any(AigPackage.class));
    }

    @Test
    @DisplayName("包级启用：置回正常，并说明被停用的版本不会因此恢复")
    void enablePackageRestoresStatus() {
        AigPackage pkg = existingPackage();
        pkg.setStatus("1");
        when(packageMapper.selectById(PKG_ID)).thenReturn(pkg);

        AigPackageStatusVo vo = service.enablePackage(PKG_ID, 7L);

        assertFalse(vo.disabled());
        assertTrue(vo.changed());
        assertTrue(vo.note().contains("不会因此恢复"), vo.note());

        ArgumentCaptor<AigPackage> captor = ArgumentCaptor.forClass(AigPackage.class);
        verify(packageMapper).updateById(captor.capture());
        assertEquals("0", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("包级停用与安装检查接上了：停用后安装被拒（那条检查原先永远为假——没有任何入口能置位）")
    void packageLevelDisableNowBlocksInstall() {
        // 生产端：disablePackage 把 status 置 1
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());
        AigPackageStatusVo status = service.disablePackage(PKG_ID, 7L);
        assertTrue(status.changed());

        // 消费端：status=1 时安装被拒（同一条检查）
        AigPackage disabled = existingPackage();
        disabled.setStatus("1");
        when(packageMapper.selectById(PKG_ID)).thenReturn(disabled);
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", defaultManifest()));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.install(PKG_VERSION_ID, 7L));

        assertTrue(error.getMessage().contains("已停用"), error.getMessage());
    }

    @Test
    @DisplayName("包级停用入参：ID 为空、包不存在都报可读错误")
    void packageStatusValidatesInput() {
        assertTrue(assertThrows(ServiceException.class, () -> service.disablePackage(null, 7L))
            .getMessage().contains("不能为空"));
        when(packageMapper.selectById(99999L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.enablePackage(99999L, 7L))
            .getMessage().contains("不存在"));
    }

    /**
     * 停用用例的公共桩：Package 版本在、包在、父记录在。
     */
    private void stubDisableContext() {
        when(packageVersionMapper.selectById(PKG_VERSION_ID))
            .thenReturn(packageVersion("PASS", defaultManifest()));
        when(packageMapper.selectById(PKG_ID)).thenReturn(existingPackage());
        when(agentMapper.selectById(AGENT_ID)).thenReturn(existingAgent());
        when(skillMapper.selectById(SKILL_ID)).thenReturn(existingSkill());
    }

    /**
     * 造一个 Agent 版本。
     *
     * @param versionId 版本ID
     * @param status    发布状态
     * @return 版本
     */
    private static AigAgentVersion agentVersion(long versionId, String status) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(versionId);
        version.setAgentId(AGENT_ID);
        version.setVersion("1.0.0");
        version.setReleaseStatus(status);
        version.setPackageVersionId(PKG_VERSION_ID);
        return version;
    }

    /**
     * 造一个 Skill 版本。
     *
     * @param versionId 版本ID
     * @param status    发布状态
     * @return 版本
     */
    private static AigSkillVersion skillVersion(long versionId, String status) {
        AigSkillVersion version = new AigSkillVersion();
        version.setSkillVersionId(versionId);
        version.setSkillId(SKILL_ID);
        version.setVersion("1.0.0");
        version.setReleaseStatus(status);
        version.setPackageVersionId(PKG_VERSION_ID);
        return version;
    }

    /**
     * 造一个已存在的 Agent。
     *
     * @return Agent
     */
    private static AigAgent existingAgent() {
        AigAgent agent = new AigAgent();
        agent.setAgentId(AGENT_ID);
        agent.setAgentCode("vision-planner");
        return agent;
    }

    /**
     * 造一个已存在的包。
     *
     * @return 包
     */
    private static AigPackage existingPackage() {
        AigPackage pkg = new AigPackage();
        pkg.setPackageId(PKG_ID);
        pkg.setPackageCode("vision-planning-skill");
        pkg.setStatus("0");
        return pkg;
    }

    /**
     * 造一个 Package 版本。
     *
     * @param scanResult 扫描结论
     * @param manifestJson Manifest 原文
     * @return 版本
     */
    private static AigPackageVersion packageVersion(String scanResult, String manifestJson) {
        AigPackageVersion version = new AigPackageVersion();
        version.setPackageVersionId(PKG_VERSION_ID);
        version.setPackageId(PKG_ID);
        version.setVersion("1.0.0");
        version.setScanResult(scanResult);
        version.setManifestJson(manifestJson);
        version.setManifestHash(AigPackageManifestValidator.manifestHash(manifestJson));
        version.setReleaseStatus("DRAFT");
        return version;
    }

    /**
     * 造一个已存在的 Skill。
     *
     * @return Skill
     */
    private static AigSkill existingSkill() {
        AigSkill skill = new AigSkill();
        skill.setSkillId(SKILL_ID);
        skill.setSkillCode("image-fitter");
        return skill;
    }

    /**
     * 归一化哈希比较（大小写无关）。
     *
     * @param value 值
     * @return 小写
     */
    private static String norm(String value) {
        return value == null ? null : value.toLowerCase();
    }

}
