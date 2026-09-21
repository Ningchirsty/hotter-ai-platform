package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 学位枚举。
 * <p>对应数据字典 {@code talent_degree}。</p>
 * <p>设计文档 §10 未定义，本次按 hr_talent_education.degree 建表注释「字典编码」补齐。
 * 学位与学历是两列两字典，不得混用。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentDegreeEnum {

    /**
     * 无学位
     */
    NONE("none", "无"),
    /**
     * 学士
     */
    BACHELOR("bachelor", "学士"),
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
    public static TalentDegreeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentDegreeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
