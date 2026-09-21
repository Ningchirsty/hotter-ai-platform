package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 招聘形式枚举。
 * <p>对应数据字典 {@code recruit_mode}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum RecruitModeEnum {

    /**
     * 内部招聘
     */
    INTERNAL("internal", "内部招聘"),
    /**
     * 社会招聘
     */
    SOCIAL("social", "社会招聘"),
    /**
     * 校园招聘
     */
    CAMPUS("campus", "校园招聘"),
    /**
     * 猎头招聘
     */
    HEADHUNTER("headhunter", "猎头"),
    /**
     * 内部推荐
     */
    REFERRAL("referral", "内推"),
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
    public static RecruitModeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (RecruitModeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
