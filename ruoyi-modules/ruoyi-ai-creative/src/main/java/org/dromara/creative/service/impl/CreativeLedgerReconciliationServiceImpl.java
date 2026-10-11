package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpTask;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.CreativeLedgerReconciliationVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.helper.CreativeTaskStatusMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeLedgerReconciliationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 创作域候选侧父子对账实现（增量 19）。
 *
 * <h3>对账的两条边</h3>
 * <ol>
 *     <li><b>父边</b>：项目（{@code cp_task}）的 {@code platform_task_id} → 平台任务。
 *         没有它说明项目不是派发创建的（页面新建），此时父任务为 null、不算缺失。</li>
 *     <li><b>子边</b>：候选（{@code dp_generation}）的 {@code aig_task_id} → 治理任务。
 *         两边状态按 {@link CreativeTaskStatusMapper} 的<b>同一张映射表</b>核对——
 *         这正是回写时用的那张表；对账与回写共用一处口径，才不会"回写这么写、对账那么判"。</li>
 * </ol>
 *
 * <h3>为什么只读</h3>
 * <p>创作域的列表接口会"顺手刷新内核状态"（{@code QUEUED} 的候选还会真的派发）。
 * 对账是观测面，浏览它绝不该推进候选、更不该计费——所以这里只读库。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeLedgerReconciliationServiceImpl implements ICreativeLedgerReconciliationService {

    /**
     * 只读解析派发快照（小、本地、确定性；与模块内其他 helper 同口径用 Jackson 2）。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CpTaskMapper taskMapper;
    private final DpGenerationMapper generationMapper;
    private final IAigTaskService aigTaskService;

    @Override
    public CreativeLedgerReconciliationVo reconcile(Long projectId) {
        if (projectId == null) {
            throw new ServiceException("项目ID不能为空");
        }
        CpTask project = taskMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("项目不存在：" + projectId);
        }

        Long platformTaskId = project.getPlatformTaskId();
        CreativeLedgerReconciliationVo result = new CreativeLedgerReconciliationVo();
        result.setProjectId(projectId);
        result.setPlatformTaskId(platformTaskId);

        boolean parentMissing = false;
        if (platformTaskId != null) {
            AigTask parent = loadTask(platformTaskId);
            if (parent == null) {
                // 项目标记了来源却读不到那条任务：数据缺失，必须显式暴露而不是装作"没有父任务"
                parentMissing = true;
            } else {
                result.setParent(toRow(parent));
            }
        }

        List<DpGeneration> candidates = generationMapper.selectList(
            Wrappers.<DpGeneration>lambdaQuery()
                .eq(DpGeneration::getTaskId, projectId)
                .orderByAsc(DpGeneration::getCandidateNo)
                .orderByAsc(DpGeneration::getId));

        List<CreativeLedgerReconciliationVo.ChildRow> children = new ArrayList<>();
        int consistent = 0;
        int drifted = 0;
        int unregistered = 0;
        for (DpGeneration candidate : candidates) {
            CreativeLedgerReconciliationVo.ChildRow row = reconcileChild(candidate);
            children.add(row);
            if (row.ledgerTaskId() == null) {
                unregistered++;
            } else if (Boolean.TRUE.equals(row.consistent())) {
                consistent++;
            } else if (Boolean.FALSE.equals(row.consistent())) {
                drifted++;
            }
        }
        result.setChildren(children);
        result.setSummary(new CreativeLedgerReconciliationVo.Summary(
            candidates.size(), consistent, drifted, unregistered, parentMissing));
        return result;
    }

    /**
     * 对账一个候选：候选状态 → 应记的任务状态，与登记任务的实际状态比较。
     *
     * @param candidate 候选
     * @return 候选行
     */
    private CreativeLedgerReconciliationVo.ChildRow reconcileChild(DpGeneration candidate) {
        Long generationId = candidate.getId();
        DpGenerationStatusEnum status = DpGenerationStatusEnum.find(candidate.getStatus());
        AigTaskStatusEnum expected = expectedTaskStatus(status);
        String expectedCode = expected == null ? null : expected.getCode();

        if (candidate.getAigTaskId() == null) {
            return new CreativeLedgerReconciliationVo.ChildRow(generationId, candidate.getCandidateNo(),
                candidate.getStatus(), expectedCode, null, null, null, null,
                "候选未登记治理任务（历史候选或登记失败）——治理台看不到它");
        }
        AigTask task = loadTask(candidate.getAigTaskId());
        if (task == null) {
            return new CreativeLedgerReconciliationVo.ChildRow(generationId, candidate.getCandidateNo(),
                candidate.getStatus(), expectedCode, candidate.getAigTaskId(), null, null, Boolean.FALSE,
                "候选登记的治理任务读不到（可能已被清理）：aigTaskId=" + candidate.getAigTaskId());
        }
        if (expectedCode == null) {
            // 两套状态机在这里不映射（候选状态未知）：如实说"判不了"，不猜成一致或漂移
            return new CreativeLedgerReconciliationVo.ChildRow(generationId, candidate.getCandidateNo(),
                candidate.getStatus(), null, task.getTaskId(), task.getTaskNo(), task.getStatus(),
                null, "候选状态无法映射到任务状态，无法对账：" + candidate.getStatus());
        }
        boolean ok = expectedCode.equals(task.getStatus());
        return new CreativeLedgerReconciliationVo.ChildRow(generationId, candidate.getCandidateNo(),
            candidate.getStatus(), expectedCode, task.getTaskId(), task.getTaskNo(), task.getStatus(),
            ok, ok ? null : "候选=" + candidate.getStatus() + "（应记 " + expectedCode
                + "），但任务=" + task.getStatus() + "——回写可能漏了或没跑到");
    }

    /**
     * 候选状态 → 期望的任务状态。
     *
     * <p>内核六态直接用 {@link CreativeTaskStatusMapper#forKernel}；人工结论（{@code APPROVED}/
     * {@code REJECTED}）走两步迁移后<b>落点</b>就是同名状态，故直接对应。</p>
     *
     * @param status 候选状态（可空）
     * @return 期望的任务状态；无映射返回 null
     */
    private static AigTaskStatusEnum expectedTaskStatus(DpGenerationStatusEnum status) {
        if (status == DpGenerationStatusEnum.APPROVED) {
            return AigTaskStatusEnum.APPROVED;
        }
        if (status == DpGenerationStatusEnum.REJECTED) {
            return AigTaskStatusEnum.REJECTED;
        }
        return CreativeTaskStatusMapper.forKernel(status);
    }

    /**
     * 读一条治理任务；读不到只记日志并返回 null（对账不能因为一条任务缺失就整体失败）。
     *
     * @param taskId 任务ID
     * @return 任务；不存在/读取失败返回 null
     */
    private AigTask loadTask(Long taskId) {
        try {
            return aigTaskService.getTask(taskId);
        } catch (Exception e) {
            log.warn("对账读取治理任务失败：aigTaskId={}, 异常={}", taskId, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 任务行 → 视图行。
     *
     * @param task 任务
     * @return 视图行
     */
    private static CreativeLedgerReconciliationVo.TaskRow toRow(AigTask task) {
        return new CreativeLedgerReconciliationVo.TaskRow(task.getTaskId(), task.getTaskNo(),
            task.getTaskType(), task.getStatus(), task.getExecutionMode(), task.getProviderCode(),
            task.getScenarioCode(), task.getCapabilityCode(),
            externalRefOf(task.getRouteSnapshot()), task.getRouteSnapshot(), task.getCreateTime());
    }

    /**
     * 从派发快照里取 externalRef（父任务指向的业务域引用，通常就是项目ID）。
     *
     * @param routeSnapshot 快照原文（可空）
     * @return externalRef；无/解析不出返回 null
     */
    private static String externalRefOf(String routeSnapshot) {
        if (StringUtils.isBlank(routeSnapshot)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(routeSnapshot);
            JsonNode ref = node == null ? null : node.get("externalRef");
            return ref == null || ref.isNull() ? null : ref.asText();
        } catch (Exception e) {
            return null;
        }
    }

}
