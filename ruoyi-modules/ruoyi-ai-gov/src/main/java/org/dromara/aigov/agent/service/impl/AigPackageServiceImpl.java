package org.dromara.aigov.agent.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageInstallLog;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigSkill;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.bo.AigPackageUploadBo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallLogVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;
import org.dromara.aigov.agent.enums.AigPackageInstallActionEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.manifest.AigManifestAgentSpec;
import org.dromara.aigov.agent.manifest.AigManifestScanResult;
import org.dromara.aigov.agent.manifest.AigManifestSkillSpec;
import org.dromara.aigov.agent.manifest.AigPackageManifest;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageInstallLogMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigPackageService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package 上传登记与安装实现（设计 §6.1、§6.3）。
 *
 * <h3>两处必须写清楚的落库口径（Manifest 有、表里没有对应列）</h3>
 * <ol>
 *     <li><b>{@code data_level}</b> → 进 {@code config_json}。<b>不新增列</b>的理由：目前没有任何
 *         代码按版本数据等级做拦截（真正生效的是模型路由层的数据等级），此时加列只是多一处
 *         「存了但没人读」；等要做「按 Agent 版本限制可处理的数据等级」时再把它提升为列，
 *         那时才有判据可依据。</li>
 *     <li><b>{@code scenario_codes}</b> → 进 {@code config_json}；**恰好声明一个时同时写
 *         版本的 {@code scenario_code} 列**（该列是单值），这样既有的按场景过滤/路由能认得它；
 *         声明多个就只进 config_json，不擅自挑一个塞进单值列。</li>
 * </ol>
 * <p>其余无列字段（{@code min_platform_version} / {@code dependencies} / {@code roles} /
 * {@code network_access} / {@code network_hosts} / {@code upgrade_policy} / {@code rollback_policy}）
 * 一并进 {@code config_json}，原样保留、不丢；{@code knowledge_scope} 有列（
 * {@code knowledge_scope_json}），写列。</p>
 *
 * <h3>两处刻意的语义选择</h3>
 * <ul>
 *     <li><b>要求携包体</b>：服务端据此算 SHA-256 并与 Manifest 声明的 {@code checksum} 比对，
 *         校验和从此不是「调用方说了算」。<b>包体本身不入库</b>——声明式 Package 的安装只读
 *         Manifest，包体只用于核对哈希；要留存包体应交给平台文件服务并把键填进 {@code source_ref}。</li>
 *     <li><b>{@code aig_package.checksum} 随每次上传更新为「最近一次包体哈希」</b>：这样
 *         Manifest 校验里「Manifest 声明 vs 包记录」的交叉核对在同一版本上恒成立；
 *         若把它冻结在首次上传，第二个版本就会被自己的校验规则误判为「校验和不一致」。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigPackageServiceImpl implements IAigPackageService {

    /**
     * 包体大小上限（声明式包是 Manifest + 资源描述，不该是大文件；超限通常说明传错了东西）
     */
    private static final long MAX_BODY_BYTES = 64L * 1024L * 1024L;

    /**
     * 日志说明的列宽（{@code detail varchar(1000)}）
     */
    private static final int DETAIL_MAX = 1000;

    /**
     * scan_detail 的列宽
     */
    private static final int SCAN_DETAIL_MAX = 1000;

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 新建的第三方对象（非平台内置）
     */
    private static final String BUILTIN_NO = "N";

    /**
     * 是否允许外部调用：否
     */
    private static final String NO = "N";

    private final AigPackageMapper packageMapper;

    private final AigPackageVersionMapper packageVersionMapper;

    private final AigPackageInstallLogMapper installLogMapper;

    private final AigAgentMapper agentMapper;

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigSkillMapper skillMapper;

    private final AigSkillVersionMapper skillVersionMapper;

    private final AigPackageManifestValidator manifestValidator;

    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPackageRegisterVo register(AigPackageUploadBo bo, byte[] body, String bodyName,
                                         Long operatorId) {
        if (bo == null) {
            throw new ServiceException("上传入参不能为空");
        }
        if (StringUtils.isBlank(bo.getManifestJson())) {
            throw new ServiceException("Manifest 不能为空");
        }
        if (body == null || body.length == 0) {
            throw new ServiceException("包体不能为空：上传必须携包体，平台据此计算校验和"
                + "（否则 checksum 只是调用方的声明，而不是可核对的事实）");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new ServiceException("包体超过上限 " + MAX_BODY_BYTES + " 字节（实际 "
                + body.length + "）：声明式 Package 不该这么大，请确认传的不是别的东西");
        }

        String rawManifest = bo.getManifestJson();
        AigManifestScanResult scan = manifestValidator.scan(rawManifest);
        AigPackageManifest manifest = scan.getManifest();
        if (manifest == null) {
            throw new ServiceException("Manifest 无法解析为 JSON 对象，未登记任何东西："
                + scan.getDetail());
        }

        // 包体哈希由服务端算，并与 Manifest 声明比对——不一致就不登记（两者不是一对）
        String bodyHash = DigestUtil.sha256Hex(body);
        String declared = StringUtils.trim(manifest.checksum());
        if (!bodyHash.equalsIgnoreCase(declared)) {
            throw new ServiceException("包体校验和不一致，未登记任何东西：Manifest 声明 "
                + declared + "，平台对上传字节算出 " + bodyHash
                + "。上传说的是「这份包体就是 Manifest 描述的那一份」，不一致就不落库");
        }

        AigPackage pkg = packageMapper.selectOne(new LambdaQueryWrapper<AigPackage>()
            .eq(AigPackage::getPackageCode, manifest.packageCode()));
        if (pkg == null) {
            pkg = new AigPackage();
            pkg.setPackageCode(manifest.packageCode());
            pkg.setPackageName(manifest.name());
            pkg.setPackageType(manifest.packageType());
            pkg.setPublisher(manifest.publisher());
            pkg.setLicenseCode(manifest.license());
            pkg.setSourceType(StringUtils.isBlank(bo.getSourceRef()) ? "UPLOAD" : "TRUSTED_SOURCE");
            pkg.setSourceRef(StringUtils.substring(bo.getSourceRef(), 0, 500));
            pkg.setDescription("由上传登记，包体 " + StringUtils.substring(bodyName, 0, 120)
                + "（" + body.length + " 字节）；包体不入库，校验和见 checksum");
            pkg.setStatus(STATUS_NORMAL);
            pkg.setDelFlag(STATUS_NORMAL);
            pkg.setRemark(StringUtils.substring(bo.getRemark(), 0, 500));
            pkg.setChecksum(bodyHash);
            packageMapper.insert(pkg);
        } else {
            if (!STATUS_NORMAL.equals(pkg.getStatus())) {
                throw new ServiceException("该 Package 已停用，不接受新版本上传：" + pkg.getPackageCode());
            }
            // checksum 语义 = 最近一次包体哈希（见类注释：冻结首次会让后续版本被自己的交叉核对误判）
            AigPackage update = new AigPackage();
            update.setPackageId(pkg.getPackageId());
            update.setChecksum(bodyHash);
            packageMapper.updateById(update);
            pkg.setChecksum(bodyHash);
        }

        Long exists = packageVersionMapper.selectCount(new LambdaQueryWrapper<AigPackageVersion>()
            .eq(AigPackageVersion::getPackageId, pkg.getPackageId())
            .eq(AigPackageVersion::getVersion, manifest.version()));
        if (exists != null && exists > 0L) {
            throw new ServiceException("该包的这个版本已存在，不允许覆盖：" + manifest.packageCode()
                + "@" + manifest.version() + "（版本不可变——覆盖会让已发布的结论失去依据）");
        }

        AigPackageVersion version = new AigPackageVersion();
        version.setPackageId(pkg.getPackageId());
        version.setVersion(manifest.version());
        version.setManifestJson(rawManifest);
        version.setManifestHash(AigPackageManifestValidator.manifestHash(rawManifest));
        version.setScanResult(scan.scanResult());
        version.setScanDetail(StringUtils.substring(scan.getDetail(), 0, SCAN_DETAIL_MAX));
        version.setReleaseStatus(AigReleaseStatusEnum.DRAFT.getCode());
        version.setReleaseChannel("TESTING");
        version.setStatus(STATUS_NORMAL);
        version.setDelFlag(STATUS_NORMAL);
        version.setRemark(StringUtils.substring(bo.getRemark(), 0, 500));
        packageVersionMapper.insert(version);

        writeLog(version.getPackageVersionId(), AigPackageInstallActionEnum.UPLOAD,
            scan.isPass() ? "PASS" : "REJECT", scan.getDetail(), operatorId);
        log.info("Package 上传登记完成, packageCode={}, version={}, scan={}, bodySha256={}",
            manifest.packageCode(), manifest.version(), scan.scanResult(), bodyHash);
        return new AigPackageRegisterVo(pkg.getPackageId(), version.getPackageVersionId(),
            manifest.packageCode(), manifest.version(), bodyHash, version.getManifestHash(),
            scan.isPass(), scan.scanResult(), scan.getDetail(), manifest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPackageInstallVo install(Long packageVersionId, Long operatorId) {
        if (packageVersionId == null) {
            throw new ServiceException("Package 版本ID不能为空");
        }
        AigPackageVersion version = packageVersionMapper.selectById(packageVersionId);
        if (version == null) {
            throw new ServiceException("Package 版本不存在：" + packageVersionId);
        }
        AigPackage pkg = packageMapper.selectById(version.getPackageId());
        if (pkg == null) {
            throw new ServiceException("Package 主记录不存在：" + version.getPackageId());
        }
        if (!STATUS_NORMAL.equals(pkg.getStatus())) {
            throw new ServiceException("该 Package 已停用，不能安装：" + pkg.getPackageCode());
        }
        // 查库里的证据，而不是认调用方的声明
        if (!AigManifestScanResult.PASS.equals(version.getScanResult())) {
            throw new ServiceException("Manifest 校验未通过（scan_result="
                + (StringUtils.isBlank(version.getScanResult()) ? "未扫描" : version.getScanResult())
                + "），不能安装。请先执行 Manifest 扫描并取得 PASS。Package 版本 #"
                + packageVersionId);
        }

        AigPackageManifest manifest = manifestValidator.parse(version.getManifestJson());
        if (manifest == null) {
            throw new ServiceException("库中的 Manifest 原文无法解析，不能安装：Package 版本 #"
                + packageVersionId);
        }
        if (manifest.agents().isEmpty() && manifest.skills().isEmpty()) {
            throw new ServiceException("该 Manifest 没有声明任何 Agent/Skill，没有内容可安装。"
                + "请在 Manifest 里用 agents/skills 声明内容物（声明式：只写配置，不写代码）");
        }

        // 幂等判据：已存在指向该 Package 版本的 Agent/Skill 版本行（日志是审计，不是状态）
        List<AigAgentVersion> existingAgents = agentVersionMapper.selectList(
            new LambdaQueryWrapper<AigAgentVersion>()
                .eq(AigAgentVersion::getPackageVersionId, packageVersionId));
        List<AigSkillVersion> existingSkills = skillVersionMapper.selectList(
            new LambdaQueryWrapper<AigSkillVersion>()
                .eq(AigSkillVersion::getPackageVersionId, packageVersionId));
        if (!existingAgents.isEmpty() || !existingSkills.isEmpty()) {
            return new AigPackageInstallVo(pkg.getPackageId(), packageVersionId, true,
                toAgentItems(existingAgents), toSkillItems(existingSkills),
                "该 Package 版本此前已安装（以 Agent/Skill 版本行的 package_version_id 为判据），"
                    + "本次未重复创建");
        }

        List<AigPackageInstallVo.InstalledItem> agents = new ArrayList<>();
        for (AigManifestAgentSpec spec : manifest.agents()) {
            agents.add(installAgent(spec, manifest, pkg, version, operatorId));
        }
        List<AigPackageInstallVo.InstalledItem> skills = new ArrayList<>();
        for (AigManifestSkillSpec spec : manifest.skills()) {
            skills.add(installSkill(spec, manifest, pkg, version, operatorId));
        }

        String detail = "安装 " + agents.size() + " 个 Agent、 " + skills.size() + " 个 Skill（均为 DRAFT）："
            + codes(agents) + codes(skills);
        writeLog(packageVersionId, AigPackageInstallActionEnum.INSTALL, "PASS", detail, operatorId);
        log.info("Package 安装完成, packageVersionId={}, agents={}, skills={}", packageVersionId,
            codes(agents), codes(skills));
        return new AigPackageInstallVo(pkg.getPackageId(), packageVersionId, false, agents, skills,
            "安装出的版本一律为 DRAFT：安装不等于发布，发布门槛照旧要逐道过");
    }

    @Override
    public List<AigPackageInstallLogVo> listInstallLog(Long packageVersionId) {
        if (packageVersionId == null) {
            throw new ServiceException("Package 版本ID不能为空");
        }
        return installLogMapper.selectVoList(new LambdaQueryWrapper<AigPackageInstallLog>()
            .eq(AigPackageInstallLog::getPackageVersionId, packageVersionId)
            .orderByAsc(AigPackageInstallLog::getOperateTime)
            .orderByAsc(AigPackageInstallLog::getLogId));
    }

    /**
     * 装一个 Agent（复用同编码的 Agent 定义，建/复用版本）。
     *
     * @param spec     声明
     * @param manifest Manifest（取包级字段）
     * @param pkg      包
     * @param version  Package 版本
     * @param operatorId 操作人
     * @return 安装项
     */
    private AigPackageInstallVo.InstalledItem installAgent(AigManifestAgentSpec spec,
                                                           AigPackageManifest manifest,
                                                           AigPackage pkg,
                                                           AigPackageVersion version,
                                                           Long operatorId) {
        AigAgent agent = agentMapper.selectOne(new LambdaQueryWrapper<AigAgent>()
            .eq(AigAgent::getAgentCode, spec.code()));
        if (agent == null) {
            agent = new AigAgent();
            agent.setAgentCode(spec.code());
            agent.setAgentName(spec.name());
            agent.setCategory(spec.category());
            agent.setBuiltin(BUILTIN_NO);
            agent.setDescription("由 Package " + pkg.getPackageCode() + "@" + version.getVersion()
                + " 安装带入");
            agent.setStatus(STATUS_NORMAL);
            agent.setDelFlag(STATUS_NORMAL);
            agentMapper.insert(agent);
        }

        AigAgentVersion exists = agentVersionMapper.selectOne(new LambdaQueryWrapper<AigAgentVersion>()
            .eq(AigAgentVersion::getAgentId, agent.getAgentId())
            .eq(AigAgentVersion::getVersion, version.getVersion()));
        if (exists != null) {
            return new AigPackageInstallVo.InstalledItem(spec.code(), agent.getAgentId(),
                exists.getAgentVersionId(), version.getVersion(), false);
        }

        AigAgentVersion entity = new AigAgentVersion();
        entity.setAgentId(agent.getAgentId());
        entity.setVersion(version.getVersion());
        entity.setReleaseStatus(AigReleaseStatusEnum.DRAFT.getCode());
        entity.setReleaseChannel("TESTING");
        // scenario_codes 恰好一个时才落到单值列（多个不擅自挑一个）
        entity.setScenarioCode(manifest.scenarioCodes().size() == 1
            ? manifest.scenarioCodes().get(0) : null);
        entity.setInputSchema(spec.inputSchemaJson());
        entity.setOutputSchema(spec.outputSchemaJson());
        entity.setPromptTemplate(spec.promptTemplate());
        entity.setProviderCapability(spec.providerCapability());
        entity.setAllowExternal(StringUtils.isBlank(spec.allowExternal())
            ? NO : spec.allowExternal().trim().toUpperCase());
        entity.setAllowedTools(join(manifest.requiredTools()));
        entity.setForbiddenTools(join(manifest.forbiddenTools()));
        entity.setKnowledgeScopeJson(toJson(manifest.knowledgeScope()));
        entity.setConfigJson(agentConfigJson(manifest, spec, pkg, version));
        entity.setPackageVersionId(version.getPackageVersionId());
        entity.setStatus(STATUS_NORMAL);
        entity.setDelFlag(STATUS_NORMAL);
        entity.setRemark("由 Package " + pkg.getPackageCode() + "@" + version.getVersion() + " 安装带入");
        agentVersionMapper.insert(entity);
        return new AigPackageInstallVo.InstalledItem(spec.code(), agent.getAgentId(),
            entity.getAgentVersionId(), version.getVersion(), true);
    }

    /**
     * 装一个 Skill。
     *
     * @param spec     声明
     * @param manifest Manifest
     * @param pkg      包
     * @param version  Package 版本
     * @param operatorId 操作人
     * @return 安装项
     */
    private AigPackageInstallVo.InstalledItem installSkill(AigManifestSkillSpec spec,
                                                           AigPackageManifest manifest,
                                                           AigPackage pkg,
                                                           AigPackageVersion version,
                                                           Long operatorId) {
        AigSkill skill = skillMapper.selectOne(new LambdaQueryWrapper<AigSkill>()
            .eq(AigSkill::getSkillCode, spec.code()));
        if (skill == null) {
            skill = new AigSkill();
            skill.setSkillCode(spec.code());
            skill.setSkillName(spec.name());
            skill.setCapabilities(join(spec.capabilities()));
            skill.setBuiltin(BUILTIN_NO);
            skill.setDescription("由 Package " + pkg.getPackageCode() + "@" + version.getVersion()
                + " 安装带入");
            skill.setStatus(STATUS_NORMAL);
            skill.setDelFlag(STATUS_NORMAL);
            skillMapper.insert(skill);
        }

        AigSkillVersion exists = skillVersionMapper.selectOne(new LambdaQueryWrapper<AigSkillVersion>()
            .eq(AigSkillVersion::getSkillId, skill.getSkillId())
            .eq(AigSkillVersion::getVersion, version.getVersion()));
        if (exists != null) {
            return new AigPackageInstallVo.InstalledItem(spec.code(), skill.getSkillId(),
                exists.getSkillVersionId(), version.getVersion(), false);
        }

        AigSkillVersion entity = new AigSkillVersion();
        entity.setSkillId(skill.getSkillId());
        entity.setVersion(version.getVersion());
        entity.setReleaseStatus(AigReleaseStatusEnum.DRAFT.getCode());
        entity.setReleaseChannel("TESTING");
        entity.setConfigJson(skillConfigJson(manifest, spec, pkg, version));
        entity.setToolPolicyJson(spec.toolPolicyJson());
        entity.setProviderCapability(spec.providerCapability());
        entity.setAllowExternal(StringUtils.isBlank(spec.allowExternal())
            ? NO : spec.allowExternal().trim().toUpperCase());
        entity.setInputSchema(spec.inputSchemaJson());
        entity.setOutputSchema(spec.outputSchemaJson());
        // 来源 Package 版本：Agent 版本一早就带这一列，Skill 版本是这一步补的
        // （没有它就只能去翻安装日志判断「装过没有」）
        entity.setPackageVersionId(version.getPackageVersionId());
        entity.setStatus(STATUS_NORMAL);
        entity.setDelFlag(STATUS_NORMAL);
        entity.setRemark("由 Package " + pkg.getPackageCode() + "@" + version.getVersion() + " 安装带入");
        skillVersionMapper.insert(entity);
        return new AigPackageInstallVo.InstalledItem(spec.code(), skill.getSkillId(),
            entity.getSkillVersionId(), version.getVersion(), true);
    }

    /**
     * Agent 版本的 config_json（包级 + 该 Agent 自己的黄金用例）。
     *
     * @param manifest Manifest
     * @param spec     声明
     * @param pkg      包
     * @param version  Package 版本
     * @return JSON 文本
     */
    private String agentConfigJson(AigPackageManifest manifest, AigManifestAgentSpec spec,
                                   AigPackage pkg, AigPackageVersion version) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("source_package", sourceRef(pkg, version));
        config.put("golden_cases", spec.goldenCases());
        config.put("capabilities", manifest.capabilities());
        config.put("scenario_codes", manifest.scenarioCodes());
        putPackageLevel(config, manifest);
        return toJson(config);
    }

    /**
     * Skill 版本的 config_json。
     *
     * @param manifest Manifest
     * @param spec     声明
     * @param pkg      包
     * @param version  Package 版本
     * @return JSON 文本
     */
    private String skillConfigJson(AigPackageManifest manifest, AigManifestSkillSpec spec,
                                   AigPackage pkg, AigPackageVersion version) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("source_package", sourceRef(pkg, version));
        config.put("capabilities", spec.capabilities());
        config.put("scenario_codes", manifest.scenarioCodes());
        putPackageLevel(config, manifest);
        return toJson(config);
    }

    /**
     * 把「Manifest 有、版本表没有列」的字段原样放进 config_json（见类注释的口径）。
     *
     * @param config   目标
     * @param manifest Manifest
     */
    private void putPackageLevel(Map<String, Object> config, AigPackageManifest manifest) {
        config.put("data_level", manifest.dataLevel());
        config.put("min_platform_version", manifest.minPlatformVersion());
        config.put("dependencies", manifest.dependencyCodes());
        config.put("roles", manifest.roles());
        config.put("network_access", manifest.networkAccess());
        config.put("network_hosts", manifest.networkHosts());
        config.put("upgrade_policy", manifest.upgradePolicy());
        config.put("rollback_policy", manifest.rollbackPolicy());
    }

    /**
     * 来源包引用（放进 config_json，便于回溯「这个版本是哪次安装带进来的」）。
     *
     * @param pkg     包
     * @param version Package 版本
     * @return 引用
     */
    private Map<String, Object> sourceRef(AigPackage pkg, AigPackageVersion version) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("package_code", pkg.getPackageCode());
        source.put("version", version.getVersion());
        source.put("package_version_id", version.getPackageVersionId());
        source.put("manifest_hash", version.getManifestHash());
        return source;
    }

    /**
     * 写一行安装日志（追加型账本）。
     *
     * @param packageVersionId Package 版本ID
     * @param action           动作
     * @param result           结果
     * @param detail           说明
     * @param operatorId       操作人
     */
    private void writeLog(Long packageVersionId, AigPackageInstallActionEnum action, String result,
                          String detail, Long operatorId) {
        AigPackageInstallLog log = new AigPackageInstallLog();
        log.setPackageVersionId(packageVersionId);
        log.setAction(action.getCode());
        log.setResult(result);
        log.setDetail(StringUtils.substring(detail, 0, DETAIL_MAX));
        log.setOperatorId(operatorId);
        log.setOperateTime(LocalDateTime.now());
        installLogMapper.insert(log);
    }

    /**
     * 已装的 Agent 版本 -> 安装项。
     *
     * @param versions 版本行
     * @return 安装项
     */
    private List<AigPackageInstallVo.InstalledItem> toAgentItems(List<AigAgentVersion> versions) {
        List<AigPackageInstallVo.InstalledItem> items = new ArrayList<>();
        for (AigAgentVersion version : versions) {
            AigAgent agent = agentMapper.selectById(version.getAgentId());
            items.add(new AigPackageInstallVo.InstalledItem(
                agent == null ? null : agent.getAgentCode(), version.getAgentId(),
                version.getAgentVersionId(), version.getVersion(), false));
        }
        return items;
    }

    /**
     * 已装的 Skill 版本 -> 安装项。
     *
     * @param versions 版本行
     * @return 安装项
     */
    private List<AigPackageInstallVo.InstalledItem> toSkillItems(List<AigSkillVersion> versions) {
        List<AigPackageInstallVo.InstalledItem> items = new ArrayList<>();
        for (AigSkillVersion version : versions) {
            AigSkill skill = skillMapper.selectById(version.getSkillId());
            items.add(new AigPackageInstallVo.InstalledItem(
                skill == null ? null : skill.getSkillCode(), version.getSkillId(),
                version.getSkillVersionId(), version.getVersion(), false));
        }
        return items;
    }

    /**
     * 逗号拼接（空清单返回 null，避免「有值但为空」）。
     *
     * @param items 清单
     * @return 文本
     */
    private static String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join(",", items);
    }

    /**
     * 安装项编码摘要。
     *
     * @param items 安装项
     * @return 文本
     */
    private static String codes(List<AigPackageInstallVo.InstalledItem> items) {
        StringBuilder sb = new StringBuilder();
        for (AigPackageInstallVo.InstalledItem item : items) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(item.code());
        }
        return sb.toString();
    }

    /**
     * 序列化为 JSON 文本。
     *
     * @param value 值
     * @return 文本；失败返回 null（配置写不进去也不能让安装失败，另有日志）
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("config_json 序列化失败，已放弃写入该字段: {}", e.getMessage());
            return null;
        }
    }

}
