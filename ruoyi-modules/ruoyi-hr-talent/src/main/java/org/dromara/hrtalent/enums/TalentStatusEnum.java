package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才生命周期状态枚举。
 * <p>对应数据字典 {@code talent_status}（设计文档 §10）。
 * {@code merged} 为被合并主档的终态，列表查询一律排除。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentStatusEnum {

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),
    /**
     * 有效
     */
    ACTIVE("active", "生效"),
    /**
     * 招聘中
     */
    RECRUITING("recruiting", "招聘中"),
    /**
     * 储备
     */
    RESERVED("reserved", "储备"),
    /**
     * 已入职
     */
    HIRED("hired", "已入职"),
    /**
     * 禁止联系
     */
    DO_NOT_CONTACT("do_not_contact", "请勿联系"),
    /**
     * 受限
     */
    RESTRICTED("restricted", "受限"),
    /**
     * 已归档
     */
    ARCHIVED("archived", "已归档"),
    /**
     * 已合并
     */
    MERGED("merged", "已合并");

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
    public static TalentStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
