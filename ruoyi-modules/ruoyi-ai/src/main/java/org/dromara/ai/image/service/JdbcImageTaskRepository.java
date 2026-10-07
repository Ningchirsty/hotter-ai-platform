package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.exception.ImageTaskException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图像任务的 JDBC 实现。
 *
 * <p>与视频模块一致，全部走显式 SQL：不依赖 MyBatis 租户拦截器，归属条件写在每一条
 * WHERE 里。主键由调用方生成（雪花 ID），因为表结构里 {@code id} 是 NOT NULL 且非自增。</p>
 */
@Slf4j
@Repository
public class JdbcImageTaskRepository implements ImageTaskRepository {

    private static final String TERMINAL_PLACEHOLDERS = "?,?,?,?";

    private final JdbcTemplate jdbc;

    public JdbcImageTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insertAsset(AssetRow row) {
        return jdbc.update("""
            INSERT INTO image_asset (id, tenant_id, user_id, task_id, asset_type, source_kind, original_name,
                                     storage_key, content_type, size_bytes, checksum, width, height, has_alpha,
                                     create_dept, create_by, create_time, del_flag)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),'0')
            """,
            row.id(), row.tenantId(), row.userId(), row.taskId(), row.assetType(), row.sourceKind(),
            row.originalName(), row.storageKey(), row.contentType(), row.sizeBytes(), row.checksum(),
            row.width(), row.height(), row.hasAlpha() != null && row.hasAlpha() ? 1 : 0,
            row.deptId(), row.userId());
    }

    @Override
    public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, tenant_id, user_id, task_id, asset_type, source_kind, original_name, storage_key,
                   content_type, size_bytes, checksum, width, height, has_alpha
              FROM image_asset
             WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, assetId, tenantId, userId);
        if (rows.isEmpty()) {
            throw ImageTaskException.assetNotFound("素材不存在或无权访问");
        }
        return toAssetRow(rows.get(0));
    }

    /**
     * 把一行查询结果映射成 {@link AssetRow}（包级可见，便于离线单测直接覆盖列类型映射）。
     */
    static AssetRow toAssetRow(Map<String, Object> row) {
        return new AssetRow(
            ((Number) row.get("id")).longValue(),
            (String) row.get("tenant_id"),
            ((Number) row.get("user_id")).longValue(),
            row.get("task_id") == null ? null : ((Number) row.get("task_id")).longValue(),
            (String) row.get("asset_type"),
            (String) row.get("source_kind"),
            (String) row.get("original_name"),
            (String) row.get("storage_key"),
            (String) row.get("content_type"),
            row.get("size_bytes") == null ? 0L : ((Number) row.get("size_bytes")).longValue(),
            (String) row.get("checksum"),
            row.get("width") == null ? null : ((Number) row.get("width")).intValue(),
            row.get("height") == null ? null : ((Number) row.get("height")).intValue(),
            row.get("has_alpha") == null ? null : tinyIntToBoolean(row.get("has_alpha")),
            null);
    }

    /**
     * 把 {@code TINYINT(1)} 列的值转成 Boolean，**不能假设它是 Number**。
     *
     * <p>MySQL Connector/J 默认 {@code tinyInt1isBit=true}，会把 {@code TINYINT(1)} 直接映射为
     * {@code java.lang.Boolean}。本类曾经写成 {@code ((Number) row.get("has_alpha")).intValue() == 1}，
     * 结果隔离联调时素材下载/缩略图接口一律 500：
     * {@code ClassCastException: class java.lang.Boolean cannot be cast to class java.lang.Number}。
     * 上传与列表接口不受影响（前者不读该列、后者直接把原始行交给 camelCase 转换），
     * 所以离线单测与「只上传不下载」的用例都发现不了。</p>
     */
    static Boolean tinyIntToBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        // 注意：Boolean.parseBoolean("1") 返回 false，所以不能直接用它兜底
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    @Override
    public long insertTask(TaskRow row) {
        return jdbc.update("""
            INSERT INTO image_task (id, tenant_id, user_id, task_no, task_name, capability_code, workflow_code,
                                    workflow_version, model_code, status, size_label, strength_label, prompt,
                                    negative_prompt, input_json, idempotency_key, progress, attempt_count,
                                    create_dept, create_by, create_time, update_time, del_flag)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,?,?,NOW(),NOW(),'0')
            """,
            row.id(), row.tenantId(), row.userId(), row.taskNo(), row.taskName(), row.capabilityCode(),
            row.workflowCode(), row.workflowVersion(), row.modelCode(), row.status(), row.sizeLabel(),
            row.strengthLabel(), row.prompt(), row.negativePrompt(), row.inputJson(), row.idempotencyKey(),
            row.deptId(), row.userId());
    }

    @Override
    public Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey) {
        List<Long> ids = jdbc.queryForList("""
            SELECT id FROM image_task
             WHERE tenant_id = ? AND user_id = ? AND idempotency_key = ? AND del_flag = '0'
            """, Long.class, tenantId, userId, idempotencyKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(taskSelect() + """
             WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, taskId, tenantId, userId);
        if (rows.isEmpty()) {
            throw new ImageTaskException("TASK_NOT_FOUND", "任务不存在或无权访问");
        }
        return rows.get(0);
    }

    @Override
    public List<Map<String, Object>> listOwnedTasks(String tenantId, long userId, String status, int offset, int limit) {
        return listOwnedTasks(tenantId, userId, status, null, offset, limit);
    }

    @Override
    public long countOwnedTasks(String tenantId, long userId, String status) {
        return countOwnedTasks(tenantId, userId, status, null);
    }

    @Override
    public List<Map<String, Object>> listOwnedTasks(String tenantId, long userId, String status,
                                                  String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder(taskSelect());
        List<Object> args = new ArrayList<>();
        appendTaskFilters(sql, args, tenantId, userId, status, keyword);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Override
    public long countOwnedTasks(String tenantId, long userId, String status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM image_task");
        List<Object> args = new ArrayList<>();
        appendTaskFilters(sql, args, tenantId, userId, status, keyword);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static void appendTaskFilters(StringBuilder sql, List<Object> args, String tenantId,
                                          long userId, String status, String keyword) {
        sql.append(" WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'");
        args.add(tenantId);
        args.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOCATE(LOWER(?), LOWER(task_no)) > 0 OR LOCATE(LOWER(?), LOWER(task_name)) > 0)");
            args.add(keyword.trim());
            args.add(keyword.trim());
        }
    }

    @Override
    public List<Map<String, Object>> listOwnedAssets(String tenantId, long userId, int offset, int limit) {
        return jdbc.queryForList("""
            SELECT id, asset_type, source_kind, original_name, content_type, size_bytes, width, height, has_alpha,
                   task_id, create_time
              FROM image_asset
             WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'
             ORDER BY id DESC LIMIT ? OFFSET ?
            """, tenantId, userId, limit, offset);
    }

    @Override
    public long countOwnedAssets(String tenantId, long userId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM image_asset WHERE tenant_id = ? AND user_id = ? AND del_flag = '0'",
            Long.class, tenantId, userId);
        return count == null ? 0 : count;
    }

    @Override
    public int softDeleteAsset(long assetId, String tenantId, long userId) {
        return jdbc.update("""
            UPDATE image_asset SET del_flag = '2', update_time = NOW()
             WHERE id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'
            """, assetId, tenantId, userId);
    }

    @Override
    public int transition(long taskId, ImageTaskStatus expectedFrom, ImageTaskStatus target,
                          String errorCode, String errorMessage) {
        boolean terminal = target.isTerminal();
        return jdbc.update("""
            UPDATE image_task
               SET status = ?, error_code = ?, error_message = ?, update_time = NOW(),
                   finished_time = CASE WHEN ? = 1 THEN NOW() ELSE finished_time END
             WHERE id = ? AND status = ?
            """, target.name(), errorCode, errorMessage, terminal ? 1 : 0, taskId, expectedFrom.name());
    }

    @Override
    public int reopen(long taskId, String tenantId, long userId, ImageTaskStatus expectedFrom) {
        if (!expectedFrom.isTerminal() || expectedFrom == ImageTaskStatus.SUCCEEDED) {
            return 0;
        }
        return jdbc.update("""
            UPDATE image_task
               SET status = 'QUEUED', error_code = NULL, error_message = NULL, progress = 0,
                   finished_time = NULL, started_time = NULL, submitted_time = NULL,
                   comfy_prompt_id = NULL, comfy_worker = NULL, output_asset_id = NULL,
                   cover_asset_id = NULL, output_width = NULL, output_height = NULL,
                   output_has_alpha = 0, output_size_bytes = NULL, update_time = NOW()
             WHERE id = ? AND tenant_id = ? AND user_id = ? AND status = ? AND del_flag = '0'
            """, taskId, tenantId, userId, expectedFrom.name());
    }

    @Override
    public int markFailedIfActive(long taskId, String errorCode, String errorMessage) {
        return jdbc.update("""
            UPDATE image_task
               SET status = 'FAILED', error_code = ?, error_message = ?, progress = 0,
                   update_time = NOW(), finished_time = NOW()
             WHERE id = ? AND status NOT IN (""" + TERMINAL_PLACEHOLDERS + ")",
            errorCode, errorMessage, taskId,
            ImageTaskStatus.SUCCEEDED.name(), ImageTaskStatus.FAILED.name(),
            ImageTaskStatus.CANCELED.name(), ImageTaskStatus.TIMEOUT.name());
    }

    @Override
    public int failAllRunning(String errorCode, String errorMessage) {
        return jdbc.update("""
            UPDATE image_task
               SET status = 'FAILED', error_code = ?, error_message = ?,
                   update_time = NOW(), finished_time = NOW()
             WHERE status = 'RUNNING' AND del_flag = '0'
            """, errorCode, errorMessage);
    }

    @Override
    public int markSubmitted(long taskId, String promptId, int attemptCount, String worker) {
        return jdbc.update("""
            UPDATE image_task
               SET comfy_prompt_id = ?, comfy_worker = ?, attempt_count = ?,
                   submitted_time = NOW(), started_time = NOW(), progress = 10, update_time = NOW()
             WHERE id = ? AND status = 'RUNNING'
            """, promptId, worker, attemptCount, taskId);
    }

    @Override
    public int markSucceeded(long taskId, long outputAssetId, Integer width, Integer height,
                             boolean hasAlpha, long sizeBytes) {
        return jdbc.update("""
            UPDATE image_task
               SET status = 'SUCCEEDED', output_asset_id = ?, cover_asset_id = ?, progress = 100,
                   output_width = ?, output_height = ?, output_has_alpha = ?, output_size_bytes = ?,
                   update_time = NOW(), finished_time = NOW()
             WHERE id = ? AND status = 'RUNNING'
            """, outputAssetId, outputAssetId, width, height, hasAlpha ? 1 : 0, sizeBytes, taskId);
    }

    @Override
    public void appendEvent(long eventId, long taskId, String tenantId, int sequence,
                            String eventType, String detail) {
        try {
            jdbc.update("""
                INSERT INTO image_task_event (id, task_id, tenant_id, sequence, event_type, detail, create_time)
                VALUES (?,?,?,?,?,?,NOW())
                """, eventId, taskId, tenantId, sequence, eventType, detail);
        } catch (Exception e) {
            log.warn("任务 {} 事件写入失败：{}", taskId, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listEvents(long taskId, String tenantId) {
        return jdbc.queryForList("""
            SELECT sequence, event_type, detail, create_time
              FROM image_task_event
             WHERE task_id = ? AND tenant_id = ?
             ORDER BY sequence ASC
            """, taskId, tenantId);
    }

    @Override
    public int cancelQueued(long taskId, String tenantId, long userId) {
        return jdbc.update("""
            UPDATE image_task
               SET status = 'CANCELED', update_time = NOW(), finished_time = NOW()
             WHERE id = ? AND tenant_id = ? AND user_id = ? AND status = 'QUEUED' AND del_flag = '0'
            """, taskId, tenantId, userId);
    }

    /**
     * 任务查询的统一列清单。
     *
     * <p><b>为什么把 {@code submitted_time} / {@code started_time} 也查出来</b>：
     * 视觉工厂的候选列表要用它们算「提交时间」与「耗时」
     * （{@code CreativeGenerationServiceImpl#applyKernelState} 读
     * {@code started_time} 与 {@code finished_time} 相减）。这两列以前不在 SELECT 里，
     * 于是那个 Map 里根本没有这个键 → {@code durationOf} 恒返回 null →
     * 生产页「耗时」列对**每一个**候选都显示「—」（v1 人工测试反馈：时间取不到实际值）。
     * 加了列以后同一个方法立刻能算出耗时，不需要改调用方。</p>
     *
     * <p>包可见是为了让 {@code JdbcImageTaskRepositoryTest} 能直接钉住"这几列必须在"——
     * 这是个只靠读代码很难发现、只在页面上表现为"永远空着"的缺陷。</p>
     *
     * @return SELECT 片段（不带 WHERE）
     */
    static String taskSelect() {
        return """
            SELECT id, tenant_id, user_id, task_no, task_name, capability_code, workflow_code, workflow_version,
                   model_code, status, size_label, strength_label, prompt, negative_prompt, input_json,
                   comfy_prompt_id, comfy_worker, output_asset_id, cover_asset_id, progress,
                   error_code, error_message, attempt_count, output_width, output_height, output_has_alpha,
                   output_size_bytes, submitted_time, started_time, create_time, finished_time
              FROM image_task
            """;
    }

    /**
     * 测试用：把一行 snake_case 结果转成可变 Map。
     */
    static Map<String, Object> mutable(Map<String, Object> row) {
        return new HashMap<>(row);
    }
}
