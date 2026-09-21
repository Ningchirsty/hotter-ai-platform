package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 学历枚举。
 * <p>对应数据字典 {@code talent_education}。</p>
 * <p>设计文档 §10 未定义，本次按 hr_talent_education.education /
 * hr_talent_profile.highest_education 建表注释「字典编码」补齐。
 * 学历与学位是两列两字典，不得混用。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum EducationLevelEnum {

    /**
     * 高中
     */
    HIGH_SCHOOL("high_school", "高中"),
    /**
     * 大专
     */
    COLLEGE("college", "大专"),
    /**
     * 本科
     */
    BACHELOR("bachelor", "本科"),
    /**
     * 硕士
     */
    MASTER("master", "硕士"),
    /**
     * 博士
     */
    DOCTOR("doctor", "博士"),
    /**
     * 其他
     */
    OTHER("other", "其他");

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
    public static EducationLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (EducationLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
