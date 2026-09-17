package org.dromara.ai.video.domain;

import java.util.List;

/**
 * 一个可提交的工作流版本（契约中的一个 binding）。
 *
 * @param workflowCode      工作流编码，如 wf-t2v-h3
 * @param capabilityCode    能力编码
 * @param modelCode         底模编码
 * @param version           版本号
 * @param status            DRAFT / TESTING / PUBLISHED / RETIRED
 * @param apiJsonFile       后端受控目录下 API Format JSON 的相对路径
 * @param checksum          模板文件 SHA-256，加载时必须校验
 * @param mapping           字段映射白名单
 * @param fixedFieldValidation 固定值校验（当前 tier/dur 只接受单一档位）
 * @param maxDurationSeconds 成片时长上限（秒）；超出必须在服务端截断
 * @param outputNodeId      输出节点 ID
 * @param outputField       输出字段名
 */
public record WorkflowVersion(
    String workflowCode,
    String capabilityCode,
    String modelCode,
    String version,
    String status,
    String apiJsonFile,
    String checksum,
    List<WorkflowMapping> mapping,
    FixedFieldValidation fixedFieldValidation,
    Integer maxDurationSeconds,
    String outputNodeId,
    String outputField
) {

    /**
     * 仅 PUBLISHED 可用于正式环境提交；TESTING 只允许在受控联调环境使用。
     */
    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }

    /**
     * 是否可在联调环境提交。
     */
    public boolean isTestable() {
        return isPublished() || "TESTING".equalsIgnoreCase(status);
    }

    /**
     * 契约中声明的固定字段取值。
     *
     * @param tier 固定输出档位
     * @param dur  固定时长档位
     */
    public record FixedFieldValidation(String tier, String dur) {
    }
}
