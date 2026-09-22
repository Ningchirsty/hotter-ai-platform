package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 成品一致性检查状态枚举。
 *
 * <p>与 {@link ContentAsyncJobStatusEnum} 分开：作业状态描述「这次调度跑没跑完」，
 * 检查状态描述「这次比对有没有结果」。二者会不一致——例如作业 SUCCESS 但模型输出
 * 不符合输出模板时，作业是成功的（流程跑通了），检查是 FAILED（没拿到结论）。
 * 合成一个字段就再也区分不出「流程坏了」和「结论没拿到」。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentCheckStatusEnum {

    /**
     * 待检查（已登记，尚未执行）
     */
    PENDING("PENDING", "待检查"),
    /**
     * 检查中
     */
    RUNNING("RUNNING", "检查中"),
    /**
     * 已完成（无论结论是通过还是不通过，都算拿到了结论）
     */
    DONE("DONE", "已完成"),
    /**
     * 失败（没拿到结论，需看 failureReason）
     */
    FAILED("FAILED", "失败");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentCheckStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentCheckStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
