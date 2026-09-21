package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 录用邀约结果枚举。
 * <p>对应数据字典 {@code recruit_offer_result}，取值 {@code pending/accepted/rejected}，
 * 与 {@code hr_recruit_application.offer_result} 的稳定编码一致。</p>
 *
 * <p><b>依据</b>：{@code hr_recruit_application.offer_result} 建表注释
 * 「accepted/rejected 等稳定编码（设计文档 §7.2）」；§14「邀约接受率＝接受邀约人数 ÷ 有效邀约人数」
 * 要求结果可按稳定编码统计，故补齐本字典组，前端不再使用自由文本。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum OfferResultEnum {

    /**
     * 待反馈（已发出邀约，候选人尚未答复）
     */
    PENDING("pending", "待反馈"),
    /**
     * 已接受（候选人接受邀约）
     */
    ACCEPTED("accepted", "已接受"),
    /**
     * 已拒绝（候选人拒绝邀约）
     */
    REJECTED("rejected", "已拒绝");

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
    public static OfferResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (OfferResultEnum item : values()) {
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
     *
     * @param code 编码
     * @return 中文标签，未知编码返回 null
     */
    public static String labelOf(String code) {
        OfferResultEnum item = find(code);
        return item == null ? null : item.getDesc();
    }

}
