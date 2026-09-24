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
import org.dromara.ai.image.service.ImageTaskSubmissionService;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.support.CamelCase;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p><b>为什么控制器也要挂 {@code @ConditionalOnProperty}</b>：本控制器的依赖 Bean
 * （契约注册表、模板填充器、编排器、素材门面…）全部来自
 * {@code @ConditionalOnProperty(prefix="image", name="enabled", havingValue="true")} 的配置类。
 * 如果控制器无条件注册，那么在本模块的<b>默认状态</b>（不配置 {@code image.enabled}）下，
 * 容器会因为找不到构造参数而抛出
 * {@code UnsatisfiedDependencyException: ... 'imageCreationController' ... No qualifying bean of type
 * 'org.dromara.ai.image.service.ImageWorkflowContractRegistry'}，
 * 也就是<b>部署新镜像而忘了打开开关会把整个若依平台拖垮</b>，而不是安静地不启用图像功能。
 * 这条约束由 {@code ImageModuleWiringTest} 守住。</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
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
     * 建任务/派发的唯一装配入口（与视觉工厂共用一处，避免两处漂移）
     */
    private final ImageTaskSubmissionService submissionService;

    /**
     * 图像模块自有的素材存储门面（配置类在内部构造，不作为 {@code AssetStorage} Bean 暴露，
     * 避免与视频模块的同类型 Bean 互相顶替；原因见 {@code ImageModuleConfiguration} 类注释）。
     */
    private final ImageAssetStore assetStore;

    /**
     * 本模块自用的 {@code ObjectMapper}：<b>刻意不注入</b>。
     *
     * <p>视频模块已注册 {@code videoObjectMapper}，图像模块若再注册/注入 {@code ObjectMapper}，
     * 两模块同时启用（生产即是）时会出现两个候选，启动直接失败——这是真实发生过的事故，
     * 见 {@code ImageModuleConfiguration#newMapper()}。静态实例无状态且线程安全，够用。</p>
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     *
     * <p>装配逻辑（能力与契约校验、字段白名单、素材归属、幂等、入库、事件）统一委托给
     * {@link ImageTaskSubmissionService}：视觉工厂要在同进程内按屏出图，也走这同一个服务。
     * 曾经是「控制器一套、服务一套」两处装配，改一处忘另一处就会出现
     * 「单张能出、批量少记字段」这类偏差，因此在这里收敛为一处。</p>
     */
    @PostMapping("/tasks")
    @SaCheckPermission("image:creation:submit")
    public R<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        Map<String, Object> fields = asMap(payload.get("fields"));
        String capabilityCode = text(payload.get("capabilityCode"));
        ImageCapability capability = ImageCapability.parse(capabilityCode);

        // 素材槽位顺序即契约：EDIT 为 image1..image3（允许空位），其余需图能力为 img
        List<Long> inputAssetIds = new ArrayList<>();
        if (capability == ImageCapability.EDIT) {
            for (String field : List.of("image1", "image2", "image3")) {
                inputAssetIds.add(longOf(fields.get(field)));
            }
        } else if (capability != null && capability.requiresImage()) {
            inputAssetIds.add(longOf(fields.get("img")));
        }

        ImageTaskSubmissionService.Submission submission = submissionService.submit(
            new ImageTaskSubmissionService.Command(
                capabilityCode,
                text(payload.get("workflowCode")),
                text(payload.get("taskName")),
                text(fields.get("prompt")),
                text(fields.get("negative_prompt")),
                text(fields.get("size")),
                text(fields.get("strength")),
                inputAssetIds,
                text(payload.get("idempotencyKey")),
                false,
                fields));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", submission.imageTaskId());
        summary.put("taskNo", submission.taskNo());
        summary.put("status", submission.status());
        if (submission.idempotent()) {
            summary.put("idempotent", true);
        }
        return R.ok(summary);
    }

    /**
     * 执行任务（后台执行，立即返回）。
     */
    @PostMapping("/tasks/{taskId}/execute")
    @SaCheckPermission("image:creation:submit")
    public R<Map<String, Object>> executeTask(@PathVariable Long taskId) {
        String tenantId = requireTenantId();
        long userId = requireUserId();
        // 归属校验、状态可执行性判断与派发同样委托给提交服务（与建任务收敛在同一处）
        String outcome = submissionService.dispatchOwned(taskId, tenantId, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("accepted", ImageTaskDispatchService.Outcome.ACCEPTED.name().equals(outcome)
            || ImageTaskDispatchService.Outcome.ALREADY_CLAIMED.name().equals(outcome));
        result.put("status", ImageTaskDispatchService.Outcome.QUEUE_FULL.name().equals(outcome)
            ? ImageTaskStatus.QUEUED.name() : ImageTaskStatus.RUNNING.name());
        if (ImageTaskDispatchService.Outcome.QUEUE_FULL.name().equals(outcome)) {
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
