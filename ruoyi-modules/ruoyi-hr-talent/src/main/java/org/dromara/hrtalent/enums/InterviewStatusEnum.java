package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试状态枚举。
 * <p>对应数据字典 {@code recruit_interview_status}（设计文档 §10 无此组，
 * 按 §9.2 {@code hr_recruit_interview.status} 建表注释
 * 「pending/scheduled/finished/cancelled 等稳定编码」补齐）。</p>
 *
 * <p><b>与既有常量的关系（重要）</b>：{@code RecruitInterviewServiceImpl} 中已定义五个等值
 * 私有常量 {@code STATUS_PENDING / STATUS_SCHEDULED / STATUS_FINISHED / STATUS_CANCELLED /
 * STATUS_RESCHEDULED}（{@code pending/scheduled/finished/cancelled/rescheduled}），
 * 本枚举的编码与其<b>一一对应且取值完全一致</b>。本枚举仅作为字典标签与前端下拉展示的权威来源，
 * <b>不替换</b>服务实现中的私有常量；因此在代码中<b>存在两处定义</b>，建议后续由主控统一
 * （例如服务实现改用本枚举的 {@link #getCode()}），在此之前请勿单独变更任一侧取值。</p>
 *
 * <p><b>说明</b>：入库一律保存 {@link #getCode()} 稳定编码，中文名称仅用于服务端提示与页面兜底展示；
 * 编码值一旦投入使用不得随意变更。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum InterviewStatusEnum {

    /**
     * 待安排：面试已创建但时间未定
     */
    PENDING("pending", "待安排"),
    /**
     * 已安排：面试时间与方式已确定
     */
    SCHEDULED("scheduled", "已安排"),
    /**
     * 已完成：面试已结束并回执
     */
    FINISHED("finished", "已完成"),
    /**
     * 已取消：面试被取消，保留历史记录
     */
    CANCELLED("cancelled", "已取消"),
    /**
     * 已改期：原安排被改期，作为历史记录保留
     */
    RESCHEDULED("rescheduled", "已改期");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，页面展示由字典转换）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static InterviewStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (InterviewStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
