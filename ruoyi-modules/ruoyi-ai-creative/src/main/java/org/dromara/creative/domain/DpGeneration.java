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
 * 生成记录 dp_generation
 *
 * <p>回答「哪一屏、第几次候选、什么提示词、谁执行的、产出哪个图、质检结论是什么」。
 * 出图的<b>真相在 {@code image_task}</b>（内核表），本表是视觉领域的业务视角：
 * 关联分镜屏与候选序号，并记下执行者身份（内核按 tenant+user 校验素材归属，
 * 回读状态/产出必须带同一身份，因此这里必须存下来）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_generation")
public class DpGeneration extends BaseEntity implements Serializable {

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
     * 分镜单屏（dp_storyboard_screen.id；方向预览图为 null）
     */
    private Long screenId;

    /**
     * 生成时采用的视觉基因版本（dp_visual_dna.id；R0 历史候选为 null）
     */
    private Long dnaId;

    /**
     * 生成时采用的视觉方向（dp_visual_direction.id）
     */
    private Long directionId;

    /**
     * 生成时采用的分镜版本（dp_storyboard.id）
     */
    private Long storyboardId;

    /**
     * 候选序号（同屏第几次）
     */
    private Integer candidateNo;

    /**
     * 出图能力编码（如 WF-HERO-001 / wf-i2i-qwen21）
     */
    private String workflowCode;

    /**
     * 契约版本（发布态）
     */
    private String workflowVersion;

    /**
     * 正向提示词（实际下发）
     */
    private String prompt;

    /**
     * 负向提示词（实际下发）
     */
    private String negativePrompt;

    /**
     * 随机种子（可复现）
     */
    private Long seed;

    /**
     * 输入明细（结构化）
     */
    private String inputJson;

    /**
     * 输入参考图附件ID（cp_task_file.file_id，业务留痕）
     */
    private Long inputFileId;

    /**
     * 输入参考图内核素材ID（image_asset.id，执行用）
     */
    private Long inputAssetId;

    /**
     * 产品保真基准：产品图附件ID（cp_task_file.file_id，source_type=PRODUCT）
     *
     * <p>与 {@link #inputFileId}（本次出图实际喂进模型的参考图）分开记：
     * 「模型看的是哪张图」与「产品本体长什么样」是两件事，
     * 出图之后要能回答「这版是按哪张产品图核对保真的」。</p>
     */
    private Long productFileId;

    /**
     * 执行内核任务ID（image_task.id）
     */
    private Long imageTaskId;

    /**
     * 执行者租户
     */
    private String execTenantId;

    /**
     * 执行者用户
     */
    private Long execUserId;

    /**
     * 产出附件ID（cp_task_file.file_id，业务留痕）
     */
    private Long outputFileId;

    /**
     * 产出内核素材ID（image_asset.id，预览代理走它）
     */
    private Long outputAssetId;

    /**
     * 产出实际宽度
     */
    private Integer outputWidth;

    /**
     * 产出实际高度
     */
    private Integer outputHeight;

    /**
     * 状态（见 DpGenerationStatusEnum）
     */
    private String status;

    /**
     * 失败码（内核错误码原样保留）
     */
    private String errorCode;

    /**
     * 失败原因（用户可读）
     */
    private String errorMessage;

    /**
     * QA 检查ID（cp_output_check.check_id）
     */
    private Long qaCheckId;

    /**
     * QA 结论镜像（权威在 cp_output_check）
     */
    private String qaVerdict;

    /**
     * 产品图基准检查ID（cp_output_check.check_id）
     *
     * <p>参考图基准（{@link #qaCheckId}）回答「改动了多少风格」；
     * 产品图基准回答「产品还是不是那个产品」。两者都跑，结论分别留痕。</p>
     */
    private Long productCheckId;

    /**
     * 产品图基准质检结论镜像（CONSISTENT/INCONSISTENT/UNCERTAIN）
     *
     * <p><b>只提示，不自动筛除</b>：产品图与生成图的差异可能正是设计意图（换背景、换角度），
     * 自动筛除会替人做艺术判断；这里只把结论响亮地摆出来。</p>
     */
    private String productVerdict;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 执行的 GPU 节点名
     */
    private String gpuNode;

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
