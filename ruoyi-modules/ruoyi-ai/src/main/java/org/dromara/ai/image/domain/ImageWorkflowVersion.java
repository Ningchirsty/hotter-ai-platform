package org.dromara.ai.image.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约里的一个工作流绑定（图像创作模块）。
 *
 * @param workflowCode       工作流编码（前端唯一可见的标识）
 * @param capabilityCode     所属能力编码
 * @param modelCode          模型编码
 * @param version            版本号
 * @param status             DRAFT / TESTING / PUBLISHED / RETIRED
 * @param apiJsonFile        模板路径（相对 image.contract-root）
 * @param checksum           模板文件字节的 SHA-256
 * @param capabilityFields   该能力声明的前端字段白名单（请求体 fields 只允许出现这些键）
 * @param mapping            字段 → 节点输入白名单
 * @param outputNodeId       输出节点 ID
 * @param outputField        输出字段名（必须为 images）
 * @param outputFormat       输出格式（png）
 * @param outputMime         输出 MIME
 * @param sizePresets        size 档位标签 → {width, height}
 * @param defaultSize        默认 size 档位
 * @param supportedStrengths strength 档位标签集合（图生图）
 * @param defaultStrength    默认 strength 档位
 * @param requireAlpha       输出是否必须带 alpha 通道（抠图）
 * @param maxPixels          输出像素上限
 * @param maxSizeMb          输出体积上限（MB）
 * @param timeoutSeconds     轮询预算（秒）
 */
public record ImageWorkflowVersion(
    String workflowCode,
    String capabilityCode,
    String modelCode,
    String version,
    String status,
    String apiJsonFile,
    String checksum,
    List<String> capabilityFields,
    List<ImageWorkflowMapping> mapping,
    String outputNodeId,
    String outputField,
    String outputFormat,
    String outputMime,
    Map<String, int[]> sizePresets,
    String defaultSize,
    Set<String> supportedStrengths,
    String defaultStrength,
    boolean requireAlpha,
    int maxPixels,
    int maxSizeMb,
    int timeoutSeconds) {

    /**
     * 是否已发布（正式环境只允许发布版本提交）。
     */
    public boolean isPublished() {
        return status != null && "PUBLISHED".equalsIgnoreCase(status);
    }

    /**
     * 是否可用于隔离联调：PUBLISHED 与 TESTING 都算，DRAFT / RETIRED 不算。
     */
    public boolean isTestable() {
        return isPublished() || (status != null && "TESTING".equalsIgnoreCase(status));
    }

    /**
     * 该绑定允许的前端字段集合（mapping 的字段名），用于白名单校验。
     */
    public List<String> mappedFields() {
        return mapping == null ? List.of() : mapping.stream().map(ImageWorkflowMapping::field).toList();
    }
}
