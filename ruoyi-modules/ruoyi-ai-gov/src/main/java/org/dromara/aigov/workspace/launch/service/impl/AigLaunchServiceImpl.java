package org.dromara.aigov.workspace.launch.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.enums.AigActionLaunchModeEnum;
import org.dromara.aigov.workspace.enums.AigLaunchTargetTypeEnum;
import org.dromara.aigov.workspace.helper.AigScenarioRef;
import org.dromara.aigov.workspace.launch.config.AigLaunchProperties;
import org.dromara.aigov.workspace.launch.domain.AigLaunchRecord;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTicket;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchCommitVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchPrepareVo;
import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.dromara.aigov.workspace.launch.helper.AigLaunchChecklist;
import org.dromara.aigov.workspace.launch.helper.AigLaunchRequestDigest;
import org.dromara.aigov.workspace.launch.helper.IAigLaunchTicketStore;
import org.dromara.aigov.workspace.launch.mapper.AigLaunchRecordMapper;
import org.dromara.aigov.workspace.launch.service.IAigLaunchService;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
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

    private final IAigPortalService portalService;
    private final IAigLaunchTicketStore ticketStore;
    private final AigLaunchRecordMapper recordMapper;
    private final IAigTaskService taskService;
    private final IAigUserQuotaService quotaService;
    private final AigScenarioMapper scenarioMapper;
    private final AigScenarioVersionMapper scenarioVersionMapper;
    private final AigLaunchProperties properties;

    @Override
    public AigLaunchPrepareVo prepare(AigLaunchRequestBo bo, AigPortalActor actor) {
        requireActor(actor);
        if (bo == null) {
            throw new ServiceException("启动请求不能为空");
        }
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
        AigLaunchTargetTypeEnum targetType = AigLaunchTargetTypeEnum.find(context.targetType());
        if (targetType == AigLaunchTargetTypeEnum.SCENARIO) {
            scenarioUsable = scenarioUsable(context.targetRef());
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
            // 项目权：本增量**不引入**项目权判定（跨域项目权在增量 4 随专业台桥接一起接）。
            // 这里显式传 false 并写清楚，而不是留一个"以后再说"的沉默——沉默会让下一个人以为已经判过了。
            false,
            // 运行时健康：健康数据在建模路由解析（aig_route → aig_model_governance.health_status）之后才确定
            // "这张卡片该用哪个模型"。本增量的权威门槛仍在下游（任务执行时的策略与路由点），
            // 所以这里不主动判，留出这个位置让增量 3b/4 接上。
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
     * @param targetRef 场景引用
     * @return 可用返回 true
     */
    private boolean scenarioUsable(String targetRef) {
        AigScenarioRef ref = AigScenarioRef.parse(targetRef);
        if (ref == null) {
            return false;
        }
        AigScenario scenario = scenarioMapper.selectOne(Wrappers.<AigScenario>lambdaQuery()
            .eq(AigScenario::getScenarioCode, ref.code())
            .last("limit 1"));
        if (scenario == null) {
            return false;
        }
        AigScenarioVersion version = scenarioVersionMapper.selectOne(
            Wrappers.<AigScenarioVersion>lambdaQuery()
                .eq(AigScenarioVersion::getScenarioId, scenario.getScenarioId())
                .eq(AigScenarioVersion::getVersion, ref.version())
                .last("limit 1"));
        return version != null && AigLaunchChecklist.scenarioUsable(version.getReleaseStatus());
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
        vo.setProblems(problems);
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
        vo.setProblems(problems);
        vo.setPassed(problems.isEmpty());
        return vo;
    }

    /**
     * 记录 → commit 结果。
     *
     * @param record 记录
     * @return 结果
     */
    private AigLaunchCommitVo toCommitVo(AigLaunchRecord record) {
        AigLaunchCommitVo vo = new AigLaunchCommitVo();
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
