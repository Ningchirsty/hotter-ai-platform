package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 闸门档案 dp_gate_profile（V0.2 B2，文档 §25 "不同场景使用不同 Gate Profile"）。
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_gate_profile")
public class DpGateProfile extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 闸门档案编码（如 GATE_ECOM_DETAIL_V1）
     */
    private String profileCode;

    /**
     * 交付类型（与 cp_task.deliverable_type 同词表）
     */
    private String deliveryType;

    /**
     * 版本
     */
    private String version;

    /**
     * 状态（DRAFT/PUBLISHED/RETIRED）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
