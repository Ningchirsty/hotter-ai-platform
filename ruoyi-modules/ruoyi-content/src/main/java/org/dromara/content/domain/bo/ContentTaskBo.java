package org.dromara.content.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.content.domain.CpTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 内容生产任务业务对象 cp_task
 *
 * @author content
 */
@Data
@AutoMapper(target = CpTask.class, reverseConvertGenerate = false)
public class ContentTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 255, message = "任务名称长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String taskName;

    /**
     * 交付类型（ECOM_DETAIL 等）
     */
    @NotBlank(message = "交付类型不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 32, message = "交付类型长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String deliverableType;

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
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 任务负责人（互动卡默认指派人，基线 B6）
     */
    private Long ownerId;

    /**
     * 任务负责人姓名
     */
    @Size(max = 64, message = "负责人姓名长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String ownerName;

    /**
     * 资料敏感级别（PUBLIC/INTERNAL/RESTRICTED）
     */
    @Size(max = 16, message = "资料敏感级别长度不能超过 16", groups = {AddGroup.class, EditGroup.class})
    private String dataLevel;

    /**
     * 是否允许外部AI（Y/N）。阶段1A 的路由策略一律仅本地，本字段为后续阶段预留。
     */
    private String allowExternal;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    // ---------------- 查询条件 ----------------

    /**
     * 任务号（模糊）
     */
    @Size(max = 32, message = "任务号长度不能超过 32", groups = {QueryGroup.class})
    private String queryTaskNo;

    /**
     * 任务名称（模糊）
     */
    @Size(max = 255, message = "任务名称长度不能超过 255", groups = {QueryGroup.class})
    private String queryTaskName;

    /**
     * 交付类型（精确）
     */
    @Size(max = 32, message = "交付类型长度不能超过 32", groups = {QueryGroup.class})
    private String queryDeliverableType;

    /**
     * 任务状态（精确）
     */
    @Size(max = 32, message = "任务状态长度不能超过 32", groups = {QueryGroup.class})
    private String queryStatus;

    /**
     * 负责人（精确）
     */
    private Long queryOwnerId;

    /**
     * 分页参数容器
     */
    private Map<String, Object> params;

}
