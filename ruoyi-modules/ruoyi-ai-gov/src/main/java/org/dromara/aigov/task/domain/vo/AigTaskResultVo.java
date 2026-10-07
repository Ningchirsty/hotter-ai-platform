package org.dromara.aigov.task.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTaskResult;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务结果（候选资产）视图。
 *
 * <p>{@code validationResult}（是否符合 Schema）与 {@code qaVerdict}
 * （内容是否与事实/品牌一致）分列展示，不合并——「格式对但内容错」是最常见的失败形态，
 * 合成一个「是否通过」会让它无法表达。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTaskResult.class)
public class AigTaskResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long resultId;

    private Long taskId;

    private Integer attemptNo;

    private String resultType;

    private Long assetId;

    private String structuredOutputJson;

    private String validationResult;

    private String validationDetail;

    private String candidateStatus;

    /**
     * 候选状态描述
     */
    private String candidateStatusLabel;

    private String qaVerdict;

    private String qaDetail;

    private Long selectedBy;

    private LocalDateTime selectedAt;

    private LocalDateTime createTime;

    private String remark;

}
