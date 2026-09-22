package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpOutputCheck;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成品一致性检查视图对象 cp_output_check
 *
 * <p>除表字段外，额外带上任务号、任务名、产品名与文件引用：
 * 检查列表/详情若不显示「这是哪个任务的哪两张图」，用户就得靠 file_id 去别处翻，
 * 交互成本本身就是模块被考核的指标（SPEC 引用设计文档 §18.1）。</p>
 *
 * @author content
 */
@Data
@AutoMapper(target = CpOutputCheck.class)
public class CpOutputCheckVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 检查ID
     */
    private Long checkId;

    /**
     * 检查单号
     */
    private String checkNo;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务号（补齐字段）
     */
    private String taskNo;

    /**
     * 任务名称（补齐字段）
     */
    private String taskName;

    /**
     * 交付类型（补齐字段）
     */
    private String deliverableType;

    /**
     * 产品ID（补齐字段）
     */
    private Long productId;

    /**
     * 产品名称（补齐字段）
     */
    private String productName;

    /**
     * 原参考图附件ID
     */
    private Long referenceFileId;

    /**
     * 原参考图文件名
     */
    private String referenceFileName;

    /**
     * 原参考图对象键（预览接口据此取图）
     */
    private String referenceFileRef;

    /**
     * 生成结果附件ID
     */
    private Long resultFileId;

    /**
     * 生成结果文件名
     */
    private String resultFileName;

    /**
     * 生成结果对象键
     */
    private String resultFileRef;

    /**
     * 检查状态
     */
    private String status;

    /**
     * 检查结论
     */
    private String verdict;

    /**
     * 一致性得分
     */
    private BigDecimal score;

    /**
     * 结论摘要
     */
    private String summary;

    /**
     * 差异清单 JSON
     */
    private String findingsJson;

    /**
     * 本地确定性度量 JSON
     */
    private String metricsJson;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 模型标识
     */
    private String modelKey;

    /**
     * 部署类型
     */
    private String deploymentType;

    /**
     * 调用器名称
     */
    private String invokerName;

    /**
     * 治理层调用链ID
     */
    private String traceId;

    /**
     * 未取得结论的原因
     */
    private String failureReason;

    /**
     * 检查发起人
     */
    private Long checkedBy;

    /**
     * 检查完成时间
     */
    private LocalDateTime checkedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，不可省略）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

}
