package org.dromara.ai.image.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskDispatchService;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.support.CamelCase;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图像创作接口（若依鉴权）。
 *
 * <p>权限标识与 {@code script/sql/ry_image_menu.sql} 一致：
 * {@code image:creation:view} / {@code image:creation:submit}。</p>
 *
 * <p>安全边界：节点 ID、模板 JSON、模型路径从不下发前端；请求体只允许出现契约声明的字段，
 * 白名单校验用的是契约里的 {@code fields}（而不是硬编码列表），提交时后端深拷贝模板并只覆写
 * {@code mapping} 声明的输入键。</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageCreationController extends BaseController {

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

    private final ImageWorkflowContractRegistry registry;
    private final ImageTemplatePreparer preparer;
    private final ImageTaskRepository repository;
    private final ImageTaskDispatchService dispatchService;

    /**
     * 图像模块自有的素材存储门面（配置类在内部构造，不作为 {@code AssetStorage} Bean 暴露，
     * 避免与视频模块的同类型 Bean 互相顶替；原因见 {@code ImageModuleConfiguration} 类注释）。
     */
    private final ImageAssetStore assetStore;

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    /**
     * 模块内异常处理：契约拒绝、白名单拒绝、素材越权等都属于客户端可纠正问题，
     * 必须返回 400 + 具体原因，否则前端只能显示「未知异常 500」。
     */
    @ExceptionHandler(ImageTaskException.class)
    public R<Void> handleImageTaskException(ImageTaskException e) {
        log.warn("图像任务校验拒绝 [{}]：{}", e.getErrorCode(), e.getMessage());
        return R.fail(400, e.getMessage());
    }

    /**
     * 上传超过容器级 multipart 上限时给出可行动的原因。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传被容器级大小限制拒绝：{}", e.getMessage());
        return R.fail(400, "素材太大，请压缩到 " + (MAX_UPLOAD_BYTES / 1024 / 1024)
            + "MB 以内再上传（手机截图/相机原图常见 5~15MB，必要时先转 JPG）");
    }

    /**
     * 可用工作流视图：能力编码、状态与档位选项。
     *
     * <p>只下发前端渲染所需的最小信息；节点 ID / 模板路径 / 模型路径一律不下发。</p>
     */
    @GetMapping("/capabilities")
    @SaCheckPermission("image:creation:view")
    public R<List<Map<String, Object>>> capabilities() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImageWorkflowVersion version : registry.registeredVersions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflowCode", version.workflowCode());
            item.put("capabilityCode", version.capabilityCode());
            item.put("modelCode", version.modelCode());
            item.put("version", version.version());
            item.put("status", version.status());
            item.put("submittable", version.isPublished());
            item.put("testable", version.isTestable());
            item.put("requireAlpha", version.requireAlpha());
            item.put("maxPixels", version.maxPixels());
            item.put("defaultSize", version.defaultSize());
            item.put("sizes", version.sizePresets().entrySet().stream().map(entry -> {
                Map<String, Object> size = new LinkedHashMap<>();
                size.put("label", entry.getKey());
                size.put("width", entry.getValue()[0]);
                size.put("height", entry.getValue()[1]);
                return size;
            }).toList());
            item.put("defaultStrength", version.defaultStrength());
            item.put("strengths", version.supportedStrengths());
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * 上传素材，立即返回素材 ID（提交任务时只传 ID，浏览器本地文件名不进后端）。
     */
    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("image:creation:submit")
    public R<Map<String, Object>> uploadAsset(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageTaskException("INVALID_CONTRACT", "上传文件为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ImageTaskException("INVALID_CONTRACT",
                "素材太大，请压缩到 " + (MAX_UPLOAD_BYTES / 1024 / 1024) + "MB 以内再上传");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ImageTaskException("INVALID_CONTRACT", "只支持 PNG / JPG / WEBP 图片");
        }
        String tenantId = requireTenantId();
        long userId = requireUserId();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new ImageTaskException("INVALID_CONTRACT", "读取上传文件失败");
        }
        String storageKey = assetStore.storeUpload(tenantId, userId, file.getOriginalFilename(), content, contentType);
        long assetId = IdGeneratorUtil.nextLongId();
        repository.insertAsset(new ImageTaskRepository.AssetRow(
            assetId, tenantId, userId, null, "IMAGE", "UPLOAD", file.getOriginalFilename(),
            storageKey, contentType, content.length, sha256Hex(content), null, null, null, LoginHelper.getDeptId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("contentType", contentType);
        result.put("sizeBytes", content.length);
        return R.ok(result);
    }

    /**
     * 我的素材分页。
     */
    @GetMapping("/assets")
    @SaCheckPermission("image:creation:view")
    public R<PageResult<Map<String, Object>>> listAssets(PageQuery pageQuery) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        int pageNum = pageQuery.getPageNum() == null || pageQuery.getPageNum() < 1 ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery.getPageSize() == null ? 20 : Math.min(100, Math.max(1, pageQuery.getPageSize()));
        List<Map<String, Object>> rows = repository.listOwnedAssets(tenantId, userId, (pageNum - 1) * pageSize, pageSize);
        long total = repository.countOwnedAssets(tenantId, userId);
        return R.ok(new PageResult<>(CamelCase.rows(rows), total));
    }

    /**
     * 读取素材内容。
     */
    @GetMapping("/assets/{assetId}/content")
    @SaCheckPermission("image:creation:view")
    public ResponseEntity<byte[]> assetContent(@PathVariable Long assetId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        ImageTaskRepository.AssetRow asset = repository.requireOwnedAsset(assetId, tenantId, userId);
        byte[] content = assetStore.read(asset.storageKey());
        if (content == null || content.length == 0) {
            throw ImageTaskException.assetNotFound("素材内容为空或已丢失");
        }
        String contentType = asset.contentType() == null ? "application/octet-stream" : asset.contentType();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.VARY, "Authorization")
            .body(content);
    }

    /**
     * 素材缩略图（无法生成时返回 404，前端退回原图）。
     */
    @GetMapping("/assets/{assetId}/thumbnail")
    @SaCheckPermission("image:creation:view")
    public ResponseEntity<byte[]> assetThumbnail(@PathVariable Long assetId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        ImageTaskRepository.AssetRow asset = repository.requireOwnedAsset(assetId, tenantId, userId);
        byte[] thumbnail = assetStore.thumbnail(asset.storageKey(), asset.contentType());
        if (thumbnail == null || thumbnail.length == 0) {
            throw ImageTaskException.assetNotFound("该素材暂无缩略图");
        }
        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
            .header("X-Content-Type-Options", "nosniff")
            .body(thumbnail);
    }

    /**
     * 删除素材（软删）。
     */
    @DeleteMapping("/assets/{assetId}")
    @SaCheckPermission("image:creation:submit")
    public R<Void> deleteAsset(@PathVariable Long assetId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        repository.requireOwnedAsset(assetId, tenantId, userId);
        repository.softDeleteAsset(assetId, tenantId, userId);
        return R.ok();
    }

    /**
     * 创建任务（只入库，不执行）。
     */
    @PostMapping("/tasks")
    @SaCheckPermission("image:creation:submit")
    public R<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        String tenantId = requireTenantId();
        long userId = requireUserId();

        String capabilityCode = text(payload.get("capabilityCode"));
        String workflowCode = text(payload.get("workflowCode"));
        ImageCapability capability = ImageCapability.parse(capabilityCode);
        if (capability == null) {
            throw ImageTaskException.invalidContract("不支持的能力编码：" + capabilityCode);
        }
        ImageWorkflowVersion version = registry.require(workflowCode, false);
        if (!capability.code().equalsIgnoreCase(version.capabilityCode())) {
            throw ImageTaskException.invalidContract("能力与工作流不匹配");
        }

        Map<String, Object> fields = asMap(payload.get("fields"));
        preparer.validateFieldWhitelist(version.capabilityFields(), fields);

        String prompt = text(fields.get("prompt"));
        String negativePrompt = text(fields.get("negative_prompt"));
        String sizeLabel = text(fields.get("size"));
        String strengthLabel = text(fields.get("strength"));
        if (sizeLabel == null || sizeLabel.isBlank()) {
            sizeLabel = version.defaultSize();
        }
        if (strengthLabel == null || strengthLabel.isBlank()) {
            strengthLabel = version.defaultStrength();
        }

        List<Long> inputAssetIds = new ArrayList<>();
        Long firstAssetId = null;
        if (capability == ImageCapability.EDIT) {
            for (String field : List.of("image1", "image2", "image3")) {
                Long assetId = longOf(fields.get(field));
                inputAssetIds.add(assetId);
                if (firstAssetId == null) {
                    firstAssetId = assetId;
                }
            }
        } else if (capability.requiresImage()) {
            Long assetId = longOf(fields.get("img"));
            inputAssetIds.add(assetId);
            firstAssetId = assetId;
        }
        // 归属校验：非本人素材按「不存在」处理，不能等到执行阶段才发现
        for (Long assetId : inputAssetIds) {
            if (assetId != null) {
                repository.requireOwnedAsset(assetId, tenantId, userId);
            }
        }

        preparer.validateFields(capability, version, new ImageTemplatePreparer.ImageFields(
            prompt, negativePrompt, sizeLabel, strengthLabel,
            inputAssetIds.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList(), 0L));

        String idempotencyKey = text(payload.get("idempotencyKey"));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Long existing = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existing != null) {
                return R.ok(taskSummary(existing, tenantId, userId, true));
            }
        }

        long taskId = IdGeneratorUtil.nextLongId();
        String taskNo = taskNoOf(taskId);
        String taskName = text(payload.get("taskName"));
        String inputJson;
        try {
            inputJson = mapper.writeValueAsString(fields);
        } catch (Exception e) {
            inputJson = null;
        }
        try {
            repository.insertTask(new ImageTaskRepository.TaskRow(
                taskId, tenantId, userId, taskNo,
                taskName == null || taskName.isBlank() ? capability.label() + " · " + version.modelCode() : taskName,
                capability.code().toUpperCase(), workflowCode, version.version(), version.modelCode(),
                ImageTaskStatus.QUEUED.name(), sizeLabel, strengthLabel,
                prompt, negativePrompt, inputJson, idempotencyKey, LoginHelper.getDeptId()));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下的幂等兜底：插入冲突说明另一个请求刚建好同一幂等键的任务
            Long existing = idempotencyKey == null ? null : repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existing != null) {
                return R.ok(taskSummary(existing, tenantId, userId, true));
            }
            throw e;
        }
        AtomicInteger sequence = new AtomicInteger();
        repository.appendEvent(IdGeneratorUtil.nextLongId(), taskId, tenantId, sequence.incrementAndGet(), "CREATED", "任务已创建");
        return R.ok(taskSummary(taskId, tenantId, userId, false));
    }

    /**
     * 执行任务（后台执行，立即返回）。
     */
    @PostMapping("/tasks/{taskId}/execute")
    @SaCheckPermission("image:creation:submit")
    public R<Map<String, Object>> executeTask(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        Map<String, Object> task = repository.requireOwnedTask(taskId, tenantId, userId);
        String status = String.valueOf(task.get("status"));
        if (!ImageTaskStatus.QUEUED.name().equals(status) && !ImageTaskStatus.RUNNING.name().equals(status)) {
            throw new ImageTaskException("INVALID_CONTRACT", "任务当前状态不可执行：" + status);
        }
        ImageTaskDispatchService.Outcome outcome = dispatchService.dispatch(taskId, () -> buildContext(task, tenantId, userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("accepted", outcome == ImageTaskDispatchService.Outcome.ACCEPTED
            || outcome == ImageTaskDispatchService.Outcome.ALREADY_CLAIMED);
        result.put("status", outcome == ImageTaskDispatchService.Outcome.QUEUE_FULL
            ? ImageTaskStatus.QUEUED.name() : ImageTaskStatus.RUNNING.name());
        if (outcome == ImageTaskDispatchService.Outcome.QUEUE_FULL) {
            throw new ImageTaskException("QUEUE_FULL", "执行队列已满，请稍后重试");
        }
        return R.ok(result);
    }

    /**
     * 我的任务分页。
     */
    @GetMapping("/tasks")
    @SaCheckPermission("image:creation:view")
    public R<PageResult<Map<String, Object>>> listTasks(PageQuery pageQuery,
                                                        @RequestParam(value = "status", required = false) String status) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        int pageNum = pageQuery.getPageNum() == null || pageQuery.getPageNum() < 1 ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery.getPageSize() == null ? 20 : Math.min(100, Math.max(1, pageQuery.getPageSize()));
        List<Map<String, Object>> rows = repository.listOwnedTasks(tenantId, userId, status, (pageNum - 1) * pageSize, pageSize);
        long total = repository.countOwnedTasks(tenantId, userId, status);
        return R.ok(new PageResult<>(CamelCase.rows(rows), total));
    }

    /**
     * 任务详情（含事件时间线）。
     */
    @GetMapping("/tasks/{taskId}")
    @SaCheckPermission("image:creation:view")
    public R<Map<String, Object>> taskDetail(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        Map<String, Object> task = repository.requireOwnedTask(taskId, tenantId, userId);
        Map<String, Object> detail = CamelCase.row(task);
        detail.put("events", CamelCase.rows(repository.listEvents(taskId, tenantId)));
        return R.ok(detail);
    }

    /**
     * 取消任务（仅排队中可取消）。先校验归属，避免泄露任务是否存在。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @SaCheckPermission("image:creation:submit")
    public R<Void> cancelTask(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        repository.requireOwnedTask(taskId, tenantId, userId);
        int updated = repository.cancelQueued(taskId, tenantId, userId);
        if (updated == 0) {
            throw new ImageTaskException("INVALID_CONTRACT", "任务不在排队中，无法取消");
        }
        return R.ok();
    }

    private ImageTaskOrchestrator.TaskContext buildContext(Map<String, Object> task, String tenantId, long userId) {
        List<Long> inputAssetIds = new ArrayList<>();
        Object raw = task.get("input_json");
        if (raw != null) {
            try {
                Map<String, Object> fields = asMap(mapper.readValue(String.valueOf(raw), Map.class));
                for (String field : List.of("image1", "image2", "image3", "img")) {
                    Long assetId = longOf(fields.get(field));
                    if (assetId != null) {
                        inputAssetIds.add(assetId);
                    }
                }
            } catch (Exception e) {
                log.warn("任务 {} 的 input_json 解析失败：{}", task.get("id"), e.getMessage());
            }
        }
        return new ImageTaskOrchestrator.TaskContext(
            ((Number) task.get("id")).longValue(),
            tenantId,
            userId,
            task.get("create_dept") == null ? null : ((Number) task.get("create_dept")).longValue(),
            String.valueOf(task.get("capability_code")),
            String.valueOf(task.get("workflow_code")),
            task.get("prompt") == null ? null : String.valueOf(task.get("prompt")),
            task.get("negative_prompt") == null ? null : String.valueOf(task.get("negative_prompt")),
            task.get("size_label") == null ? null : String.valueOf(task.get("size_label")),
            task.get("strength_label") == null ? null : String.valueOf(task.get("strength_label")),
            inputAssetIds,
            false,
            IdGeneratorUtil::nextLongId,
            new AtomicInteger());
    }

    private Map<String, Object> taskSummary(long taskId, String tenantId, long userId, boolean idempotent) {
        Map<String, Object> task = repository.requireOwnedTask(taskId, tenantId, userId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", task.get("id"));
        summary.put("taskNo", task.get("task_no"));
        summary.put("status", task.get("status"));
        if (idempotent) {
            summary.put("idempotent", true);
        }
        return summary;
    }

    private static String taskNoOf(long taskId) {
        return "IMAGE-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + String.format("%06d", Math.floorMod(taskId, 1_000_000L));
    }

    private static String sha256Hex(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ImageTaskException("INVALID_CONTRACT", "素材 ID 必须是数字：" + text);
        }
    }

    private long requireUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ImageTaskException("UNAUTHENTICATED", "当前未登录");
        }
        return userId;
    }

    /**
     * 解析当前租户。
     *
     * <p>{@code LoginUser} 没有租户字段，因此按 {@code sys_user.tenant_id} 反查；
     * 查询失败时回落单租户默认值并告警（该库可能未启用多租户字段）。</p>
     */
    private String requireTenantId() {
        long userId = requireUserId();
        try {
            List<String> tenants = jdbc.queryForList(
                "SELECT tenant_id FROM sys_user WHERE user_id = ?", String.class, userId);
            if (!tenants.isEmpty() && tenants.get(0) != null && !tenants.get(0).isBlank()) {
                return tenants.get(0);
            }
        } catch (Exception e) {
            log.debug("读取 sys_user.tenant_id 失败，使用默认租户：{}", e.getClass().getSimpleName());
        }
        log.warn("用户 {} 未能解析租户，回落默认租户 {}", userId, DEFAULT_TENANT);
        return DEFAULT_TENANT;
    }
}
