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
 * 成品一致性检查对象 cp_output_check
 *
 * <p><b>它解决什么</b>：任务出稿之后，需要把「生成的结果图」和「当初的参考图」摆在一起，
 * 判断成品是不是真的照着参考图做的（主体形态、颜色、数量、结构、Logo、包装文字是否一致）。
 * 此前模块只覆盖到「开工前」——上传资料、抽事实、确认、出开工包；出稿之后的验收环节是空白。</p>
 *
 * <p><b>它不是产品事实的来源</b>（SPEC §0.1 红线第 2 条）：参考图与 AI 输出不得反向成为
 * 产品结构、颜色、数量、包装、参数的依据。因此本表<b>只记录检查结论</b>，
 * 不写入、也不更新任何 {@code cp_fact_snapshot}；结论只用于提示人复核与流转判断。</p>
 *
 * <p><b>图片不落业务库</b>：两张图都作为 {@code cp_task_file} 附件存对象存储，
 * 本表只存 {@code *_file_id} 引用（与 SPEC §16.1 一致）。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_output_check")
public class CpOutputCheck extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 检查ID
     */
    @TableId(value = "check_id")
    private Long checkId;

    /**
     * 检查单号（对外展示）
     */
    private String checkNo;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 原参考图附件ID（cp_task_file.file_id）
     */
    private Long referenceFileId;

    /**
     * 生成结果附件ID（cp_task_file.file_id）
     */
    private Long resultFileId;

    /**
     * 检查状态（见 ContentCheckStatusEnum）
     */
    private String status;

    /**
     * 检查结论（见 ContentCheckVerdictEnum），未出结论时为 null
     */
    private String verdict;

    /**
     * 一致性得分 0–100（越高越一致）；无法量化时为 null，不编造
     */
    private BigDecimal score;

    /**
     * 结论摘要（一句话给用户看）
     */
    private String summary;

    /**
     * 差异清单 JSON：每项含 category/severity/description/expectation/observation
     */
    private String findingsJson;

    /**
     * 本地确定性度量 JSON（尺寸、比例、网格差异等），与模型结论并列留痕
     */
    private String metricsJson;

    /**
     * 实际执行的模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 实际执行的模型标识（model_key）
     */
    private String modelKey;

    /**
     * 实际执行的部署类型（LOCAL/GROUP/EXTERNAL_API/EXTERNAL_ENTERPRISE）
     */
    private String deploymentType;

    /**
     * 实际执行的调用器名称
     */
    private String invokerName;

    /**
     * 治理层调用链ID（aig_invocation_audit.trace_id），排障与追责用
     */
    private String traceId;

    /**
     * 未取得结论的可读原因（status=FAILED 时必填）
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
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
