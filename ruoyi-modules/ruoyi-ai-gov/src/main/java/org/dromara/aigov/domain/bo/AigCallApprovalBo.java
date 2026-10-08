package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用授权审批业务对象（<b>查询条件</b>与<b>提交申请</b>共用）。
 *
 * <p>共用一个类是本仓既有做法（如 {@code AigUserQuotaBo}）：校验注解只在
 * 「提交申请」那个接口上生效（控制器对它加 {@code @Validated}，列表查询不加），
 * 所以查询时字段全可空，申请时能力与数据等级必填。</p>
 *
 * <p>{@code requesterId} 既可作为查询条件（谁申请的），<b>也可被服务端用来限定"只看自己的"</b>
 * ——{@code /my} 接口会用登录人覆盖它，客户端传什么都不作数。</p>
 *
 * @author ai-gov
 */
@Data
public class AigCallApprovalBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力编码（申请时必填；授权粒度之一）
     */
    @NotBlank(message = "能力编码不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String capabilityCode;

    /**
     * 数据等级（申请时必填；授权粒度之一）
     *
     * <p>刻意保留 STRICT：允许为严格级数据申请审批是有意义的（例如"仅走本地模型"，
     * 外发红线仍由路由层强制）。这里若把 STRICT 判为非法，严格级数据就没法走流程了。</p>
     */
    @NotBlank(message = "数据等级不能为空")
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED|STRICT)$",
        message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED/STRICT")
    private String dataLevel;

    /**
     * 申请理由（为什么需要这次授权；审批人据此判断）
     */
    @Size(max = 500, message = "申请理由长度不能超过 500")
    private String reason;

    /**
     * 申请人用户ID（仅作为<b>查询条件</b>；申请时由服务端从登录态取，客户端传值不生效）
     */
    private Long requesterId;

    /**
     * 状态（仅作为查询条件：PENDING/APPROVED/REJECTED/EXPIRED/CANCELLED）
     */
    private String status;

    /**
     * 审批人用户ID（仅作为查询条件：谁批的）
     */
    private Long approverId;

}
