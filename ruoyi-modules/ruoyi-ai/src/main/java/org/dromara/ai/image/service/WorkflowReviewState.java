package org.dromara.ai.image.service;

/**
 * 一条工作流的<b>审核状态</b>（来自 {@code image_workflow_version} 镜像表）。
 *
 * <p>分工：契约文件（打进后端镜像）定义"是什么"——模板、mapping、checksum、输出规则；
 * 审核结果（是否已发布）由人工落在库里，是运行时发布状态的<b>权威</b>。</p>
 *
 * <p>为什么要把发布状态放到库里：契约被打进镜像，而"补丁式重建镜像"
 * （{@code FROM 旧镜像 + COPY 新 jar}）会把旧基座里的契约一起带回来。
 * 真实事故：图像工作流已验收发布（契约为 PUBLISHED），随后一次后端镜像重建带回旧基座的 DRAFT 契约，
 * 页面立刻退回"暂不可提交"。发布状态入库后，换任何镜像都不会改变审核结论。</p>
 *
 * @param workflowCode 工作流编码
 * @param version      审核时的版本号（新版本不继承旧版本的审核结论）
 * @param checksum     审核时的模板 SHA-256（模板原地改动过则不继承）
 * @param status       审核状态：DRAFT / TESTING / PUBLISHED / RETIRED
 */
public record WorkflowReviewState(String workflowCode, String version, String checksum, String status) {
}
