package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 背调记录状态枚举。
 * <p>对应数据字典 {@code recruit_background_status}，取值 {@code draft/checking/finished/cancelled}，
 * 与 {@code hr_recruit_background.status} 的稳定编码一致（SPEC-P3 §3.4、设计文档 §7.4）。</p>
 *
 * <p><b>编码复用说明</b>：{@code hr_recruit_background} 没有 {@code current_flag} 列，
 * 也没有 {@code superseded}（被替代）编码，因此「同一应聘记录出现新背调时旧记录失效」
 * 只能复用 {@link #CANCELLED} 表达；该语义无法与「人工取消背调」区分，
 * 已在交付说明中作为 DDL 缺口上报主控，<b>本模块不自行加列或造字典</b>。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum BackgroundStatusEnum {

    /**
     * 草稿（已登记但尚未开始核查）
     */
    DRAFT("draft", "草稿"),
    /**
     * 核查中
     */
    CHECKING("checking", "核查中"),
    /**
     * 已完成（已回执结论）
     */
    FINISHED("finished", "已完成"),
    /**
     * 已取消（含因被新背调替代而失效）
     */
    CANCELLED("cancelled", "已取消");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，仅用于提示与兜底展示）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static BackgroundStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (BackgroundStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断编码是否合法。
     *
     * @param code 编码
     * @return 合法返回 true
     */
    public static boolean isValid(String code) {
        return find(code) != null;
    }

    /**
     * 取编码对应的中文标签。
     * <p>设计文档 §10 未为背调状态定义字典组，故由本枚举兜底转换，保证页面始终有中文可展示。</p>
     *
     * @param code 编码
     * @return 中文标签，未知编码返回 null
     */
    public static String labelOf(String code) {
        BackgroundStatusEnum item = find(code);
        return item == null ? null : item.getDesc();
    }

}
