package org.dromara.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.config.ImageModuleConfiguration;
import org.dromara.ai.image.controller.ImageCreationController;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.ai.video.config.VideoModuleConfiguration;
import org.dromara.ai.video.controller.VideoCreationController;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 视频模块 + 图像模块**同时启用**时的装配测试。
 *
 * <p><b>为什么必须单独写这一条</b>：各自的装配测试（{@code VideoModuleWiringTest}、
 * {@code ImageModuleWiringTest}）只注册自己的配置类，因此永远看不到「两个模块一起启用」的组合。
 * 可生产恰恰就是这种组合 —— 结果上线时后端启动即崩、整站不可用，只能回滚：</p>
 *
 * <pre>
 * Unsatisfied dependency expressed through constructor parameter 5:
 * No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available:
 * expected single matching bean but found 2: imageObjectMapper,videoObjectMapper
 * </pre>
 *
 * <p>教训：跨模块的 Bean 冲突（{@code ComfyClient}、{@code AssetStorage}、{@code ObjectMapper}）
 * 只能靠「同时装配」的测试发现，单模块测试与隔离实例（只开一个模块）都测不到。</p>
 */
class VideoImageCoexistenceTest {

    private static final String CONTRACT_ROOT = Path.of("..", "..", "script").toAbsolutePath().toString();
    private static final String COMFY = "http://192.168.2.223:8188";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(VideoModuleConfiguration.class, VideoCreationController.class,
            ImageModuleConfiguration.class, ImageCreationController.class)
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(VideoTaskRepository.class, () -> mock(VideoTaskRepository.class))
        .withBean(ImageTaskRepository.class, () -> mock(ImageTaskRepository.class))
        .withPropertyValues(
            "video.enabled=true",
            "video.contract-root=" + CONTRACT_ROOT,
            "video.comfy-base-url=" + COMFY,
            "video.sync-contract-to-db=false",
            "video.fail-stale-running-on-startup=false",
            "image.enabled=true",
            "image.contract-root=" + CONTRACT_ROOT,
            "image.comfy-base-url=" + COMFY,
            "image.sync-contract-to-db=false",
            "image.fail-stale-running-on-startup=false");

    @Test
    @DisplayName("两个创作模块同时启用：上下文必须能启动，且 ObjectMapper 不出现多个候选")
    void bothModulesCoexist() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VideoCreationController.class);
            assertThat(context).hasSingleBean(ImageCreationController.class);
            assertThat(context).hasSingleBean(org.dromara.ai.video.service.WorkflowContractRegistry.class);
            assertThat(context).hasSingleBean(org.dromara.ai.image.service.ImageWorkflowContractRegistry.class);
            // 真实事故的根因：两个 ObjectMapper 候选导致按类型注入失败。
            // 图像模块不贡献 ObjectMapper Bean（只用视频模块那一个或各自 new），因此这里最多 1 个。
            assertThat(context.getBeansOfType(ObjectMapper.class)).hasSizeLessThanOrEqualTo(1);
        });
    }
}
