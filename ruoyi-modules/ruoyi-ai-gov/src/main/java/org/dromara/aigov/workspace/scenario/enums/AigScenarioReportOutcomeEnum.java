package org.dromara.aigov.workspace.scenario.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 场景任务回执结论（业务域 → 平台；增量 16）。
 *
 * <p>封闭集合：回执是跨模块的写操作，认不出的结论一律拒绝，绝不"当成成功"。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigScenarioReportOutcomeEnum {

    /**
     * 仍在执行（仅进度，不改变平台任务状态）
     */
    RUNNING("RUNNING", "仍在执行"),

    /**
     * 已完成（平台任务收尾成功）
     */
    SUCCEEDED("SUCCEEDED", "已完成"),

    /**
     * 已失败（平台任务收尾失败，message 为可读原因）
     */
    FAILED("FAILED", "已失败");

    /**
     * 编码（入库/传输口径）
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
    public static AigScenarioReportOutcomeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigScenarioReportOutcomeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
