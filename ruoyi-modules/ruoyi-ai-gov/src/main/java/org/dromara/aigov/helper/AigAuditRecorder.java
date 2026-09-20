package org.dromara.aigov.helper;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.enums.AigAuditLevelEnum;
import org.dromara.aigov.enums.AigInvokeResultEnum;
import org.dromara.aigov.enums.AigManualDecisionEnum;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 调用逐次审计记录器。
 * <p>关键设计（验收点）：</p>
 * <ul>
 *     <li><b>独立 Spring Bean</b>：不能写成某个 Service 的私有方法，否则 {@code @Transactional} 失效。</li>
 *     <li>{@code REQUIRES_NEW}：独立于调用方事务，业务回滚不丢审计，审计失败不回滚业务。</li>
 *     <li>异常只 {@code log.error} 不外抛，审计绝不阻断调用（与 {@code TalentAuditRecorder} 同口径）。</li>
 *     <li>{@code input_summary} 只写字段名/长度/计数，<b>绝不写人才个人资料</b>（见 {@link AigInputSanitizer}）。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigAuditRecorder {

    /**
     * policy_hit 列长度上限（aig_invocation_audit.policy_hit varchar(255)）。
     */
    private static final int POLICY_HIT_MAX = 255;

    /**
     * error_summary 列长度上限（varchar(500)）。
     */
    private static final int ERROR_MAX = 500;

    /**
     * output_ref 列长度上限（varchar(500)）。
     */
    private static final int OUTPUT_REF_MAX = 500;

    /**
     * 审计 Mapper。
     */
    private final AigInvocationAuditMapper auditMapper;

    /**
     * 记录一次调用审计。
     * <p>{@code REQUIRES_NEW} 会挂起调用方事务并开启独立事务，因此即使业务随后回滚，
     * 审计记录也已落库；审计自身失败只记日志。</p>
     *
     * @param ctx 审计上下文
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(AigAuditContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            AigInvocationAudit audit = new AigInvocationAudit();
            audit.setTraceId(ctx.getTraceId());
            audit.setCapabilityCode(ctx.getCapabilityCode());
            audit.setCallerId(ctx.getCallerId() == null ? LoginHelper.getUserId() : ctx.getCallerId());
            audit.setCallerName(StringUtils.isBlank(ctx.getCallerName()) ? LoginHelper.getUsername() : ctx.getCallerName());
            audit.setDataLevel(ctx.getDataLevel());
            audit.setModelId(ctx.getModelId());
            audit.setModelKey(ctx.getModelKey());
            audit.setModelVersion(ctx.getModelVersion());
            audit.setDeploymentType(ctx.getDeploymentType());
            audit.setExternalCall(ctx.isExternalCall() ? "Y" : "N");
            audit.setPolicyHit(AigInputSanitizer.truncate(joinHits(ctx), POLICY_HIT_MAX));
            // 输入哈希恒定写入；摘要按审计等级裁剪，且永不含个人资料
            audit.setInputHash(AigInputSanitizer.hashInput(ctx.getCapabilityCode(), ctx.getDataLevel(),
                ctx.getPrompt(), ctx.getPayload()));
            audit.setInputSummary(resolveSummary(ctx));
            audit.setOutputRef(AigInputSanitizer.truncate(ctx.getOutputRef(), OUTPUT_REF_MAX));
            audit.setResult(StringUtils.isBlank(ctx.getResult())
                ? AigInvokeResultEnum.FAILED.getCode() : ctx.getResult());
            audit.setErrorSummary(AigInputSanitizer.truncate(
                AigInputSanitizer.mask(ctx.getErrorSummary()), ERROR_MAX));
            audit.setLatencyMs(ctx.getLatencyMs());
            audit.setCost(ctx.getCost());
            audit.setRetryCount(ctx.getRetryCount() == null ? 0 : ctx.getRetryCount());
            audit.setManualDecision(StringUtils.isBlank(ctx.getManualDecision())
                ? AigManualDecisionEnum.NOT_REQUIRED.getCode() : ctx.getManualDecision());
            audit.setOperateTime(ctx.getOperateTime() == null ? LocalDateTime.now() : ctx.getOperateTime());
            auditMapper.insert(audit);
        } catch (Exception e) {
            // 审计失败绝不中断调用，但必须可见
            log.error("AI调用审计写入失败, traceId={}, capabilityCode={}, result={}",
                ctx.getTraceId(), ctx.getCapabilityCode(), ctx.getResult(), e);
        }
    }

    /**
     * 按审计等级决定是否写输入摘要。
     * <p>{@code HASH_ONLY} 只留哈希；其余等级写「字段名 + 长度」型摘要。</p>
     *
     * @param ctx 审计上下文
     * @return 可入库摘要，不写时返回 null
     */
    private String resolveSummary(AigAuditContext ctx) {
        AigAuditLevelEnum level = AigAuditLevelEnum.find(ctx.getAuditLevel());
        if (level == AigAuditLevelEnum.HASH_ONLY) {
            return null;
        }
        return AigInputSanitizer.buildSummary(ctx.getPrompt(), ctx.getPayload());
    }

    /**
     * 拼接策略命中明细。
     *
     * @param ctx 审计上下文
     * @return 拼接后的文本，无内容返回 null
     */
    private String joinHits(AigAuditContext ctx) {
        if (CollUtil.isEmpty(ctx.getPolicyHits())) {
            return null;
        }
        return String.join(" | ", ctx.getPolicyHits());
    }

}
