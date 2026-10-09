package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigSandboxRunRecordBo;
import org.dromara.aigov.agent.evaluation.AigSandboxRunEvidence;

/**
 * 沙箱运行证据服务（发布门槛 {@code SANDBOX_RUN} 的判据来源）。
 *
 * <p>与 {@link IAigCanaryEvidenceService} / {@link IAigEvaluationService#goldenCaseEvidence}
 * 并列：三者都只提供证据，判据各自只实现一次，发布推进时只消费结论。</p>
 *
 * <p>证据本体来自宿主侧 worker 的 {@code result.json}（见
 * {@code script/deploy/sandbox-worker.sh} 与 ADR-015）：平台后端<b>不</b>执行不可信代码，
 * 它只登记、查询、据此判门槛。</p>
 *
 * @author ai-gov
 */
public interface IAigSandboxRunService {

    /**
     * 登记一次沙箱运行（证据入账）。
     *
     * <p>校验（任一不满足即拒绝，不静默接受）：对象类型与版本存在、{@code resultJson}
     * 是合法 JSON 且含执行器必有的字段、原文里的 jobId 与请求一致、同一 {@code jobId}
     * 未被登记过（一个作业只能记一次——否则"跑通过几次"会变成假账）。</p>
     *
     * @param bo         登记请求（只含原文与归属）
     * @param operatorId 登记人ID
     * @return 新记录ID
     */
    Long record(AigSandboxRunRecordBo bo, Long operatorId);

    /**
     * 取某个版本当前的沙箱运行证据结论。
     *
     * <p>任何前提缺失（对象类型/版本ID 为空、从未运行）都返回 {@code satisfied=false}
     * 的结论并说明原因，<b>不抛异常</b>——页面要能渲染"为什么不满足"，抛异常只会变成 500。</p>
     *
     * @param targetType      对象类型（{@code AigReleaseTargetTypeEnum.code}）
     * @param targetVersionId 对象版本ID
     * @return 结论（恒不为 null）
     */
    AigSandboxRunEvidence sandboxRunEvidence(String targetType, Long targetVersionId);

}
