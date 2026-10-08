package org.dromara.aigov.task.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskExecuteVo;
import org.dromara.aigov.task.domain.vo.AigTaskSnapshotVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务执行实现（任务层 → 统一调用入口）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskExecutorImpl implements IAigTaskExecutor {

    private final IAigTaskService taskService;
    private final IAigInvokeService invokeService;
    private final JsonMapper jsonMapper;

    @Override
    public AigTaskExecuteVo execute(AigTaskExecuteBo bo) {
        if (bo == null || bo.getTaskId() == null) {
            throw new ServiceException("任务ID不能为空");
        }
        AigTaskDetailVo detail = taskService.getDetail(bo.getTaskId());
        AigTaskVo task = detail.getTask();
        AigTaskSnapshotVo snapshot = detail.getSnapshot();
        if (snapshot == null) {
            throw new ServiceException("任务没有输入快照，拒绝执行：taskId=" + bo.getTaskId());
        }
        // 快照不可变性的校验：哈希不等于冻结值时说明快照被改过，
        // 此时执行出来的结果无法归因，宁可停下来说清楚
        assertSnapshotUntouched(snapshot, bo.getTaskId());

        AigTaskStatusEnum current = AigTaskStatusEnum.find(task.getStatus());
        if (current != AigTaskStatusEnum.QUEUED) {
            throw new ServiceException("只有「已入队」的任务可以执行，当前状态="
                + task.getStatus() + "：taskId=" + bo.getTaskId()
                + "（若在等待重试，请等调度器重新入队；若已失败待人工，请先人工重新入队）");
        }

        // 1) 入队 → 执行中。同步 Provider 没有「派发」这一可观测中间态，故直接进 RUNNING。
        AigTask running = taskService.transition(bo.getTaskId(), task.getVersion(),
            AigTaskStatusEnum.RUNNING, "开始执行（走统一调用入口）", null);

        // 2) 组装调用入参：能力、数据等级、场景都取自任务本身——
        //    这样「任务是怎么登记的」与「实际怎么调用的」不可能不一致
        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(task.getCapabilityCode());
        invokeBo.setDataLevel(task.getDataLevel());
        invokeBo.setScenarioCode(task.getScenarioCode());
        // 任务知道自己挂在哪个 Agent 版本上，必须带进调用入参，否则审计里就没有这个维度，
        // 灰度的「按版本统计调用次数/失败率」永远拿不到任务发起的那些调用
        invokeBo.setAgentVersionId(task.getAgentVersionId());
        invokeBo.setPrompt(bo.getPrompt());
        invokeBo.setPayload(bo.getPayload());
        invokeBo.setInputSnapshotRef("task:" + bo.getTaskId() + "/snapshot:" + snapshot.getSnapshotId());
        invokeBo.setMaxCost(bo.getMaxCost() != null ? bo.getMaxCost() : snapshot.getBudgetAmount());

        if (StringUtils.isBlank(invokeBo.getCapabilityCode())) {
            throw new ServiceException("任务未登记能力编码，无法路由：taskId=" + bo.getTaskId());
        }

        // 3) 统一调用入口：路由 / 有序 fallback / 退避重试 / 逐次审计都在里面
        AigInvokeVo invoked = invokeService.invoke(invokeBo);

        // 4) 记录执行事实（路由快照 / traceId / 是否外发 / 耗时）。
        //    路由快照是执行与排障的唯一依据：治理配置会变，只有当时那一刻的结论能解释「为什么跑的是它」
        AigTask afterFacts = taskService.recordExecutionFacts(bo.getTaskId(), running.getVersion(),
            invoked.getTraceId(), buildRouteSnapshot(invoked),
            Boolean.TRUE.equals(invoked.getExternalCall()), invoked.getLatencyMs());

        // 5) 成功判定：**必须有输出且没有错误码**。
        //    只看 errorCode 会漏掉「决策为 MANUAL 但既无输出也无错误码」这类空转；
        //    只看 output 又可能把「恰好回了一段文本的错误响应」当成功。
        boolean success = StringUtils.isNotBlank(invoked.getOutput()) && StringUtils.isBlank(invoked.getErrorCode());

        AigTaskExecuteVo vo = new AigTaskExecuteVo();
        vo.setTaskId(bo.getTaskId());
        vo.setTraceId(invoked.getTraceId());
        vo.setModelKey(invoked.getModelKey());
        vo.setDeploymentType(invoked.getDeploymentType());
        vo.setInvoker(invoked.getInvoker());
        vo.setExternalCall(Boolean.TRUE.equals(invoked.getExternalCall()));
        vo.setLatencyMs(invoked.getLatencyMs());
        vo.setErrorCode(invoked.getErrorCode());

        if (success) {
            AigTask succeeded = taskService.transition(bo.getTaskId(), afterFacts.getVersion(),
                AigTaskStatusEnum.SUCCEEDED, "调用成功，产出待复核（执行成功不等于审核通过）",
                buildRouteSnapshot(invoked));
            vo.setSuccess(true);
            vo.setOutput(invoked.getOutput());
            vo.setStatus(succeeded.getStatus());
            log.info("任务执行成功, taskId={}, traceId={}, modelKey={}, externalCall={}",
                bo.getTaskId(), invoked.getTraceId(), invoked.getModelKey(), vo.isExternalCall());
            return vo;
        }

        // 失败：把调用层已算好的错误分类原样交给任务层（不让它去解析中文文案猜）
        AigErrorClassEnum errorClass = AigErrorClassEnum.find(invoked.getErrorCode());
        String reason = StringUtils.blankToDefault(invoked.getReason(),
            StringUtils.blankToDefault(invoked.getOutput(), "模型调用未产出结果"));
        AigTaskStatusEnum resting = taskService.recordFailure(bo.getTaskId(), afterFacts.getVersion(),
            errorClass, StringUtils.substring(reason, 0, 500));
        vo.setSuccess(false);
        vo.setReason(reason);
        vo.setStatus(resting == null ? null : resting.getCode());
        // 调用层没给出分类时补一个 UNKNOWN，避免返回体里出现「失败了但不知道为什么」的空白
        if (StringUtils.isBlank(vo.getErrorCode())) {
            vo.setErrorCode(AigErrorClassEnum.UNKNOWN.getCode());
        }
        log.warn("任务执行失败, taskId={}, traceId={}, errorCode={}, 落点={}",
            bo.getTaskId(), invoked.getTraceId(), vo.getErrorCode(), vo.getStatus());
        return vo;
    }

    /**
     * 校验快照未被改写：重算哈希并与冻结值比对。
     *
     * @param snapshot 快照视图
     * @param taskId   任务ID（用于报错定位）
     */
    private void assertSnapshotUntouched(AigTaskSnapshotVo snapshot, Long taskId) {
        if (StringUtils.isBlank(snapshot.getSnapshotHash())) {
            throw new ServiceException("快照缺少哈希，无法校验其未被改写，拒绝执行：taskId=" + taskId);
        }
        String actual = DigestUtil.sha256Hex(StringUtils.blankToDefault(snapshot.getSnapshotJson(), ""));
        if (!snapshot.getSnapshotHash().equalsIgnoreCase(actual)) {
            throw new ServiceException("输入快照哈希不匹配（冻结值 " + snapshot.getSnapshotHash()
                + "，实算 " + actual + "）：快照已被改写，执行结果将无法归因，拒绝执行 taskId=" + taskId);
        }
    }

    /**
     * 组装路由快照：执行与排障的唯一依据。
     *
     * @param invoked 调用结果
     * @return JSON 文本
     */
    private String buildRouteSnapshot(AigInvokeVo invoked) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("decision", invoked.getDecision());
        snapshot.put("modelId", invoked.getModelId());
        snapshot.put("modelKey", invoked.getModelKey());
        snapshot.put("deploymentType", invoked.getDeploymentType());
        snapshot.put("invoker", invoked.getInvoker());
        snapshot.put("externalCall", invoked.getExternalCall());
        snapshot.put("traceId", invoked.getTraceId());
        // 策略命中明细一并冻结：只看「用了哪个模型」回答不了「当时为什么是它」
        snapshot.put("policyHits", invoked.getPolicyHits());
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // 快照组装失败不该让一次已经成功的调用变成失败，但要留痕
            log.error("组装路由快照失败, traceId={}", invoked.getTraceId(), e);
            return null;
        }
    }

}
