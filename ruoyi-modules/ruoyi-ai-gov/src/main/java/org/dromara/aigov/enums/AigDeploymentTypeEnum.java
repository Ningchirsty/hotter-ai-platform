package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 部署类型枚举
 * <p>{@link #external()} 为 true 表示数据会离开本地环境，
 * 受路由策略 {@code allow_external='N'} 约束并在审计中标记外发。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigDeploymentTypeEnum {

    /**
     * 本地私有部署
     */
    LOCAL("LOCAL", "本地私有", false),
    /**
     * 集团共享部署
     */
    GROUP("GROUP", "集团共享", false),
    /**
     * 外部企业服务（私有化托管）
     */
    EXTERNAL_ENTERPRISE("EXTERNAL_ENTERPRISE", "外部企业服务", true),
    /**
     * 外部 API
     */
    EXTERNAL_API("EXTERNAL_API", "外部API", true);

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 是否属于外部调用（数据外发）
     */
    private final boolean external;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigDeploymentTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigDeploymentTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
