package org.dromara.ai.image.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.comfy.ImageComfyClient;
import org.dromara.ai.image.service.ImageAssetProbe;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageContractDbSync;
import org.dromara.ai.image.service.ImageTaskDispatchService;
import org.dromara.ai.image.service.ImageTaskExecutionService;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.image.service.ImageWorkflowVersionRepository;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.LocalFileAssetStorage;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 图像创作模块装配（默认关闭：{@code image.enabled=false}）。
 *
 * <p><b>为什么不把 {@code ComfyClient} / {@code AssetStorage} 注册成 Spring Bean</b>：
 * 视频模块用 {@code @ConditionalOnMissingBean} 装配同名类型。如果图像模块也注册一个
 * 同类型 Bean，装配顺序一旦不利，视频模块就会跳过自己的 Bean、拿到图像模块的实例
 * （存储根目录、ComfyUI 客户端语义都会被串味），而视频链路是已实测通过的。因此这里
 * 在 Bean 方法内部<b>直接构造</b>这两个依赖并显式注入给图像侧组件，类型上互不干扰。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ImageModuleConfiguration.ImageProperties.class)
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageModuleConfiguration {

    /**
     * 图像创作模块配置。
     */
    @Data
    @ConfigurationProperties(prefix = "image")
    public static class ImageProperties {

        /**
         * 是否启用图像创作模块。
         */
        private boolean enabled = false;

        /**
         * 契约与模板所在的后端受控目录。
         */
        private String contractRoot = "script";

        /**
         * 素材与产出目录。
         */
        private String storageRoot = "/opt/ai-video-poc/data/image-assets";

        /**
         * ComfyUI 地址（不要填回环地址）。
         */
        private String comfyBaseUrl = "";

        /**
         * 是否允许回环地址（仅同机进程直连联调）。
         */
        private boolean comfyAllowLoopback = false;

        /**
         * 轮询总预算（秒），模板未声明 timeoutSeconds 时生效。
         */
        private long pollBudgetSeconds = 300;

        /**
         * 轮询间隔（秒）。
         */
        private long pollIntervalSeconds = 3;

        /**
         * 正式环境必须为 true：只有 PUBLISHED 工作流可提交。
         */
        private boolean requirePublished = true;

        /**
         * 隔离联调允许提交的 TESTING 工作流（逗号分隔），require-published=true 时被忽略。
         */
        private String testingWorkflows = "";

        /**
         * 后台并发（与 ComfyUI 实例/显卡数一致）。
         */
        private int concurrency = 1;

        /**
         * 执行队列上限。
         */
        private int executorQueueCapacity = 16;

        /**
         * 是否把契约同步到 image_workflow_version 镜像表。
         */
        private boolean syncContractToDb = true;

        /**
         * 启动时把上次遗留的 RUNNING 任务收敛为失败。
         */
        private boolean failStaleRunningOnStartup = true;

        /**
         * 提交前是否请求 ComfyUI 释放显存（会让下次生成重新加载权重，默认关闭）。
         */
        private boolean comfyFreeBeforeSubmit = false;
    }

    /**
     * 构造图像模块自用的 {@code ObjectMapper}，<b>不注册成 Spring Bean</b>。
     *
     * <p><b>这是一个真实的生产事故换来的教训</b>：视频模块已经注册了 {@code videoObjectMapper}，
     * 图像模块再注册一个 {@code imageObjectMapper} 后，两个模块同时启用（生产就是这种情况）时
     * 容器里出现两个 {@code ObjectMapper} 候选，按类型注入一律失败：</p>
     *
     * <pre>
     * Unsatisfied dependency expressed through constructor parameter 5:
     * No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available:
     * expected single matching bean but found 2: imageObjectMapper,videoObjectMapper
     * </pre>
     *
     * <p>结果就是后端启动即崩、生产整站不可用，只能回滚。因此图像模块<b>不贡献任何
     * ObjectMapper Bean</b>，需要的地方各自 new 一个（廉价且无状态），从根上消除歧义。
     * 这条约束由 {@code VideoImageCoexistenceTest} 守住。</p>
     */
    private ObjectMapper newMapper() {
        // 刻意不使用 Web 层的 JsonMapper：RuoYi v6 只定制了 HTTP 用的 JsonMapper
        return new ObjectMapper();
    }

    @Bean
    public ImageTemplatePreparer imageTemplatePreparer() {
        return new ImageTemplatePreparer(newMapper());
    }

    @Bean
    public ImageWorkflowContractRegistry imageWorkflowContractRegistry(ImageProperties properties) {
        Path root = Path.of(properties.getContractRoot());
        ImageWorkflowContractRegistry registry = new ImageWorkflowContractRegistry(root, newMapper());
        registry.load();
        applyTestingWorkflows(registry, properties);
        return registry;
    }

    private void applyTestingWorkflows(ImageWorkflowContractRegistry registry, ImageProperties properties) {
        String configured = properties.getTestingWorkflows();
        if (configured == null || configured.isBlank()) {
            return;
        }
        if (properties.isRequirePublished()) {
            log.error("image.require-published=true 时忽略 image.testing-workflows（正式环境不得提交 TESTING 工作流）");
            return;
        }
        for (String code : configured.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                registry.markTesting(trimmed);
            }
        }
    }

    @Bean
    public ImageAssetProbe imageAssetProbe() {
        return new ImageAssetProbe();
    }

    @Bean
    public ImageAssetStore imageAssetStore(ImageProperties properties) {
        return new ImageAssetStore(createAssetStorage(properties));
    }

    @Bean
    public ImageTaskOrchestrator imageTaskOrchestrator(ImageWorkflowContractRegistry registry,
                                                       ImageTemplatePreparer preparer,
                                                       ImageTaskRepository repository,
                                                       ImageAssetStore assetStore,
                                                       ImageAssetProbe probe,
                                                       ImageProperties properties) {
        ImageComfyClient client = new ImageComfyClient(
            properties.getComfyBaseUrl(), newMapper(), properties.isComfyAllowLoopback());
        log.info("图像创作模块 ComfyUI 端点：{}", client.getBaseUrl());
        return new ImageTaskOrchestrator(
            registry, preparer, repository, assetStore, probe, client,
            IdGeneratorUtil::nextLongId,
            Duration.ofSeconds(properties.getPollBudgetSeconds()),
            Duration.ofSeconds(Math.max(1, properties.getPollIntervalSeconds())),
            properties.isComfyFreeBeforeSubmit());
    }

    @Bean(destroyMethod = "shutdown")
    public ImageTaskExecutionService imageTaskExecutionService(ImageTaskOrchestrator orchestrator,
                                                               ImageProperties properties) {
        ImageTaskExecutionService service = new ImageTaskExecutionService(
            orchestrator::execute, properties.getExecutorQueueCapacity(), properties.getConcurrency());
        log.info("图像任务后台执行器已装配：并发 {}，队列上限 {}", service.concurrency(), service.queueCapacity());
        return service;
    }

    @Bean
    public ImageTaskDispatchService imageTaskDispatchService(ImageTaskRepository repository,
                                                             ImageTaskExecutionService executionService) {
        return new ImageTaskDispatchService(repository, executionService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "image", name = "sync-contract-to-db", havingValue = "true", matchIfMissing = true)
    public ImageContractDbSync imageContractDbSync(ImageWorkflowContractRegistry registry,
                                                   ImageWorkflowVersionRepository repository,
                                                   ImageProperties properties) {
        log.info("已启用图像契约→数据库同步镜像（运行时仍以契约文件为权威）");
        return new ImageContractDbSync(registry, repository, newMapper(), Path.of(properties.getContractRoot()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "image", name = "fail-stale-running-on-startup", havingValue = "true",
        matchIfMissing = true)
    public ApplicationRunner imageStaleRunningTaskReconciler(ImageTaskRepository repository) {
        return (ApplicationArguments args) -> {
            int moved = repository.failAllRunning("ORPHANED_BY_RESTART", "服务重启导致执行中断，请重新执行该任务");
            if (moved > 0) {
                log.warn("启动收敛：{} 个图像任务因上次进程退出而中断，已置为 FAILED（ORPHANED_BY_RESTART）", moved);
            }
        };
    }

    /**
     * 图像模块自有存储实例：不注册为 Bean（见类注释），避免与视频模块的同类型 Bean 互相顶替。
     */
    private AssetStorage createAssetStorage(ImageProperties properties) {
        return new LocalFileAssetStorage(Path.of(properties.getStorageRoot()));
    }
}
