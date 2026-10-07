package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 路由候选（有序 fallback 的一项）。
 *
 * <p><b>为什么需要它</b>：此前 {@link AigRouteDecision} 只带「命中的那一个模型」，
 * 于是调用失败时无路可退——候选里明明还有 GRAY / FALLBACK 模型，主选失败就直接把
 * 失败抛给业务侧。bluocto 那条绑定就是 {@code usage_type=FALLBACK}，
 * 「有备选但从不启用」等于没有备选。</p>
 *
 * <p><b>顺序即优先级</b>：列表由 {@code orderBindings} 排好（PRIMARY → GRAY → FALLBACK，
 * 同级按 priority 升序），调用编排按序尝试、命中即止，不再自行排序——
 * 排序口径只应该有一处。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRouteCandidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID（{@code sai_model_config.id}）
     */
    private Long modelId;

    /**
     * 模型键（发给上游的 model 标识）
     */
    private String modelKey;

    /**
     * 模型类型（{@code sai_model_config.model_type}），调用编排据此派发调用器
     */
    private String modelType;

    /**
     * 部署类型（LOCAL/GROUP/SELF/EXTERNAL_API…）
     */
    private String deploymentType;

    /**
     * 决策时为该候选解析出的调用器名；为 null 表示当时没找到（调用编排会再解析一次，
     * 仍找不到则跳过该候选、顺延下一个，而不是直接失败）
     */
    private String invoker;

    /**
     * 用途（PRIMARY/GRAY/FALLBACK），仅用于决策说明与排障
     */
    private String usageType;

    /**
     * 优先级，数值越小越优先（仅用于决策说明与排障）
     */
    private Integer priority;

    /**
     * 在候选列表中的序号（1 起），用于决策说明与审计
     */
    private Integer order;

}
