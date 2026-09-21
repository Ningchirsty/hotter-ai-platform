package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试结果枚举。
 * <p>对应数据字典 {@code recruit_interview_result}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum InterviewResultEnum {

    /**
     * 待反馈
     */
    PENDING("pending", "待反馈"),
    /**
     * 通过
     */
    PASS("pass", "通过"),
    /**
     * 未通过
     */
    FAIL("fail", "不通过"),
    /**
     * 备选
     */
    RESERVE("reserve", "待定"),
    /**
     * 缺席
     */
    ABSENT("absent", "未到场");

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
    public static InterviewResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (InterviewResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
