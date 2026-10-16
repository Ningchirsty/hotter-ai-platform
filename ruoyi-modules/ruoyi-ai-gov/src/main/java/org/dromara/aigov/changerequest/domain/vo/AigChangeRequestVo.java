package org.dromara.aigov.changerequest.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.changerequest.domain.AigChangeRequest;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更单视图（只读；带状态/类型的中文标签，避免前端各写一份映射）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigChangeRequest.class)
public class AigChangeRequestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long changeRequestId;
    private String capabilityCode;
    private String changeType;
    /** 变更类型中文 */
    private String changeTypeLabel;
    private Long targetRef;
    /** 变更前快照（JSON 文本；新增时为空） */
    private String beforeJson;
    /** 变更后目标值（JSON 文本；删除时为空） */
    private String afterJson;
    private String reason;
    private String status;
    /** 状态中文 */
    private String statusLabel;
    private Long requesterId;
    private String requesterName;
    private Long approverId;
    private String approverName;
    private LocalDateTime decidedAt;
    private String decisionRemark;
    private LocalDateTime appliedAt;
    /** 执行结果说明（冲突/失败原因） */
    private String resultRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
