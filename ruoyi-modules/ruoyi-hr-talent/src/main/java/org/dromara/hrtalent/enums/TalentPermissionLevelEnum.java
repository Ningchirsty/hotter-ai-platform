package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才共享授权级别枚举。
 * <p>对应数据字典 {@code talent_permission_level}（设计文档 §10）。
 * 级别由低到高：摘要 &lt; 明细 &lt; 附件，比较使用 {@link #ordinal()} 顺序；
 * 获得查看权不等于获得电话、附件、背调或导出权（设计文档 §9.4）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentPermissionLevelEnum {

    /**
     * 摘要
     */
    SUMMARY("summary", "摘要"),
    /**
     * 明细
     */
    DETAIL("detail", "明细"),
    /**
     * 附件
     */
    ATTACHMENT("attachment", "含附件");

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
    public static TalentPermissionLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentPermissionLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
