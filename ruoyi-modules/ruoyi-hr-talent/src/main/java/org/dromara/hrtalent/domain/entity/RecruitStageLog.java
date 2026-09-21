package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
 * 应聘阶段历史对象 hr_recruit_stage_log（SPEC-P3 §3.2 / 设计文档 §8.5）。
 *
 * <p><b>只追加</b>：每次阶段变化写入一条独立历史记录，<b>不允许</b>覆盖或由业务人员物理删除
 * （{@code del_flag} 仅供系统一致性修复使用）。阶段流转与历史写入必须在<b>同一事务</b>内完成（§21.7）。</p>
 *
 * <p>{@link #comment} 对应的列名是 MySQL 关键字 {@code comment}，DDL 中带反引号，
 * 因此这里显式声明 {@code @TableField("`comment`")}，避免生成的 SQL 出现歧义。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐列一致；主键列为 {@code log_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_stage_log")
public class RecruitStageLog extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 阶段历史ID（主键）
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 原阶段（字典 recruit_candidate_stage）
     */
    private String fromStage;

    /**
     * 目标阶段（字典 recruit_candidate_stage；转入淘汰/放弃/暂缓/人才保留时与 from_stage 相同）
     */
    private String toStage;

    /**
     * 操作动作（move/reject/withdraw/pause/talent_pool/arrive 等稳定编码）
     */
    private String actionType;

    /**
     * 阶段结果（字典 recruit_application_result）
     */
    private String result;

    /**
     * 原因编码（字典编码，不存中文）
     */
    private String reasonCode;

    /**
     * 阶段说明
     */
    @TableField("`comment`")
    private String comment;

    /**
     * 本次跟进的下一步日期（逐次留痕，§8.5）
     */
    private LocalDateTime nextFollowTime;

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
