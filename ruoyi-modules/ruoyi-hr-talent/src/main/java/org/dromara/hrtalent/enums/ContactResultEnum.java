package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才联系结果枚举。
 * <p>对应数据字典 {@code talent_contact_result}（设计文档 §10）。
 * 联系记录属敏感操作，明细不得写入应用日志。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum ContactResultEnum {

    /**
     * 已接通
     */
    CONNECTED("connected", "已接通"),
    /**
     * 未接听
     */
    NO_ANSWER("no_answer", "未接听"),
    /**
     * 已拒绝
     */
    REFUSED("refused", "已拒绝"),
    /**
     * 有意向
     */
    INTERESTED("interested", "有意向"),
    /**
     * 稍后跟进
     */
    FOLLOW_UP_LATER("follow_up_later", "稍后跟进"),
    /**
     * 联系方式无效
     */
    INVALID("invalid", "联系方式无效");

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
    public static ContactResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContactResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
