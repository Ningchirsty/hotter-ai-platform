package org.dromara.ai.image.service;

import org.dromara.ai.image.domain.ImageWorkflowVersion;

/**
 * 工作流版本镜像表（{@code image_workflow_version}）仓储。
 *
 * <p>定位与视频模块一致：该表是<b>契约文件的同步镜像</b>，供查询/审计/运维；
 * 运行时权威始终是契约文件。因此 upsert 的 UPDATE 子句刻意不含
 * {@code status / published_by / published_time} —— 一次同步不得把审核结果冲掉。</p>
 */
public interface ImageWorkflowVersionRepository {

    /**
     * 幂等写入一条工作流版本。
     *
     * @param templateLoaded 模板是否通过校验并加载（未加载时 checksum 写 NULL，不伪装成已校验）
     * @return true 表示本次是新增
     */
    boolean upsert(ImageWorkflowVersion version, String checksum, boolean templateLoaded, String mappingJson,
                   String outputRuleJson, String billingJson, String perfJson, String supportedOutputsJson);

    long count();
}
