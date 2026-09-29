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
 * 闸门项 dp_gate_item（V0.2 B2）：配置"有哪些检查项、什么等级、什么顺序"。
 *
 * <p>注意边界：**每一项怎么判**在代码里（按 {@link #itemCode} 找检查器）。
 * 配置里出现代码没有的编码时按**未通过**处理（fail-closed），不会静默放过。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_gate_item")
public class DpGateItem extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 所属闸门档案（dp_gate_profile.id）
     */
    private Long profileId;

    /**
     * 闸门项编码（代码里按它找检查器）
     */
    private String itemCode;

    /**
     * 展示名
     */
    private String itemLabel;

    /**
     * 等级（BLOCK=阻断提交 / CONDITION=提示）
     */
    private String level;

    /**
     * 顺序
     */
    private Integer sortNo;

    /**
     * 可选参数（给具体检查器用；当前 7 项都不需要）
     */
    private String optionsJson;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
