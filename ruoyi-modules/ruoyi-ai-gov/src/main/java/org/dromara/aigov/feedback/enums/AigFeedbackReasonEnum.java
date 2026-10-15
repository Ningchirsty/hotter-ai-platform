package org.dromara.aigov.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 结果反馈原因（设计 §6.9：有用、事实有误、引用不符、资料缺失、格式不符、流程中断）。
 *
 * <p><b>为什么用封闭枚举</b>：这些原因要能进问题队列被"接着处理"——补知识、改规则、加用例。
 * 自由文本会变成一堆没人能分类的留言，队列也就退化成收件箱。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigFeedbackReasonEnum {

    /** 有用（正向反馈，同样要能被统计） */
    USEFUL("USEFUL", "有用"),

    /** 事实有误 */
    FACT_ERROR("FACT_ERROR", "事实有误"),

    /** 引用不符（引用与结论对不上） */
    CITATION_MISMATCH("CITATION_MISMATCH", "引用不符"),

    /** 资料缺失 */
    MISSING_SOURCE("MISSING_SOURCE", "资料缺失"),

    /** 格式不符 */
    FORMAT_MISMATCH("FORMAT_MISMATCH", "格式不符"),

    /** 流程中断 */
    FLOW_INTERRUPTED("FLOW_INTERRUPTED", "流程中断");

    /** 编码 */
    private final String code;

    /** 描述 */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigFeedbackReasonEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigFeedbackReasonEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
