package org.dromara.aigov.workspace.launch.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.aigov.workspace.enums.AigActionLaunchModeEnum;
import org.dromara.aigov.workspace.enums.AigLaunchTargetTypeEnum;
import org.dromara.aigov.workspace.helper.AigScenarioDispatch;
import org.dromara.aigov.workspace.helper.AigScenarioRef;
import org.dromara.aigov.workspace.launch.config.AigLaunchProperties;
import org.dromara.aigov.workspace.launch.domain.AigLaunchRecord;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTicket;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchCommitVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchPrepareVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchProblemVo;
import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchRecordVo;
import org.dromara.aigov.workspace.launch.helper.AigLaunchChecklist;
import org.dromara.aigov.workspace.launch.helper.AigLaunchProjectPolicy;
import org.dromara.aigov.workspace.launch.helper.AigLaunchRequestDigest;
import org.dromara.aigov.workspace.launch.helper.IAigLaunchTicketStore;
import org.dromara.aigov.workspace.launch.mapper.AigLaunchRecordMapper;
import org.dromara.aigov.workspace.launch.service.IAigLaunchService;
import org.dromara.aigov.workspace.launch.service.IAigLaunchTemplateService;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.dromara.aigov.workspace.mapper.AigRoleProfileMapper;
import org.dromara.aigov.workspace.portal.domain.AigPortalActionContext;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 启动解析实现（主文档线增量 3）。
 *
 * <h3>不做的事（同样重要）</h3>
 * <ul>
 *     <li><b>不自己判断可见性</b>：找卡片一律通过 {@link IAigPortalService}，
 *         这样"门户里看得到"与"能启动"永远是同一个答案。</li>
 *     <li><b>不信任调用方报的身份</b>：用户/组织一律取自登录态。</li>
 *     <li><b>不把票当成内容</b>：票只带"prepare 时算出来的结论"，commit 时重新校验一次当前状态。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigLaunchServiceImpl implements IAigLaunchService {

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 启动状态：已提交（本增量只写这一个取值；原因见脚本注释）
     */
    private static final String LAUNCH_COMMITTED = "COMMITTED";

    /**
     * 无组织时的哨兵值（**不能**用 NULL，见 setOrgId 处的说明）
     */
    private static final Long NO_ORG_SENTINEL = 0L;

    /**
     * 项目权"判定不可用"时给员工看的话（不是"你没权限"，而是"平台还判断不了"）
     */
    private static final String PROJECT_NOT_ENFORCEABLE_MESSAGE =
        "项目绑定暂不可用：平台还没有接入项目权限校验，因此带项目的启动会被拒绝";

    private final IAigPortalService portalService;
    private final IAigLaunchTicketStore ticketStore;
    private final AigLaunchRecordMapper recordMapper;
    private final IAigTaskService taskService;
    private final IAigUserQuotaService quotaService;
    private final AigScenarioMapper scenarioMapper;
    private final AigScenarioVersionMapper scenarioVersionMapper;
    /** Agent 版本与它所属的 Agent（卡片 targetType=AGENT_VERSION；增量 21／STUDIO-006） */
    private final AigAgentVersionMapper agentVersionMapper;
    private final AigAgentMapper agentMapper;
    private final AigRoleProfileMapper roleProfileMapper;
    private final AigLaunchProjectPolicy projectPolicy;
    private final AigLaunchProperties properties;
    /** 启动任务模板（D11：员工只填业务输入，执行字段由服务端推导） */
    private final IAigLaunchTemplateService launchTemplateService;

    @Override
    public AigLaunchPrepareVo prepare(AigLaunchRequestBo bo, AigPortalActor actor) {
        requireActor(actor);
        if (bo == null) {
            throw new ServiceException("启动请求不能为空");
        }
        // D11：若该岗位/卡片配置了任务模板，用模板推导执行字段（任务类型/业务域/数据等级/快照）。
        // 必须在算摘要之前：prepare 与 commit 看到同一份有效字段，摘要才可能一致。
        launchTemplateService.apply(bo);
        AigPortalActionContext context = null;
        try {
            context = portalService.resolveAction(bo.getRoleCode(), bo.getActionCode(), actor);
        } catch (ServiceException e) {
            // 岗位不可见与卡片不可用对用户是两件事，所以分开判：先问"这个岗位对我开放吗"
            List<String> problems = new ArrayList<>();
            problems.add(roleVisible(bo.getRoleCode(), actor)
                ? AigLaunchErrorEnum.ACTION_NOT_AVAILABLE.getCode()
                : AigLaunchErrorEnum.ROLE_NOT_GRANTED.getCode());
            return prepareVo(null, problems, List.of(), null, null);
        }
        String digest = digest(bo, context);
        AigLaunchRecord existing = findExisting(actor, bo.getIdempotencyKey());
        if (existing != null) {
            if (digest.equals(existing.getRequestDigest())) {
                // 同一次启动已经做过：直接把结果告诉用户，不再发票（再发一张票会让"确认"变成第二次启动）
                AigLaunchPrepareVo vo = prepareVo(context, List.of(), missingContextKeys(context, bo.getContext()), null, null);
                vo.setExistingLaunchId(existing.getLaunchId());
                vo.setExistingTaskId(existing.getTaskId());
                vo.setExistingTaskNo(existing.getTaskNo());
                vo.setPassed(true);
                return vo;
            }
            return prepareVo(context, List.of(AigLaunchErrorEnum.IDEMPOTENCY_CONFLICT.getCode()), missingContextKeys(context, bo.getContext()), null, null);
        }
        List<String> problems = checkNow(bo, context, actor);
        if (!problems.isEmpty()) {
            return prepareVo(context, problems, missingContextKeys(context, bo.getContext()), null, null);
        }
        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.effectiveTicketTtl());
        String ticketId = UUID.randomUUID().toString().replace("-", "");
        AigLaunchTicket ticket = new AigLaunchTicket(ticketId, actor.userId(), actor.deptId(),
            bo.getRoleCode().trim(), context.roleVersionId(), context.actionCode(), context.launchMode(),
            context.targetType(), context.targetRef(), bo.getProjectType(), bo.getProjectId(), digest, expiresAt);
        ticketStore.save(ticket, properties.effectiveTicketTtl());
        return prepareVo(context, List.of(), missingContextKeys(context, bo.getContext()), ticketId, expiresAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigLaunchCommitVo commit(AigLaunchRequestBo bo, AigPortalActor actor) {
        requireActor(actor);
        if (bo == null || StringUtils.isBlank(bo.getTicket())) {
            return commitVo(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()));
        }
        AigLaunchTicket ticket = ticketStore.load(bo.getTicket().trim());
        if (ticket == null) {
            return commitVo(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()));
        }
        // 票是"持有即能用"的凭证，必须绑到人：否则把票转给别人就等于替别人启动
        if (!actor.userId().equals(ticket.userId())) {
            log.warn("启动票据与当前用户不匹配：ticketUser={} actor={}", ticket.userId(), actor.userId());
            return commitVo(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()));
        }
        if (ticket.isExpired(LocalDateTime.now())) {
            ticketStore.consume(ticket.ticketId());
            return commitVo(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()));
        }
        // D11：与 prepare 完全一致地套用模板，再算摘要——否则确认时摘要会变（假冲突）
        launchTemplateService.apply(bo);
        String digest = digest(bo, ticket);
        if (!digest.equals(ticket.requestDigest())) {
            // 确认时改了内容：不能"按新内容默默执行"，也不能"按旧内容执行"（用户看到的不是这个）
            return commitVo(List.of(AigLaunchErrorEnum.IDEMPOTENCY_CONFLICT.getCode()));
        }
        AigLaunchRecord existing = findExisting(actor, bo.getIdempotencyKey());
        if (existing != null && digest.equals(existing.getRequestDigest())) {
            ticketStore.consume(ticket.ticketId());
            AigLaunchCommitVo vo = toCommitVo(existing);
            vo.setReplayed(true);
            vo.setPassed(true);
            vo.setProblems(List.of());
            return vo;
        }
        AigPortalActionContext context;
        try {
            context = portalService.resolveAction(bo.getRoleCode(), bo.getActionCode(), actor);
        } catch (ServiceException e) {
            return commitVo(List.of(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE.getCode()));
        }
        List<String> problems = checkNow(bo, context, actor);
        if (!problems.isEmpty()) {
            return commitVo(problems);
        }
        Long taskId = null;
        String taskNo = null;
        if (requiresTask(context.launchMode())) {
            taskId = taskService.create(toTaskBo(bo, context, actor));
            AigTask task = taskService.getTask(taskId);
            taskNo = task == null ? null : task.getTaskNo();
        }
        AigLaunchRecord record = new AigLaunchRecord();
        // ★无组织时写 0 而不是 NULL：唯一键不约束 NULL，(NULL,user,key) 能插入多条，
        // 会让"没有部门的人"整类失去幂等（实测过）。本仓已有同类先例：aig_task 的 SYSTEM_SUBMITTER_ID
        record.setOrgId(actor.deptId() == null ? NO_ORG_SENTINEL : actor.deptId());
        record.setUserId(actor.userId());
        record.setRoleCode(bo.getRoleCode().trim());
        record.setRoleVersionId(ticket.roleVersionId());
        record.setActionCode(context.actionCode());
        record.setLaunchMode(context.launchMode());
        record.setTargetType(context.targetType());
        record.setTargetRef(context.targetRef());
        record.setRequestDigest(digest);
        record.setIdempotencyKey(bo.getIdempotencyKey().trim());
        record.setProjectType(bo.getProjectType());
        record.setProjectId(bo.getProjectId());
        record.setTaskId(taskId);
        record.setTaskNo(taskNo);
        record.setLaunchStatus(LAUNCH_COMMITTED);
        record.setCommittedAt(LocalDateTime.now());
        record.setStatus(STATUS_NORMAL);
        record.setRemark(bo.getRemark());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求已经用同一个键落库：以库里的那条为准（唯一键是最终裁判）
            AigLaunchRecord winner = findExisting(actor, bo.getIdempotencyKey());
            if (winner == null) {
                throw e;
            }
            ticketStore.consume(ticket.ticketId());
            AigLaunchCommitVo vo = toCommitVo(winner);
            vo.setReplayed(true);
            vo.setPassed(true);
            vo.setProblems(List.of());
            return vo;
        }
        ticketStore.consume(ticket.ticketId());
        AigLaunchCommitVo vo = toCommitVo(record);
        applyScenarioDispatch(vo, context);
        vo.setReplayed(false);
        vo.setPassed(true);
        vo.setProblems(List.of());
        log.info("岗位卡片已启动：launchId={} user={} role={} action={} taskId={}",
            record.getLaunchId(), actor.userId(), record.getRoleCode(), record.getActionCode(), taskId);
        return vo;
    }

    /**
     * 现在的校验链（prepare 与 commit 都用这一份）。
     *
     * @param bo      请求
     * @param context 卡片上下文
     * @param actor   当前用户
     * @return 问题码
     */
    private List<String> checkNow(AigLaunchRequestBo bo, AigPortalActionContext context, AigPortalActor actor) {
        boolean scenarioUsable = true;
        boolean agentVersionUsable = true;
        AigLaunchTargetTypeEnum targetType = AigLaunchTargetTypeEnum.find(context.targetType());
        if (targetType == AigLaunchTargetTypeEnum.SCENARIO) {
            scenarioUsable = scenarioUsable(context.targetRef());
        } else if (targetType == AigLaunchTargetTypeEnum.AGENT_VERSION) {
            agentVersionUsable = agentVersionUsable(context.targetRef());
        }
        boolean quotaExhausted = false;
        if (requiresTask(context.launchMode())) {
            try {
                quotaService.assertWithinQuota(actor.userId());
            } catch (ServiceException e) {
                // 配额超限在既有实现里是 ServiceException；这里把它落成稳定的错误码
                quotaExhausted = true;
                log.info("启动被配额拦截：userId={} 原因={}", actor.userId(), e.getMessage());
            }
        }
        AigLaunchChecklist.Input input = new AigLaunchChecklist.Input(context.launchMode(),
            context.targetType(), context.targetRef(), context.studioRouteKey(), context.requiredContextKeys(),
            bo.getContext(), bo.getTaskType(), bo.getProjectType(), bo.getDataLevel(), bo.getSnapshotJson(),
            scenarioUsable,
            agentVersionUsable,
            // 项目权：由判定口回答。当前实现是 **fail-closed**（判不了就拒绝）——
            // "没校验的放行"会让越权请求看起来一切正常，比拒绝糟得多
            bo.getProjectId() != null && !projectPolicy.allowed(bo.getProjectId(), actor),
            // 运行时健康：健康数据在建模路由解析（aig_route → aig_model_governance.health_status）之后才确定
            // "这张卡片该用哪个模型"。本增量的权威门槛仍在下游（任务执行时的策略与路由点）。
            // 这里刻意**不**把"健康未知"当成不可用：那会让所有启动都失败，而未知并不等于不可用。
            false);
        List<String> problems = new ArrayList<>();
        for (AigLaunchErrorEnum problem : AigLaunchChecklist.check(input)) {
            problems.add(problem.getCode());
        }
        if (quotaExhausted) {
            problems.add(AigLaunchErrorEnum.RESOURCE_EXHAUSTED.getCode());
        }
        return problems;
    }

    /**
     * 场景版本此刻是否可用（查库；判定规则在 {@link AigLaunchChecklist#scenarioUsable}）。
     *
     * <p>可用除了"状态是 STABLE"，还要求 {@code workflow_adapter} 是封闭枚举里的值：
     * 适配器认不出意味着**没人知道这次交给谁跑**，任务建出来也只会卡在那里。与其建一个
     * 起不来的任务，不如在启动前就拒绝（与枚举文档警告的"卡片能点、任务起不来"对齐）。</p>
     *
     * @param targetRef 场景引用
     * @return 可用返回 true
     */
    private boolean scenarioUsable(String targetRef) {
        AigScenarioVersion version = scenarioVersionByRef(targetRef);
        if (version == null || !AigLaunchChecklist.scenarioUsable(version.getReleaseStatus())) {
            return false;
        }
        return AigScenarioDispatch.normalize(version.getWorkflowAdapter()) != null;
    }

    /**
     * 按场景引用读出对应的版本行。
     *
     * @param targetRef 场景引用（{@code scenario://code@version}）
     * @return 版本行；引用不可解析或查不到返回 null
     */
    private AigScenarioVersion scenarioVersionByRef(String targetRef) {
        AigScenarioRef ref = AigScenarioRef.parse(targetRef);
        if (ref == null) {
            return null;
        }
        AigScenario scenario = scenarioMapper.selectOne(Wrappers.<AigScenario>lambdaQuery()
            .eq(AigScenario::getScenarioCode, ref.code())
            .last("limit 1"));
        if (scenario == null) {
            return null;
        }
        return scenarioVersionMapper.selectOne(Wrappers.<AigScenarioVersion>lambdaQuery()
            .eq(AigScenarioVersion::getScenarioId, scenario.getScenarioId())
            .eq(AigScenarioVersion::getVersion, ref.version())
            .last("limit 1"));
    }

    /**
     * 取卡片对应场景版本（非场景卡片返回 null）。
     *
     * @param context 卡片上下文
     * @return 场景版本；不适用返回 null
     */
    private AigScenarioVersion scenarioVersion(AigPortalActionContext context) {
        if (context == null
            || !AigLaunchTargetTypeEnum.SCENARIO.getCode().equals(context.targetType())) {
            return null;
        }
        return scenarioVersionByRef(context.targetRef());
    }

    /**
     * Agent 卡片此刻能否被启动。
     *
     * <p><b>四条同时成立才算可用</b>：版本存在、发布状态是 {@code STABLE}、它所属 Agent 未停用、
     * 且 {@code provider_capability} 非空。最后一条最容易被忽略：任务路由只认能力编码，
     * 没有它建出来的任务会卡在"未登记能力编码"上——与其建一个跑不起来的任务，不如在启动前拒绝。</p>
     *
     * <p>与场景同一口径：岗位包 <b>发布</b> 时只要求"引用存在"，而员工真正点下去时必须就绪。</p>
     *
     * @param targetRef Agent 版本ID（十进制字符串）
     * @return 可用返回 true
     */
    private boolean agentVersionUsable(String targetRef) {
        AigAgentVersion version = agentVersionByRef(targetRef);
        if (version == null
            || !AigReleaseStatusEnum.STABLE.getCode().equalsIgnoreCase(
                trim(version.getReleaseStatus()))) {
            return false;
        }
        if (StringUtils.isBlank(version.getProviderCapability())) {
            return false;
        }
        AigAgent agent = version.getAgentId() == null ? null : agentMapper.selectById(version.getAgentId());
        return agent != null && STATUS_NORMAL.equals(trim(agent.getStatus()));
    }

    /**
     * 按引用读 Agent 版本行。
     *
     * @param targetRef Agent 版本ID（十进制字符串）
     * @return 版本行；引用非法或查不到返回 null
     */
    private AigAgentVersion agentVersionByRef(String targetRef) {
        Long id = positiveLong(targetRef);
        return id == null ? null : agentVersionMapper.selectById(id);
    }

    /**
     * 解析十进制正整数ID。
     *
     * @param value 文本
     * @return 正数；非法返回 null
     */
    private static Long positiveLong(String value) {
        String text = trim(value);
        if (StringUtils.isBlank(text) || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * null 安全 trim（空白视为没有值）。
     *
     * @param value 值
     * @return 去空白后的值；null 输入返回 ""
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 把场景分发信息（交给谁跑 / 结果页）贴到 commit 结果上。
     *
     * @param vo      commit 结果
     * @param context 卡片上下文
     */
    private void applyScenarioDispatch(AigLaunchCommitVo vo, AigPortalActionContext context) {
        AigScenarioVersion scenarioVersion = scenarioVersion(context);
        if (scenarioVersion == null) {
            return;
        }
        vo.setWorkflowAdapter(AigScenarioDispatch.normalize(scenarioVersion.getWorkflowAdapter()));
        vo.setScenarioRouteKey(trimToNull(scenarioVersion.getRouteKey()));
    }

    /**
     * 空白转 null（空字符串与"没有这个值"对界面应当是同一件事）。
     *
     * @param value 值
     * @return 去空白后的值；空为 null
     */
    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 组装任务创建入参。
     *
     * @param bo      请求
     * @param context 卡片上下文
     * @param actor   当前用户
     * @return 任务入参
     */
    private AigTaskCreateBo toTaskBo(AigLaunchRequestBo bo, AigPortalActionContext context, AigPortalActor actor) {
        AigTaskCreateBo taskBo = new AigTaskCreateBo();
        taskBo.setTaskType(bo.getTaskType());
        taskBo.setProjectType(bo.getProjectType());
        taskBo.setProjectId(bo.getProjectId());
        taskBo.setDataLevel(bo.getDataLevel());
        taskBo.setSnapshotJson(bo.getSnapshotJson());
        // 幂等键**原样透传**：任务域自己也按 (业务域, 提交人, 幂等键) 幂等，
        // 两边用同一个键，重复提交在两层都不会建出第二个任务
        taskBo.setIdempotencyKey(bo.getIdempotencyKey());
        if (AigLaunchTargetTypeEnum.SCENARIO.getCode().equals(context.targetType())) {
            AigScenarioRef ref = AigScenarioRef.parse(context.targetRef());
            if (ref != null) {
                taskBo.setScenarioCode(ref.code());
            }
        } else if (AigLaunchTargetTypeEnum.QUICK_CAPABILITY.getCode().equals(context.targetType())) {
            taskBo.setCapabilityCode(context.targetRef());
        } else if (AigLaunchTargetTypeEnum.AGENT_VERSION.getCode().equals(context.targetType())) {
            // Agent 卡片：任务必须把"用哪个 Agent 版本"和"按什么能力路由"都带上——
            // execution_mode 仍由任务域按有无 scenarioCode 决定，这里不越权改。
            AigAgentVersion version = agentVersionByRef(context.targetRef());
            if (version != null) {
                taskBo.setAgentVersionId(version.getAgentVersionId());
                taskBo.setCapabilityCode(version.getProviderCapability());
            }
        }
        taskBo.setRemark("由岗位卡片启动：" + bo.getRoleCode() + "/" + context.actionCode());
        return taskBo;
    }

    /**
     * 计算请求摘要（prepare 用卡片上下文，commit 用票据里那份事实）。
     *
     * @param bo      请求
     * @param context 卡片上下文
     * @return 摘要
     */
    private String digest(AigLaunchRequestBo bo, AigPortalActionContext context) {
        return AigLaunchRequestDigest.of(bo.getRoleCode() == null ? null : bo.getRoleCode().trim(),
            context.roleVersionId(), context.actionCode(), context.targetRef(), bo.getTaskType(),
            bo.getProjectType(), bo.getProjectId(), bo.getDataLevel(), bo.getSnapshotJson(), bo.getContext());
    }

    /**
     * commit 时的摘要（目标/版本以票据为准：用户在确认界面看到的就是它）。
     *
     * @param bo     请求
     * @param ticket 票据
     * @return 摘要
     */
    private String digest(AigLaunchRequestBo bo, AigLaunchTicket ticket) {
        return AigLaunchRequestDigest.of(ticket.roleCode(), ticket.roleVersionId(), ticket.actionCode(),
            ticket.targetRef(), bo.getTaskType(), bo.getProjectType(), bo.getProjectId(),
            bo.getDataLevel(), bo.getSnapshotJson(), bo.getContext());
    }

    /**
     * 该启动方式是否会产生平台任务。
     *
     * @param launchMode 启动方式
     * @return 会建任务返回 true
     */
    private boolean requiresTask(String launchMode) {
        return AigActionLaunchModeEnum.find(launchMode) != AigActionLaunchModeEnum.NAVIGATION;
    }

    /**
     * 岗位对当前用户是否可见（用于区分"岗位没开放"与"卡片不可用"）。
     *
     * @param roleCode 岗位编码
     * @param actor    当前用户
     * @return 可见返回 true
     */
    private boolean roleVisible(String roleCode, AigPortalActor actor) {
        if (StringUtils.isBlank(roleCode)) {
            return false;
        }
        return portalService.listMyRoles(actor).stream()
            .anyMatch(role -> roleCode.trim().equals(role.getRoleCode()));
    }

    /**
     * 按幂等作用域查既有启动记录。
     *
     * @param actor          当前用户
     * @param idempotencyKey 幂等键
     * @return 记录；不存在返回 null
     */
    private AigLaunchRecord findExisting(AigPortalActor actor, String idempotencyKey) {
        if (StringUtils.isBlank(idempotencyKey)) {
            return null;
        }
        return recordMapper.selectOne(Wrappers.<AigLaunchRecord>lambdaQuery()
            .eq(AigLaunchRecord::getUserId, actor.userId())
            .eq(AigLaunchRecord::getIdempotencyKey, idempotencyKey.trim())
            .last("limit 1"));
    }

    /**
     * 组装 prepare 结果。
     *
     * @param context     卡片上下文（可空：拿不到卡片时只有问题码）
     * @param problems    问题码
     * @param missingKeys 卡片要求但本次没给的上下文键
     * @param ticketId    票据ID（通过时才有）
     * @param expiresAt   过期时刻
     * @return 结果
     */
    private AigLaunchPrepareVo prepareVo(AigPortalActionContext context, List<String> problems,
                                         List<String> missingKeys, String ticketId, LocalDateTime expiresAt) {
        AigLaunchPrepareVo vo = new AigLaunchPrepareVo();
        vo.setProblems(problemsOf(problems));
        vo.setPassed(problems.isEmpty());
        vo.setTicketId(ticketId);
        vo.setExpiresAt(expiresAt);
        vo.setMissingContextKeys(missingKeys == null ? List.of() : missingKeys);
        if (context != null) {
            vo.setRoleCode(context.roleCode());
            vo.setRoleVersionId(context.roleVersionId());
            vo.setActionCode(context.actionCode());
            vo.setTitle(context.title());
            vo.setLaunchMode(context.launchMode());
            vo.setTargetType(context.targetType());
            vo.setTargetRef(context.targetRef());
            vo.setStudioRouteKey(context.studioRouteKey());
            AigScenarioVersion scenarioVersion = scenarioVersion(context);
            if (scenarioVersion != null) {
                vo.setWorkflowAdapter(AigScenarioDispatch.normalize(scenarioVersion.getWorkflowAdapter()));
                vo.setScenarioRouteKey(trimToNull(scenarioVersion.getRouteKey()));
            }
            vo.setWillCreateTask(requiresTask(context.launchMode()));
        }
        return vo;
    }

    /**
     * 卡片要求但本次没给的上下文键（无论通过与否都给出来，便于界面提示"还差什么"）。
     *
     * @param context       卡片上下文
     * @param providedValues 本次给出的上下文值
     * @return 缺失的键
     */
    private List<String> missingContextKeys(AigPortalActionContext context, Map<String, String> providedValues) {
        List<String> missing = new ArrayList<>();
        if (context == null || context.requiredContextKeys() == null) {
            return missing;
        }
        Map<String, String> provided = providedValues == null ? Map.of() : providedValues;
        for (String key : context.requiredContextKeys()) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            String value = provided.get(key.trim());
            if (StringUtils.isBlank(value)) {
                missing.add(key.trim());
            }
        }
        return missing;
    }

    /**
     * 组装 commit 结果。
     *
     * @param problems 问题码
     * @return 结果
     */
    private AigLaunchCommitVo commitVo(List<String> problems) {
        AigLaunchCommitVo vo = new AigLaunchCommitVo();
        vo.setProblems(problemsOf(problems));
        vo.setPassed(problems.isEmpty());
        return vo;
    }

    /**
     * 问题码 → 对外问题（码 + 文案）。
     *
     * <p>文案来自 {@code AigLaunchErrorEnum}——它是**唯一**的来源：前端不再写第二份"码 → 中文"表，
     * 否则后端改了措辞，界面还显示老话，而这类不一致没人会报 bug。</p>
     *
     * <p><b>例外只有一处</b>：{@code PROJECT_ACCESS_DENIED} 在这条链路上今天**只可能**
     * 因为"项目权判定还没接入"而被触发，此时枚举里那句"你没有这个项目的访问权限"是**假话**——
     * 员工会以为自己被拒了权限，其实是平台还判断不了。所以判定口自报不可判定时，
     * 换成如实说明（判定口换实现后 {@code enforceable()} 变 true，文案自动恢复）。</p>
     *
     * @param codes    问题码
     * @param overrides 指定码的自定义文案（可空）
     * @return 对外问题（保持码的顺序）
     */
    private static List<AigLaunchProblemVo> toProblemVos(List<String> codes, Map<String, String> overrides) {
        List<AigLaunchProblemVo> problems = new ArrayList<>();
        if (codes == null) {
            return problems;
        }
        Map<String, String> custom = overrides == null ? Map.of() : overrides;
        for (String code : codes) {
            if (custom.containsKey(code)) {
                problems.add(new AigLaunchProblemVo(code, custom.get(code)));
                continue;
            }
            AigLaunchErrorEnum known = AigLaunchErrorEnum.find(code);
            problems.add(known == null
                // 未知码也要能显示（而不是空白），同时保留码本身便于排查
                ? new AigLaunchProblemVo(code, "启动未能完成（" + code + "）")
                : new AigLaunchProblemVo(known.getCode(), known.getMessage()));
        }
        return problems;
    }

    /**
     * 组装对外问题（在标准文案之上叠加"判定不可用"这处例外）。
     *
     * @param codes 问题码
     * @return 对外问题
     */
    private List<AigLaunchProblemVo> problemsOf(List<String> codes) {
        if (projectPolicy.enforceable()) {
            return toProblemVos(codes, null);
        }
        return toProblemVos(codes, Map.of(AigLaunchErrorEnum.PROJECT_ACCESS_DENIED.getCode(),
            PROJECT_NOT_ENFORCEABLE_MESSAGE));
    }

    @Override
    public AigLaunchRecordVo findByTaskId(Long taskId, AigPortalActor actor) {
        requireActor(actor);
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        // ★按登录用户过滤，而不是"先查再判"：不这么做，任何登录用户猜到 taskId
        // 就能看到"别人从哪张卡片启动的"。它不是敏感数据，但没有理由做成可枚举的接口。
        AigLaunchRecord record = recordMapper.selectOne(Wrappers.<AigLaunchRecord>lambdaQuery()
            .eq(AigLaunchRecord::getTaskId, taskId)
            .eq(AigLaunchRecord::getUserId, actor.userId())
            .last("limit 1"));
        if (record == null) {
            throw new ServiceException("找不到这次启动的岗位来源（可能不是由岗位卡片发起的）");
        }
        AigLaunchRecordVo vo = new AigLaunchRecordVo();
        vo.setLaunchId(record.getLaunchId());
        vo.setTaskId(record.getTaskId());
        vo.setTaskNo(record.getTaskNo());
        vo.setRoleCode(record.getRoleCode());
        vo.setActionCode(record.getActionCode());
        vo.setLaunchMode(record.getLaunchMode());
        vo.setTargetType(record.getTargetType());
        vo.setTargetRef(record.getTargetRef());
        vo.setCommittedAt(record.getCommittedAt());
        // 岗位名称：回跳入口上直接显示"返回 <岗位>"，省掉专业页再查一次
        AigRoleProfile profile = roleProfileMapper.selectOne(Wrappers.<AigRoleProfile>lambdaQuery()
            .eq(AigRoleProfile::getRoleCode, record.getRoleCode())
            .last("limit 1"));
        vo.setRoleName(profile == null ? null : profile.getRoleName());
        return vo;
    }

    /**
     * 记录 → commit 结果。
     *
     * @param record 记录
     * @return 结果
     */
    private AigLaunchCommitVo toCommitVo(AigLaunchRecord record) {        AigLaunchCommitVo vo = new AigLaunchCommitVo();
        vo.setLaunchId(record.getLaunchId());
        vo.setTaskId(record.getTaskId());
        vo.setTaskNo(record.getTaskNo());
        vo.setLaunchMode(record.getLaunchMode());
        vo.setTargetType(record.getTargetType());
        vo.setTargetRef(record.getTargetRef());
        vo.setLaunchStatus(record.getLaunchStatus());
        vo.setCommittedAt(record.getCommittedAt());
        return vo;
    }

    /**
     * 门户必须有登录用户。
     *
     * @param actor 用户
     */
    private void requireActor(AigPortalActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
    }

}
