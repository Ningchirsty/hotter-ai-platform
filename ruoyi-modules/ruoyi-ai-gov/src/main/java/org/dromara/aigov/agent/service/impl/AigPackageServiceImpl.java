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
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.domain.vo.AigPackageDisableVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallLogVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;
import org.dromara.aigov.agent.domain.vo.AigPackageStatusVo;
import org.dromara.aigov.agent.enums.AigPackageInstallActionEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.helper.AigArchiveScanResult;
import org.dromara.aigov.agent.helper.AigPackageArchiveScanner;
import org.dromara.aigov.agent.helper.AigPackageRejectionRecorder;
import org.dromara.aigov.agent.helper.IAigPackageBodyStore;
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
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.aigov.agent.service.IAigPackageService;
import org.dromara.aigov.agent.state.AigReleaseStateMachine;
import org.dromara.aigov.config.AigPackageProperties;
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
 * Package 上传登记、安装与停用实现（设计 §6.1、§6.3）。
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
 *         校验和从此不是「调用方说了算」。<b>包体默认不留存</b>（声明式 Package 的安装只读
 *         Manifest，包体只用于核对哈希）；开启 {@code aigov.package.store-body} 后留存到对象存储，
 *         对象键写进<b>该版本</b>的 {@code body_ref}（逐版本而非逐包：每次上传的包体可能不同，
 *         记在包上会被下一个版本覆盖）。留存失败即整笔上传失败，不落半截状态。</li>
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
     * 记录状态：停用（包级停用 = 不再接受新版本上传、不能安装；<b>不动已装出去的版本</b>）
     */
    private static final String PACKAGE_DISABLED = "1";

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

    /**
     * 发布状态的唯一写入口。停用<b>不直接改</b>版本的 {@code release_status}，
     * 而是逐个交给它——那里才有状态机边判定、CAS 并发保护与发布事件账本。
     */
    private final IAigAgentRegistryService registryService;

    /**
     * Package 链路行为配置（当前只有「是否留存包体」一个开关，默认关）。
     */
    private final AigPackageProperties packageProperties;

    /**
     * 包体留存（对象存储）。只在开关开启时被调用。
     */
    private final IAigPackageBodyStore bodyStore;

    /**
     * 包体（压缩包）安全检查器（F-02）。
     *
     * <p>与 {@link #manifestValidator} 分工：后者查"包声明了什么"，前者查"包里装了什么"。
     * 少了它，一个 Manifest 干净、包里塞着 {@code install.sh} 的包会被照单收下。</p>
     */
    private final AigPackageArchiveScanner archiveScanner;

    /**
     * 被拒证据登记（独立事务）：注册被拒时「对方交了什么」必须留下来，见 ADR-013。
     */
    private final AigPackageRejectionRecorder rejectionRecorder;

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
            // 证据先落（独立事务），再拒绝：否则"有人传了份畸形 Manifest"这件事
            // 只活在调用方那条 oper_log 里，事后看不到对方到底交了什么
            rejectionRecorder.record(null, null, bodyName, body, DigestUtil.sha256Hex(body),
                AigPackageRejectionRecorder.REASON_MANIFEST_INVALID,
                String.join(",", scan.hitRuleCodes()), scan.getDetail(), operatorId);
            throw new ServiceException("Manifest 无法解析为 JSON 对象，未登记任何东西："
                + scan.getDetail());
        }

        // 包体哈希由服务端算，并与 Manifest 声明比对——不一致就不登记（两者不是一对）
        String bodyHash = DigestUtil.sha256Hex(body);
        String declared = StringUtils.trim(manifest.checksum());
        if (!bodyHash.equalsIgnoreCase(declared)) {
            rejectionRecorder.record(manifest.packageCode(), manifest.version(), bodyName, body,
                bodyHash, AigPackageRejectionRecorder.REASON_CHECKSUM_MISMATCH, null,
                "Manifest 声明 " + declared + "，平台对上传字节算出 " + bodyHash, operatorId);
            throw new ServiceException("包体校验和不一致，未登记任何东西：Manifest 声明 "
                + declared + "，平台对上传字节算出 " + bodyHash
                + "。上传说的是「这份包体就是 Manifest 描述的那一份」，不一致就不落库");
        }

        // 包体内容检查（F-02）：声明与哈希都对，不代表"包里没有不该有的东西"。
        // 不通过就整笔拒绝，但**证据先落**（独立事务，见 AigPackageRejectionRecorder）
        AigArchiveScanResult archive = archiveScanner.scan(body);
        if (!archive.isPass()) {
            rejectionRecorder.record(manifest.packageCode(), manifest.version(), bodyName, body,
                bodyHash, AigPackageRejectionRecorder.REASON_ARCHIVE_UNSAFE,
                String.join(",", archive.hitRuleCodes()), archive.getDetail(), operatorId);
            throw new ServiceException("包体安全检查未通过，未登记任何东西：命中 "
                + archive.hitRuleCodes() + "；" + archive.getDetail());
        }
        if (!archive.isScanned()) {
            // 没扫 ≠ 通过：留痕，便于回答"这个包当初到底查过没有"
            log.info("包体未做内容检查, packageCode={}, 原因={}", manifest.packageCode(), archive.getDetail());
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
                + "（" + body.length + " 字节）；包体是否留存见版本 body_ref，校验和见 checksum");
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

        // 包体留存（可选，默认关）。
        // 刻意放在**写库之前**：留存失败就整笔失败、不落任何行——否则会出现
        // 「版本登记了、body_ref 也写了、对象存储里其实没有」这种最难查的半截状态。
        // 对象键由包编码/版本/哈希算出，不依赖入库后才有的ID，因此可以先放对象再写库。
        String bodyRef = null;
        if (packageProperties.isStoreBody()) {
            bodyRef = bodyStore.buildKey(manifest.packageCode(), manifest.version(), bodyHash);
            bodyStore.put(bodyRef, body);
        }

        AigPackageVersion version = new AigPackageVersion();
        version.setPackageId(pkg.getPackageId());
        version.setVersion(manifest.version());
        version.setManifestJson(rawManifest);
        version.setManifestHash(AigPackageManifestValidator.manifestHash(rawManifest));
        version.setBodyRef(bodyRef);
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
        log.info("Package 上传登记完成, packageCode={}, version={}, scan={}, bodySha256={}, bodyStored={}",
            manifest.packageCode(), manifest.version(), scan.scanResult(), bodyHash, bodyRef != null);
        return new AigPackageRegisterVo(pkg.getPackageId(), version.getPackageVersionId(),
            manifest.packageCode(), manifest.version(), bodyHash, version.getManifestHash(),
            bodyRef != null, bodyRef, scan.isPass(), scan.scanResult(), scan.getDetail(), manifest);
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
    @Transactional(rollbackFor = Exception.class)
    public AigPackageStatusVo disablePackage(Long packageId, Long operatorId) {
        return changePackageStatus(packageId, PACKAGE_DISABLED, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPackageStatusVo enablePackage(Long packageId, Long operatorId) {
        return changePackageStatus(packageId, STATUS_NORMAL, operatorId);
    }

    /**
     * 包级状态变更（停用 / 启用）。
     *
     * <p>只改 {@code aig_package.status}：它管的是「这个包还能不能被使用」。
     * <b>不顺手改任何版本状态</b>——把已装出去的版本下线是另一件事（影响面大得多），
     * 必须由人显式走 {@link #disable}。这两件事在接口语义上分开，是为了挡住「误以为停了」。</p>
     *
     * @param packageId  Package ID
     * @param target     目标状态（{@link #STATUS_NORMAL} / {@link #PACKAGE_DISABLED}）
     * @param operatorId 操作人
     * @return 变更结果
     */
    private AigPackageStatusVo changePackageStatus(Long packageId, String target, Long operatorId) {
        if (packageId == null) {
            throw new ServiceException("Package ID不能为空");
        }
        AigPackage pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("Package 不存在：" + packageId);
        }
        boolean disabled = !STATUS_NORMAL.equals(target);
        if (target.equals(pkg.getStatus())) {
            // 幂等：已是目标状态，不改库也不报错（但要说清楚「没改动」）
            return new AigPackageStatusVo(packageId, pkg.getPackageCode(), disabled, false,
                "该 Package 已是" + (disabled ? "停用" : "正常") + "状态，本次未做任何改动");
        }
        AigPackage update = new AigPackage();
        update.setPackageId(packageId);
        update.setStatus(target);
        // update_by / update_time 由 MyBatis-Plus 自动填充；包级状态属主数据，
        // 审计走这两列，不进 aig_package_install_log（那张表是**安装链路**的账本，逐 Package 版本）
        packageMapper.updateById(update);
        log.info("Package 包级状态变更, packageId={}, packageCode={}, {}→{}, operatorId={}",
            packageId, pkg.getPackageCode(), pkg.getStatus(), target, operatorId);
        return new AigPackageStatusVo(packageId, pkg.getPackageCode(), disabled, true, disabled
            ? "已停用该 Package：不再接受新版本上传，也不能安装它的版本。"
                + "包带进来的**既有版本不受影响**——要下线正在使用中的版本，请对该版本执行「停用」"
            : "已启用该 Package：可以继续上传新版本与安装。"
                + "此前被停用的版本不会因此恢复（版本重新启用走发布推进）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPackageDisableVo disable(Long packageVersionId, Long operatorId) {
        if (packageVersionId == null) {
            throw new ServiceException("Package 版本ID不能为空");
        }
        AigPackageVersion pkgVersion = packageVersionMapper.selectById(packageVersionId);
        if (pkgVersion == null) {
            throw new ServiceException("Package 版本不存在：" + packageVersionId);
        }
        AigPackage pkg = packageMapper.selectById(pkgVersion.getPackageId());
        if (pkg == null) {
            throw new ServiceException("Package 主记录不存在：" + pkgVersion.getPackageId());
        }

        // 停用对象 = **回指该 Package 版本**的版本行（与安装的幂等判据同源）。
        // 用 package_version_id 精确定位，绝不按 agent_code 连坐：同一个 Agent 的其它版本
        // 可能来自别的包或是平台内置，而且很可能正在被业务使用。
        List<AigAgentVersion> agents = agentVersionMapper.selectList(
            new LambdaQueryWrapper<AigAgentVersion>()
                .eq(AigAgentVersion::getPackageVersionId, packageVersionId));
        List<AigSkillVersion> skills = skillVersionMapper.selectList(
            new LambdaQueryWrapper<AigSkillVersion>()
                .eq(AigSkillVersion::getPackageVersionId, packageVersionId));
        if (agents.isEmpty() && skills.isEmpty()) {
            throw new ServiceException("该 Package 版本没有安装出任何版本，没有可停用的对象：Package 版本 #"
                + packageVersionId + "。停用是「把装出来的东西下线」，请先安装；"
                + "若目的是「这个包以后不要再装新版本」，那是包级停用，不是本接口");
        }

        List<AigPackageDisableVo.DisabledItem> disabled = new ArrayList<>();
        List<AigPackageDisableVo.SkippedItem> skipped = new ArrayList<>();
        for (AigAgentVersion item : agents) {
            AigAgent agent = agentMapper.selectById(item.getAgentId());
            disableOne(AigReleaseTargetTypeEnum.AGENT_VERSION,
                agent == null ? null : agent.getAgentCode(), item.getAgentId(),
                item.getAgentVersionId(), item.getVersion(), item.getReleaseStatus(),
                packageVersionId, operatorId, disabled, skipped);
        }
        for (AigSkillVersion item : skills) {
            AigSkill skill = skillMapper.selectById(item.getSkillId());
            disableOne(AigReleaseTargetTypeEnum.SKILL_VERSION,
                skill == null ? null : skill.getSkillCode(), item.getSkillId(),
                item.getSkillVersionId(), item.getVersion(), item.getReleaseStatus(),
                packageVersionId, operatorId, disabled, skipped);
        }

        long already = 0L;
        for (AigPackageDisableVo.SkippedItem item : skipped) {
            if (AigReleaseStatusEnum.DISABLED.getCode().equals(item.fromStatus())) {
                already++;
            }
        }
        if (disabled.isEmpty()) {
            if (already > 0L) {
                // 幂等命中：不改任何东西，也不写账本（账本记的是发生过的动作，不是重复的意图）
                log.info("Package 停用幂等命中, packageVersionId={}, 带进来的版本均已在停用状态",
                    packageVersionId);
                return new AigPackageDisableVo(pkg.getPackageId(), packageVersionId, true,
                    disabled, skipped, "该 Package 版本带进来的 " + (agents.size() + skills.size())
                        + " 个版本都已在停用状态，本次未做任何改动（幂等）");
            }
            throw new ServiceException("该 Package 版本带进来的版本一条也停不了："
                + skipReasons(skipped) + "（归档是终态，要改请出新版本）");
        }

        writeLog(packageVersionId, AigPackageInstallActionEnum.DISABLE, "PASS",
            "停用 " + disabled.size() + " 个版本（跳过 " + skipped.size() + " 个）："
                + disabledSummary(disabled) + skippedSummary(skipped), operatorId);
        log.info("Package 停用完成, packageVersionId={}, disabled={}, skipped={}", packageVersionId,
            disabled.size(), skipped.size());
        return new AigPackageDisableVo(pkg.getPackageId(), packageVersionId, false, disabled, skipped,
            "已停用 " + disabled.size() + " 个版本。停用只改发布状态、不动版本内容；"
                + "重新启用走发布推进（DISABLED → STABLE 需证明该版本曾 STABLE 过）");
    }

    /**
     * 停用一个版本，或如实记下为什么没停。
     *
     * <p>状态迁移<b>不在这里直接写库</b>：交给 {@link IAigAgentRegistryService#advanceRelease}，
     * 由它做状态机边判定、CAS 更新与发布事件留痕。本方法只负责判定「能不能停」并翻译成可读结果。</p>
     *
     * @param type            对象类型
     * @param code            编码（可能取不到——父记录缺失时如实留 null，不编造）
     * @param parentId        父记录ID
     * @param versionId       版本ID
     * @param version         版本号
     * @param rawStatus       库中发布状态（原始值）
     * @param packageVersionId Package 版本ID（写进发布事件说明）
     * @param operatorId      操作人
     * @param disabled        收集器：真正被停用的
     * @param skipped         收集器：未改动的
     */
    private void disableOne(AigReleaseTargetTypeEnum type, String code, Long parentId, Long versionId,
                            String version, String rawStatus, Long packageVersionId, Long operatorId,
                            List<AigPackageDisableVo.DisabledItem> disabled,
                            List<AigPackageDisableVo.SkippedItem> skipped) {
        AigReleaseStatusEnum from = AigReleaseStatusEnum.find(rawStatus);
        if (from == AigReleaseStatusEnum.DISABLED) {
            skipped.add(new AigPackageDisableVo.SkippedItem(type.getCode(), code, parentId, versionId,
                version, rawStatus, "已在停用状态"));
            return;
        }
        if (from == null) {
            // 不猜、不顺手改成 DISABLED：状态读不出来时改动可能掩盖真正的数据问题
            skipped.add(new AigPackageDisableVo.SkippedItem(type.getCode(), code, parentId, versionId,
                version, rawStatus, "库中发布状态为空或非法（" + rawStatus + "），需人工确认，未改动"));
            return;
        }
        if (!AigReleaseStateMachine.canTransition(from, AigReleaseStatusEnum.DISABLED)) {
            skipped.add(new AigPackageDisableVo.SkippedItem(type.getCode(), code, parentId, versionId,
                version, rawStatus, "当前状态 " + from.getCode() + " 不能停用——"
                    + AigReleaseStateMachine.describeAllowed(from)));
            return;
        }

        AigReleaseAdvanceBo bo = new AigReleaseAdvanceBo();
        bo.setTargetType(type.getCode());
        bo.setTargetVersionId(versionId);
        bo.setExpectedStatus(from.getCode());
        bo.setToStatus(AigReleaseStatusEnum.DISABLED.getCode());
        bo.setOperatorId(operatorId);
        bo.setDetail("停用 Package 版本 #" + packageVersionId + " 带入的 " + type.getDesc()
            + " " + (code == null ? String.valueOf(versionId) : code) + "（停用前 " + from.getCode() + "）");
        registryService.advanceRelease(bo);
        disabled.add(new AigPackageDisableVo.DisabledItem(type.getCode(), code, parentId, versionId,
            version, from.getCode()));
    }

    /**
     * 被停用版本的摘要（写明停用前状态：STABLE 下线与 DRAFT 下线的影响面不是一回事）。
     *
     * @param items 停用项
     * @return 摘要文本
     */
    private static String disabledSummary(List<AigPackageDisableVo.DisabledItem> items) {
        StringBuilder sb = new StringBuilder();
        for (AigPackageDisableVo.DisabledItem item : items) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(item.code()).append('(').append(item.fromStatus()).append("→DISABLED)");
        }
        return sb.toString();
    }

    /**
     * 跳过项摘要。
     *
     * @param items 跳过项
     * @return 摘要文本（无跳过时为空串）
     */
    private static String skippedSummary(List<AigPackageDisableVo.SkippedItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("；跳过：");
        boolean first = true;
        for (AigPackageDisableVo.SkippedItem item : items) {
            if (!first) {
                sb.append('、');
            }
            sb.append(item.code()).append('(').append(item.reason()).append('）');
            first = false;
        }
        return sb.toString();
    }

    /**
     * 跳过原因汇总（全部跳不过去时报错用，必须能看出是为什么）。
     *
     * @param items 跳过项
     * @return 原因文本
     */
    private static String skipReasons(List<AigPackageDisableVo.SkippedItem> items) {
        StringBuilder sb = new StringBuilder();
        for (AigPackageDisableVo.SkippedItem item : items) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(item.code()).append("：").append(item.reason());
        }
        return sb.toString();
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
