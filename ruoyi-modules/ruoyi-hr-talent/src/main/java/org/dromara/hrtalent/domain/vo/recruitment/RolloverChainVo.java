package org.dromara.hrtalent.domain.vo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划任务跨月结转链视图对象（SPEC-P2 §3.2：{@code /recruit/plan-items/{id}/rollover-chain}）。
 *
 * <p>同一根任务（{@code root_plan_item_id}）下的所有月份任务构成一条结转链，
 * 按计划月份升序排列，便于前端按时间线展示「8 月 3 人 → 9 月结转 2 人 → 10 月结转 1 人」。</p>
 *
 * @author hr-talent
 */
@Data
public class RolloverChainVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查询入口任务ID
     */
    private Long currentItemId;

    /**
     * 根计划任务ID（结转链起点）
     */
    private Long rootPlanItemId;

    /**
     * 链条上的任务数量
     */
    private Integer itemCount;

    /**
     * 链条节点（按计划月份升序）
     */
    private List<RecruitPlanItemVo> items = new ArrayList<>();

}
