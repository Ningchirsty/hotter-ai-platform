package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * AI 能力调用入参业务对象
 * <p>唯一对外调用入口 {@code IAigInvokeService} 的入参；<b>不得</b>包含任何密钥字段。</p>
 *
 * @author ai-gov
 */
@Data
public class AigInvokeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String capabilityCode;

    /**
     * 本次数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     *
     * <p><b>必须包含 STRICT</b>：严格级是「任何策略都不允许外发」的数据。
     * 若入口把它判为非法，持有严格级数据的业务域就只有两条路——放弃调用，
     * 或者把等级标低再调。后者正是 STRICT 要防的事，且从请求上看不出发生过。
     * 因此入口一律放行，由路由引擎负责「STRICT 只走非外部部署」。</p>
     */
    @NotBlank(message = "数据等级不能为空")
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED|STRICT)$",
        message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED/STRICT")
    private String dataLevel;

    /**
     * 场景编码（LONG_PAGE、POSTER、MULTI_IMAGE 等，可为空）
     *
     * <p>用于设计 §4.4 第 4 步的「场景强制绑定 Provider」：命中绑定时，候选会被收窄为
     * 仅指定供应商。留空表示不做场景收窄——没有场景概念的调用占绝大多数。
     * 大小写不敏感（引擎与配置都按大写比对），因此这里不做字符集约束，只限长度。</p>
     */
    @Size(max = 64, message = "场景编码长度不能超过 64")
    private String scenarioCode;

    /**
     * 提示词（业务输入）
     */
    private String prompt;

    /**
     * 结构化业务载荷（如 talent_match 的 skillTags / candidates）
     */
    private Map<String, Object> payload;

    /**
     * 不可变输入快照引用（可选，设计 §4.3）
     *
     * <p>只填<b>引用</b>（对象键 / 业务ID），不要填快照本身。审计会原样落库，
     * 因此这是事后复现一次结论的唯一入口：同一个 traceId 只看到「用了什么模型」，
     * 看不到「当时喂进去的是什么」，就无法判断当时的输出是否合理。</p>
     */
    @Size(max = 500, message = "输入快照引用长度不能超过 500")
    private String inputSnapshotRef;

    /**
     * 本次调用可接受的最高成本（单次，可选；单位由部署方统一，本平台按元）
     *
     * <p>与模型治理的 {@code cost_limit_amount} 比对：声明上限高于本次预算的候选会被排除
     * （设计 §4.4 第 3 步「过滤…超过预算…的 Provider」）。
     * <b>留空表示没有预算约束</b>，此时不做过滤也不产生提示——
     * 把「没提要求」当成「预算为零」会把所有候选排除干净。</p>
     */
    @DecimalMin(value = "0", message = "本次预算不能为负数")
    @Digits(integer = 10, fraction = 8, message = "本次预算整数位最多 10 位、小数位最多 8 位")
    private BigDecimal maxCost;

}
