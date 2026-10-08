package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.enums.AigReleaseChannelEnum;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigCanaryEvidence;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;
import org.dromara.aigov.agent.manifest.AigManifestScanResult;
import org.dromara.aigov.agent.manifest.AigPackageIdentity;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.aigov.agent.service.IAigCanaryEvidenceService;
import org.dromara.aigov.agent.service.IAigEvaluationService;
import org.dromara.aigov.agent.state.AigReleaseStateMachine;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent/Skill/Package 注册与发布服务实现（设计 §5.4、§6.3）。
 *
 * <p><b>发布推进的写库方式：按当前状态做条件更新</b>——
 * {@code update ... set release_status=? where id=? and release_status=?}。
 * 这比通用乐观锁更精确：它同时表达「必须是那个状态」与「只能推进到下一个状态」；
 * 影响 0 行即说明有并发方先改了，此时报错而不是重试覆盖——两条各自合法的迁移
 * （如「灰度达标」与「停用」）合在一起会互相矛盾。</p>
 *
 * <p><b>调用方必须先声明所见状态</b>（{@code expectedStatus}）：审批页面停留十分钟后
 * 别人已经推进一步，此时点「批准」应当收到「你的视图已过期」这种可读提示，
 * 而不是一条「更新影响 0 行」的并发异常。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigAgentRegistryServiceImpl implements IAigAgentRegistryService {

    /**
     * 启用（Y）
     */
    private static final String YES = "Y";

    /**
     * 未启用（N）
     */
    private static final String NO = "N";

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigSkillVersionMapper skillVersionMapper;

    private final AigPackageVersionMapper packageVersionMapper;

    private final AigReleaseEventMapper releaseEventMapper;

    private final AigAgentBindingMapper bindingMapper;

    private final AigPackageMapper packageMapper;

    private final AigPackageManifestValidator manifestValidator;

    private final IAigEvaluationService evaluationService;

    private final IAigCanaryEvidenceService canaryEvidenceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigReleaseStatusEnum advanceRelease(AigReleaseAdvanceBo bo) {
        if (bo == null) {
            throw new ServiceException("发布推进入参不能为空");
        }
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(bo.getTargetType());
        if (type == null) {
            throw new ServiceException("未知的对象类型：" + bo.getTargetType()
                + "；可选：" + describeTargetTypes());
        }
        if (bo.getTargetVersionId() == null) {
            throw new ServiceException("对象版本ID不能为空");
        }
        AigReleaseStatusEnum to = AigReleaseStatusEnum.find(bo.getToStatus());
        if (to == null) {
            throw new ServiceException("未知的目标状态：" + bo.getToStatus());
        }
        AigReleaseStatusEnum expected = AigReleaseStatusEnum.find(bo.getExpectedStatus());
        if (expected == null) {
            throw new ServiceException("未知的期望状态：" + bo.getExpectedStatus());
        }

        ReleaseRow row = loadRow(type, bo.getTargetVersionId());
        AigReleaseStatusEnum from = AigReleaseStatusEnum.find(row.status());
        if (from == null) {
            throw new ServiceException("库中发布状态非法：" + row.status() + "（对象 " + type.getCode()
                + " #" + bo.getTargetVersionId() + "）");
        }
        if (from != expected) {
            // 先比对这个，而不是直接写库：能给出可读原因，也让「视图过期」与「并发冲突」区分开
            throw new ServiceException("你的视图已过期：库中当前状态是 " + from.getCode()
                + "（" + from.getDesc() + "），而本次请求以为它是 " + expected.getCode()
                + "。请刷新后重试（对象 " + type.getCode() + " #" + bo.getTargetVersionId() + "）");
        }

        Set<AigReleaseGateEnum> passed = parseGates(bo.getPassedGates());
        Set<AigReleaseGateEnum> requiredNow = AigReleaseStateMachine.requiredGates(from);
        if (!requiredNow.isEmpty() && isGateDrivenTarget(to)) {
            // 门槛驱动的推进：目标必须正好是「当前状态该去的下一步」
            AigReleaseStatusEnum derived = AigReleaseStateMachine.nextIfAllGatesPassed(from, passed);
            if (derived == null) {
                throw new ServiceException("本次不能推进到 " + to.getCode() + "：" + describeMissing(from, passed));
            }
            if (derived != to) {
                throw new ServiceException("门槛顺序不对：当前状态 " + from.getCode() + " 推进的下一步只能是 "
                    + derived.getCode() + "（" + derived.getDesc() + "），而不是 " + to.getCode()
                    + "；跳步或乱序都会让审核结论失去意义");
            }
        } else if (!AigReleaseStateMachine.canTransition(from, to)) {
            // 运维动作（停用/归档/重新启用）：只按状态机的边判断
            throw new ServiceException("非法的发布状态迁移：当前 " + from.getCode() + "，"
                + AigReleaseStateMachine.describeAllowed(from) + "，本次请求 " + to.getCode());
        }

        // 门槛「Manifest 校验」不允许只凭调用方声明：Package 版本上有可查证据（scan_result）。
        // 其余门槛的证据各在别处（人工批准落 approved_by、曾 STABLE 过查发布事件账本），
        // 唯独这一条以前是「调用方说过了就过了」——那正是「不生效也不会报错」的那一类。
        assertManifestScanEvidence(type, row.scanResult(), passed);

        // 同理，「黄金用例通过」也不能只凭声明：证据是评测账本（§13.2）。
        // 判据只实现一次（IAigEvaluationService#goldenCaseEvidence），这里只消费结论。
        assertGoldenCaseEvidence(type, bo.getTargetVersionId(), passed);

        // 「灰度达标」此前是唯一没有证据校验的门槛（CANDIDATE→STABLE）。
        // 证据是逐次调用审计按 Agent 版本统计出来的 a+b+c，判据只实现一次
        // （IAigCanaryEvidenceService#canaryEvidence），这里只消费结论。
        assertCanaryEvidence(type, bo.getTargetVersionId(), passed);

        // 后门：DISABLED → STABLE 必须能证明该版本曾经 STABLE 过（状态机看不到历史，只能在这里兜）
        if (from == AigReleaseStatusEnum.DISABLED && to == AigReleaseStatusEnum.STABLE
            && !wasEverStable(type.getCode(), bo.getTargetVersionId())) {
            throw new ServiceException("不允许把从未发布过的版本直接启用为 STABLE："
                + "「先停用再启用」不能变成一条绕道发布的后门。"
                + "该版本在发布事件账本里没有任何 STABLE 记录（对象 " + type.getCode()
                + " #" + bo.getTargetVersionId() + "）");
        }

        // 受限通道发布必须有启用中的绑定，否则「已发布」与「谁都看不见」会同时成立
        assertBindingExistsIfLimitedChannel(type, bo.getTargetVersionId(), row.channel(), to);

        LocalDateTime now = LocalDateTime.now();
        int rows = casUpdate(type, bo.getTargetVersionId(), from, to, passed, bo.getOperatorId(), now);
        if (rows == 0) {
            // 与前面那次比对之间的窗口内被别人改过：宁可失败，不可覆盖
            throw new ServiceException("发布状态已被并发修改，请刷新后重试：对象 " + type.getCode()
                + " #" + bo.getTargetVersionId() + "，期望状态=" + from.getCode());
        }

        appendEvent(type, bo.getTargetVersionId(), from, to, passed, bo.getOperatorId(), bo.getDetail(), now);
        log.info("发布状态推进, targetType={}, targetVersionId={}, {}→{}, passedGates={}, operatorId={}",
            type.getCode(), bo.getTargetVersionId(), from.getCode(), to.getCode(), passed, bo.getOperatorId());
        return to;
    }

    @Override
    public Set<AigReleaseGateEnum> missingGates(String targetType, Long targetVersionId,
                                                Set<AigReleaseGateEnum> passedGates) {
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(targetType);
        if (type == null || targetVersionId == null) {
            throw new ServiceException("对象类型与版本ID不能为空");
        }
        ReleaseRow row = loadRow(type, targetVersionId);
        return AigReleaseStateMachine.missingGates(AigReleaseStatusEnum.find(row.status()), passedGates);
    }

    @Override
    public boolean wasEverStable(String targetType, Long targetVersionId) {
        if (StringUtils.isBlank(targetType) || targetVersionId == null) {
            return false;
        }
        Long count = releaseEventMapper.selectCount(new LambdaQueryWrapper<AigReleaseEvent>()
            .eq(AigReleaseEvent::getTargetType, targetType.trim())
            .eq(AigReleaseEvent::getTargetVersionId, targetVersionId)
            .eq(AigReleaseEvent::getToStatus, AigReleaseStatusEnum.STABLE.getCode()));
        return count != null && count > 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigManifestScanResult scanStoredManifest(Long packageVersionId) {
        if (packageVersionId == null) {
            throw new ServiceException("Package 版本ID不能为空");
        }
        AigPackageVersion version = packageVersionMapper.selectById(packageVersionId);
        if (version == null) {
            throw new ServiceException("Package 版本不存在：" + packageVersionId);
        }
        if (AigReleaseStatusEnum.find(version.getReleaseStatus()) != AigReleaseStatusEnum.DRAFT) {
            throw new ServiceException("Manifest 扫描只对 DRAFT 版本进行：该版本已处于 "
                + version.getReleaseStatus() + "，库中的扫描结论是 DRAFT 阶段的历史记录，"
                + "重算覆盖会让「当时凭什么放行」查不到（要复核请重新走一次新版本）");
        }
        AigPackage pkg = packageMapper.selectById(version.getPackageId());
        if (pkg == null) {
            throw new ServiceException("Package 主记录不存在：" + version.getPackageId());
        }

        AigPackageIdentity identity = new AigPackageIdentity(pkg.getPackageCode(), pkg.getPublisher(),
            pkg.getLicenseCode(), pkg.getChecksum(), pkg.getPackageType(), version.getVersion(),
            version.getManifestHash());
        AigManifestScanResult result = manifestValidator.scan(version.getManifestJson(), identity);

        // 只写结论与说明，刻意不写 manifest_hash：原文哈希与重算值不一致时结论是拒绝，
        // 顺手把哈希改成新的恰好会抹掉「原文被动过」这个事实。
        int rows = packageVersionMapper.update(null, new LambdaUpdateWrapper<AigPackageVersion>()
            .eq(AigPackageVersion::getPackageVersionId, packageVersionId)
            .eq(AigPackageVersion::getReleaseStatus, AigReleaseStatusEnum.DRAFT.getCode())
            .set(AigPackageVersion::getScanResult, result.scanResult())
            .set(AigPackageVersion::getScanDetail, result.getDetail()));
        if (rows == 0) {
            throw new ServiceException("Package 版本已离开 DRAFT（并发修改）：本次结论未落库，"
                + "请刷新后重试。Package 版本 #" + packageVersionId);
        }
        log.info("Manifest 扫描完成, packageVersionId={}, scanResult={}, hitRules={}",
            packageVersionId, result.scanResult(), result.hitRuleCodes());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addBinding(AigAgentBindingBo bo) {
        if (bo == null || bo.getAgentVersionId() == null) {
            throw new ServiceException("Agent 版本ID不能为空");
        }
        AigAgentVersion version = agentVersionMapper.selectById(bo.getAgentVersionId());
        if (version == null) {
            throw new ServiceException("Agent 版本不存在：" + bo.getAgentVersionId());
        }
        // 通道：留空取版本当前的通道；填了必须一致——「通道说 A、绑定说 B」会让按通道查询自相矛盾。
        // 用 null 安全的 equalsIgnoreCase：版本通道可能为空，直接 equalsIgnoreCase 会 NPE
        //（而那是「刚建的版本还没设通道」这种正常情形，不该以异常收场）
        String channel = StringUtils.isBlank(bo.getReleaseChannel())
            ? version.getReleaseChannel() : bo.getReleaseChannel().trim();
        if (!StringUtils.equalsIgnoreCase(channel, version.getReleaseChannel())) {
            throw new ServiceException("绑定的发布通道（" + channel + "）与该版本当前通道（"
                + version.getReleaseChannel() + "）不一致：按通道查询会得到自相矛盾的结果");
        }

        AigAgentBinding binding = new AigAgentBinding();
        binding.setAgentVersionId(bo.getAgentVersionId());
        binding.setCompanyId(bo.getCompanyId());
        binding.setBrandId(bo.getBrandId());
        binding.setScenarioCode(bo.getScenarioCode());
        binding.setRoleScope(bo.getRoleScope());
        binding.setKnowledgeScope(bo.getKnowledgeScope());
        binding.setReleaseChannel(channel);
        binding.setEnabled(StringUtils.isBlank(bo.getEnabled()) ? YES : bo.getEnabled().trim());
        binding.setEffectiveFrom(bo.getEffectiveFrom());
        binding.setEffectiveTo(bo.getEffectiveTo());
        binding.setDelFlag("0");
        binding.setRemark(bo.getRemark());
        bindingMapper.insert(binding);
        log.info("新增 Agent 版本绑定, bindingId={}, agentVersionId={}, brandId={}, channel={}",
            binding.getBindingId(), bo.getAgentVersionId(), bo.getBrandId(), channel);
        return binding.getBindingId();
    }

    /**
     * 落实「黄金用例」这道门槛的证据要求（设计 §5.4、§13.2）。
     *
     * <p>对三类版本一视同仁：评测对象本来就分 Agent/Skill/Package 三种，而「用没用过评测」
     * 与对象类型无关。声明了这道门槛就必须拿得出评测账本里的结论——否则「黄金用例通过」
     * 这句声明的实际含义只是「调用方这么认为」。</p>
     *
     * @param type   对象类型
     * @param id     对象版本ID
     * @param passed 本次声明的已通过门槛
     */
    private void assertGoldenCaseEvidence(AigReleaseTargetTypeEnum type, Long id,
                                         Set<AigReleaseGateEnum> passed) {
        if (!passed.contains(AigReleaseGateEnum.GOLDEN_CASE)) {
            return;
        }
        AigGoldenCaseEvidence evidence = evaluationService.goldenCaseEvidence(type.getCode(), id);
        if (evidence != null && evidence.satisfied()) {
            return;
        }
        String reason = evidence == null ? "评测证据不可用" : evidence.reason();
        String verdicts = evidence == null ? "" : "逐用例结论：" + evidence.verdictSummary() + "。";
        throw new ServiceException("不允许声明「黄金用例已通过」而库里没有证据：" + reason + "。"
            + verdicts + "请先对版本声明的黄金用例集合跑一遍评测并取得通过"
            + "（对象 " + type.getCode() + " #" + id + "）");
    }

    /**
     * 落实「灰度达标」这道门槛的证据要求（CANDIDATE → STABLE）。
     *
     * <p><b>这是发布链路上最后一个"只凭声明"的门槛</b>：Manifest 校验与黄金用例此前已各有人
     * 兜住，唯独「灰度达标」调用方说过了就过了——而"灰度跑得好不好"是<b>唯一能证明
     * 这个版本在真实流量下没问题</b>的一环，恰恰最不该没有证据。</p>
     *
     * <p>证据是逐次调用审计里<b>归属该 Agent 版本</b>的那些行（判据 a+b+c，见
     * {@link AigCanaryEvidence}）。因此这条校验有一个前提：调用必须真的带上
     * {@code agent_version_id}（任务域已打通）。不经任务的直接调用没有版本归属，
     * 不会计入任何版本——这是刻意的，把它记到随便一个版本头上会让"灰度达标"变成假账。</p>
     *
     * @param type   对象类型
     * @param id     对象版本ID
     * @param passed 本次声明的已通过门槛
     */
    private void assertCanaryEvidence(AigReleaseTargetTypeEnum type, Long id,
                                      Set<AigReleaseGateEnum> passed) {
        if (!passed.contains(AigReleaseGateEnum.CANARY)) {
            return;
        }
        AigCanaryEvidence evidence = canaryEvidenceService.canaryEvidence(type.getCode(), id);
        if (evidence != null && evidence.satisfied()) {
            return;
        }
        String reason = evidence == null ? "灰度证据不可用" : evidence.reason();
        String detail = evidence == null ? "" : "实测：" + evidence.verdictSummary()
            + "；严重错误明细：" + evidence.severeSummary() + "。";
        throw new ServiceException("不允许声明「灰度已达标」而库里没有证据：" + reason + "。"
            + detail + "灰度期内该 Agent 版本的真实调用表现是转正式的唯一依据"
            + "（对象 " + type.getCode() + " #" + id + "）");
    }

    /**
     * 落实「Manifest 校验」这道门槛的证据要求。
     *
     * <p>只有 Package 版本有这条证据：{@code scan_result} 只存在于 {@code aig_package_version}
     * （Agent / Skill 版本不带 Manifest，它们的内容物是平台内的配置记录）。</p>
     *
     * @param type       对象类型
     * @param scanResult 版本行上的扫描结论（Agent/Skill 版本为 null）
     * @param passed     本次声明的已通过门槛
     */
    private void assertManifestScanEvidence(AigReleaseTargetTypeEnum type, String scanResult,
                                           Set<AigReleaseGateEnum> passed) {
        if (type != AigReleaseTargetTypeEnum.PACKAGE_VERSION
            || !passed.contains(AigReleaseGateEnum.MANIFEST_VALIDATION)) {
            return;
        }
        if (AigManifestScanResult.PASS.equals(scanResult)) {
            return;
        }
        throw new ServiceException("不允许声明「Manifest 校验已通过」而库里没有证据："
            + "该 Package 版本的 scan_result=" + (StringUtils.isBlank(scanResult) ? "未扫描" : scanResult)
            + "。请先执行 Manifest 扫描（scanStoredManifest）拿到 PASS 再推进"
            + "（§6.2 的拒绝规则必须真的被执行，而不是被声明）");
    }

    /**
     * 该状态是否属于「门槛驱动的前进」目标。
     *
     * @param to 目标状态
     * @return 是则 true
     */
    private boolean isGateDrivenTarget(AigReleaseStatusEnum to) {
        return to == AigReleaseStatusEnum.VALIDATED
            || to == AigReleaseStatusEnum.SANDBOX_TESTED
            || to == AigReleaseStatusEnum.CANDIDATE
            || to == AigReleaseStatusEnum.STABLE;
    }

    /**
     * 受限通道发布到 CANDIDATE/STABLE 时必须有启用中的绑定。
     *
     * <p>只对 <b>Agent 版本</b> 校验：按设计 §10.2，绑定表是 {@code ai_agent_binding}（按 Agent
     * 版本组织）；Skill/Package 版本的可见性由「使用它们的 Agent」决定，本身没有独立可见范围，
     * 因此不对它们套用这条规则——套用只会逼出一堆无意义的绑定行。</p>
     *
     * @param type   对象类型
     * @param id     对象版本ID
     * @param rawChannel 版本当前通道（原始值）
     * @param to     目标状态
     */
    private void assertBindingExistsIfLimitedChannel(AigReleaseTargetTypeEnum type, Long id,
                                                     String rawChannel, AigReleaseStatusEnum to) {
        if (to != AigReleaseStatusEnum.CANDIDATE && to != AigReleaseStatusEnum.STABLE) {
            return;
        }
        AigReleaseChannelEnum channel = AigReleaseChannelEnum.find(rawChannel);
        if (channel == null || !channel.isLimited() || type != AigReleaseTargetTypeEnum.AGENT_VERSION) {
            return;
        }
        Long bindings = bindingMapper.selectCount(new LambdaQueryWrapper<AigAgentBinding>()
            .eq(AigAgentBinding::getAgentVersionId, id)
            .eq(AigAgentBinding::getEnabled, YES));
        if (bindings == null || bindings == 0L) {
            throw new ServiceException("通道 " + channel.getCode() + "（" + channel.getDesc()
                + "）是受限通道，但该版本当前没有任何启用中的绑定：发布出去谁都看不见，等同于没发布。"
                + "请先为该版本添加绑定（品牌/部门/测试项目）再发布");
        }
    }

    /**
     * 按当前状态做条件更新（三类对象各自构型）。
     *
     * @param type       对象类型
     * @param id         对象版本ID
     * @param from       期望的当前状态
     * @param to         目标状态
     * @param passed     本次依据的门槛
     * @param operatorId 操作人
     * @param now        操作时间
     * @return 影响行数（0 = 并发冲突）
     */
    private int casUpdate(AigReleaseTargetTypeEnum type, Long id, AigReleaseStatusEnum from,
                          AigReleaseStatusEnum to, Set<AigReleaseGateEnum> passed,
                          Long operatorId, LocalDateTime now) {
        boolean approved = passed.contains(AigReleaseGateEnum.HUMAN_APPROVAL);
        switch (type) {
            case AGENT_VERSION: {
                LambdaUpdateWrapper<AigAgentVersion> w = new LambdaUpdateWrapper<>();
                w.eq(AigAgentVersion::getAgentVersionId, id)
                    .eq(AigAgentVersion::getReleaseStatus, from.getCode())
                    .set(AigAgentVersion::getReleaseStatus, to.getCode());
                if (to == AigReleaseStatusEnum.VALIDATED) {
                    w.set(AigAgentVersion::getValidatedAt, now);
                }
                if (to == AigReleaseStatusEnum.SANDBOX_TESTED) {
                    w.set(AigAgentVersion::getSandboxTestedAt, now);
                }
                if (approved) {
                    w.set(AigAgentVersion::getApprovedBy, operatorId);
                    w.set(AigAgentVersion::getApprovedAt, now);
                }
                return agentVersionMapper.update(null, w);
            }
            case SKILL_VERSION: {
                LambdaUpdateWrapper<AigSkillVersion> w = new LambdaUpdateWrapper<>();
                w.eq(AigSkillVersion::getSkillVersionId, id)
                    .eq(AigSkillVersion::getReleaseStatus, from.getCode())
                    .set(AigSkillVersion::getReleaseStatus, to.getCode());
                if (approved) {
                    w.set(AigSkillVersion::getApprovedBy, operatorId);
                    w.set(AigSkillVersion::getApprovedAt, now);
                }
                return skillVersionMapper.update(null, w);
            }
            case PACKAGE_VERSION: {
                LambdaUpdateWrapper<AigPackageVersion> w = new LambdaUpdateWrapper<>();
                w.eq(AigPackageVersion::getPackageVersionId, id)
                    .eq(AigPackageVersion::getReleaseStatus, from.getCode())
                    .set(AigPackageVersion::getReleaseStatus, to.getCode());
                if (approved) {
                    w.set(AigPackageVersion::getApprovedBy, operatorId);
                    w.set(AigPackageVersion::getApprovedAt, now);
                }
                return packageVersionMapper.update(null, w);
            }
            default:
                throw new ServiceException("未支持的对象类型：" + type.getCode());
        }
    }

    /**
     * 追加一条发布事件（账本：审计 + 「曾 STABLE 过」的唯一证据）。
     *
     * @param type       对象类型
     * @param id         对象版本ID
     * @param from       源状态
     * @param to         目标状态
     * @param passed     本次依据的门槛
     * @param operatorId 操作人
     * @param detail     说明
     * @param now        操作时间
     */
    private void appendEvent(AigReleaseTargetTypeEnum type, Long id, AigReleaseStatusEnum from,
                             AigReleaseStatusEnum to, Set<AigReleaseGateEnum> passed,
                             Long operatorId, String detail, LocalDateTime now) {
        AigReleaseEvent event = new AigReleaseEvent();
        event.setTargetType(type.getCode());
        event.setTargetVersionId(id);
        event.setFromStatus(from.getCode());
        event.setToStatus(to.getCode());
        event.setPassedGates(joinGates(passed));
        event.setOperatorId(operatorId);
        event.setDetail(StringUtils.substring(detail, 0, 500));
        event.setOperateTime(now);
        releaseEventMapper.insert(event);
    }

    /**
     * 解析门槛编码集合。
     *
     * <p>未知编码<b>报错而不是忽略</b>：静默忽略会表现为「还差某个门槛」，
     * 而真正的原因是调用方把门槛名字拼错了——那会让人去查流程而不是查拼写。</p>
     *
     * @param codes 门槛编码（可空）
     * @return 门槛集合
     */
    private Set<AigReleaseGateEnum> parseGates(List<String> codes) {
        Set<AigReleaseGateEnum> gates = new LinkedHashSet<>();
        if (codes == null || codes.isEmpty()) {
            return gates;
        }
        for (String code : codes) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            AigReleaseGateEnum gate = AigReleaseGateEnum.find(code);
            if (gate == null) {
                throw new ServiceException("未知的门槛编码：" + code);
            }
            gates.add(gate);
        }
        return gates;
    }

    /**
     * 拼接门槛编码。
     *
     * @param gates 门槛集合
     * @return 逗号分隔文本；空集合返回 null（不使用空串，避免与「有值但为空」混淆）
     */
    private String joinGates(Set<AigReleaseGateEnum> gates) {
        if (gates == null || gates.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (AigReleaseGateEnum gate : gates) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(gate.getCode());
        }
        return sb.toString();
    }

    /**
     * 组装「还差哪些门槛」的可读说明。
     *
     * @param from   当前状态
     * @param passed 已通过门槛
     * @return 说明文本
     */
    private String describeMissing(AigReleaseStatusEnum from, Set<AigReleaseGateEnum> passed) {
        Set<AigReleaseGateEnum> missing = AigReleaseStateMachine.missingGates(from, passed);
        if (missing.isEmpty()) {
            return "当前状态（" + from.getCode() + "）不接受门槛驱动的推进，"
                + AigReleaseStateMachine.describeAllowed(from);
        }
        StringBuilder sb = new StringBuilder("还差以下门槛：");
        boolean first = true;
        for (AigReleaseGateEnum gate : missing) {
            if (!first) {
                sb.append('、');
            }
            sb.append(gate.getDesc()).append('（').append(gate.getCode()).append("，依据 ")
                .append(gate.getBasis()).append('）');
            first = false;
        }
        return sb.toString();
    }

    /**
     * 读取版本行的发布状态与通道。
     *
     * @param type 对象类型
     * @param id   对象版本ID
     * @return 行
     */
    private ReleaseRow loadRow(AigReleaseTargetTypeEnum type, Long id) {
        switch (type) {
            case AGENT_VERSION: {
                AigAgentVersion v = agentVersionMapper.selectById(id);
                if (v == null) {
                    throw new ServiceException("Agent 版本不存在：" + id);
                }
                return new ReleaseRow(id, v.getReleaseStatus(), v.getReleaseChannel(), null);
            }
            case SKILL_VERSION: {
                AigSkillVersion v = skillVersionMapper.selectById(id);
                if (v == null) {
                    throw new ServiceException("Skill 版本不存在：" + id);
                }
                return new ReleaseRow(id, v.getReleaseStatus(), v.getReleaseChannel(), null);
            }
            case PACKAGE_VERSION: {
                AigPackageVersion v = packageVersionMapper.selectById(id);
                if (v == null) {
                    throw new ServiceException("Package 版本不存在：" + id);
                }
                return new ReleaseRow(id, v.getReleaseStatus(), v.getReleaseChannel(), v.getScanResult());
            }
            default:
                throw new ServiceException("未支持的对象类型：" + type.getCode());
        }
    }

    /**
     * 对象类型可选值（报错时列出，避免调用方去翻代码）。
     *
     * @return 可读列表
     */
    private String describeTargetTypes() {
        StringBuilder sb = new StringBuilder();
        for (AigReleaseTargetTypeEnum item : AigReleaseTargetTypeEnum.values()) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(item.getCode());
        }
        return sb.toString();
    }

    /**
     * 版本行的关键列。
     *
     * @param id         对象版本ID
     * @param status     发布状态
     * @param channel    发布通道
     * @param scanResult 扫描结论（仅 Package 版本有，其余为 null）
     */
    private record ReleaseRow(Long id, String status, String channel, String scanResult) {
    }

}
