package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视觉方向 dp_visual_direction
 *
 * <p>A/B/C 三套方向，同一个锁定基因下的不同取舍（配色偏向、光线气氛、场景、构图）。
 * {@code strategy_json} 是差异点明细的权威内容；选定一条后其余置为 REJECTED，
 * 但<b>不删除</b>——「当时为什么没选 B」是可复盘信息。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_visual_direction")
public class DpVisualDirection extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 方向代号（A/B/C）
     */
    private String directionCode;

    /**
     * 方向名称
     */
    private String directionName;

    /**
     * 一句话概念
     */
    private String concept;

    /**
     * 策略明细（差异点，结构化）
     */
    private String strategyJson;

    /**
     * 预览图附件ID（逗号分隔，可空＝尚未出预览）
     */
    private String previewFileIds;

    /**
     * 状态（GENERATED待选/SELECTED已选/REJECTED已弃）
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 第几轮生成（同一任务内从 1 递增；一次「生成方向」插入的 3 行共用同一个值）。
     *
     * <p>v1 人工测试反馈裁定（⑨「方向卡要加第几轮标记」）：表里此前没有任何轮次概念，
     * 重新生成只是又插 3 行，页面上出现两组同名 A/B/C，只能靠名字与状态猜哪组是哪次生成的。</p>
     */
    private Integer batchNo;

    /**
     * 本次生成的差异种子（v1 裁定 ⑤「可以复现，但每次生成都要有差异化」的落点）。
     *
     * <p>由 {@code CreativeDraftFactory#variantSeed(taskId, batchNo)} 算出（纯函数，只跟"哪个任务、第几轮"有关），
     * 一次生成插入的 3 行共用同一个值：同一颗种子必然产出逐字相同的三份草稿（可复现），
     * 相邻轮次必然不同（每次重新生成都换一版拍法）。</p>
     *
     * <p>为 {@code null} 表示<b>历史数据</b>——那时还没有差异化逻辑，不能反推说它当初用的是哪个种子。</p>
     */
    private Long variantSeed;

    /**
     * 来源（AI生成/MANUAL人工）
     */
    private String source;

    /**
     * 生成所用模型标识
     */
    private String modelKey;

    /**
     * 治理层调用链ID
     */
    private String traceId;

    /**
     * 选定人
     */
    private Long selectedBy;

    /**
     * 选定时间
     */
    private LocalDateTime selectedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
