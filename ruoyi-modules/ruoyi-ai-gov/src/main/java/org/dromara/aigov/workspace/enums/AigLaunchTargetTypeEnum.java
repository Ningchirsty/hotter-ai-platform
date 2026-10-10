package org.dromara.aigov.workspace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Action 的启动目标类型（附件 §4.1：一个 Action 只引用一个确定的启动目标）。
 *
 * <p><b>为什么不允许多个目标</b>：一个卡片若同时能"启动场景"又能"跳转页面"，
 * 那么它到底做了什么就取决于运行时分支——而岗位包是给人配的，
 * 配错了也不会报错。一个 Action 一个目标，配的时候就必须想清楚。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigLaunchTargetTypeEnum {

    /**
     * 场景包（ScenarioVersion）：有输入/审批/交付契约的业务流程
     */
    SCENARIO("SCENARIO", "场景包"),

    /**
     * 轻量能力（已发布的小能力，仍建议包成小场景）
     */
    QUICK_CAPABILITY("QUICK_CAPABILITY", "轻量能力"),

    /**
     * 受控 routeKey（打开既有业务模块，不启动 AI）
     */
    NAVIGATION("NAVIGATION", "受控跳转");

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
    public static AigLaunchTargetTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigLaunchTargetTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
