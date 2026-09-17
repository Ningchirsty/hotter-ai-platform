package org.dromara.ai.video.service;

import org.dromara.ai.video.domain.VideoTaskStatus;

import java.util.List;
import java.util.Map;

/**
 * 视频任务与素材的持久化接口。
 *
 * <p>归属隔离是硬约束：实现里每条查询都必须同时带 {@code tenant_id} 与 {@code user_id}，
 * 不依赖 MyBatis 租户拦截器。抽成接口是为了让编排逻辑能用替身做离线测试。</p>
 */
public interface VideoTaskRepository {

    /**
     * 插入素材，返回主键。
     */
    long insertAsset(AssetRow asset);

    /**
     * 按 ID 读取素材并校验归属；查不到即视为不存在（不区分"无权访问"）。
     */
    AssetRow requireOwnedAsset(long assetId, String tenantId, long userId);

    /**
     * 插入任务，返回主键。
     */
    long insertTask(TaskRow task);

    /**
     * 幂等查询：同一租户同一用户同一幂等键只应有一条任务。
     */
    Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey);

    /**
     * 按 ID 读取任务并校验归属。
     */
    Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId);

    /**
     * 分页列出本人任务。
     */
    List<Map<String, Object>> listOwnedTasks(String tenantId, long userId, String status,
                                             int offset, int limit);

    /**
     * 统计本人任务数。
     */
    long countOwnedTasks(String tenantId, long userId, String status);

    /**
     * 列表本人素材。
     */
    List<Map<String, Object>> listOwnedAssets(String tenantId, long userId, int offset, int limit);

    /**
     * 统计本人素材数。
     */
    long countOwnedAssets(String tenantId, long userId);

    /**
     * 软删本人素材。
     */
    int softDeleteAsset(long assetId, String tenantId, long userId);

    /**
     * 状态流转（带状态机守卫）。
     *
     * @return 影响行数，0 表示状态已被其他执行者改变
     */
    int transition(long taskId, VideoTaskStatus expectedFrom, VideoTaskStatus target,
                   String errorCode, String errorMessage);

    /**
     * 把「还没进终态」的任务置为失败，不要求已知起始状态。
     *
     * <p>为什么需要它：{@link #transition} 必须给出期望的起始状态。任务一旦
     * {@code markSubmitted} 进入 RUNNING，后续任何一步失败（下载成片、落盘、
     * ffprobe、分辨率断言、写素材行）如果用 {@code QUEUED→FAILED} 去落库，
     * WHERE 不匹配、影响 0 行，失败被静默吞掉——任务永远停在 RUNNING，
     * 用户既拿不到成片也看不到原因。</p>
     *
     * @return 影响行数，0 表示任务已是终态（不该被覆盖）
     */
    int markFailedIfActive(long taskId, String errorCode, String errorMessage);

    /**
     * 启动时收敛上一个进程遗留的 RUNNING 任务。
     *
     * <p>执行线程活在请求线程里，进程退出后不会再有人推进这些任务。</p>
     *
     * @return 影响行数
     */
    int failAllRunning(String errorCode, String errorMessage);

    /**
     * 记录已提交 ComfyUI。
     */
    int markSubmitted(long taskId, String comfyPromptId, int attemptCount);

    /**
     * 记录成片归档与实测指标。
     */
    int markSucceeded(long taskId, long outputAssetId, long coverAssetId,
                      Integer width, Integer height, Double fps, Long durationMillis,
                      boolean truncated);

    /**
     * 追加事件。
     */
    void appendEvent(long id, long taskId, String tenantId, int sequence,
                     String eventType, String detail);

    /**
     * 读取任务事件序列。
     */
    List<Map<String, Object>> listEvents(long taskId, String tenantId);

    /**
     * 取消排队中的任务。
     */
    int cancelQueued(long taskId, String tenantId, long userId);

    /**
     * 素材行。
     */
    record AssetRow(Long id, String tenantId, Long userId, Long taskId, String assetType,
                    String sourceKind, String originalName, String storageKey,
                    String contentType, Long sizeBytes, String checksum,
                    Integer width, Integer height, Long durationMillis, Long createDept) {
    }

    /**
     * 任务行。
     */
    record TaskRow(Long id, String tenantId, Long userId, String taskNo, String taskName,
                   String capabilityCode, String workflowCode, String workflowVersion,
                   String modelCode, String status, String tier, Integer durationSeconds,
                   String prompt, String inputJson, String idempotencyKey, Long createDept) {
    }
}
