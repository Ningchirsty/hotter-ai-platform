package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布状态的承载对象类型（设计 §5.4 的发布状态机是三处共用的）。
 *
 * <p>与 {@code aig_release_event.target_type} 的取值一一对应。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigReleaseTargetTypeEnum {

    /**
     * Agent 版本
     */
    AGENT_VERSION("AGENT_VERSION", "Agent 版本"),

    /**
     * Skill 版本
     */
    SKILL_VERSION("SKILL_VERSION", "Skill 版本"),

    /**
     * Package 版本
     */
    PACKAGE_VERSION("PACKAGE_VERSION", "Package 版本");

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
     * @return 枚举；未命中返回 null
     */
    public static AigReleaseTargetTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigReleaseTargetTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
