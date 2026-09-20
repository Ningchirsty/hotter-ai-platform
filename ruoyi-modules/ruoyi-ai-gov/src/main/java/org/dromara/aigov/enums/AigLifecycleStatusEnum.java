package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型生命周期状态枚举
 * <p>只有试验/灰度/生产三种状态可被路由选中（{@link #callable()}）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigLifecycleStatusEnum {

    /**
     * 候选（未接入完成）
     */
    CANDIDATE("CANDIDATE", "候选", false),
    /**
     * 试验
     */
    TRIAL("TRIAL", "试验", true),
    /**
     * 灰度
     */
    GRAY("GRAY", "灰度", true),
    /**
     * 生产
     */
    PRODUCTION("PRODUCTION", "生产", true),
    /**
     * 暂停
     */
    SUSPENDED("SUSPENDED", "暂停", false),
    /**
     * 退役
     */
    RETIRED("RETIRED", "退役", false);

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 是否可被调用
     */
    private final boolean callable;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigLifecycleStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigLifecycleStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
