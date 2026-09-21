package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才合并日志视图对象 hr_talent_merge_log（SPEC-P4 §2.5、设计文档 §8.18）。
 *
 * <p>合并后<b>不可普通撤销</b>：页面只能查看合并快照（字段决策 + 关系迁移数量）。
 * 快照 JSON 字符串原样返回，由前端按字段清单渲染。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentMergeLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 合并日志ID
     */
    private Long mergeId;

    /**
     * 保留（主）人才主档ID
     */
    private Long keepTalentId;

    /**
     * 被合并（从）人才主档ID
     */
    private Long mergedTalentId;

    /**
     * 字段取值决策快照 JSON
     */
    private String fieldDecisionJson;

    /**
     * 关系迁移数量快照 JSON
     */
    private String relationCountJson;

    /**
     * 合并原因
     */
    private String mergeReason;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人昵称（由 {@link #operatorId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
