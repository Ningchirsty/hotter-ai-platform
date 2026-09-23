package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图像任务提交服务：把「入库 + 派发执行」这套动作从控制器里抽出来，供同进程的其它模块（AI视觉工厂）复用。
 *
 * <p><b>为什么需要它</b>：出图内核（契约校验、模板填充、ComfyUI 提交、轮询、产出落盘、素材归属校验）
 * 本来只能从 {@code ImageCreationController} 走 HTTP 触发。视觉工厂要在<b>同一个进程内</b>按分镜逐屏出图，
 * 走 HTTP 自调用既拿不到登录上下文也白白多一层网络；而内核的建任务逻辑当时写在控制器里，
 * 没有 service 层入口，因此这里补一个。</p>
 *
 * <p><b>边界（重要）</b>：本类不碰 ComfyUI、不碰模板、不碰契约文件——那些仍由
 * {@link ImageTaskOrchestrator} / {@link ImageWorkflowContractRegistry} 负责。
 * 它只做三件事：校验（能力/契约/字段/素材归属）、入库（image_asset / image_task）、派发（QUEUED→RUNNING）。</p>
 *
 * <p><b>与控制器重复的部分</b>：{@code ImageCreationController.createTask} 里有一套同样的装配逻辑。
 * 本次以「纯新增」方式落地（不改动线上图像页的控制器，避免把在用的功能一起改坏），
 * 两处重复是<b>已知技术债</b>，计划在视觉工厂 R1 把控制器改为委托本服务，收敛为一处。
 * 语义对齐由 {@code ImageTaskSubmissionServiceTest} 钉住。</p>
 *
 * <p><b>启用条件</b>：与 {@link org.dromara.ai.image.config.ImageModuleConfiguration} 一致，
 * 仅在 {@code image.enabled=true} 时注册——否则它依赖的 AssetStore/Orchestrator 等 Bean 并不存在。
 * 调用方（视觉工厂）请用 {@code ObjectProvider} 注入，并在为空时给出可读提示。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageTaskSubmissionService {

    /**
     * 未能解析租户时的回落值（与控制器一致；该库可能未启用多租户字段）
     */
    private static final String DEFAULT_TENANT = "000000";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImageWorkflowContractRegistry registry;
    private final ImageTemplatePreparer preparer;
    private final ImageTaskRepository repository;
    private final ImageAssetStore assetStore;
    private final ImageTaskDispatchService dispatchService;
    private final JdbcTemplate jdbc;

    public ImageTaskSubmissionService(ImageWorkflowContractRegistry registry,
                                      ImageTemplatePreparer preparer,
                                      ImageTaskRepository repository,
                                      ImageAssetStore assetStore,
                                      ImageTaskDispatchService dispatchService,
                                      JdbcTemplate jdbc) {
        this.registry = registry;
        this.preparer = preparer;
        this.repository = repository;
        this.assetStore = assetStore;
        this.dispatchService = dispatchService;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // 身份
    // ------------------------------------------------------------------

    /**
     * 当前登录用户主键。
     */
    public long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ImageTaskException("UNAUTHENTICATED", "当前未登录");
        }
        return userId;
    }

    /**
     * 当前用户所属租户。
     *
     * <p>{@code LoginUser} 没有租户字段，按 {@code sys_user.tenant_id} 反查；
     * 查询失败回落默认租户并告警。</p>
     */
    public String currentTenantId() {
        long userId = currentUserId();
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

    // ------------------------------------------------------------------
    // 素材
    // ------------------------------------------------------------------

    /**
     * 把一段图片字节登记为当前用户的「上传素材」，返回素材 ID。
     *
     * <p>内核执行时按 {@code tenant_id + user_id} 校验素材归属，因此<b>必须</b>由执行者本人上传，
     * 不能拿别人的素材 ID 去执行。</p>
     *
     * @param originalName 原始文件名（仅展示）
     * @param content      图片字节
     * @param contentType  MIME
     * @return 素材 ID（image_asset.id）
     */
    public long storeAsset(String originalName, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new ImageTaskException("ASSET_NOT_FOUND", "素材内容为空");
        }
        String tenantId = currentTenantId();
        long userId = currentUserId();
        String storageKey = assetStore.storeUpload(tenantId, userId, originalName, content, contentType);
        long assetId = IdGeneratorUtil.nextLongId();
        repository.insertAsset(new ImageTaskRepository.AssetRow(
            assetId, tenantId, userId, null, "IMAGE", "UPLOAD",
            originalName, storageKey, contentType, content.length, sha256Hex(content),
            null, null, null, LoginHelper.getDeptId()));
        return assetId;
    }

    /**
     * 读取素材字节（归属校验后再读，越权一律按不存在处理）。
     *
     * @param assetId  素材 ID
     * @param tenantId 素材所属租户
     * @param userId   素材所属用户
     * @return 图片字节
     */
    public byte[] readAssetBytes(long assetId, String tenantId, long userId) {
        ImageTaskRepository.AssetRow asset = repository.requireOwnedAsset(assetId, tenantId, userId);
        return assetStore.read(asset.storageKey());
    }

    /**
     * 读取某个任务的产出图字节。
     *
     * @param imageTaskId 内核任务 ID
     * @param tenantId    执行者租户
     * @param userId      执行者用户
     * @return 产出图字节；任务未成功或无产出时返回 null
     */
    public byte[] readOutputBytes(long imageTaskId, String tenantId, long userId) {
        Map<String, Object> task = repository.requireOwnedTask(imageTaskId, tenantId, userId);
        Object outputAssetId = task.get("output_asset_id");
        if (outputAssetId == null) {
            return null;
        }
        ImageTaskRepository.AssetRow asset =
            repository.requireOwnedAsset(((Number) outputAssetId).longValue(), tenantId, userId);
        return assetStore.read(asset.storageKey());
    }

    /**
     * 读取素材缩略图（列表页用；内核只对图片类型生成缩略图，其它返回 null）。
     *
     * @param assetId  素材 ID
     * @param tenantId 所属租户
     * @param userId   所属用户
     * @return JPEG 缩略图字节；生成不出时返回 null（调用方应回落原图）
     */
    public byte[] readAssetThumbnail(long assetId, String tenantId, long userId) {
        ImageTaskRepository.AssetRow asset = repository.requireOwnedAsset(assetId, tenantId, userId);
        String contentType = asset.contentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return null;
        }
        return assetStore.thumbnail(asset.storageKey(), contentType);
    }

    // ------------------------------------------------------------------
    // 任务
    // ------------------------------------------------------------------

    /**
     * 提交参数。
     *
     * @param capabilityCode  能力编码（如 I2I / WHITEBG）
     * @param workflowCode    工作流编码（如 wf-i2i-qwen21）
     * @param taskName        任务名（空则自动生成）
     * @param prompt          正向提示词（能力不支持提示词时忽略）
     * @param negativePrompt  负向提示词（可空）
     * @param sizeLabel       尺寸档位标签（空则取契约默认值）
     * @param strengthLabel   重绘强度标签（可空，取契约默认值）
     * @param inputAssetIds   输入素材 ID（按槽位顺序：img 或 image1..3）
     * @param idempotencyKey  幂等键（可空）
     * @param requirePublished 是否要求工作流已发布（生产用 true）
     */
    public record Command(
        String capabilityCode,
        String workflowCode,
        String taskName,
        String prompt,
        String negativePrompt,
        String sizeLabel,
        String strengthLabel,
        List<Long> inputAssetIds,
        String idempotencyKey,
        boolean requirePublished) {
    }

    /**
     * 提交结果。
     *
     * @param imageTaskId 内核任务 ID
     * @param taskNo      任务号
     * @param tenantId    执行者租户（回读状态/产出必须带上）
     * @param userId      执行者用户
     * @param status      入库后的状态
     * @param accepted    是否已成功派发执行
     * @param outcome     派发结果（ACCEPTED / ALREADY_CLAIMED / QUEUE_FULL）
     * @param idempotent  是否命中幂等（返回的是已有任务）
     */
    public record Submission(
        long imageTaskId,
        String taskNo,
        String tenantId,
        long userId,
        String status,
        boolean accepted,
        String outcome,
        boolean idempotent) {
    }

    /**
     * 只入库，不执行。
     */
    public Submission submit(Command command) {
        return doSubmit(command, false);
    }

    /**
     * 入库并派发执行（等价于控制器的 create + execute 两步）。
     */
    public Submission submitAndDispatch(Command command) {
        return doSubmit(command, true);
    }

    private Submission doSubmit(Command command, boolean dispatch) {
        String tenantId = currentTenantId();
        long userId = currentUserId();
        Long deptId = LoginHelper.getDeptId();

        String capabilityCode = command.capabilityCode();
        ImageCapability capability = ImageCapability.parse(capabilityCode);
        if (capability == null) {
            throw ImageTaskException.invalidContract("不支持的能力编码：" + capabilityCode);
        }
        ImageWorkflowVersion version = registry.require(command.workflowCode(), command.requirePublished());
        if (!capability.code().equalsIgnoreCase(version.capabilityCode())) {
            throw ImageTaskException.invalidContract("能力与工作流不匹配");
        }

        // 1) 组装 fields：只放该能力契约声明过的键，避免白名单校验误伤
        Map<String, Object> fields = new LinkedHashMap<>();
        if (capability.allowsPrompt()) {
            fields.put("prompt", command.prompt());
            if (command.negativePrompt() != null && !command.negativePrompt().isBlank()) {
                fields.put("negative_prompt", command.negativePrompt());
            }
        }
        if (version.capabilityFields() != null && version.capabilityFields().contains("size")
            && command.sizeLabel() != null && !command.sizeLabel().isBlank()) {
            fields.put("size", command.sizeLabel());
        }
        if (version.capabilityFields() != null && version.capabilityFields().contains("strength")
            && command.strengthLabel() != null && !command.strengthLabel().isBlank()) {
            fields.put("strength", command.strengthLabel());
        }

        List<Long> inputAssetIds = command.inputAssetIds() == null ? List.of() : command.inputAssetIds();
        if (capability == ImageCapability.EDIT) {
            for (int i = 0; i < 3; i++) {
                Long assetId = i < inputAssetIds.size() ? inputAssetIds.get(i) : null;
                fields.put("image" + (i + 1), assetId);
            }
        } else if (capability.requiresImage()) {
            Long assetId = inputAssetIds.isEmpty() ? null : inputAssetIds.get(0);
            fields.put("img", assetId);
        }
        preparer.validateFieldWhitelist(version.capabilityFields(), fields);

        // 2) 档位取默认值 + 归属校验（非本人素材按「不存在」处理，不能等执行阶段才发现）
        String sizeLabel = command.sizeLabel();
        if (sizeLabel == null || sizeLabel.isBlank()) {
            sizeLabel = version.defaultSize();
        }
        String strengthLabel = command.strengthLabel();
        if (strengthLabel == null || strengthLabel.isBlank()) {
            strengthLabel = version.defaultStrength();
        }
        List<Long> effectiveAssets = new ArrayList<>();
        if (capability == ImageCapability.EDIT) {
            for (String field : List.of("image1", "image2", "image3")) {
                Long assetId = longOf(fields.get(field));
                if (assetId != null) {
                    repository.requireOwnedAsset(assetId, tenantId, userId);
                    effectiveAssets.add(assetId);
                }
            }
        } else if (capability.requiresImage()) {
            Long assetId = longOf(fields.get("img"));
            if (assetId != null) {
                repository.requireOwnedAsset(assetId, tenantId, userId);
                effectiveAssets.add(assetId);
            }
        }

        String prompt = command.prompt();
        preparer.validateFields(capability, version, new ImageTemplatePreparer.ImageFields(
            prompt, command.negativePrompt(), sizeLabel, strengthLabel,
            effectiveAssets.stream().map(String::valueOf).toList(), 0L));

        // 3) 幂等：同键已有任务直接返回
        String idempotencyKey = command.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Long existing = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existing != null) {
                Map<String, Object> task = repository.requireOwnedTask(existing, tenantId, userId);
                return new Submission(existing, String.valueOf(task.get("task_no")), tenantId, userId,
                    String.valueOf(task.get("status")), false, "IDEMPOTENT", true);
            }
        }

        // 4) 入库
        long taskId = IdGeneratorUtil.nextLongId();
        String taskNo = taskNoOf(taskId);
        String taskName = command.taskName();
        String inputJson;
        try {
            inputJson = MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            inputJson = null;
        }
        try {
            repository.insertTask(new ImageTaskRepository.TaskRow(
                taskId, tenantId, userId, taskNo,
                taskName == null || taskName.isBlank()
                    ? capability.label() + " · " + version.modelCode() : taskName,
                capability.code().toUpperCase(), command.workflowCode(), version.version(), version.modelCode(),
                ImageTaskStatus.QUEUED.name(), sizeLabel, strengthLabel,
                prompt, command.negativePrompt(), inputJson, idempotencyKey, deptId));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            Long existing = idempotencyKey == null ? null
                : repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existing != null) {
                Map<String, Object> task = repository.requireOwnedTask(existing, tenantId, userId);
                return new Submission(existing, String.valueOf(task.get("task_no")), tenantId, userId,
                    String.valueOf(task.get("status")), false, "IDEMPOTENT", true);
            }
            throw e;
        }
        repository.appendEvent(IdGeneratorUtil.nextLongId(), taskId, tenantId,
            new AtomicInteger().incrementAndGet(), "CREATED", "任务已创建");

        // 5) 派发
        if (!dispatch) {
            return new Submission(taskId, taskNo, tenantId, userId,
                ImageTaskStatus.QUEUED.name(), false, "NOT_DISPATCHED", false);
        }
        Map<String, Object> task = repository.requireOwnedTask(taskId, tenantId, userId);
        ImageTaskDispatchService.Outcome outcome =
            dispatchService.dispatch(taskId, () -> buildContext(task, tenantId, userId));
        boolean accepted = outcome == ImageTaskDispatchService.Outcome.ACCEPTED
            || outcome == ImageTaskDispatchService.Outcome.ALREADY_CLAIMED;
        return new Submission(taskId, taskNo, tenantId, userId,
            String.valueOf(task.get("status")), accepted, outcome.name(), false);
    }

    /**
     * 读取任务状态（归属校验）。
     *
     * @param imageTaskId 内核任务 ID
     * @param tenantId    执行者租户
     * @param userId      执行者用户
     * @return 任务行（snake_case 字段：status / output_asset_id / output_width / error_message …）
     */
    public Map<String, Object> statusOf(long imageTaskId, String tenantId, long userId) {
        return repository.requireOwnedTask(imageTaskId, tenantId, userId);
    }

    /**
     * 派发一个仍在 QUEUED 的任务。
     *
     * <p><b>存在意义</b>：入库成功但派发时线程池队列已满（{@code QUEUE_FULL}）的任务会永远停在
     * QUEUED——内核没有定时补派发。视觉工厂按屏批量出图时很容易撞上这种情况，
     * 因此在轮询刷新时会顺手补一次派发。</p>
     *
     * @param imageTaskId 内核任务 ID
     * @param tenantId    执行者租户
     * @param userId      执行者用户
     * @return 派发结果（outcome 为 ACCEPTED / ALREADY_CLAIMED / QUEUE_FULL / INVALID_STATUS）
     */
    public String dispatchQueued(long imageTaskId, String tenantId, long userId) {
        Map<String, Object> task = repository.requireOwnedTask(imageTaskId, tenantId, userId);
        String status = String.valueOf(task.get("status"));
        if (!ImageTaskStatus.QUEUED.name().equals(status)) {
            return "INVALID_STATUS";
        }
        return dispatchService.dispatch(imageTaskId, () -> buildContext(task, tenantId, userId)).name();
    }

    /**
     * 取消排队中的任务。
     *
     * @param imageTaskId 内核任务 ID
     * @param tenantId    执行者租户
     * @param userId      执行者用户
     * @return 是否取消成功
     */
    public boolean cancel(long imageTaskId, String tenantId, long userId) {
        repository.requireOwnedTask(imageTaskId, tenantId, userId);
        return repository.cancelQueued(imageTaskId, tenantId, userId) > 0;
    }

    /**
     * 可提交的工作流（供视觉工厂在页面上列出可选能力）。
     */
    public List<ImageWorkflowVersion> availableWorkflows() {
        return registry.registeredVersions().stream()
            .filter(ImageWorkflowVersion::isTestable)
            .toList();
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private ImageTaskOrchestrator.TaskContext buildContext(Map<String, Object> task, String tenantId, long userId) {
        List<Long> inputAssetIds = new ArrayList<>();
        Object raw = task.get("input_json");
        if (raw != null) {
            try {
                Map<String, Object> fields = asMap(MAPPER.readValue(String.valueOf(raw), Map.class));
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
            Objects.toString(task.get("prompt"), null),
            Objects.toString(task.get("negative_prompt"), null),
            Objects.toString(task.get("size_label"), null),
            Objects.toString(task.get("strength_label"), null),
            inputAssetIds,
            false,
            IdGeneratorUtil::nextLongId,
            new AtomicInteger());
    }

    private static String taskNoOf(long taskId) {
        return "IMAGE-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + String.format("%06d", Math.floorMod(taskId, 1_000_000L));
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
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

}
