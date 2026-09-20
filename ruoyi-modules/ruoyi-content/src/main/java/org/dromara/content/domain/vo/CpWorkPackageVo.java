package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpWorkPackage;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设计开工包视图对象 cp_work_package
 *
 * @author content
 */
@Data
@AutoMapper(target = CpWorkPackage.class)
public class CpWorkPackageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 开工包ID
     */
    private Long packageId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务号（列表页显示）
     */
    private String taskNo;

    /**
     * 签发时冻结的事实版本
     */
    private Integer snapshotVersion;

    /**
     * 开工包全文（JSON）
     */
    private String contentJson;

    /**
     * 状态（DRAFT/ISSUED）
     */
    private String status;

    /**
     * 生成人
     */
    private Long generatedBy;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 签发人
     */
    private Long issuedBy;

    /**
     * 签发时间
     */
    private LocalDateTime issuedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
