package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招聘岗位执行项分配业务对象。
 * <p>对应接口 {@code POST /recruit/jobs/{id}/assign}，<b>只允许</b>调整以下四类人员字段：
 * 招聘负责人（{@code owner_id}）、协助人（{@code assistant_ids}）、
 * 一面面试官（{@code first_interviewer_id}）、二面面试官（{@code second_interviewer_id}）。
 * 不承载岗位状态、名称、薪资等其它字段，避免「分配」动作被当成万能更新使用。</p>
 *
 * <p>字段语义：为 {@code null} 表示本次不修改；{@link #assistantIds} 传空数组表示清空协助人。
 * 一面/二面面试官是岗位上的计划默认值，每轮面试的实际面试官归属
 * {@code hr_recruit_interviewer}（按 {@code interview_id} 关联，后续阶段实现）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitJobAssignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位执行项ID（必填）
     */
    @NotNull(message = "岗位ID不能为空")
    private Long jobId;

    /**
     * 招聘负责人用户ID
     */
    private Long ownerId;

    /**
     * 协助人用户ID数组（入库时以英文逗号拼接，最多 20 人；传空数组表示清空协助人）
     */
    @Size(max = 20, message = "协助人最多 20 人")
    private Long[] assistantIds;

    /**
     * 一面面试官用户ID（岗位计划默认值）
     */
    private Long firstInterviewerId;

    /**
     * 二面面试官用户ID（岗位计划默认值）
     */
    private Long secondInterviewerId;

}
