package org.dromara.aigov.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行用途（设计 §6.6「通过 {@code executionPurpose} 区分 TRAINING、EVALUATION、CANARY、PRODUCTION」）。
 *
 * <p><b>为什么必须显式区分</b>：同样一次模型调用，"开发时试着跑一下"和"灰度里真实的一次使用"
 * 在统计上完全不同。测试与培训对话若计入灰度，会用测试数据把版本的关键指标抬上去——
 * 那是拿测试伪造发布证据。因此用途要成为调用的一条显式属性，而不是靠"从哪个页面发起"去猜。</p>
 *
 * <p>{@link #countsAsCanary()} 是这条规则的唯一实现点：只有 {@link #CANARY} 计入灰度统计。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigExecutionPurposeEnum {

    /**
     * 训练台测试 / 培训助手对话（不计入灰度）
     */
    TRAINING("TRAINING", "训练台测试与助手对话", false),

    /**
     * 正式评测（黄金用例，不计入灰度）
     */
    EVALUATION("EVALUATION", "正式评测运行", false),

    /**
     * 灰度真实使用（<b>唯一</b>计入灰度统计的用途）
     */
    CANARY("CANARY", "灰度真实使用", true),

    /**
     * 正式生产使用
     */
    PRODUCTION("PRODUCTION", "正式生产使用", false);

    /**
     * 编码（入库与日志口径）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否计入灰度统计
     */
    private final boolean countsAsCanary;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigExecutionPurposeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigExecutionPurposeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
