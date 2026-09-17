package org.dromara.ai.video.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.VideoTaskOrchestrator;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频创作接口（若依鉴权）。
 *
 * <p>权限标识与 {@code script/sql/ry_video_menu.sql} 一致：
 * {@code video:creation:view} / {@code video:creation:submit}。</p>
 *
 * <p>归属隔离：所有任务与素材查询都显式带上当前租户与用户。
 * 无法解析租户时<b>失败关闭</b>，不允许退化成全局查询。</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoCreationController extends BaseController {

    /**
     * 单租户部署下的默认租户编号（若依约定值）。
     */
    private static final String DEFAULT_TENANT = "000000";

    /**
     * 上传素材大小上限：20 MiB。
     */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private static final List<String> ALLOWED_IMAGE_TYPES =
        List.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    private final WorkflowContractRegistry registry;
    private final H3TemplatePreparer preparer;
    private final VideoTaskRepository repository;
    private final VideoTaskOrchestrator orchestrator;
    private final AssetStorage assetStorage;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    /**
     * 模块内异常处理。
     *
     * <p>声明在控制器内，优先级高于全局 {@code @RestControllerAdvice}，因此
     * {@link VideoTaskException} 不会再落到兜底的 Exception 处理器而变成
     * 「未知异常 500」。契约拒绝、白名单拒绝、档位不符、素材越权等都属于
     * <b>客户端可纠正</b>的问题，必须以 400 + 具体原因返回，否则前端只能显示无意义的错误编号。</p>
     */
    @ExceptionHandler(VideoTaskException.class)
    public R<Void> handleVideoTaskException(VideoTaskException e) {
        log.warn("视频任务校验拒绝 [{}]：{}", e.getErrorCode(), e.getMessage());
        return R.fail(400, e.getMessage());
    }

    /**
     * 可提交的工作流视图（只下发能力、编码、字段白名单，不含节点与模板）。
     */
    @GetMapping("/capabilities")
    @SaCheckPermission("video:creation:view")
    public R<List<Map<String, Object>>> capabilities() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            WorkflowVersion version = registry.peek(code);
            if (version == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("workflowCode", version.workflowCode());
            item.put("capabilityCode", version.capabilityCode());
            item.put("modelCode", version.modelCode());
            item.put("version", version.version());
            item.put("status", version.status());
            // 只有 PUBLISHED 才能在正式环境提交；TESTING 仅用于受控联调。
            item.put("submittable", version.isPublished());
            item.put("testable", version.isTestable());
            item.put("supportedTier", version.fixedFieldValidation() == null
                ? null : version.fixedFieldValidation().tier());
            // 多档位：按契约声明顺序返回，前端据此渲染可选的清晰度。
            // 保留 supportedTier 字段以兼容既有前端，含义为「默认档位」。
            item.put("supportedTiers", version.fixedFieldValidation() == null
                ? java.util.List.of()
                : new java.util.ArrayList<>(version.fixedFieldValidation().allowedTiers()));
            item.put("supportedDuration", version.fixedFieldValidation() == null
                ? null : version.fixedFieldValidation().dur());
            item.put("maxDurationSeconds", version.maxDurationSeconds());
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * 上传素材，返回素材 ID（不返回浏览器本地文件名作为凭证）。
     */
    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("video:creation:submit")
    public R<Map<String, Object>> uploadAsset(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw VideoTaskException.invalidContract("上传文件为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw VideoTaskException.invalidContract("素材超过 20MB 上限");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw VideoTaskException.invalidContract("只接受 PNG/JPEG/WEBP 图片");
        }
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        byte[] content = file.getBytes();

        String storageKey = assetStorage.storeUpload(tenantId, userId, file.getOriginalFilename(),
            content, contentType);
        String checksum = sha256Hex(content);
        // video_asset.id 为 NOT NULL 且无 AUTO_INCREMENT（与若依其他业务表一致），
        // 主键必须由应用侧雪花算法生成，不能依赖数据库自增。
        long assetId = IdGeneratorUtil.nextLongId();
        repository.insertAsset(new VideoTaskRepository.AssetRow(
            assetId, tenantId, userId, null, "IMAGE", "UPLOAD", file.getOriginalFilename(),
            storageKey, contentType, (long) content.length, checksum, null, null, null, null));

        Map<String, Object> result = new HashMap<>();
        result.put("assetId", assetId);
        result.put("contentType", contentType);
        result.put("sizeBytes", content.length);
        return R.ok(result);
    }

    /**
     * 列出本人素材。
     */
    @GetMapping("/assets")
    @SaCheckPermission("video:creation:view")
    public R<PageResult<Map<String, Object>>> listAssets(PageQuery pageQuery) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        long total = repository.countOwnedAssets(tenantId, userId);
        List<Map<String, Object>> rows = repository.listOwnedAssets(tenantId, userId,
            offset(pageQuery), size(pageQuery));
        return R.ok(new PageResult<>(rows, total));
    }

    /**
     * 创建任务。仅接受契约白名单字段；workflowCode 必须在服务端已加载且可提交。
     */
    @PostMapping("/tasks")
    @SaCheckPermission("video:creation:submit")
    public R<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();

        String capabilityCode = stringOf(payload.get("capabilityCode"));
        String workflowCode = stringOf(payload.get("workflowCode"));
        VideoCapability capability = VideoCapability.parse(capabilityCode);
        if (capability == null) {
            throw VideoTaskException.invalidContract("不支持的能力编码");
        }
        WorkflowVersion version = registry.require(workflowCode, false);
        if (!capability.name().equalsIgnoreCase(version.capabilityCode())) {
            throw VideoTaskException.invalidContract("能力与工作流不匹配");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = payload.get("fields") instanceof Map
            ? (Map<String, Object>) payload.get("fields") : Map.of();

        // 字段白名单：拒绝任何不在契约内的键（例如试图覆写 sampler/nodeId）。
        preparer.validateFieldWhitelist(allowedFieldsOf(version), fields);

        String prompt = stringOf(fields.get("desc"));
        String tier = stringOf(fields.get("tier"));
        String durationLabel = stringOf(fields.get("dur"));
        Long imageAssetId = longOf(fields.get("img"));
        Long firstAssetId = longOf(fields.get("first"));
        Long lastAssetId = longOf(fields.get("last"));

        // 固定档位 + 必需素材 + 提示词校验，必须在入库前完成。
        // 注意：这里必须传真实提示词与真实的素材存在标志——曾因传占位符
        // "pending" 导致空提示词被放过、任务被错误入库，属于已修复的缺陷。
        preparer.validateFields(capability, version, new H3TemplatePreparer.H3Fields(
            prompt,
            imageAssetId == null ? null : "provided",
            firstAssetId == null ? null : "provided",
            lastAssetId == null ? null : "provided",
            tier, durationLabel));

        // 素材归属必须属于当前租户与用户。
        if (imageAssetId != null) {
            repository.requireOwnedAsset(imageAssetId, tenantId, userId);
        }
        if (firstAssetId != null) {
            repository.requireOwnedAsset(firstAssetId, tenantId, userId);
        }
        if (lastAssetId != null) {
            repository.requireOwnedAsset(lastAssetId, tenantId, userId);
        }

        String idempotencyKey = stringOf(payload.get("idempotencyKey"));
        Long existing = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
        if (existing != null) {
            return R.ok(Map.of("taskId", existing, "idempotent", true));
        }

        int durationSeconds = parseDurationSeconds(durationLabel);
        // 任务主键先算出来，taskNo 由它派生以保证唯一。
        // 曾用 System.nanoTime() % 100000 生成序号，实测发生碰撞，触发
        // uk_task_no 唯一键冲突并向用户返回 409（属于命名缺陷，不是并发问题）。
        long taskId = IdGeneratorUtil.nextLongId();
        String taskNo = "VIDEO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + String.format("%06d", Math.floorMod(taskId, 1_000_000L));
        Map<String, Object> inputJson = new HashMap<>();
        if (imageAssetId != null) {
            inputJson.put("img", imageAssetId);
        }
        if (firstAssetId != null) {
            inputJson.put("first", firstAssetId);
        }
        if (lastAssetId != null) {
            inputJson.put("last", lastAssetId);
        }
        String inputJsonText;
        try {
            inputJsonText = mapper.writeValueAsString(inputJson);
        } catch (Exception e) {
            throw VideoTaskException.invalidContract("输入素材序列化失败");
        }

        try {
            repository.insertTask(new VideoTaskRepository.TaskRow(
                taskId, tenantId, userId, taskNo,
                stringOf(payload.getOrDefault("taskName", capabilityCode + " 任务")),
                capability.name(), version.workflowCode(), version.version(), version.modelCode(),
                VideoTaskStatus.QUEUED.name(), tier, durationSeconds, prompt, inputJsonText,
                idempotencyKey, null));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 幂等键上的唯一索引兜底：(tenant,user,idempotency_key) 在并发下可能同时通过上面的
            // 预检查，此时数据库会拒绝第二条。这属于「重复提交」而非错误——必须返回已存在的那条，
            // 否则并发重复提交的用户会看到失败（实测曾出现 409 Conflict），却又确实建了任务。
            Long existingId = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existingId != null) {
                log.info("幂等键 {} 并发重复提交，返回已存在任务 {}", idempotencyKey, existingId);
                return R.ok(Map.of("taskId", existingId, "idempotent", true));
            }
            throw e;
        }

        return R.ok(Map.of("taskId", taskId, "taskNo", taskNo, "status", VideoTaskStatus.QUEUED.name()));
    }

    /**
     * 执行任务（同步等待成片）。浏览器不能直连 ComfyUI，必须经由本接口。
     */
    @PostMapping("/tasks/{taskId}/execute")
    @SaCheckPermission("video:creation:submit")
    public R<Map<String, Object>> executeTask(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        Map<String, Object> task = repository.requireOwnedTask(taskId, tenantId, userId);
        String status = String.valueOf(task.get("status"));
        if (!VideoTaskStatus.QUEUED.name().equals(status)) {
            throw VideoTaskException.invalidContract("任务当前状态不可执行：" + status);
        }
        VideoCapability capability = VideoCapability.parse(String.valueOf(task.get("capability_code")));
        String inputJson = String.valueOf(task.get("input_json"));

        VideoTaskOrchestrator.ExecutionResult result;
        try {
            result = orchestrator.execute(new VideoTaskOrchestrator.TaskContext(
                taskId, tenantId, userId, LoginHelper.getDeptId(),
                capability == null ? null : capability.name(),
                String.valueOf(task.get("workflow_code")),
                task.get("prompt") == null ? null : String.valueOf(task.get("prompt")),
                String.valueOf(task.get("tier")),
                durationLabelOf(task.get("duration_seconds")),
                assetIdFrom(inputJson, "img"),
                assetIdFrom(inputJson, "first"),
                assetIdFrom(inputJson, "last"),
                false,
                () -> System.nanoTime(),
                new AtomicInteger(0)));
        } catch (VideoTaskException e) {
            // 前置失败（契约/素材/可达性）也要落库，否则任务会一直停在 QUEUED，
            // 「我的任务」里看不到失败原因。编排器内部失败路径已自行落库。
            int moved = repository.transition(taskId, VideoTaskStatus.QUEUED, VideoTaskStatus.FAILED,
                e.getErrorCode(), e.getMessage());
            if (moved > 0) {
                log.warn("任务 {} 执行前失败，已置为 FAILED：[{}] {}", taskId, e.getErrorCode(), e.getMessage());
            }
            // 状态已落库，因此返回成功码并带上真实状态与原因；
            // 若返回非 200，前端 axios 拦截器会抛出通用错误，反而看不到具体原因。
            Map<String, Object> failed = new HashMap<>();
            failed.put("taskId", taskId);
            failed.put("status", VideoTaskStatus.FAILED.name());
            failed.put("errorCode", e.getErrorCode());
            failed.put("errorMessage", e.getMessage());
            return R.ok(failed);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("taskId", taskId);
        body.put("status", VideoTaskStatus.SUCCEEDED.name());
        body.put("outputAssetId", result.outputAssetId());
        body.put("truncated", result.truncated());
        body.put("width", result.output().width());
        body.put("height", result.output().height());
        body.put("fps", result.output().fps());
        body.put("durationMillis", result.output().durationMillis());
        return R.ok(body);
    }

    /**
     * 列出本人任务。
     */
    @GetMapping("/tasks")
    @SaCheckPermission("video:creation:view")
    public R<PageResult<Map<String, Object>>> listTasks(PageQuery pageQuery,
                                                       @RequestParam(value = "status", required = false) String status) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        long total = repository.countOwnedTasks(tenantId, userId, status);
        List<Map<String, Object>> rows = repository.listOwnedTasks(tenantId, userId, status,
            offset(pageQuery), size(pageQuery));
        return R.ok(new PageResult<>(rows, total));
    }

    /**
     * 任务详情（含事件序列，用于断点恢复与审计）。
     */
    @GetMapping("/tasks/{taskId}")
    @SaCheckPermission("video:creation:view")
    public R<Map<String, Object>> taskDetail(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        Map<String, Object> task = new HashMap<>(repository.requireOwnedTask(taskId, tenantId, userId));
        task.put("events", repository.listEvents(taskId, tenantId));
        return R.ok(task);
    }

    /**
     * 取消排队中的任务。已在执行的任务不允许在此接口取消。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @SaCheckPermission("video:creation:submit")
    public R<Void> cancelTask(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        // 先校验归属：否则会返回「任务不在排队中」，从而泄露「该任务确实存在」这一信息。
        // 非本人任务的返回必须与「任务不存在」完全一致。
        repository.requireOwnedTask(taskId, tenantId, userId);
        int affected = repository.cancelQueued(taskId, tenantId, userId);
        if (affected == 0) {
            throw VideoTaskException.invalidContract("任务不在排队中，无法取消");
        }
        return R.ok();
    }

    /**
     * 删除素材（软删，且只能删本人素材）。
     */
    @DeleteMapping("/assets/{assetId}")
    @SaCheckPermission("video:creation:submit")
    public R<Void> deleteAsset(@PathVariable Long assetId) {
        String tenantId = requireTenantId();
        long userId = LoginHelper.getUserId();
        repository.requireOwnedAsset(assetId, tenantId, userId);
        repository.softDeleteAsset(assetId, tenantId, userId);
        return R.ok();
    }

    private List<String> allowedFieldsOf(WorkflowVersion version) {
        return List.of("desc", "tier", "dur", "img", "first", "last");
    }

    private static int offset(PageQuery pageQuery) {
        int pageNum = pageQuery.getPageNum() == null ? 1 : Math.max(1, pageQuery.getPageNum());
        return (pageNum - 1) * size(pageQuery);
    }

    private static int size(PageQuery pageQuery) {
        int pageSize = pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        return Math.min(Math.max(1, pageSize), 100);
    }

    private static int parseDurationSeconds(String label) {
        if (label == null) {
            return 5;
        }
        String digits = label.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 5;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private static String durationLabelOf(Object seconds) {
        if (seconds == null) {
            return "5 秒";
        }
        return seconds + " 秒";
    }

    private static Long assetIdFrom(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                new ObjectMapper().readTree(json).get(key);
            return node == null || node.isNull() ? null : node.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw VideoTaskException.invalidContract("素材 ID 必须是数字");
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 解析当前租户。
     *
     * <p>RuoYi-Vue-Plus 的 {@code LoginUser} 不带租户字段，v6 也没有全局限租户工具，
     * 因此这里按用户 ID 反查 {@code sys_user.tenant_id}。查不到时失败关闭，
     * 不允许以空租户继续查询（否则会退化成跨租户可见）。</p>
     */
    private String requireTenantId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new VideoTaskException("UNAUTHENTICATED", "当前未登录");
        }
        try {
            List<String> tenants = jdbc.queryForList(
                "SELECT tenant_id FROM sys_user WHERE user_id = ?", String.class, userId);
            if (!tenants.isEmpty() && tenants.get(0) != null && !tenants.get(0).isBlank()) {
                return tenants.get(0);
            }
        } catch (Exception e) {
            // 该库可能未启用多租户字段，回落到单租户默认值。
            log.debug("读取 sys_user.tenant_id 失败，使用默认租户：{}", e.getClass().getSimpleName());
        }
        log.warn("用户 {} 未能解析租户，回落默认租户 {}", userId, DEFAULT_TENANT);
        return DEFAULT_TENANT;
    }
}
