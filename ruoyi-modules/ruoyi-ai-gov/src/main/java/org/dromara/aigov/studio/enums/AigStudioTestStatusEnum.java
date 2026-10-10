package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 训练台一次测试的状态（专题 C §C7、§C11 ST-009/ST-010）。
 *
 * <p><b>它只描述"这次测试跑得怎么样"，不代表能力可发布</b>：测试通过 ≠ 沙箱门槛达成 ≠ 发布通过。
 * 真正的门槛判定在 {@code aig_agent_version} 的发布状态机（五道门槛）上，
 * 本状态仅用于页面展示与证据链核对。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioTestStatusEnum {

    /**
     * 待执行：已登记测试意图（钉住了 revision），尚未真正调用
     */
    PENDING("PENDING", "待执行", false),

    /**
     * 执行中
     */
    RUNNING("RUNNING", "执行中", false),

    /**
     * 已通过（终态）
     */
    SUCCEEDED("SUCCEEDED", "通过", true),

    /**
     * 已失败（终态；失败原因落 error_message，可重新发起一次测试）
     */
    FAILED("FAILED", "失败", true);

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否终态
     */
    private final boolean terminal;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigStudioTestStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigStudioTestStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
