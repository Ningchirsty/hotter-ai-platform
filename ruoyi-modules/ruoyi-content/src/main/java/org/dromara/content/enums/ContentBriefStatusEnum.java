package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 品牌 Brief 状态（{@code dp_brand_brief.status}）。
 *
 * <p>只有两态，而且**只能由确认接口推进**：草稿是"品牌方还在改"，已确认是"品牌方认了这份要求"。
 * 视觉门的闸门项「品牌 Brief 已填写并确认」判的就是这里有没有 CONFIRMED——
 * 所以保存表单不能改它（否则闸门的钥匙就交给了任何能保存的人）。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentBriefStatusEnum {

    /**
     * 草稿（已录入但品牌方还没确认）
     */
    DRAFT("DRAFT", "草稿"),

    /**
     * 品牌方已确认
     */
    CONFIRMED("CONFIRMED", "已确认");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static ContentBriefStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentBriefStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 描述（未命中返回原始编码，便于排障展示）。
     *
     * @param code 编码
     * @return 可读描述
     */
    public static String descOf(String code) {
        ContentBriefStatusEnum item = find(code);
        return item == null ? code : item.desc;
    }

}
