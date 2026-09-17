package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 视频任务编排：校验 → 填充模板 → 提交 ComfyUI → 轮询 → 归档 → 更新状态。
 *
 * <p>安全边界（交接文档 §5）：</p>
 * <ul>
 *   <li>浏览器不能直接访问 ComfyUI，也不能提交任意节点图；</li>
 *   <li>只有契约 mapping 白名单内的字段会被写入模板；</li>
 *   <li>成片超过时长上限时由服务端截断，并记录 truncation_applied；</li>
 *   <li>素材归属校验同时约束租户与用户。</li>
 * </ul>
 *
 * <p>本类不直接依赖 Spring Web，便于用可控 ComfyUI 替身做离线测试。</p>
 */
@Slf4j
public class VideoTaskOrchestrator {

    private final WorkflowContractRegistry registry;
    private final H3TemplatePreparer preparer;
    private final ComfyClient comfyClient;
    private final VideoTaskRepository repository;
    private final AssetStorage assetStorage;
    private final ObjectMapper mapper;

    /**
     * 轮询上限，避免请求线程无限等待。
     */
    private final Duration pollBudget;

    /**
     * 单次轮询间隔。
     */
    private final Duration pollInterval;

    /**
     * 主键生成器。
     *
     * <p>抽成可注入依赖而非直接调用 {@code IdGeneratorUtil}：后者在静态初始化时
     * 从 Spring 容器取 bean，纯 JUnit 环境下会抛 {@code ExceptionInInitializerError}，
     * 使编排逻辑无法离线测试。</p>
     */
    private final java.util.function.Supplier<Long> idGenerator;

    /**
     * 成片实测与截断。ComfyUI 不返回时长/分辨率，必须由 ffprobe 实测。
     */
    private final MediaProbe mediaProbe;

    /**
     * 提交前是否先请求 ComfyUI 释放显存与模型缓存。
     *
     * <p>ComfyUI 不主动释放缓存，多轮生成后显存可能被历史缓存占满，导致任务在采样
     * 节点拿不到显存而失败。默认关闭（保留 ComfyUI 的复用缓存带来的速度），
     * 由 {@code video.comfy-free-before-submit} 在显存紧张时打开。</p>
     */
    private final boolean freeBeforeSubmit;

    /**
     * 多 GPU 工作节点池。为 null 时退化为「只用 {@link #comfyClient} 这一个实例」的单机模式。
     *
     * <p>一台 ComfyUI 的 history / 输出目录都在进程内，所以一个任务从提交到取回成片
     * 必须始终打在同一台实例上，否则轮询永远查不到 prompt。池化就是保证这件事。</p>
     */
    private final ComfyWorkerPool workerPool;

    /**
     * 提交前要求工作节点至少空闲的显存（MiB）。{@code <= 0} 表示不做闸门检查。
     *
     * <p>H3 单任务峰值实测 80,805 MiB / 81,920 MiB，只要 GPU 上还压着别的进程
     * （vLLM、残留的 python），提交后必然在采样节点 OOM 并白等十几分钟。
     * 与其 OOM，不如提前换一台或者直接快速失败。</p>
     */
    private final long minFreeVramMb;

    /**
     * 从池中借一台工作节点的最长等待时间。
     */
    private final Duration workerAcquireTimeout;

    public VideoTaskOrchestrator(WorkflowContractRegistry registry,
                                 H3TemplatePreparer preparer,
                                 ComfyClient comfyClient,
                                 VideoTaskRepository repository,
                                 AssetStorage assetStorage,
                                 ObjectMapper mapper,
                                 Duration pollBudget,
                                 Duration pollInterval) {
        this(registry, preparer, comfyClient, repository, assetStorage, mapper,
            pollBudget, pollInterval,
            () -> org.dromara.common.mybatis.utils.IdGeneratorUtil.nextLongId(),
            new MediaProbe("ffprobe", "ffmpeg", 120));
    }

    public VideoTaskOrchestrator(WorkflowContractRegistry registry,
                                 H3TemplatePreparer preparer,
                                 ComfyClient comfyClient,
                                 VideoTaskRepository repository,
                                 AssetStorage assetStorage,
                                 ObjectMapper mapper,
                                 Duration pollBudget,
                                 Duration pollInterval,
                                 java.util.function.Supplier<Long> idGenerator) {
        this(registry, preparer, comfyClient, repository, assetStorage, mapper,
            pollBudget, pollInterval, idGenerator, new MediaProbe("ffprobe", "ffmpeg", 120));
    }

    public VideoTaskOrchestrator(WorkflowContractRegistry registry,
                                 H3TemplatePreparer preparer,
                                 ComfyClient comfyClient,
                                 VideoTaskRepository repository,
                                 AssetStorage assetStorage,
                                 ObjectMapper mapper,
                                 Duration pollBudget,
                                 Duration pollInterval,
                                 java.util.function.Supplier<Long> idGenerator,
                                 MediaProbe mediaProbe) {
        this(registry, preparer, comfyClient, repository, assetStorage, mapper,
            pollBudget, pollInterval, idGenerator, mediaProbe, false);
    }

    /**
     * 完整构造器。
     *
     * @param freeBeforeSubmit 提交前是否先请求 ComfyUI 释放显存与模型缓存。
     *                         默认 false；ComfyUI 不主动释放缓存，多轮生成后显存
     *                         可能被历史缓存占满，导致任务在采样节点拿不到显存而失败
     *                         （实测 A100 只剩 14% 空闲时任务在 MiniMaxH3Director 中断）。
     *                         打开后每次生成会重新加载权重，换来的是稳定的显存水位。
     */
    public VideoTaskOrchestrator(WorkflowContractRegistry registry,
                                 H3TemplatePreparer preparer,
                                 ComfyClient comfyClient,
                                 VideoTaskRepository repository,
                                 AssetStorage assetStorage,
                                 ObjectMapper mapper,
                                 Duration pollBudget,
                                 Duration pollInterval,
                                 java.util.function.Supplier<Long> idGenerator,
                                 MediaProbe mediaProbe,
                                 boolean freeBeforeSubmit) {
        this(registry, preparer, comfyClient, repository, assetStorage, mapper,
            pollBudget, pollInterval, idGenerator, mediaProbe, freeBeforeSubmit, null, 0L,
            Duration.ofMinutes(30));
    }

    /**
     * 完整构造器（含多 GPU 工作节点池）。
     *
     * @param workerPool          工作节点池；null 表示单实例模式，直接用 {@code comfyClient}。
     * @param minFreeVramMb       提交前要求的最少空闲显存（MiB）；{@code <= 0} 关闭闸门。
     * @param workerAcquireTimeout 借节点的最长等待时间。
     */
    public VideoTaskOrchestrator(WorkflowContractRegistry registry,
                                 H3TemplatePreparer preparer,
                                 ComfyClient comfyClient,
                                 VideoTaskRepository repository,
                                 AssetStorage assetStorage,
                                 ObjectMapper mapper,
                                 Duration pollBudget,
                                 Duration pollInterval,
                                 java.util.function.Supplier<Long> idGenerator,
                                 MediaProbe mediaProbe,
                                 boolean freeBeforeSubmit,
                                 ComfyWorkerPool workerPool,
                                 long minFreeVramMb,
                                 Duration workerAcquireTimeout) {
        this.freeBeforeSubmit = freeBeforeSubmit;
        this.registry = registry;
        this.preparer = preparer;
        this.comfyClient = comfyClient;
        this.repository = repository;
        this.assetStorage = assetStorage;
        this.mapper = mapper;
        this.pollBudget = pollBudget;
        this.pollInterval = pollInterval;
        this.idGenerator = idGenerator;
        this.mediaProbe = mediaProbe;
        this.workerPool = workerPool;
        this.minFreeVramMb = minFreeVramMb;
        this.workerAcquireTimeout = workerAcquireTimeout == null
            ? Duration.ofMinutes(30) : workerAcquireTimeout;
    }

    /**
     * 执行一次任务。
     *
     * <p>本方法是「任务失败必须落库」的唯一负责人：无论失败发生在提交前（能力/契约/素材/
     * ComfyUI 可达性）还是提交后（下载成片、落盘、ffprobe、分辨率断言、写素材行），
     * 都会把任务置为失败。历史上失败落库只认 {@code QUEUED→FAILED}，任务一旦进入 RUNNING
     * 就再也落不了库，结果是一批「永远运行中、成片已丢失」的僵尸任务。</p>
     *
     * @param context 已通过服务端校验的任务上下文
     * @return 执行结果
     */
    public ExecutionResult execute(TaskContext context) {
        try {
            return doExecute(context);
        } catch (VideoTaskException e) {
            int moved = repository.markFailedIfActive(context.taskId(), e.getErrorCode(), e.getMessage());
            if (moved > 0) {
                log.warn("任务 {} 执行失败，已置为 FAILED：[{}] {}", context.taskId(),
                    e.getErrorCode(), e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            // 非业务异常同样要落库，否则会留下说不清状态的僵尸任务。
            repository.markFailedIfActive(context.taskId(), "EXECUTION_FAILED",
                sanitize(e.getMessage()));
            throw e;
        }
    }

    private ExecutionResult doExecute(TaskContext context) {
        if (workerPool == null || workerPool.size() == 0) {
            return doExecuteOn(context, null);
        }
        // 一个任务从「上传素材 → 提交 → 轮询 → 取回成片」必须始终打在同一台 ComfyUI 上：
        // history 与输出目录都是进程内的，中途换节点就永远轮询不到结果。所以借到节点后
        // 整条链路独占它，直到成片落盘才归还。
        VideoTaskException lastRefusal = null;
        int attempts = Math.max(1, workerPool.size());
        for (int attempt = 0; attempt < attempts; attempt++) {
            ComfyWorkerPool.Lease lease = workerPool.acquire(workerAcquireTimeout);
            if (lease == null) {
                break;
            }
            try {
                return doExecuteOn(context, lease);
            } catch (WorkerRefusedException e) {
                // 这台节点的显存被别的进程占了：已标记冷却，换下一台重试。
                lastRefusal = e;
                log.warn("任务 {} 放弃 GPU 节点 {}：{}", context.taskId(), e.workerName, e.getMessage());
            } finally {
                lease.close();
            }
        }
        if (lastRefusal != null) {
            throw lastRefusal;
        }
        throw VideoTaskException.comfyFailure("GPU 工作节点全部繁忙，等待 "
            + workerAcquireTimeout.toSeconds() + " 秒仍未排到，请稍后重试", null);
    }

    /**
     * 在指定工作节点上执行任务。
     *
     * @param lease 工作节点租约；{@code null} 表示单实例模式，直接使用 {@code comfyClient}。
     */
    private ExecutionResult doExecuteOn(TaskContext context, ComfyWorkerPool.Lease lease) {
        ComfyClient client = lease == null ? comfyClient : lease.client();
        WorkflowVersion version = registry.require(context.workflowCode(), context.requirePublished());
        VideoCapability capability = VideoCapability.parse(context.capabilityCode());
        if (capability == null) {
            throw VideoTaskException.invalidContract("不支持的能力编码：" + context.capabilityCode());
        }
        if (!capability.name().equalsIgnoreCase(version.capabilityCode())) {
            throw VideoTaskException.invalidContract("能力与工作流不匹配");
        }

        // 提交前先确认 ComfyUI 可达，避免把网络问题误报为工作流失败。
        if (!client.isReachable()) {
            // 任务此时已被认领为 RUNNING（见控制器），不能再用 QUEUED 去落库。
            repository.markFailedIfActive(context.taskId(), "COMFY_UNREACHABLE",
                "ComfyUI 服务当前不可达");
            throw VideoTaskException.comfyFailure("ComfyUI 服务当前不可达", null);
        }

        // 可选：先让 ComfyUI 归还显存与模型缓存，避免历史缓存把显存占满导致
        // 本次生成在采样节点拿不到显存而失败。默认关闭，由配置决定。
        if (freeBeforeSubmit && client.freeMemory()) {
            log.info("任务 {} 提交前已请求 ComfyUI 释放显存", context.taskId());
        }

        // 显存闸门：H3 单任务峰值实测 80,805 MiB / 81,920 MiB，GPU 上只要还压着别的
        // 进程（vLLM、残留 python），提交后必然在采样节点 OOM，白等十几分钟才失败。
        // 与其 OOM，不如立刻换一台节点；都在忙就快速失败，把原因明确写给用户。
        assertVramAvailable(client, lease);

        String firstFile = null;
        String lastFile = null;
        if (capability != VideoCapability.T2V) {
            long assetId = capability == VideoCapability.I2V ? context.imageAssetId() : context.firstAssetId();
            firstFile = uploadOwnedAsset(assetId, context, client);
        }
        if (capability == VideoCapability.FL2V) {
            lastFile = uploadOwnedAsset(context.lastAssetId(), context, client);
        }

        JsonNode graph = preparer.prepare(registry.templateOf(context.workflowCode()), capability, version,
            new H3TemplatePreparer.H3Fields(
                context.prompt(),
                capability == VideoCapability.I2V ? firstFile : null,
                capability == VideoCapability.FL2V ? firstFile : null,
                capability == VideoCapability.FL2V ? lastFile : null,
                context.tier(),
                context.durationLabel()));

        String promptId = client.submitPrompt(graph);
        String workerName = lease == null ? null : lease.name();
        repository.markSubmitted(context.taskId(), promptId, 1, workerName);
        appendEvent(context, "SUBMITTED",
            workerName == null ? "已提交 ComfyUI" : "已提交 ComfyUI · GPU " + workerName);

        ComfyOutput output = awaitOutput(promptId, context, client);
        // 此刻任务已经是 RUNNING（认领时落库）。这之后的每一步（下载成片、落盘、
        // ffprobe、分辨率断言、写素材行）失败，都由外层 execute 统一负责落库。
        return archiveOutput(context, version, output, client);
    }

    /**
     * 显存闸门：借到节点后、提交前确认真有空闲显存。
     *
     * @throws WorkerRefusedException 该节点显存不足（调用方应换一台重试）。
     */
    private void assertVramAvailable(ComfyClient client, ComfyWorkerPool.Lease lease) {
        if (minFreeVramMb <= 0) {
            return;
        }
        long freeMb = client.freeVramMb();
        if (freeMb < 0) {
            // 读不到显存信息（老版本 ComfyUI / 接口变更）时不做拦截，避免误杀。
            return;
        }
        if (freeMb >= minFreeVramMb) {
            log.info("任务开工前显存检查通过：空闲 {} MiB ≥ 闸门 {} MiB", freeMb, minFreeVramMb);
            return;
        }
        String detail = "GPU 空闲显存仅 " + freeMb + " MiB，低于本次生成所需的 " + minFreeVramMb + " MiB";
        if (lease != null) {
            workerPool.markUnavailable(lease.name(), detail);
        }
        throw new WorkerRefusedException(lease == null ? "default" : lease.name(), detail);
    }

    /**
     * 节点被占用/显存不足：可换一台重试，因此与普通任务失败区分开。
     */
    private static final class WorkerRefusedException extends VideoTaskException {

        private static final long serialVersionUID = 1L;

        private final String workerName;

        WorkerRefusedException(String workerName, String detail) {
            super("GPU_NOT_READY", workerName + " 暂时不可用：" + detail,
                null);
            this.workerName = workerName;
        }
    }

    /**
     * 把 ComfyUI 产物归档成素材并结算任务。
     *
     * <p>只在任务已进入 RUNNING 之后调用；失败由 {@link #execute} 负责落库。</p>
     */
    private ExecutionResult archiveOutput(TaskContext context, WorkflowVersion version,
                                          ComfyOutput output, ComfyClient client) {
        long maxDurationMillis = resolveDurationCapMillis(context.durationLabel(), version);

        byte[] content = client.fetchOutput(output);
        String storageKey = assetStorage.storeOutput(context.tenantId(), context.userId(),
            context.taskId(), output.fileName(), content, "video/mp4");

        // 关键一步：用 ffprobe 实测成片。
        // ComfyUI 的 SaveVideo 不返回宽高/时长，而模板固定产出 124 帧@24fps = 5.167 秒，
        // 超过产品 5 秒上限；只依赖 ComfyUI 元数据会漏判。
        Path storagePath = assetStorage.localPath(storageKey);
        MediaProbe.Probe probe = mediaProbe.probe(storagePath);
        boolean truncated = false;
        if (probe.measured() && probe.exceeds(maxDurationMillis)) {
            // 传入实测帧率：24fps 下 5.000 秒 = 恰好 120 帧，必须帧精确截断。
            Path capped = mediaProbe.truncate(storagePath, maxDurationMillis, probe.fps());
            if (capped != null && !capped.equals(storagePath)) {
                truncated = true;
                // 截断会生成新文件并删除原文件：存储键必须随之更新，
                // 否则库里存的键指向已删除的文件。
                String cappedKey = assetStorage.keyOf(capped);
                if (cappedKey != null) {
                    storageKey = cappedKey;
                }
                storagePath = capped;
                probe = mediaProbe.probe(capped);
            } else {
                // 截断未成功：如实记录超时事实，不能假装已合规。
                log.warn("任务 {} 成片时长 {}ms 超过上限 {}ms 且截断未生效",
                    context.taskId(), probe.durationMillis(), maxDurationMillis);
            }
        }
        if (probe.measured()) {
            // 分辨率断言必须跟随所选档位：这里曾经写死 1920×1080，开放 720P/480P 后
            // 把已经生成并落盘的 720P 成片误判为不合规，任务卡在 RUNNING、成片被丢弃。
            int[] expected = preparer.expectedOutputSize(context.tier());
            if (expected == null) {
                log.warn("任务 {} 档位 {} 未配置目标分辨率，跳过分辨率断言",
                    context.taskId(), context.tier());
            } else {
                mediaProbe.assertAcceptable(probe, expected[0], expected[1]);
            }
        }

        // 大小与校验和统一以最终落盘文件为准（截断后文件名与内容都已变化）。
        final String finalName = storagePath == null
            ? output.fileName() : storagePath.getFileName().toString();
        long finalSize = content.length;
        String checksum = sha256Bytes(content);
        if (storagePath != null && Files.isRegularFile(storagePath)) {
            try {
                finalSize = Files.size(storagePath);
                checksum = sha256Bytes(Files.readAllBytes(storagePath));
            } catch (java.io.IOException e) {
                log.warn("读取最终成片失败，沿用原始大小与校验和：{}", e.getClass().getSimpleName());
            }
        }

        long outputAssetId = idGenerator.get();
        repository.insertAsset(new VideoTaskRepository.AssetRow(
            outputAssetId, context.tenantId(), context.userId(), context.taskId(), "VIDEO", "OUTPUT",
            finalName, storageKey, "video/mp4", finalSize,
            checksum, probe.width(), probe.height(), probe.durationMillis(), context.deptId()));

        // 任务上的实测指标只能来自 ffprobe：ComfyUI 的产物描述不含宽高/时长，
        // 之前这里误用 output.* 导致 output_width/height/fps/duration_ms 全为 NULL。
        long coverAssetId = outputAssetId;
        repository.markSucceeded(context.taskId(), outputAssetId, coverAssetId,
            probe.width(), probe.height(), probe.fps(), probe.durationMillis(), truncated);
        if (truncated) {
            appendEvent(context, "TRUNCATED",
                "成片实测超过 " + maxDurationMillis + "ms，已截断交付");
        }
        appendEvent(context, "SUCCEEDED", "任务完成");
        // 对外回报实测值与最终文件名，而不是 ComfyUI 的空元数据。
        ComfyOutput measuredOutput = new ComfyOutput(finalName, output.subfolder(), output.type(),
            probe.width(), probe.height(), probe.fps(), probe.durationMillis(), finalSize);
        return new ExecutionResult(context.taskId(), outputAssetId, measuredOutput, truncated);
    }

    private String uploadOwnedAsset(Long assetId, TaskContext context, ComfyClient client) {
        if (assetId == null) {
            throw VideoTaskException.invalidContract("缺少必需的输入素材");
        }
        // 归属校验：同时带租户与用户，跨租户/跨用户一律按不存在处理。
        VideoTaskRepository.AssetRow asset =
            repository.requireOwnedAsset(assetId, context.tenantId(), context.userId());
        byte[] content = assetStorage.read(asset.storageKey());
        String targetName = "hotter_" + context.taskId() + "_" + UUID.randomUUID().toString().substring(0, 8)
            + guessExtension(asset.contentType(), asset.originalName());
        return client.uploadImage(targetName, content,
            asset.contentType() == null ? "image/png" : asset.contentType());
    }

    /**
     * 计算成片时长上限（毫秒）。
     *
     * <p>契约的 {@code maxDurationSeconds} 是「这一代工作流允许的最长时长」，
     * 而不是「本次任务的目标时长」——模板固定产出 124 帧@24fps = 5.167 秒，
     * 所以原先一律截断到 5 秒是对的；但开放 10/20 秒档位后，若仍用契约上限截断，
     * 长时长成片会被误截回 5 秒。</p>
     *
     * <p>因此取「任务请求时长」与「契约上限」的较小值：请求 20 秒而契约只允许 5 秒时
     * 仍以 5 秒为准（校验层本应拦住这种提交，这里是纵深防御）。</p>
     */
    public long resolveDurationCapMillis(String durationLabel, WorkflowVersion version) {
        long contractCap = (version.maxDurationSeconds() == null ? 5 : version.maxDurationSeconds()) * 1000L;
        int requested = H3TemplatePreparer.parseDurationSeconds(durationLabel);
        if (requested <= 0) {
            return contractCap;
        }
        return Math.min(contractCap, requested * 1000L);
    }

    private ComfyOutput awaitOutput(String promptId, TaskContext context, ComfyClient client) {
        Instant deadline = Instant.now().plus(pollBudget);
        while (Instant.now().isBefore(deadline)) {
            ComfyClient.PollResult result = client.poll(promptId);
            switch (result.state()) {
                case SUCCEEDED -> {
                    if (result.outputs().isEmpty()) {
                        throw VideoTaskException.outputInvalid("ComfyUI 未返回可用的视频输出");
                    }
                    return result.outputs().get(0);
                }
                case FAILED -> {
                    repository.transition(context.taskId(), VideoTaskStatus.RUNNING,
                        VideoTaskStatus.FAILED, "COMFY_EXECUTION_FAILED",
                        sanitize(result.error()));
                    appendEvent(context, "FAILED", "ComfyUI 执行失败");
                    throw VideoTaskException.comfyFailure("ComfyUI 执行失败", null);
                }
                case RUNNING -> {
                    try {
                        Thread.sleep(pollInterval.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw VideoTaskException.comfyFailure("任务等待被中断", e);
                    }
                }
                default -> throw new IllegalStateException("未知轮询状态");
            }
        }
        repository.transition(context.taskId(), VideoTaskStatus.RUNNING,
            VideoTaskStatus.TIMEOUT, "COMFY_TIMEOUT", "等待 ComfyUI 输出超时");
        appendEvent(context, "TIMEOUT", "等待输出超时");
        throw new VideoTaskException("COMFY_TIMEOUT", "等待 ComfyUI 输出超时");
    }

    private void appendEvent(TaskContext context, String type, String detail) {
        try {
            long eventId = context.nextEventId();
            repository.appendEvent(eventId, context.taskId(), context.tenantId(),
                context.nextSequence(), type, detail);
        } catch (Exception e) {
            // 事件写入失败不应中断主流程，但必须留痕。
            log.warn("任务 {} 事件写入失败：{}", context.taskId(), e.getClass().getSimpleName());
        }
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "ComfyUI 执行失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /**
     * 对原始字节计算 SHA-256。
     */
    private static String sha256Bytes(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String guessExtension(String contentType, String originalName) {
        if (contentType != null) {
            return switch (contentType.toLowerCase()) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/webp" -> ".webp";
                default -> ".png";
            };
        }
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0) {
                return originalName.substring(dot);
            }
        }
        return ".png";
    }

    /**
     * 一次任务的服务端上下文。
     *
     * @param taskId            任务 ID
     * @param tenantId          租户
     * @param userId            用户
     * @param deptId            部门
     * @param capabilityCode    I2V/T2V/FL2V
     * @param workflowCode      wf-*-h3
     * @param prompt            视频描述
     * @param tier              输出档位
     * @param durationLabel     时长档位
     * @param imageAssetId      I2V 图片素材
     * @param firstAssetId      FL2V 首帧素材
     * @param lastAssetId       FL2V 尾帧素材
     * @param requirePublished  正式环境必须为 PUBLISHED
     */
    public record TaskContext(long taskId, String tenantId, long userId, Long deptId,
                              String capabilityCode, String workflowCode, String prompt,
                              String tier, String durationLabel, Long imageAssetId,
                              Long firstAssetId, Long lastAssetId, boolean requirePublished,
                              java.util.function.LongSupplier eventIdSupplier,
                              java.util.concurrent.atomic.AtomicInteger sequenceCounter) {

        /**
         * 生成事件主键。
         */
        public long nextEventId() {
            return eventIdSupplier == null ? System.nanoTime() : eventIdSupplier.getAsLong();
        }

        /**
         * 生成事件序号。
         */
        public int nextSequence() {
            return sequenceCounter == null ? 1 : sequenceCounter.incrementAndGet();
        }
    }

    /**
     * 执行结果。
     */
    public record ExecutionResult(long taskId, long outputAssetId, ComfyOutput output, boolean truncated) {
    }
}
