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
     * error_class 列长度上限（varchar(32)）。
     * <p>取值是 {@code AigErrorClassEnum} 的编码，最长 {@code OUTPUT_UNPARSABLE}（17 字符），
     * 留一倍余量。截断在这里只是兜底：正常取值远小于列宽。</p>
     */
    private static final int ERROR_CLASS_MAX = 32;

    /**
     * output_ref 列长度上限（varchar(500)）。
     */
    private static final int OUTPUT_REF_MAX = 500;

    /**
     * input_snapshot_ref 列长度上限（varchar(500)）。
     */
    private static final int SNAPSHOT_REF_MAX = 500;

    /**
     * usage_json 列长度上限（varchar(1000)）。
     * <p>给得比 error_summary 宽：用量回执是结构化内容，且是费用对账的唯一依据，
     * 截断掉就不能用于审计。当前组装结果只有几十字节，留足扩展余量。</p>
     */
    private static final int USAGE_JSON_MAX = 1000;

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
            audit.setCallerId(resolveCallerId(ctx));
            audit.setCallerName(StringUtils.isBlank(ctx.getCallerName()) ? LoginHelper.getUsername() : ctx.getCallerName());
            audit.setDataLevel(ctx.getDataLevel());
            audit.setScenarioCode(ctx.getScenarioCode());
            audit.setModelId(ctx.getModelId());
            audit.setProviderId(ctx.getProviderId());
            audit.setModelKey(ctx.getModelKey());
            audit.setModelVersion(ctx.getModelVersion());
            audit.setAgentVersionId(ctx.getAgentVersionId());
            audit.setDeploymentType(ctx.getDeploymentType());
            audit.setExternalCall(ctx.isExternalCall() ? "Y" : "N");
            audit.setPolicyHit(AigInputSanitizer.truncate(joinHits(ctx), POLICY_HIT_MAX));
            // 输入哈希恒定写入；摘要按审计等级裁剪，且永不含个人资料
            audit.setInputHash(AigInputSanitizer.hashInput(ctx.getCapabilityCode(), ctx.getDataLevel(),
                ctx.getPrompt(), ctx.getPayload()));
            audit.setInputSummary(resolveSummary(ctx));
            audit.setInputSnapshotRef(AigInputSanitizer.truncate(ctx.getInputSnapshotRef(), SNAPSHOT_REF_MAX));
            audit.setOutputRef(AigInputSanitizer.truncate(ctx.getOutputRef(), OUTPUT_REF_MAX));
            audit.setResult(StringUtils.isBlank(ctx.getResult())
                ? AigInvokeResultEnum.FAILED.getCode() : ctx.getResult());
            audit.setErrorSummary(AigInputSanitizer.truncate(
                AigInputSanitizer.mask(ctx.getErrorSummary()), ERROR_MAX));
            audit.setErrorClass(AigInputSanitizer.truncate(ctx.getErrorClass(), ERROR_CLASS_MAX));
            audit.setLatencyMs(ctx.getLatencyMs());
            audit.setCost(ctx.getCost());
            audit.setUsageJson(AigInputSanitizer.truncate(ctx.getUsageJson(), USAGE_JSON_MAX));
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
     * 解析调用人用户ID。
     *
     * <p><b>为什么单独抽一个方法</b>：人均配额要按「调用人」计数，而审计行里也要记调用人。
     * 如果两处各写一份解析逻辑，迟早会分叉成「审计里记的是 A、被限额的是 B」——
     * 那时人看到的是「按你的配额超了」，而账本上却是另一个人的调用，谁也说不清。
     * 因此这里作为<b>唯一</b>的解析入口：审计与配额都用它。</p>
     *
     * <p><b>必须异常安全</b>：没有 Sa-Token 上下文时（调度线程执行任务、单元测试）
     * {@code LoginHelper.isLogin()} 会抛 {@code SaTokenContextException}。
     * 审计侧原本就把同类取值包在 try 里（所以那种情况下 caller_id 只是为空），
     * 而人均配额是从调用入口调的——若让异常冒出去，<b>一次调度调用会因为「取不到登录态」而失败</b>。
     * 因此这里一律吞掉并按「无调用人」处理：调用不该因为取不到调用人而失败。</p>
     *
     * <p>上下文里带了调用人就用它（内部调用可显式指定），否则取登录态；
     * <b>没有登录态时返回 null</b>（调度/系统发起）——审计的 caller_id 为空，
     * 人均配额也因此不适用（没有「人」可归属），系统调用该由别的口径管。</p>
     *
     * @param ctx 审计上下文（可空）
     * @return 调用人用户ID；无法确定时 null
     */
    public static Long resolveCallerId(AigAuditContext ctx) {
        if (ctx != null && ctx.getCallerId() != null) {
            return ctx.getCallerId();
        }
        try {
            return LoginHelper.isLogin() ? LoginHelper.getUserId() : null;
        } catch (Exception e) {
            // 无 Sa-Token 上下文（调度线程/单测）：按「无调用人」处理，不把异常带给调用方
            return null;
        }
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
