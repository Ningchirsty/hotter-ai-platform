package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.evaluation.AigCanaryEvidence;

/**
 * 「灰度是否达标」的证据服务（发布门槛 {@code CANARY} 的判据来源）。
 *
 * <p>与 {@link IAigEvaluationService#goldenCaseEvidence} 并列：两者都是发布门槛的
 * <b>证据提供方</b>，判据各自只实现一次，发布推进时只消费结论
 * （见 {@code AigAgentRegistryServiceImpl#assertCanaryEvidence}）。</p>
 *
 * <p>证据来自逐次调用审计（{@code aig_invocation_audit}）中<b>归属该版本</b>的行
 * （{@code agent_version_id}）+ 发布事件账本里进入 CANDIDATE 的时间。</p>
 *
 * @author ai-gov
 */
public interface IAigCanaryEvidenceService {

    /**
     * 计算某个对象版本的灰度达标证据。
     *
     * <p>任何一项前提缺失（对象类型/版本ID 为空、账本里没有进入 CANDIDATE 的记录）
     * 都返回 {@code satisfied=false} 的证据并说明原因，<b>不抛异常</b>——
     * 这是一个查询接口，页面要能渲染「为什么不达标」；抛异常只会变成 500。</p>
     *
     * @param targetType      对象类型（{@code AigReleaseTargetTypeEnum.code}）
     * @param targetVersionId 对象版本ID
     * @return 证据结论（恒不为 null）
     */
    AigCanaryEvidence canaryEvidence(String targetType, Long targetVersionId);

}
