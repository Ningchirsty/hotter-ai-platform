package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历解析复核状态枚举。
 * <p>对应数据字典 {@code talent_resume_review_status}。</p>
 * <p>本次新增。本枚举<b>仅</b>服务于 {@code hr_talent_resume.review_status} 一列，该列建表注释要求支持
 * rejected，而既有的 {@code talent_resume_parse_status} 组没有 rejected；
 * {@code hr_talent_resume.parse_status} 继续使用 {@link ResumeParseStatusEnum}，两者不得混用。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum ResumeReviewStatusEnum {

    /**
     * 待复核
     */
    PENDING("pending", "待复核"),
    /**
     * 复核中
     */
    REVIEWING("reviewing", "复核中"),
    /**
     * 已确认
     */
    CONFIRMED("confirmed", "已确认"),
    /**
     * 已否决
     */
    REJECTED("rejected", "已否决");

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
    public static ResumeReviewStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ResumeReviewStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
