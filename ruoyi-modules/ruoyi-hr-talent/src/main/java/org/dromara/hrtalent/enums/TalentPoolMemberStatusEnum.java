package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才池成员状态枚举。
 * <p>对应数据字典 {@code talent_pool_member_status}（设计文档 §10）。
 * 同一人才在同一人才池内唯一（{@code pool_id + talent_id} 唯一索引）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentPoolMemberStatusEnum {

    /**
     * 在池
     */
    ACTIVE("active", "在池"),
    /**
     * 已暂停
     */
    PAUSED("paused", "已暂停"),
    /**
     * 已移除
     */
    REMOVED("removed", "已移出"),
    /**
     * 已转应聘
     */
    CONVERTED("converted", "已转化");

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
    public static TalentPoolMemberStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentPoolMemberStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
