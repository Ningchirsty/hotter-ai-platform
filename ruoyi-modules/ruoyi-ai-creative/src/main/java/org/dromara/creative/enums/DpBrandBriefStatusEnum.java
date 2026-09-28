package org.dromara.creative.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 品牌 Brief 状态（{@code dp_brand_brief.status}）。
 *
 * <p><b>为什么状态只能由 confirm 接口推进</b>：闸门项「品牌 Brief 已填写并确认」的判据就是这个值。
 * 如果 PUT 保存时允许前端直接传 status，那么「填了一半的草稿」和「品牌方已确认的要求」
 * 在库里长得一模一样，闸门立刻失去意义。</p>
 *
 * @author creative
 */
@Getter
@AllArgsConstructor
public enum DpBrandBriefStatusEnum {

    /**
     * 草稿（品牌方还没确认）
     */
    DRAFT("DRAFT", "草稿"),
    /**
     * 已确认（品牌方确认过，可作为出图约束与闸门依据）
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
    public static DpBrandBriefStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DpBrandBriefStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 描述（未命中返回原始编码）。
     *
     * @param code 编码
     * @return 可读描述
     */
    public static String descOf(String code) {
        DpBrandBriefStatusEnum item = find(code);
        return item == null ? code : item.desc;
    }

}
