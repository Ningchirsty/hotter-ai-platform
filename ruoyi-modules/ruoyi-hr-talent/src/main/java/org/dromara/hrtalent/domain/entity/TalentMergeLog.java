package org.dromara.hrtalent.domain.entity;

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
 * 人才合并日志对象 hr_talent_merge_log（设计文档 §8.18、§9.2、§21.7）。
 *
 * <p><b>不可撤销凭证</b>：合并把全部关系改归属到保留主档，并把被合并主档标记为 {@code merged}；
 * 本表保存「字段取值决策 + 关系迁移数量」的完整快照，是事后追溯与人工回滚的唯一依据。</p>
 *
 * <p><b>不物理删除</b>：合并过程只做 UPDATE（改归属）与状态标记，任何资料都不做物理删除。</p>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code merge_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_merge_log")
public class TalentMergeLog extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 合并日志ID（主键）
     */
    @TableId(value = "merge_id")
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
     * 字段取值决策快照（结构化 JSON：冲突字段、命中值、落选值与来源侧）
     */
    private String fieldDecisionJson;

    /**
     * 关系迁移数量快照（结构化 JSON：各关系表迁移前后数量）
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
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
