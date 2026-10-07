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
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageInstallLogMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
        service = new AigPackageServiceImpl(packageMapper, packageVersionMapper, installLogMapper,
            agentMapper, agentVersionMapper, skillMapper, skillVersionMapper,
            new AigPackageManifestValidator(JsonMapper.builder().build()),
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
    }

    @Test
    @DisplayName("Manifest 解析不出来：不登记任何东西")
    void registerRejectsUnparseableManifest() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.register(bo("{oops"), BODY.getBytes(StandardCharsets.UTF_8), "pkg.zip", 1L));
        assertTrue(error.getMessage().contains("无法解析"), error.getMessage());
        verify(packageVersionMapper, never()).insert(any(AigPackageVersion.class));
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
