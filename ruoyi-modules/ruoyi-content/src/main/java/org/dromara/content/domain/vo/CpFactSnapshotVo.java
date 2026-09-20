package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpFactSnapshot;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品事实快照视图对象 cp_fact_snapshot
 *
 * @author content
 */
@Data
@AutoMapper(target = CpFactSnapshot.class)
public class CpFactSnapshotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照行ID
     */
    private Long snapshotId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 快照版本
     */
    private Integer snapshotVersion;

    /**
     * 事实字段编码
     */
    private String fieldCode;

    /**
     * 事实字段名称
     */
    private String fieldName;

    /**
     * 字段值
     */
    private String fieldValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 来源附件ID
     */
    private Long sourceFileId;

    /**
     * 来源文件名（联表带出，互动卡要显示「来自哪份文件」）
     */
    private String sourceFileName;

    /**
     * 来源定位
     */
    private String sourceLocator;

    /**
     * 原文摘录（证据）
     */
    private String sourceExcerpt;

    /**
     * 解析置信度
     */
    private BigDecimal confidence;

    /**
     * 确认状态
     */
    private String confirmStatus;

    /**
     * 确认人
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

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
