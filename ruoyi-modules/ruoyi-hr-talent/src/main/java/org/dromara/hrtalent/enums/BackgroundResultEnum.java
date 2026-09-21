package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 背景调查结果枚举。
 * <p>对应数据字典 {@code recruit_background_result}（设计文档 §10）。
 * 背调明细属高敏感数据，列表与日志均不得展示。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum BackgroundResultEnum {

    /**
     * 待背调
     */
    PENDING("pending", "待背调"),
    /**
     * 通过
     */
    PASS("pass", "通过"),
    /**
     * 未通过
     */
    FAIL("fail", "不通过"),
    /**
     * 已豁免
     */
    WAIVED("waived", "已豁免");

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
    public static BackgroundResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (BackgroundResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
