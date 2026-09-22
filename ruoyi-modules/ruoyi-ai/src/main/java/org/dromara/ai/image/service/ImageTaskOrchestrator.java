package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 图像任务编排：可达性预检 → 上传素材 → 填充模板 → 提交 → 轮询 → 实测归档 → 记事件。
 *
 * <p>失败落库只有一个出口（{@link #execute}），因此不会出现「异常被吞掉、任务永远 RUNNING」。</p>
 */
@Slf4j
public class ImageTaskOrchestrator {

    private static final String WORKER_NAME = "default";

    private final ImageWorkflowContractRegistry registry;
    private final ImageTemplatePreparer preparer;
    private final ImageTaskRepository repository;
    private final ImageAssetStore assetStore;
    private final ImageAssetProbe probe;
    private final ComfyClient comfyClient;
    private final ImageWhiteBackgroundCompositor whiteBackgroundCompositor;
    private final Supplier<Long> idGenerator;
    private final Duration pollBudget;
    private final Duration pollInterval;
    private final boolean freeBeforeSubmit;

    public ImageTaskOrchestrator(ImageWorkflowContractRegistry registry,
                                 ImageTemplatePreparer preparer,
                                 ImageTaskRepository repository,
                                 ImageAssetStore assetStore,
                                 ImageAssetProbe probe,
                                 ComfyClient comfyClient,
                                 ImageWhiteBackgroundCompositor whiteBackgroundCompositor,
                                 Supplier<Long> idGenerator,
                                 Duration pollBudget,
                                 Duration pollInterval,
                                 boolean freeBeforeSubmit) {
        this.registry = registry;
        this.preparer = preparer;
        this.repository = repository;
        this.assetStore = assetStore;
        this.probe = probe;
        this.comfyClient = comfyClient;
        this.whiteBackgroundCompositor = whiteBackgroundCompositor;
        this.idGenerator = idGenerator;
        this.pollBudget = pollBudget;
        this.pollInterval = pollInterval;
        this.freeBeforeSubmit = freeBeforeSubmit;
    }

    /**
     * 执行一次任务。异常一律先落库为 FAILED 再抛出。
     */
    public ExecutionResult execute(TaskContext context) {
        try {
            return doExecute(context);
        } catch (ImageTaskException e) {
            int moved = repository.markFailedIfActive(context.taskId(), e.getErrorCode(), e.getMessage());
            if (moved > 0) {
                appendEvent(context, "FAILED", e.getMessage());
                log.warn("图像任务 {} 执行失败，已置为 FAILED：[{}] {}", context.taskId(), e.getErrorCode(), e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            String message = sanitize(e.getMessage());
            repository.markFailedIfActive(context.taskId(), "EXECUTION_FAILED", message);
            appendEvent(context, "FAILED", message);
            log.warn("图像任务 {} 执行异常，已置为 FAILED：{}", context.taskId(), message);
            throw e;
        }
    }

    private ExecutionResult doExecute(TaskContext context) {
        ImageCapability capability = ImageCapability.parse(context.capabilityCode());
        if (capability == null) {
            throw ImageTaskException.invalidContract("不支持的能力编码：" + context.capabilityCode());
        }
        ImageWorkflowVersion version = registry.require(context.workflowCode(), context.requirePublished());
        if (!capability.code().equalsIgnoreCase(version.capabilityCode())) {
            throw ImageTaskException.invalidContract("能力与工作流不匹配：" + capability.code() + " / " + version.workflowCode());
        }

        if (!comfyClient.isReachable()) {
            throw ImageTaskException.comfyFailure("ComfyUI 服务当前不可达（" + version.workflowCode() + "）");
        }
        if (freeBeforeSubmit) {
            comfyClient.freeMemory();
            log.info("图像任务 {} 提交前已请求 ComfyUI 释放显存", context.taskId());
        }

        // 1) 上传输入素材（只接受本人素材，跨用户按不存在处理）
        List<String> uploadedFiles = new ArrayList<>();
        for (Long assetId : context.inputAssetIds()) {
            uploadedFiles.add(uploadOwnedAsset(assetId, context));
        }

        // 2) 填充模板（深拷贝 + 白名单覆写 + 参考图槽位裁剪）
        long seed = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
        ImageTemplatePreparer.ImageFields fields = new ImageTemplatePreparer.ImageFields(
            context.prompt(), context.negativePrompt(), context.sizeLabel(), context.strengthLabel(),
            uploadedFiles, seed);
        ObjectNode graph = preparer.prepare(registry.templateOf(version.workflowCode()), capability, version, fields);

        // 3) 提交
        String promptId = comfyClient.submitPrompt(graph);
        repository.markSubmitted(context.taskId(), promptId, 1, WORKER_NAME);
        appendEvent(context, "SUBMITTED", "已提交 ComfyUI");

        // 4) 轮询
        ComfyOutput output = awaitOutput(promptId, context, version);

        // 5) 归档 + 实测
        return archiveOutput(output, context, version);
    }

    private ComfyOutput awaitOutput(String promptId, TaskContext context, ImageWorkflowVersion version) {
        // 轮询预算取「模板声明」与「全局配置」的较小值：配置是全局上限，模板可更保守
        long globalCap = Math.max(0, pollBudget.toSeconds());
        long budgetSeconds = version.timeoutSeconds() > 0
            ? Math.min(version.timeoutSeconds(), globalCap) : globalCap;
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(budgetSeconds).toMillis();
        while (System.currentTimeMillis() < deadline) {
            ComfyClient.PollResult result = comfyClient.poll(promptId);
            switch (result.state()) {
                case SUCCEEDED -> {
                    if (result.outputs().isEmpty()) {
                        throw ImageTaskException.outputInvalid("ComfyUI 未返回可用的图片输出");
                    }
                    return result.outputs().get(0);
                }
                case FAILED -> {
                    String message = sanitize(result.error());
                    repository.transition(context.taskId(), ImageTaskStatus.RUNNING, ImageTaskStatus.FAILED,
                        "COMFY_EXECUTION_FAILED", message);
                    appendEvent(context, "FAILED", "ComfyUI 执行失败");
                    throw ImageTaskException.comfyFailure(message);
                }
                case RUNNING -> {
                    try {
                        Thread.sleep(Math.max(500, pollInterval.toMillis()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw ImageTaskException.comfyFailure("任务等待被中断");
                    }
                }
                default -> throw ImageTaskException.comfyFailure("未知的 ComfyUI 轮询状态");
            }
        }
        repository.transition(context.taskId(), ImageTaskStatus.RUNNING, ImageTaskStatus.TIMEOUT,
            "COMFY_TIMEOUT", "等待 ComfyUI 输出超时");
        appendEvent(context, "TIMEOUT", "等待 ComfyUI 输出超时");
        throw new ImageTaskException("COMFY_TIMEOUT", "等待 ComfyUI 输出超时（" + budgetSeconds + " 秒）");
    }

    private ExecutionResult archiveOutput(ComfyOutput output, TaskContext context, ImageWorkflowVersion version) {
        byte[] content = comfyClient.fetchOutput(output);
        ImageCapability capability = ImageCapability.parse(version.capabilityCode());
        boolean compositedOnWhite = capability != null && capability.compositesOnWhite();
        if (compositedOnWhite) {
            // 白底图：ComfyUI 只负责给透明蒙版，白底由这里确定性合成（失败即 OUTPUT_INVALID，不产出假白底）
            content = whiteBackgroundCompositor.compositeOnWhite(content);
        }
        long maxBytes = (long) version.maxSizeMb() * 1024 * 1024;
        if (content.length > maxBytes) {
            throw ImageTaskException.outputInvalid("输出图片过大：" + content.length + " 字节，上限 " + version.maxSizeMb() + "MB");
        }
        // 合成后的产物恒为不透明 PNG，不再沿用抠图文件名推断出来的类型
        String contentType = compositedOnWhite ? "image/png" : contentTypeOf(output.fileName(), version.outputMime());
        String storageKey = assetStore.storeOutput(context.tenantId(), context.userId(), context.taskId(),
            output.fileName(), content, contentType);
        if (storageKey == null) {
            throw ImageTaskException.outputInvalid("输出归档失败（存储键为空）");
        }
        Path local = assetStore.localPath(storageKey);
        ImageAssetProbe.Probe probed = local == null
            ? ImageAssetProbe.Probe.unmeasured(content.length)
            : probe.probe(local);
        probe.assertAcceptable(probed, version.maxPixels(), version.requireAlpha());

        long outputAssetId = idGenerator.get();
        repository.insertAsset(new ImageTaskRepository.AssetRow(
            outputAssetId, context.tenantId(), context.userId(), context.taskId(), "IMAGE", "OUTPUT",
            output.fileName(), storageKey, contentType, content.length, null,
            probed.width(), probed.height(), probed.hasAlpha(), context.deptId()));
        repository.markSucceeded(context.taskId(), outputAssetId, probed.width(), probed.height(),
            probed.hasAlpha(), content.length);
        appendEvent(context, "SUCCEEDED", "任务完成");

        log.info("图像任务 {} 完成：{}×{} alpha={} {} 字节 存储键={}",
            context.taskId(), probed.width(), probed.height(), probed.hasAlpha(), content.length, storageKey);
        return new ExecutionResult(context.taskId(), outputAssetId, output,
            probed.width(), probed.height(), probed.hasAlpha());
    }

    private String uploadOwnedAsset(Long assetId, TaskContext context) {
        if (assetId == null) {
            throw ImageTaskException.assetNotFound("缺少必需的输入素材");
        }
        ImageTaskRepository.AssetRow asset = repository.requireOwnedAsset(assetId, context.tenantId(), context.userId());
        byte[] content = assetStore.read(asset.storageKey());
        if (content == null || content.length == 0) {
            throw ImageTaskException.assetNotFound("素材内容为空或已丢失");
        }
        String fileName = "hotter_img_" + context.taskId() + "_"
            + UUID.randomUUID().toString().substring(0, 8) + extensionOf(asset.contentType(), asset.originalName());
        return comfyClient.uploadImage(fileName, content, asset.contentType());
    }

    private void appendEvent(TaskContext context, String eventType, String detail) {
        repository.appendEvent(context.nextEventId(), context.taskId(), context.tenantId(),
            context.nextSequence(), eventType, sanitize(detail));
    }

    private static String contentTypeOf(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return fallback == null || fallback.isBlank() ? "image/png" : fallback;
    }

    private static String extensionOf(String contentType, String originalName) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("png")) {
            return ".png";
        }
        if (type.contains("jpeg") || type.contains("jpg")) {
            return ".jpg";
        }
        if (type.contains("webp")) {
            return ".webp";
        }
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.length() - dot <= 5 ? name.substring(dot) : ".png";
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "任务执行失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /**
     * 一次任务的执行上下文。
     *
     * @param inputAssetIds 输入素材 ID，按槽位顺序（img / image1, image2, image3）
     */
    public record TaskContext(
        long taskId,
        String tenantId,
        long userId,
        Long deptId,
        String capabilityCode,
        String workflowCode,
        String prompt,
        String negativePrompt,
        String sizeLabel,
        String strengthLabel,
        List<Long> inputAssetIds,
        boolean requirePublished,
        LongSupplier eventIdSupplier,
        AtomicInteger sequenceCounter) {

        public TaskContext {
            inputAssetIds = inputAssetIds == null ? List.of() : List.copyOf(inputAssetIds);
        }

        public long nextEventId() {
            return eventIdSupplier.getAsLong();
        }

        public int nextSequence() {
            return sequenceCounter.incrementAndGet();
        }
    }

    /**
     * 执行结果（尺寸与 alpha 均来自实测）。
     */
    public record ExecutionResult(long taskId, long outputAssetId, ComfyOutput output,
                                  Integer width, Integer height, boolean hasAlpha) {
    }
}
