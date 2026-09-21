package org.dromara.hrtalent.domainservice;

import lombok.extern.slf4j.Slf4j;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 月度计划任务三维状态与主展示状态计算领域服务（SPEC-P2 §4.2 / 设计文档 §7.1.4、§7.1.5、§21.15）。
 *
 * <p><b>职责边界</b>：本服务只做<b>纯计算</b>，不访问数据库、不读登录态、不接受前端直接写入的自动状态。
 * 数据读取、持久化与事务边界由 {@code PlanItemStatusServiceImpl} / {@code PlanRolloverServiceImpl} 负责，
 * 因此全部方法都可以脱离 Spring 容器做单元测试（这是 SPEC-P2 §6 对「主状态优先级」的硬性要求）。</p>
 *
 * <p><b>三个状态维度</b>：</p>
 * <ul>
 *     <li>{@code control_status}：人工控制（normal/paused/cancelled），<b>只能由人工动作驱动</b>，
 *     自动刷新永远不得改写该字段，${@code control_reason} 同理。</li>
 *     <li>{@code execution_status}：自动执行阶段（pending/recruiting/interviewing/offer/pending_arrival），
 *     由候选人流程驱动；P2 缺少应聘记录域，阶段值由调用方传入。</li>
 *     <li>{@code completion_status}：完成与结转状态，按人数自动计算。</li>
 * </ul>
 *
 * <p><b>人数口径</b>：{@code remaining_qty = max(plan_qty - credited_arrival_qty, 0)}，恒不为负。</p>
 *
 * <p><b>主展示状态优先级</b>（§7.1.5，从高到低）：
 * 已取消 &gt; 暂停 &gt; 已完成 &gt; 已结转 &gt; 待报到 &gt; 待录用 &gt; 面试中 &gt; 招聘中 &gt; 待启动。
 * 当 {@code credited_arrival_qty > 0 且 remaining_qty > 0} 时，另外给出「部分完成」标记，
 * <b>但不覆盖</b>执行阶段（{@link #isPartiallyCompleted}）。</p>
 *
 * <p>与设计文档 §21.15 伪代码的差异说明：伪代码在「已取消 / 暂停」分支不再改写
 * {@code completion_status}；本实现按 SPEC-P2 §4.2「completion_status 按人数自动计算」的约定，
 * 始终按人数与是否已结转计算出完成度，而把「已取消 / 暂停」只体现在主展示状态的优先级上。
 * 这样两个维度各自含义单一，页面可同时展示「已暂停 + 部分完成」。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
public class PlanItemStatusDomainService {

    /**
     * 是否允许自动结转：是。
     */
    private static final String CARRYOVER_ENABLED = "1";

    /**
     * 计划任务主展示状态（由三个状态维度合并得出，前端只读）。
     *
     * @author hr-talent
     */
    public enum DisplayStatus {

        /**
         * 已取消（人工控制）
         */
        CANCELLED("cancelled", "已取消"),

        /**
         * 已暂停（人工控制）
         */
        PAUSED("paused", "已暂停"),

        /**
         * 已完成（剩余人数为 0）
         */
        COMPLETED("completed", "已完成"),

        /**
         * 已结转（已生成下月结转任务）
         */
        ROLLED_OVER("rolled_over", "已结转"),

        /**
         * 待报到
         */
        PENDING_ARRIVAL("pending_arrival", "待报到"),

        /**
         * 待录用
         */
        OFFER("offer", "待录用"),

        /**
         * 面试中
         */
        INTERVIEWING("interviewing", "面试中"),

        /**
         * 招聘中
         */
        RECRUITING("recruiting", "招聘中"),

        /**
         * 待启动
         */
        PENDING("pending", "待启动");

        /**
         * 编码（对前端稳定）
         */
        private final String code;

        /**
         * 中文名称
         */
        private final String desc;

        DisplayStatus(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        /**
         * 获取编码。
         *
         * @return 编码
         */
        public String getCode() {
            return code;
        }

        /**
         * 获取中文名称。
         *
         * @return 中文名称
         */
        public String getDesc() {
            return desc;
        }

        /**
         * 按编码查找。
         *
         * @param code 编码
         * @return 匹配的枚举，未命中返回 null
         */
        public static DisplayStatus find(String code) {
            if (code == null) {
                return null;
            }
            for (DisplayStatus item : values()) {
                if (item.code.equals(code)) {
                    return item;
                }
            }
            return null;
        }

        /**
         * 按编码取中文名称，未知编码回落到原编码。
         *
         * @param code 编码
         * @return 中文名称
         */
        public static String labelOf(String code) {
            DisplayStatus item = find(code);
            return item == null ? code : item.desc;
        }
    }

    /* ------------------------------------------------------------------ 人数口径 ------------------------------------------------------------------ */

    /**
     * 计算剩余人数：{@code max(plan_qty - credited_arrival_qty, 0)}。
     *
     * @param planQty            计划人数，null 视为 0
     * @param creditedArrivalQty 已计入到岗人数，null 视为 0
     * @return 剩余人数，恒 &gt;= 0
     */
    public int resolveRemainingQty(Integer planQty, Integer creditedArrivalQty) {
        int plan = planQty == null ? 0 : Math.max(planQty, 0);
        int credited = creditedArrivalQty == null ? 0 : Math.max(creditedArrivalQty, 0);
        return Math.max(plan - credited, 0);
    }

    /**
     * 是否显示「部分完成」标记：到岗人数大于 0 且仍有剩余人数。
     * <p>该标记是附加标签，<b>不覆盖</b>执行阶段（§7.1.5）。</p>
     *
     * @param creditedArrivalQty 已计入到岗人数
     * @param remainingQty       剩余人数
     * @return 是否显示部分完成
     */
    public boolean isPartiallyCompleted(Integer creditedArrivalQty, Integer remainingQty) {
        return creditedArrivalQty != null && creditedArrivalQty > 0
            && remainingQty != null && remainingQty > 0;
    }

    /* ------------------------------------------------------------------ 完成度维度 ------------------------------------------------------------------ */

    /**
     * 按人数与结转结果计算 {@code completion_status}（与人工控制状态无关）。
     * <p>优先级：剩余为 0 → 已完成；否则已结转 → 已结转；否则到岗 &gt; 0 → 部分完成；否则未完成。</p>
     *
     * @param creditedArrivalQty 已计入到岗人数
     * @param remainingQty       剩余人数
     * @param rolledOver         是否已成功生成目标月份结转任务
     * @return 完成与结转状态枚举
     */
    public PlanCompletionStatusEnum resolveCompletionStatus(Integer creditedArrivalQty,
                                                            Integer remainingQty,
                                                            boolean rolledOver) {
        if (remainingQty != null && remainingQty <= 0) {
            return PlanCompletionStatusEnum.COMPLETED;
        }
        if (rolledOver) {
            return PlanCompletionStatusEnum.ROLLED_OVER;
        }
        return isPartiallyCompleted(creditedArrivalQty, remainingQty)
            ? PlanCompletionStatusEnum.PARTIAL_COMPLETED
            : PlanCompletionStatusEnum.UNFINISHED;
    }

    /* ------------------------------------------------------------------ 主展示状态 ------------------------------------------------------------------ */

    /**
     * 计算主展示状态（§7.1.5 优先级）。
     *
     * @param controlStatus    人工控制状态编码
     * @param executionStatus  自动执行阶段编码
     * @param completionStatus 完成度状态编码
     * @param remainingQty     剩余人数
     * @return 主展示状态
     */
    public DisplayStatus resolveDisplayStatus(String controlStatus,
                                              String executionStatus,
                                              String completionStatus,
                                              Integer remainingQty) {
        // 1. 已取消（人工控制）
        if (PlanControlStatusEnum.CANCELLED.getCode().equals(controlStatus)) {
            return DisplayStatus.CANCELLED;
        }
        // 2. 暂停（人工控制）
        if (PlanControlStatusEnum.PAUSED.getCode().equals(controlStatus)) {
            return DisplayStatus.PAUSED;
        }
        // 3. 已完成（remaining_qty = 0）
        if (PlanCompletionStatusEnum.COMPLETED.getCode().equals(completionStatus)
            || (remainingQty != null && remainingQty <= 0)) {
            return DisplayStatus.COMPLETED;
        }
        // 4. 已结转（已生成下月结转任务）
        if (PlanCompletionStatusEnum.ROLLED_OVER.getCode().equals(completionStatus)) {
            return DisplayStatus.ROLLED_OVER;
        }
        // 5~8. 按自动执行阶段从后往前回退，未知编码一律落到待启动
        PlanExecutionStatusEnum execution = PlanExecutionStatusEnum.find(executionStatus);
        if (execution == null) {
            return DisplayStatus.PENDING;
        }
        return switch (execution) {
            case PENDING_ARRIVAL -> DisplayStatus.PENDING_ARRIVAL;
            case OFFER -> DisplayStatus.OFFER;
            case INTERVIEWING -> DisplayStatus.INTERVIEWING;
            case RECRUITING -> DisplayStatus.RECRUITING;
            case PENDING -> DisplayStatus.PENDING;
        };
    }

    /**
     * 计算主展示状态编码（{@link #resolveDisplayStatus} 的编码形式）。
     *
     * @param controlStatus    人工控制状态编码
     * @param executionStatus  自动执行阶段编码
     * @param completionStatus 完成度状态编码
     * @param remainingQty     剩余人数
     * @return 主展示状态编码
     */
    public String resolveDisplayStatusCode(String controlStatus,
                                           String executionStatus,
                                           String completionStatus,
                                           Integer remainingQty) {
        return resolveDisplayStatus(controlStatus, executionStatus, completionStatus, remainingQty).getCode();
    }

    /**
     * 按计划任务实体计算主展示状态。
     *
     * @param item 计划任务实体，为 null 时返回 {@link DisplayStatus#PENDING}
     * @return 主展示状态
     */
    public DisplayStatus resolveDisplayStatus(RecruitPlanItem item) {
        if (item == null) {
            return DisplayStatus.PENDING;
        }
        return resolveDisplayStatus(item.getControlStatus(), item.getExecutionStatus(),
            item.getCompletionStatus(), item.getRemainingQty());
    }

    /* ------------------------------------------------------------------ 刷新落值 ------------------------------------------------------------------ */

    /**
     * 把重算结果写回计划任务实体（<b>纯内存操作</b>，调用方负责持久化与事务）。
     *
     * <p><b>硬约束</b>：本方法<b>绝不</b>改写 {@code control_status} 与 {@code control_reason}，
     * 人工暂停 / 取消结果不会被自动刷新覆盖；同时<b>绝不</b>改写 {@code plan_month}、{@code plan_qty}
     * 与 {@code item_no}。</p>
     *
     * @param item               计划任务实体
     * @param creditedArrivalQty 已计入到岗人数（权威值）
     * @param executionStatus    自动执行阶段编码；为空表示保持原值不变
     * @param rolledOver         是否已成功结转到目标月份
     * @param refreshTime        刷新时间
     * @return 本次计算出的主展示状态
     */
    public DisplayStatus applyRefresh(RecruitPlanItem item,
                                      Integer creditedArrivalQty,
                                      String executionStatus,
                                      boolean rolledOver,
                                      LocalDateTime refreshTime) {
        if (item == null) {
            return DisplayStatus.PENDING;
        }
        int credited = creditedArrivalQty == null ? 0 : Math.max(creditedArrivalQty, 0);
        int remaining = resolveRemainingQty(item.getPlanQty(), credited);
        item.setCreditedArrivalQty(credited);
        item.setRemainingQty(remaining);
        item.setCompletionStatus(resolveCompletionStatus(credited, remaining, rolledOver).getCode());
        if (PlanExecutionStatusEnum.find(executionStatus) != null) {
            item.setExecutionStatus(executionStatus);
        }
        item.setLastRefreshTime(refreshTime == null ? LocalDateTime.now() : refreshTime);
        return resolveDisplayStatus(item);
    }

    /**
     * 判断计划任务是否允许自动结转（纯规则，不含「是否已结转」的幂等判断）。
     *
     * @param item 计划任务实体
     * @return 是否开启自动结转
     */
    public boolean isCarryoverEnabled(RecruitPlanItem item) {
        return item != null && CARRYOVER_ENABLED.equals(item.getCarryoverEnabled());
    }

    /**
     * 判断计划任务是否已处于不可继续修改的终态。
     * <p>用于 {@code HR_PLAN_002}：已完成、已结转或已取消的任务不能继续修改。</p>
     *
     * @param item 计划任务实体
     * @return 是否终态
     */
    public boolean isFinalized(RecruitPlanItem item) {
        if (item == null) {
            return true;
        }
        if (PlanControlStatusEnum.CANCELLED.getCode().equals(item.getControlStatus())) {
            return true;
        }
        String completion = item.getCompletionStatus();
        return PlanCompletionStatusEnum.COMPLETED.getCode().equals(completion)
            || PlanCompletionStatusEnum.ROLLED_OVER.getCode().equals(completion);
    }

    /**
     * 终态拒绝提示（{@link HrTalentErrorCode#MSG_HR_PLAN_002}）。
     *
     * @return 中文提示
     */
    public String finalizedMessage() {
        return HrTalentErrorCode.MSG_HR_PLAN_002;
    }

}
