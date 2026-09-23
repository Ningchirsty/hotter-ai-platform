package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视觉项目业务对象。
 *
 * <p>交付类型不由前端决定：视觉工厂只处理电商详情页，服务端固定
 * {@code ECOM_DETAIL}，避免从页面传进来一个别的交付类型把项目挂到错误的流程上。</p>
 *
 * @author creative
 */
@Data
public class CreativeProjectBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID（编辑时必填）
     */
    private Long taskId;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 255, message = "项目名称长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String taskName;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * SKU编码
     */
    @Size(max = 64, message = "SKU编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String skuCode;

    /**
     * 负责人
     */
    private Long ownerId;

    /**
     * 负责人姓名
     */
    @Size(max = 64, message = "负责人姓名长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String ownerName;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
