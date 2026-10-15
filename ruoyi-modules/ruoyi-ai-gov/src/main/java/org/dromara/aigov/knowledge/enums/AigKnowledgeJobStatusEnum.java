package org.dromara.aigov.knowledge.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识处理任务的阶段状态（一条 {@code aig_knowledge_processing_job} 的结论）。
 *
 * <p>与版本状态刻意分开：版本状态回答"这份资料现在能不能用"，任务状态回答"这一步做完没有"。
 * 合在一起会出现"版本 READY 但某个阶段 FAILED"这种无法解释的组合。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigKnowledgeJobStatusEnum {

    /** 执行中 */
    RUNNING("RUNNING", "执行中"),

    /** 本阶段成功 */
    SUCCEEDED("SUCCEEDED", "已完成"),

    /** 本阶段失败 */
    FAILED("FAILED", "已失败");

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
    public static AigKnowledgeJobStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigKnowledgeJobStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
