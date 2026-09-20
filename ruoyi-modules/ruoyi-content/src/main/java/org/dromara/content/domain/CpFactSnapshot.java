package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品事实快照对象 cp_fact_snapshot
 *
 * <p><b>为什么同一 task + field_code 允许多行</b>：多来源给出不同取值时，
 * 冲突本身就是需要呈现给人的信息。若按字段唯一行存储，后解析的来源会覆盖先前的，
 * 证据链就断了——用户在互动卡上也就看不到「30cm 与 32cm 两个版本分别来自哪份文件」。</p>
 *
 * <p><b>红线</b>：解析产生的行一律 {@code confirmStatus=PENDING}；
 * 只有人工确认动作会写 {@code CONFIRMED}。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_fact_snapshot")
public class CpFactSnapshot extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照行ID
     */
    @TableId(value = "snapshot_id")
    private Long snapshotId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 快照版本（每次确认递增）
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
     * 来源定位（如：产品参数表V2 第3行）
     */
    private String sourceLocator;

    /**
     * 原文摘录（证据，用户可见）
     */
    private String sourceExcerpt;

    /**
     * 解析置信度 0-100
     */
    private BigDecimal confidence;

    /**
     * 确认状态（见 ContentFactConfirmStatusEnum）
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
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
