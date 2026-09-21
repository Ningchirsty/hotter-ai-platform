package org.dromara.ai.image;

import org.dromara.ai.image.config.ImageModuleConfiguration;
import org.dromara.ai.image.controller.ImageCreationController;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskDispatchService;
import org.dromara.ai.image.service.ImageTaskExecutionService;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 图像模块装配测试：**默认关闭时必须能安全启动**。
 *
 * <p>这条测试是针对一个真实的部署隐患写的：控制器是无条件注册的 {@code @RestController}，
 * 而它的依赖 Bean 全部来自 {@code @ConditionalOnProperty(image.enabled=true)} 的配置类。
 * 属性缺失（本模块的默认状态）时配置类被跳过 → 控制器找不到依赖 → <b>整个应用启动失败</b>。
 * 也就是说「默认关闭」如果没有把控制器一起关掉，部署新镜像而不设 {@code IMAGE_ENABLED}
 * 会把整个若依平台拖垮，而不是安静地不启用图像功能。</p>
 *
 * <p>用 {@link ApplicationContextRunner} 离线验证，不需要数据库、Redis 或 ComfyUI。</p>
 */
class ImageModuleWiringTest {

    private static final String COMFY = "http://192.168.2.223:8188";
    private static final String CONTRACT_ROOT = Path.of("..", "..", "script").toAbsolutePath().toString();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ImageModuleConfiguration.class, ImageCreationController.class)
        .withBean(ImageTaskRepository.class, ImageModuleWiringTest::stubRepository)
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

    @Test
    @DisplayName("image.enabled 未配置（默认）：上下文正常启动，且不存在控制器与契约注册表")
    void disabledByDefaultStartsCleanly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ImageCreationController.class);
            assertThat(context).doesNotHaveBean(ImageWorkflowContractRegistry.class);
            assertThat(context).doesNotHaveBean(ImageTaskOrchestrator.class);
        });
    }

    @Test
    @DisplayName("image.enabled=false：同样安全启动，不注册任何图像 Bean")
    void explicitlyDisabledStartsCleanly() {
        runner.withPropertyValues("image.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ImageCreationController.class);
            assertThat(context).doesNotHaveBean(ImageAssetStore.class);
        });
    }

    @Test
    @DisplayName("image.enabled=true 但缺少 comfy-base-url：快速失败并指明原因（不允许半启动）")
    void enabledWithoutComfyUrlFailsFast() {
        ApplicationContextRunner misconfigured = runner.withPropertyValues(
            "image.enabled=true",
            "image.contract-root=" + CONTRACT_ROOT,
            "image.sync-contract-to-db=false",
            "image.fail-stale-running-on-startup=false");
        // 注意：ApplicationContextRunner.run() 不会把启动失败抛出来，失败是交给消费方的
        // （用 assertThatThrownBy 包住 run 会永远「期待抛异常但没抛」，写成那样等于没测）。
        misconfigured.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                .hasMessageContaining("image.comfy-base-url 未配置");
        });
    }

    @Test
    @DisplayName("image.enabled=true：控制器与全部协作 Bean 就位，默认值与契约一致")
    void enabledWiresEverything() {
        runner.withPropertyValues(
                "image.enabled=true",
                "image.contract-root=" + CONTRACT_ROOT,
                "image.comfy-base-url=" + COMFY,
                "image.sync-contract-to-db=false",
                "image.fail-stale-running-on-startup=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ImageCreationController.class);
                assertThat(context).hasSingleBean(ImageWorkflowContractRegistry.class);
                assertThat(context).hasSingleBean(ImageTemplatePreparer.class);
                assertThat(context).hasSingleBean(ImageTaskOrchestrator.class);
                assertThat(context).hasSingleBean(ImageTaskDispatchService.class);
                assertThat(context).hasSingleBean(ImageTaskExecutionService.class);
                assertThat(context).hasSingleBean(ImageAssetStore.class);

                ImageWorkflowContractRegistry registry = context.getBean(ImageWorkflowContractRegistry.class);
                assertThat(registry.registeredCount()).isEqualTo(4);
                assertThat(registry.loadedCount()).isEqualTo(4);

                ImageModuleConfiguration.ImageProperties properties =
                    context.getBean(ImageModuleConfiguration.ImageProperties.class);
                assertThat(properties.isRequirePublished()).isTrue();
                assertThat(properties.getPollIntervalSeconds()).isEqualTo(3);
                assertThat(properties.getConcurrency()).isEqualTo(1);
                assertThat(properties.getContractRoot()).isEqualTo(CONTRACT_ROOT);

                ImageTaskExecutionService execution = context.getBean(ImageTaskExecutionService.class);
                assertThat(execution.concurrency()).isEqualTo(1);
                assertThat(execution.queueCapacity()).isEqualTo(16);
            });
    }

    /**
     * 只满足装配的最小仓储替身（本测试不触碰持久化）。
     */
    private static ImageTaskRepository stubRepository() {
        return new ImageTaskRepository() {
            @Override
            public long insertAsset(AssetRow row) {
                return 0;
            }

            @Override
            public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
                return null;
            }

            @Override
            public long insertTask(TaskRow row) {
                return 0;
            }

            @Override
            public Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey) {
                return null;
            }

            @Override
            public Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId) {
                return Map.of();
            }

            @Override
            public List<Map<String, Object>> listOwnedTasks(String t, long u, String s, int o, int l) {
                return List.of();
            }

            @Override
            public long countOwnedTasks(String t, long u, String s) {
                return 0;
            }

            @Override
            public List<Map<String, Object>> listOwnedAssets(String t, long u, int o, int l) {
                return List.of();
            }

            @Override
            public long countOwnedAssets(String t, long u) {
                return 0;
            }

            @Override
            public int softDeleteAsset(long assetId, String t, long u) {
                return 0;
            }

            @Override
            public int transition(long taskId, org.dromara.ai.image.domain.ImageTaskStatus expectedFrom,
                                  org.dromara.ai.image.domain.ImageTaskStatus target,
                                  String errorCode, String errorMessage) {
                return 0;
            }

            @Override
            public int markFailedIfActive(long taskId, String errorCode, String errorMessage) {
                return 0;
            }

            @Override
            public int failAllRunning(String errorCode, String errorMessage) {
                return 0;
            }

            @Override
            public int markSubmitted(long taskId, String promptId, int attemptCount, String worker) {
                return 0;
            }

            @Override
            public int markSucceeded(long taskId, long outputAssetId, Integer width, Integer height,
                                     boolean hasAlpha, long sizeBytes) {
                return 0;
            }

            @Override
            public void appendEvent(long eventId, long taskId, String tenantId, int sequence,
                                    String eventType, String detail) {
            }

            @Override
            public List<Map<String, Object>> listEvents(long taskId, String tenantId) {
                return List.of();
            }

            @Override
            public int cancelQueued(long taskId, String tenantId, long userId) {
                return 0;
            }
        };
    }
}
