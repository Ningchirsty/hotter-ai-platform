package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 策略细因编码（{@code reasonCode}）。
 *
 * <p><b>它与 {@link AigErrorClassEnum} 是两件事，刻意不合并</b>：错误码是<b>可编程的粗分类</b>
 * （决定重试/熔断/转人工），细因是<b>给人看的细因</b>——「为什么没成」的具体原因。
 * 契约 {@code contract/error-codes.json} 的 {@code reasonCodes.$comment} 明写：
 * 「不要把细因做成错误码」。合并的后果是把 `NO_ROUTE_POLICY`、`NO_MODEL_BOUND` 这类
 * 一次性的配置问题变成错误分类，于是重试/熔断策略要为它们各想一套处置，
 * 而它们真正需要的是「谁去补配置」。</p>
 *
 * <p><b>词表来源</b>：本枚举是契约 {@code reasonCodes.recommended} 的镜像，
 * 由 {@code AigContractEnumDriftTest} 双向钉住（少一个、多一个都会红）。
 * 理由与事件类型词表一致：细因一旦各处自造近义名（{@code NO_POLICY} / {@code POLICY_MISSING}…），
 * 按细因做的统计与告警就永远合不起来。</p>
 *
 * <p><b>写入方现状（2026-10-09 核对，别把"有词"读成"有事实"）</b>：
 * {@link #NO_ROUTE_POLICY} 与 {@link #NO_MODEL_BOUND} 由路由引擎在结论无歧义时写入；
 * 其余为契约预留——它们对应的判定要么尚未实现（组织范围、工具授权、沙箱证据），
 * 要么原因是多候选排除的合成结果，需要先定「以哪个候选的排除为准」才不至于写出一个
 * 看似精确、实际片面的细因。认不出就留空：<b>猜错的细因比没有细因更坏</b>，
 * 它会把排障引向错误方向，而且看不出是猜的。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigPolicyReasonCodeEnum {

    /**
     * 策略禁止外发（{@code allowExternal='N'}）而候选属于外部部署
     */
    EXTERNAL_DISALLOWED("EXTERNAL_DISALLOWED", "策略禁止外发，候选为外部部署"),

    /**
     * 模型允许的最高数据等级低于本次数据等级
     */
    DATA_LEVEL_TOO_HIGH("DATA_LEVEL_TOO_HIGH", "数据等级高于模型允许的上限"),

    /**
     * 未配置「能力 × 数据等级」的路由策略（默认拒绝）
     */
    NO_ROUTE_POLICY("NO_ROUTE_POLICY", "未配置该数据等级的路由策略"),

    /**
     * 能力未绑定任何可用模型
     */
    NO_MODEL_BOUND("NO_MODEL_BOUND", "能力未绑定可用模型"),

    /**
     * 模型声明的能力标签不覆盖能力要求的标签
     */
    MODEL_TAGS_MISMATCH("MODEL_TAGS_MISMATCH", "模型能力标签不匹配"),

    /**
     * 模型最近一次健康检查为不可用（DOWN/UNHEALTHY）
     */
    RUNTIME_UNHEALTHY("RUNTIME_UNHEALTHY", "模型运行时不健康"),

    /**
     * 组织范围不允许（组织树口径见 F-05，尚未实现）
     */
    ORG_SCOPE_DENIED("ORG_SCOPE_DENIED", "组织范围不允许"),

    /**
     * 工具未授权（工具注册尚未落地）
     */
    TOOL_NOT_GRANTED("TOOL_NOT_GRANTED", "工具未授权"),

    /**
     * 工具风险等级需要审批（工具注册尚未落地）
     */
    TOOL_RISK_NEEDS_APPROVAL("TOOL_RISK_NEEDS_APPROVAL", "工具风险等级需要审批"),

    /**
     * 制品超出允许范围（制品账本已落地，范围判定尚未实现）
     */
    ARTIFACT_OUT_OF_SCOPE("ARTIFACT_OUT_OF_SCOPE", "制品超出允许范围"),

    /**
     * 缺少沙箱证据（沙箱证据入门槛归 Skill 线 F-02）
     */
    SANDBOX_EVIDENCE_MISSING("SANDBOX_EVIDENCE_MISSING", "缺少沙箱证据");

    /**
     * 编码（入库与日志口径）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * <p>返回 null 是刻意的：外部传进来的细因可能是契约之外的词，调用方据此决定
     * 「原样存下」还是「丢弃」，而不是被本方法悄悄替换成一个错误的枚举值。</p>
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigPolicyReasonCodeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigPolicyReasonCodeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
