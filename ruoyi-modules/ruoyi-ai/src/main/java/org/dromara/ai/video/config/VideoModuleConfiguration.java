package org.dromara.ai.video.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.HttpComfyClient;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.LocalFileAssetStorage;
import org.dromara.ai.video.service.MediaProbe;
import org.dromara.ai.video.service.VideoTaskOrchestrator;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.VideoWorkflowVersionRepository;
import org.dromara.ai.video.service.WorkflowContractDbSync;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 视频创作模块装配。
 *
 * <p>全部开关集中在 {@code video.*} 前缀下。默认关闭（{@code video.enabled=false}），
 * 避免在未配置 ComfyUI 的环境里因为缺少地址而启动失败。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({VideoModuleConfiguration.VideoProperties.class,
    VideoTierResolutions.class})
@ConditionalOnProperty(prefix = "video", name = "enabled", havingValue = "true")
public class VideoModuleConfiguration {

    /**
     * 视频创作模块配置。
     */
    @Data
    @ConfigurationProperties(prefix = "video")
    public static class VideoProperties {

        /**
         * 是否启用视频创作模块。
         */
        private boolean enabled = false;

        /**
         * 契约与模板所在的后端受控目录。
         */
        private String contractRoot = "script";

        /**
         * 素材存储根目录（联调环境使用）。
         */
        private String storageRoot = "/opt/ai-video-poc/data/video-assets";

        /**
         * ComfyUI 基础地址。容器内的 127.0.0.1 指向容器自身，必须填实际可达地址。
         */
        private String comfyBaseUrl = "";

        /**
         * 是否允许把回环地址作为 ComfyUI 地址（仅同机进程直连联调时放开）。
         */
        private boolean comfyAllowLoopback = false;

        /**
         * 等待一次任务完成的轮询预算（秒）。
         */
        private long pollBudgetSeconds = 900;

        /**
         * 轮询间隔（秒）。
         */
        private long pollIntervalSeconds = 5;

        /**
         * 提交时是否必须为 PUBLISHED。正式环境保持 true；
         * 隔离联调环境可设为 false 以便验证 TESTING 工作流。
         */
        private boolean requirePublished = true;

        /**
         * 启动时置为 TESTING 的工作流编码（逗号分隔）。
         *
         * <p><b>仅限隔离联调环境</b>。交接文档要求在联调环境把已完成单侧验收的工作流设为
         * TESTING 才能从页面提交验证，而仓库契约必须保持 DRAFT。
         * 生产环境必须保持为空，工作流转 PUBLISHED 必须走审核流程。</p>
         */
        private String testingWorkflows = "";

        /**
         * ffprobe 可执行文件路径。用于实测成片分辨率与时长。
         *
         * <p>ComfyUI 的 SaveVideo 不返回这些字段，而 H3 模板固定产出 124 帧@24fps = 5.167 秒，
         * 超过产品 5 秒上限；没有 ffprobe 就无法判定超时，也无法填写真实分辨率。</p>
         */
        private String ffprobePath = "ffprobe";

        /**
         * ffmpeg 可执行文件路径。用于把超过上限的成片截断到 5 秒以内。
         */
        private String ffmpegPath = "ffmpeg";

        /**
         * 单次 ffprobe/ffmpeg 调用的超时（秒）。
         */
        private long mediaTimeoutSeconds = 120;

        /**
         * 启动时是否把契约文件同步进 {@code video_workflow_version} 表。
         *
         * <p>方案 A：**契约文件是运行时唯一权威**，该表只是同步镜像，供查询与审计。
         * 同步永不改变「是否可提交」的判定；失败也只告警、不影响启动。</p>
         */
        private boolean syncContractToDb = true;

        /**
         * 提交任务前是否先请求 ComfyUI 释放显存与模型缓存（POST /free）。
         *
         * <p>默认 <b>false</b>：ComfyUI 的默认行为是把已加载模型留在显存里复用，
         * 这让连续生成更快。只有在显存确实紧张时才打开。</p>
         *
         * <p>为什么会需要：ComfyUI 不主动释放缓存，多轮生成后显存会被历史缓存占满。
         * 实测过一次 A100 只剩 14% 空闲（{@code torch_vram_free} 近乎 0），任务在
         * {@code MiniMaxH3Director} 节点被中断、报 COMFY_EXECUTION_FAILED；
         * 调用 /free 后空闲显存恢复到 99%，同一工作流即可继续。</p>
         *
         * <p>代价：每次提交都会卸载模型，下一次生成需要重新加载权重（变慢）。
         * 因此这里只提供开关，由运维按显存实际情况决定，代码不擅自改变
         * ComfyUI 的缓存策略（那属于 ComfyUI 侧配置，例如启动参数 --cache-none）。</p>
         */
        private boolean comfyFreeBeforeSubmit = false;

        /**
         * 启动时是否把上一进程遗留的 {@code RUNNING} 任务收敛为失败。
         *
         * <p>任务执行是「请求线程内轮询」模型：执行线程随进程一起消失。因此进程重启时
         * 还处于 RUNNING 的任务，其执行者已经不存在了，永远不会有人来推进它——
         * 结果就是用户看到一条永远「运行中」的任务，既没有成片也没有失败原因。</p>
         *
         * <p>默认开启。部署新版本会打断正在生成的任务，这是无法避免的；
         * 但至少要让状态如实反映「被中断」，而不是留在运行中骗人。</p>
         */
        private boolean failStaleRunningOnStartup = true;

        /**
         * 输出档位（清晰度）→ 分辨率映射。
         *
         * <p>可用 {@code video.tier-resolutions.tiers.<档位名>.*} 覆盖默认值，
         * 例如临时把 720P 改成 960×544 做画质/速度取舍实验，无需改代码。</p>
         */
        private VideoTierResolutions tierResolutions = new VideoTierResolutions();
    }

    /**
     * 模块自有的 JSON 解析器。
     *
     * <p>刻意<b>不</b>复用 Web 层的 {@code JsonMapper}：RuoYi-Vue-Plus v6 只注册定制的
     * JsonMapper（用于 HTTP 报文），并不提供原始 {@code ObjectMapper} bean；
     * 而节点图与 timeline_data 的解析属于服务端内部逻辑，用默认配置更可预期，
     * 也避免被报文层的序列化定制（如 Long 转字符串）影响。</p>
     */
    @Bean
    public ObjectMapper videoObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public WorkflowContractRegistry workflowContractRegistry(VideoProperties properties, ObjectMapper mapper) {
        WorkflowContractRegistry registry =
            new WorkflowContractRegistry(Path.of(properties.getContractRoot()), mapper);
        registry.load();
        if (registry.loadedCount() == 0) {
            log.warn("视频创作模块已启用，但没有任何模板通过校验并加载；提交接口将全部拒绝");
        }
        // 仅隔离联调环境使用：把指定工作流置为 TESTING，使字段校验与提交流程可被真实验证。
        String testing = properties.getTestingWorkflows();
        if (testing != null && !testing.isBlank()) {
            if (properties.isRequirePublished()) {
                log.error("video.require-published=true 时不允许把工作流置为 TESTING，已忽略 video.testing-workflows");
            } else {
                for (String code : testing.split(",")) {
                    String trimmed = code.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (registry.markTesting(trimmed)) {
                        log.warn("隔离联调：工作流 {} 已置为 TESTING（生产环境不得如此配置）", trimmed);
                    } else {
                        log.warn("隔离联调：工作流 {} 无法置为 TESTING（未注册或模板未加载）", trimmed);
                    }
                }
            }
        }
        return registry;
    }

    @Bean
    public H3TemplatePreparer h3TemplatePreparer(ObjectMapper mapper, VideoProperties properties) {
        log.info("输出档位分辨率：{}，时长矩阵：{}", properties.getTierResolutions().tierNames(),
            properties.getTierResolutions().getDurations());
        return new H3TemplatePreparer(mapper, properties.getTierResolutions());
    }

    @Bean
    @ConditionalOnMissingBean(ComfyClient.class)
    public ComfyClient comfyClient(VideoProperties properties, ObjectMapper mapper) {
        return new HttpComfyClient(properties.getComfyBaseUrl(), mapper, properties.isComfyAllowLoopback());
    }

    @Bean
    @ConditionalOnMissingBean(AssetStorage.class)
    public AssetStorage assetStorage(VideoProperties properties) {
        return new LocalFileAssetStorage(Path.of(properties.getStorageRoot()));
    }

    @Bean
    @ConditionalOnMissingBean(MediaProbe.class)
    public MediaProbe mediaProbe(VideoProperties properties) {
        return new MediaProbe(properties.getFfprobePath(), properties.getFfmpegPath(),
            properties.getMediaTimeoutSeconds());
    }

    /**
     * 契约 → 数据库 的同步镜像（方案 A）。
     *
     * <p>只在显式开启时注册；它不参与运行时读取路径，因此即使同步失败，
     * 「可否提交」的判定仍完全由契约文件与校验和决定。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "video", name = "sync-contract-to-db",
        havingValue = "true", matchIfMissing = true)
    public WorkflowContractDbSync workflowContractDbSync(WorkflowContractRegistry registry,
                                                         VideoWorkflowVersionRepository repository,
                                                         ObjectMapper mapper,
                                                         VideoProperties properties) {
        log.info("已启用契约→数据库同步镜像（运行时仍以契约文件为权威）");
        return new WorkflowContractDbSync(registry, repository, mapper,
            Path.of(properties.getContractRoot()));
    }

    @Bean
    public VideoTaskOrchestrator videoTaskOrchestrator(WorkflowContractRegistry registry,
                                                       H3TemplatePreparer preparer,
                                                       ComfyClient comfyClient,
                                                       VideoTaskRepository repository,
                                                       AssetStorage assetStorage,
                                                       ObjectMapper mapper,
                                                       MediaProbe mediaProbe,
                                                       VideoProperties properties) {
        return new VideoTaskOrchestrator(registry, preparer, comfyClient, repository, assetStorage, mapper,
            Duration.ofSeconds(properties.getPollBudgetSeconds()),
            Duration.ofSeconds(properties.getPollIntervalSeconds()),
            () -> org.dromara.common.mybatis.utils.IdGeneratorUtil.nextLongId(),
            mediaProbe,
            properties.isComfyFreeBeforeSubmit());
    }

    /**
     * 启动时收敛上一个进程遗留的 RUNNING 任务。
     *
     * <p>任务执行线程活在请求线程里，进程一重启就没了；留下来的 RUNNING 任务
     * 再也没有执行者，只能永远显示「运行中」。这里把它们如实置为失败。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "video", name = "fail-stale-running-on-startup",
        havingValue = "true", matchIfMissing = true)
    public ApplicationRunner staleRunningTaskReconciler(VideoTaskRepository repository) {
        return args -> {
            int moved = repository.failAllRunning("ORPHANED_BY_RESTART",
                "服务重启导致执行中断，请重新执行该任务");
            if (moved > 0) {
                log.warn("启动收敛：{} 个任务因上次进程退出而中断，已置为 FAILED（ORPHANED_BY_RESTART）", moved);
            }
        };
    }
}
