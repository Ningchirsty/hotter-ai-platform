package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布门槛（设计 §5.4、§6.3）。
 *
 * <p><b>为什么要把「门槛」做成独立枚举，而不是在服务里 if 一堆</b>：设计 §5.4 写的是
 * 「只有通过 Manifest 校验、沙箱运行、黄金用例、人工批准和小范围灰度的版本可进入 STABLE」。
 * 这句话是一条<b>顺序且必需</b>的约束：跳过沙箱直接发布、或没有人工批准就进 STABLE，
 * 都不会报错，只会安静地放出一个没验过的版本。把门槛枚举化之后，
 * 状态机可以在「缺哪个门槛」这一层就把关（见 {@code AigReleaseStateMachine}），
 * 调用方也只能按门槛推进，没有「直接置 STABLE」的入口。</p>
 *
 * <p>{@code basis} 记录该门槛对应的设计条款，便于评审时逐条对齐，而不是靠印象引用。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigReleaseGateEnum {

    /**
     * Manifest / Schema 校验
     */
    MANIFEST_VALIDATION("MANIFEST_VALIDATION", "Manifest 与输入输出 Schema 校验通过", "§5.4、§6.3-2"),

    /**
     * 沙箱运行
     */
    SANDBOX_RUN("SANDBOX_RUN", "在沙箱项目跑通（未触碰正式资产）", "§6.3-4"),

    /**
     * 黄金用例
     */
    GOLDEN_CASE("GOLDEN_CASE", "黄金用例通过（含成本范围）", "§5.4、§13.2"),

    /**
     * 人工批准
     */
    HUMAN_APPROVAL("HUMAN_APPROVAL", "三方人工批准（业务 Owner / AI 管理员 / 平台管理员）", "§5.4、§6.3-5"),

    /**
     * 灰度达标
     */
    CANARY("CANARY", "小范围灰度达标", "§5.4、§6.3-7");

    /**
     * 编码（入库与日志口径）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 设计依据（便于评审逐条对齐）
     */
    private final String basis;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigReleaseGateEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigReleaseGateEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
