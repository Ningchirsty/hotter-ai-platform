package org.dromara.ai.image.service;

import org.dromara.ai.image.domain.ImageTaskStatus;

import java.util.List;
import java.util.Map;

/**
 * 图像任务持久化契约。
 *
 * <p><b>归属隔离是硬约束</b>：每一条查询都必须带 {@code tenant_id + user_id}，
 * 不依赖 MyBatis 租户拦截器（视频模块同款做法，避免拦截器配置漂移造成越权）。</p>
 */
public interface ImageTaskRepository {

    /**
     * 插入素材，返回影响行数。
     */
    long insertAsset(AssetRow row);

    /**
     * 按归属读取素材；不存在或不属于该用户时抛 404 语义的业务异常。
     */
    AssetRow requireOwnedAsset(long assetId, String tenantId, long userId);

    /**
     * 插入任务。
     */
    long insertTask(TaskRow row);

    /**
     * 幂等键查询，未命中返回 null。
     */
    Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey);

    /**
     * 按归属读取任务原始行（列名保持 snake_case，由控制器转 camelCase 下发）。
     */
    Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId);

    List<Map<String, Object>> listOwnedTasks(String tenantId, long userId, String status, int offset, int limit);

    long countOwnedTasks(String tenantId, long userId, String status);

    List<Map<String, Object>> listOwnedAssets(String tenantId, long userId, int offset, int limit);

    long countOwnedAssets(String tenantId, long userId);

    int softDeleteAsset(long assetId, String tenantId, long userId);

    /**
     * 带前置状态的状态流转（0 行表示前置状态不符，属于正常的并发保护）。
     */
    int transition(long taskId, ImageTaskStatus expectedFrom, ImageTaskStatus target,
                   String errorCode, String errorMessage);

    /**
     * 把非终态任务置为失败（执行失败的唯一落库点）。
     */
    int markFailedIfActive(long taskId, String errorCode, String errorMessage);

    /**
     * 启动收敛：把上次进程遗留的 RUNNING 任务置为失败。
     */
    int failAllRunning(String errorCode, String errorMessage);

    /**
     * 记录已提交 ComfyUI。
     */
    int markSubmitted(long taskId, String promptId, int attemptCount, String worker);

    /**
     * 标记成功并写入实测尺寸。
     */
    int markSucceeded(long taskId, long outputAssetId, Integer width, Integer height,
                      boolean hasAlpha, long sizeBytes);

    /**
     * 追加任务事件（失败只告警，不影响主流程）。
     */
    void appendEvent(long eventId, long taskId, String tenantId, int sequence, String eventType, String detail);

    List<Map<String, Object>> listEvents(long taskId, String tenantId);

    /**
     * 取消排队中的任务（仅 QUEUED 可取消）。
     */
    int cancelQueued(long taskId, String tenantId, long userId);

    /**
     * 素材行。
     */
    record AssetRow(long id, String tenantId, long userId, Long taskId, String assetType, String sourceKind,
                    String originalName, String storageKey, String contentType, long sizeBytes, String checksum,
                    Integer width, Integer height, Boolean hasAlpha, Long deptId) {
    }

    /**
     * 任务行。
     */
    record TaskRow(long id, String tenantId, long userId, String taskNo, String taskName, String capabilityCode,
                   String workflowCode, String workflowVersion, String modelCode, String status, String sizeLabel,
                   String strengthLabel, String prompt, String negativePrompt, String inputJson,
                   String idempotencyKey, Long deptId) {
    }
}
