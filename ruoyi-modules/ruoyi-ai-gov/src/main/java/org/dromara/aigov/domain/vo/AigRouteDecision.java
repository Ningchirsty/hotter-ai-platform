package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 路由决策结果
 * <p>不抛异常，一律用 {@code decision} 表达结果：
 * {@code MODEL}（命中模型）/ {@code MANUAL}（转人工）/ {@code DENIED}（策略拒绝）。</p>
 * <p>{@code policyHits} 是排障依据：命中的策略、被过滤的模型与<b>过滤原因</b>
 * （例如「策略禁止外发，已排除外部部署模型」）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRouteDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路由结论（MODEL/MANUAL/DENIED）
     */
    private String decision;

    /**
     * 命中的模型ID
     */
    private Long modelId;

    /**
     * 命中的模型键
     */
    private String modelKey;

    /**
     * 命中的部署类型
     */
    private String deploymentType;

    /**
     * 命中的调用器 Bean 名称（无可用调用器时为 null，调用编排据此报错）
     */
    private String invoker;

    /**
     * 决策依据明细（含被过滤原因，便于预览与排障）
     */
    private List<String> policyHits = new ArrayList<>();

    /**
     * 结论说明
     */
    private String reason;

    // ------------------------------------------------------------------
    // 以下为调用编排所需的上下文（避免重复查库）
    // ------------------------------------------------------------------

    /**
     * 命中的能力ID
     */
    private Long capabilityId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 能力审计等级（SUMMARY/FULL/HASH_ONLY）
     */
    private String auditLevel;

    /**
     * 能力输出 Schema（JSON），用于校验模型输出
     */
    private String outputSchema;

    /**
     * 能力要求人工确认的结论点
     */
    private List<String> humanConfirmPoints = new ArrayList<>();

    /**
     * 追加一条决策依据。
     *
     * @param hit 依据文本
     */
    public void addHit(String hit) {
        this.policyHits.add(hit);
    }

}
