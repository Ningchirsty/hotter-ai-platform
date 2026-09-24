package org.dromara.creative.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.creative.domain.DpGeneration;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 生成记录展示对象。
 *
 * @author creative
 */
@Data
@AutoMapper(target = DpGeneration.class)
public class DpGenerationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 项目名称（生产中心列表填充）
     */
    private String taskName;

    /**
     * 分镜单屏
     */
    private Long screenId;

    /**
     * 生成时采用的视觉基因版本（null＝R0 历史候选，早于基因功能）
     */
    private Long dnaId;

    /**
     * 采用的视觉方向
     */
    private Long directionId;

    /**
     * 采用的分镜版本
     */
    private Long storyboardId;

    /**
     * 候选序号
     */
    private Integer candidateNo;

    /**
     * 出图能力/工作流编码
     */
    private String workflowCode;

    /**
     * 契约版本
     */
    private String workflowVersion;

    /**
     * 正向提示词
     */
    private String prompt;

    /**
     * 状态编码
     */
    private String status;

    /**
     * 状态描述（服务层填充，前端直接展示）
     */
    private String statusDesc;

    /**
     * 产出内核素材ID
     */
    private Long outputAssetId;

    /**
     * 产出宽度
     */
    private Integer outputWidth;

    /**
     * 产出高度
     */
    private Integer outputHeight;

    /**
     * QA 结论
     */
    private String qaVerdict;

    /**
     * 产品保真基准的产品图附件ID（cp_task_file.file_id）
     */
    private Long productFileId;

    /**
     * 以产品图为基准的质检结论（只提示，不自动筛除）
     */
    private String productVerdict;

    /**
     * 失败码
     */
    private String errorCode;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 执行 GPU 节点
     */
    private String gpuNode;

    /**
     * 是否有可预览图（服务层填充）
     */
    private Boolean previewable;

    /**
     * 是否可重试（服务层填充）
     */
    private Boolean retryable;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
