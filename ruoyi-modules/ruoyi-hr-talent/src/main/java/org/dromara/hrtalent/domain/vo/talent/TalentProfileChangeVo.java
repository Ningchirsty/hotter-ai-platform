package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才关键字段变更历史视图对象 hr_talent_profile_change（SPEC-P3 §2.1
 * {@code GET /talent/profiles/{id}/changes}）。
 *
 * <p>只返回可展示的变更快照与操作人；{@code before_json} / {@code after_json} 均为
 * <b>脱敏后</b>的结构化快照，不含电话/邮箱明文。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentProfileChangeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更记录ID
     */
    private Long changeId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 变更类型（create/update/archive/merge/status 等稳定编码）
     */
    private String changeType;

    /**
     * 变更前快照（脱敏后结构化 JSON）
     */
    private String beforeJson;

    /**
     * 变更后快照（脱敏后结构化 JSON）
     */
    private String afterJson;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 变更类型中文兜底（设计文档 §10 未定义变更类型字典）。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getChangeTypeLabel() {
        return switch (changeType == null ? "" : changeType) {
            case "create" -> "创建";
            case "update" -> "更新";
            case "status" -> "状态变更";
            case "archive" -> "归档";
            case "merge" -> "合并";
            default -> null;
        };
    }

}
