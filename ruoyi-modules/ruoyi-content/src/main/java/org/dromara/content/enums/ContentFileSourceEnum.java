package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 附件来源角色枚举（{@code cp_task_file.source_type}）。
 *
 * <p><b>为什么单独成枚举</b>：生产实测发现产品照片、被引用的参考图、系统生成的结果图
 * 混在同一张表的同一个 {@code file_kind=IMAGE} 里，页面与出图链路都无法区分
 * 「这张图是产品本体、是风格参考、还是刚生成的候选」。角色不同，可用场景完全不同：
 * 产品图是保真基准（不该被当成风格参考），参考图是风格输入（不该被当成产品本体），
 * 生成图是产出（可以被选定、排版、终审）。枚举把口径固定下来，避免各处再写字面量。</p>
 *
 * <p>历史数据：R4 迁移把 {@code dp_generation.output_file_id} 指向的附件回填为
 * {@link #GENERATED}；其余保持 {@code UPLOAD}（不推测），由人在页面上显式「设为产品图」
 * 才变成 {@link #PRODUCT}。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentFileSourceEnum {

    /**
     * 人工上传（默认，未指定角色的原始资料）
     */
    UPLOAD("UPLOAD", "人工上传"),
    /**
     * 被引用为参考图（风格输入）
     */
    REFERENCE("REFERENCE", "参考图"),
    /**
     * 产品图（产品保真基准，回写 cp_product.product_image 的那张）
     */
    PRODUCT("PRODUCT", "产品图"),
    /**
     * 系统生成（出图产出）
     */
    GENERATED("GENERATED", "系统生成");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentFileSourceEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentFileSourceEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

}
