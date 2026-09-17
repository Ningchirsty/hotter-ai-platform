package org.dromara.ai.video.service;

import org.dromara.ai.video.domain.WorkflowVersion;

/**
 * 工作流版本表的持久化（`video_workflow_version`）。
 *
 * <p><b>定位</b>：该表是契约文件的<b>同步镜像</b>，供查询、审计与运维查看，
 * <b>不是</b>运行时权威——运行时仍以 {@code video-workflow-contracts.json} 为准并校验 SHA-256。
 * 这样既避免改动一条已实测通过的数据通路，又让表不再只是空结构。</p>
 */
public interface VideoWorkflowVersionRepository {

    /**
     * 按 (workflow_code, version) 幂等写入。
     *
     * <p>语义要点：首次插入时状态取契约状态（通常 DRAFT）；
     * 已存在时只刷新受控工件字段（路径/校验和/映射/输出规则等），
     * <b>不覆盖 status 与发布信息</b>——状态属于审核流程，不能被一次同步悄悄改写。</p>
     *
     * @return true 表示新插入，false 表示更新了已存在的行
     */
    boolean upsert(WorkflowVersion version, String checksum, boolean templateLoaded,
                   String mappingJson, String outputRuleJson, String billingJson,
                   String perfJson, String supportedOutputsJson);

    /**
     * 同步后的行数统计。
     */
    long count();
}
