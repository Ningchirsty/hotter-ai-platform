package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 制品校验结论（{@code aig_task_artifact.validation_status}）。
 *
 * <p><b>为什么被拒也要入库（而不是只回一个错误码）</b>：「生产方交了一份不合格制品」
 * 是运维要看见的事实。只把错误码回给对方，库里什么都不留，事后只剩对方自己的日志可查——
 * 而当对方是外部系统（VibePoster 一类）时，我们连它到底交了什么都无法回答。
 * 因此 FAIL 行是<b>证据</b>，不是脏数据；默认查询只取 {@link #PASS}，需要排查时再带上 FAIL。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigArtifactValidationStatusEnum {

    /**
     * 通过：已计入任务的产出制品（写 {@code AI_TASK_ARTIFACT_ADDED} 事件）
     */
    PASS("PASS", "通过"),

    /**
     * 被拒：字段级原因写在 {@code validation_detail} 里，一次列全
     */
    FAIL("FAIL", "被拒");

    /**
     * 编码
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
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigArtifactValidationStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigArtifactValidationStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
