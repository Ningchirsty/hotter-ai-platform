package org.dromara.ai.video.service;

import lombok.RequiredArgsConstructor;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.exception.VideoTaskException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * {@link VideoTaskRepository} 的 JDBC 实现。
 *
 * <p>每条查询都显式带 {@code tenant_id} 与 {@code user_id}，改动时须保持成对出现；
 * 少写任意一个都会造成跨租户或跨用户越权。</p>
 */
@Repository
@RequiredArgsConstructor
public class JdbcVideoTaskRepository implements VideoTaskRepository {

    private final JdbcTemplate jdbc;

    @Override
    public long insertAsset(AssetRow asset) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO video_asset
                  (id, tenant_id, user_id, task_id, asset_type, source_kind, original_name,
                   storage_key, content_type, size_bytes, checksum, width, height, duration_ms,
                   create_dept, create_by, create_time, del_flag)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), '0')
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, asset.id());
            ps.setString(2, asset.tenantId());
            ps.setLong(3, asset.userId());
            if (asset.taskId() == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, asset.taskId());
            }
            ps.setString(5, asset.assetType());
            ps.setString(6, asset.sourceKind());
            ps.setString(7, asset.originalName());
            ps.setString(8, asset.storageKey());
            ps.setString(9, asset.contentType());
            ps.setObject(10, asset.sizeBytes());
            ps.setString(11, asset.checksum());
            ps.setObject(12, asset.width());
            ps.setObject(13, asset.height());
            ps.setObject(14, asset.durationMillis());
            ps.setObject(15, asset.createDept());
            ps.setObject(16, asset.userId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    @Override
    public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
        List<AssetRow> rows = jdbc.query("""
            SELECT id, tenant_id, user_id, task_id, asset_type, source_kind, original_name,
                   storage_key, content_type, size_bytes, checksum
            FROM video_asset
            WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """,
            (rs, rowNum) -> new AssetRow(
                rs.getLong("id"), rs.getString("tenant_id"), rs.getLong("user_id"),
                (Long) rs.getObject("task_id"), rs.getString("asset_type"),
                rs.getString("source_kind"), rs.getString("original_name"),
                rs.getString("storage_key"), rs.getString("content_type"),
                (Long) rs.getObject("size_bytes"), rs.getString("checksum"),
                null, null, null, null),
            assetId, tenantId, userId);
        if (rows.isEmpty()) {
            throw VideoTaskException.assetNotFound("素材不存在或无权访问");
        }
        return rows.get(0);
    }

    @Override
    public long insertTask(TaskRow task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO video_task
                  (id, tenant_id, user_id, task_no, task_name, capability_code, workflow_code,
                   workflow_version, model_code, status, tier, duration_seconds, prompt, input_json,
                   progress, attempt_count, truncation_applied, idempotency_key,
                   create_dept, create_by, create_time, update_time, del_flag)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, ?, NOW(), NOW(), '0')
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, task.id());
            ps.setString(2, task.tenantId());
            ps.setLong(3, task.userId());
            ps.setString(4, task.taskNo());
            ps.setString(5, task.taskName());
            ps.setString(6, task.capabilityCode());
            ps.setString(7, task.workflowCode());
            ps.setString(8, task.workflowVersion());
            ps.setString(9, task.modelCode());
            ps.setString(10, task.status());
            ps.setString(11, task.tier());
            ps.setObject(12, task.durationSeconds());
            ps.setString(13, task.prompt());
            ps.setString(14, task.inputJson());
            ps.setString(15, task.idempotencyKey());
            ps.setObject(16, task.createDept());
            ps.setObject(17, task.userId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    @Override
    public Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("""
            SELECT id FROM video_task
            WHERE tenant_id = ? AND user_id = ? AND idempotency_key = ? AND del_flag = '0'
            """, Long.class, tenantId, userId, idempotencyKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, tenant_id, user_id, task_no, task_name, capability_code, workflow_code,
                   workflow_version, model_code, status, tier, duration_seconds, prompt,
                   input_json, comfy_prompt_id, output_asset_id, cover_asset_id, progress,
                   error_code, error_message, attempt_count, output_width, output_height,
                   output_fps, output_duration_ms, truncation_applied, create_time, finished_time
            FROM video_task
            WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, taskId, tenantId, userId);
        if (rows.isEmpty()) {
            throw new VideoTaskException("TASK_NOT_FOUND", "任务不存在或无权访问");
        }
        return rows.get(0);
    }

    @Override
    public List<Map<String, Object>> listOwnedTasks(String tenantId, long userId, String status,
                                                    int offset, int limit) {
        if (status == null || status.isBlank()) {
            return jdbc.queryForList("""
                SELECT id, task_no, task_name, capability_code, workflow_code, model_code, status,
                       tier, duration_seconds, progress, output_asset_id, error_message,
                       create_time, finished_time
                FROM video_task
                WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, tenantId, userId, limit, offset);
        }
        return jdbc.queryForList("""
            SELECT id, task_no, task_name, capability_code, workflow_code, model_code, status,
                   tier, duration_seconds, progress, output_asset_id, error_message,
                   create_time, finished_time
            FROM video_task
            WHERE tenant_id = ? AND user_id = ? AND status = ? AND del_flag = '0'
            ORDER BY id DESC LIMIT ? OFFSET ?
            """, tenantId, userId, status, limit, offset);
    }

    @Override
    public long countOwnedTasks(String tenantId, long userId, String status) {
        Long count = (status == null || status.isBlank())
            ? jdbc.queryForObject("""
                SELECT COUNT(*) FROM video_task
                WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'
                """, Long.class, tenantId, userId)
            : jdbc.queryForObject("""
                SELECT COUNT(*) FROM video_task
                WHERE tenant_id = ? AND user_id = ? AND status = ? AND del_flag = '0'
                """, Long.class, tenantId, userId, status);
        return count == null ? 0L : count;
    }

    @Override
    public List<Map<String, Object>> listOwnedAssets(String tenantId, long userId, int offset, int limit) {
        return jdbc.queryForList("""
            SELECT id, asset_type, source_kind, original_name, content_type, size_bytes,
                   width, height, duration_ms, task_id, create_time
            FROM video_asset
            WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'
            ORDER BY id DESC LIMIT ? OFFSET ?
            """, tenantId, userId, limit, offset);
    }

    @Override
    public long countOwnedAssets(String tenantId, long userId) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM video_asset
            WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, Long.class, tenantId, userId);
        return count == null ? 0L : count;
    }

    @Override
    public int softDeleteAsset(long assetId, String tenantId, long userId) {
        return jdbc.update("""
            UPDATE video_asset SET del_flag = '2', update_time = NOW()
            WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, assetId, tenantId, userId);
    }

    @Override
    public int transition(long taskId, VideoTaskStatus expectedFrom, VideoTaskStatus target,
                          String errorCode, String errorMessage) {
        return jdbc.update("""
            UPDATE video_task
            SET status = ?, error_code = ?, error_message = ?, update_time = NOW(),
                finished_time = CASE WHEN ? = 1 THEN NOW() ELSE finished_time END
            WHERE id = ? AND status = ?
            """, target.name(), errorCode, errorMessage,
            target.isTerminal() ? 1 : 0, taskId, expectedFrom.name());
    }

    @Override
    public int markSubmitted(long taskId, String comfyPromptId, int attemptCount) {
        return jdbc.update("""
            UPDATE video_task
            SET comfy_prompt_id = ?, attempt_count = ?, status = ?, submitted_time = NOW(),
                started_time = NOW(), update_time = NOW()
            WHERE id = ? AND status = ?
            """, comfyPromptId, attemptCount, VideoTaskStatus.RUNNING.name(),
            taskId, VideoTaskStatus.QUEUED.name());
    }

    @Override
    public int markSucceeded(long taskId, long outputAssetId, long coverAssetId,
                             Integer width, Integer height, Double fps, Long durationMillis,
                             boolean truncated) {
        return jdbc.update("""
            UPDATE video_task
            SET status = ?, output_asset_id = ?, cover_asset_id = ?, progress = 100,
                output_width = ?, output_height = ?, output_fps = ?, output_duration_ms = ?,
                truncation_applied = ?, finished_time = NOW(), update_time = NOW()
            WHERE id = ? AND status = ?
            """, VideoTaskStatus.SUCCEEDED.name(), outputAssetId, coverAssetId,
            width, height, fps, durationMillis, truncated ? 1 : 0,
            taskId, VideoTaskStatus.RUNNING.name());
    }

    @Override
    public void appendEvent(long id, long taskId, String tenantId, int sequence,
                            String eventType, String detail) {
        jdbc.update("""
            INSERT INTO video_task_event (id, task_id, tenant_id, sequence, event_type, detail, create_time)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """, id, taskId, tenantId, sequence, eventType, detail);
    }

    @Override
    public List<Map<String, Object>> listEvents(long taskId, String tenantId) {
        return jdbc.queryForList("""
            SELECT sequence, event_type, detail, create_time
            FROM video_task_event
            WHERE task_id = ? AND tenant_id = ?
            ORDER BY sequence ASC
            """, taskId, tenantId);
    }

    @Override
    public int cancelQueued(long taskId, String tenantId, long userId) {
        return jdbc.update("""
            UPDATE video_task
            SET status = ?, finished_time = NOW(), update_time = NOW()
            WHERE id = ? AND tenant_id = ? AND user_id = ? AND status = ? AND del_flag = '0'
            """, VideoTaskStatus.CANCELED.name(), taskId, tenantId, userId,
            VideoTaskStatus.QUEUED.name());
    }
}
