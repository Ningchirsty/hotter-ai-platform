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
 * AI 能力与模型绑定对象 aig_capability_model
 * <p>同一能力可绑定多个模型，用 {@code usage_type} + {@code priority}
 * 表达主选/灰度/备选与挑选顺序。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_capability_model")
public class AigCapabilityModel extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    @TableId(value = "bind_id")
    private Long bindId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 关联 sai_model_config.id
     */
    private Long modelId;

    /**
     * 用途（PRIMARY主选 FALLBACK备选 GRAY灰度）
     */
    private String usageType;

    /**
     * 优先级，数值越小越优先
     */
    private Integer priority;

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
