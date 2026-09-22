package org.dromara.ai.image.service;

import org.dromara.ai.image.domain.ImageWorkflowVersion;

import java.util.List;

/**
 * 工作流版本镜像表（{@code image_workflow_version}）仓储。
 *
 * <p>定位与视频模块一致：该表是<b>契约文件的同步镜像</b>，供查询/审计/运维。
 * 唯一的例外是<b>审核状态</b>：{@code status / published_by / published_time / test_report_json}
 * 由人工落定，是运行时发布状态的权威；因此 upsert 的 UPDATE 子句刻意不含它们，
 * 一次同步不得把审核结果冲掉，而 {@link #findReviewStates()} 会被启动流程读回去用于提权
 * （见 {@link ImageWorkflowContractRegistry#applyReviewStates}）。</p>
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

    /**
     * 读取表内各工作流的审核状态（workflow_code / version / checksum / status）。
     *
     * <p>启动时用于把库中已审核的发布状态叠加到内存绑定上：发布状态的权威在库，不在镜像里的契约文件。</p>
     */
    List<WorkflowReviewState> findReviewStates();
}
