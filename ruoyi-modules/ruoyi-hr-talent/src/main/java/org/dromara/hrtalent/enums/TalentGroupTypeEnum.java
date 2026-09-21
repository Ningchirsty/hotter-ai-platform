package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才分组类型枚举。
 * <p>对应数据字典 {@code talent_group_type}。</p>
 * <p>设计文档 §10 未定义，本次按 hr_talent_group.group_type 建表注释与
 * §8.15「公共分组 / 个人收藏」补齐。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentGroupTypeEnum {

    /**
     * 公共分组
     */
    PUBLIC("public", "公共分组"),
    /**
     * 个人收藏
     */
    PERSONAL("personal", "个人收藏");

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
    public static TalentGroupTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentGroupTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
