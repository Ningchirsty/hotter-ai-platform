package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Manifest 外网访问声明（设计 §6.1 权限、§6.2-2）。
 *
 * <p><b>为什么这个字段必须显式声明，而不是「不写就等于不需要外网」</b>：平台要把
 * 「这个包会不会把数据发到外面」当作一个可判定的属性。若缺省即视为无外网，那么
 * 一个忘了写、或故意不写的包会自动获得「内部件」的待遇，而它的实际行为没人知道；
 * 反过来，声明为 {@link #EXTERNAL} 的包必须同时给出目标主机清单——「需要外网」但
 * 说不出要连哪里，平台同样无法评估。因此二者都要求显式声明，缺失即命中
 * {@code UNDECLARED_MANDATORY_FIELD}。</p>
 *
 * <p>注意本枚举只表达<b>声明</b>：是否真的允许出网由路由策略与数据等级在运行期决定，
 * 声明为 EXTERNAL 不会自动获得外发许可（§4.2 的 {@code allow_external} 与 STRICT 硬拒绝优先）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigNetworkAccessEnum {

    /**
     * 声明不需要外网访问
     */
    NONE("NONE", "不需要外网访问"),

    /**
     * 声明需要外网访问（必须同时给出目标主机清单）
     */
    EXTERNAL("EXTERNAL", "需要外网访问");

    /**
     * 编码（入库/ Manifest 取值）
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
     * @return 枚举；未命中返回 null
     */
    public static AigNetworkAccessEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigNetworkAccessEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
