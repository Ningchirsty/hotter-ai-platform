package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 路由策略对象 aig_route_policy
 * <p>按 能力 × 数据等级 配置；判定口径：<b>无策略即拒绝</b>，不默认放行。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_route_policy")
public class AigRoutePolicy extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 策略ID
     */
    @TableId(value = "policy_id")
    private Long policyId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 数据等级（PUBLIC/INTERNAL/RESTRICTED）
     */
    private String dataLevel;

    /**
     * 优先部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）
     */
    private String preferredDeployment;

    /**
     * 是否允许外发（Y允许 N禁止）
     */
    private String allowExternal;

    /**
     * 调用前是否需要审批（Y是 N否；阶段1仅作为路由判定，审批流二期）
     */
    private String requireApproval;

    /**
     * 无可用模型时是否转人工待办（Y是 N否）
     */
    private String fallbackToManual;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
