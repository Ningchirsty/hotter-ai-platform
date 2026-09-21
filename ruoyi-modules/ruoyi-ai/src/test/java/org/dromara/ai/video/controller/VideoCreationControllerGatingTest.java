package org.dromara.ai.video.controller;

import org.dromara.ai.video.config.VideoModuleConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 视频模块「默认关闭」必须真的能安全启动。
 *
 * <p>这是对本模块一个部署隐患的守护：{@link VideoCreationController} 曾是无条件注册的
 * {@code @RestController}，而它的依赖 Bean 全部来自 {@link VideoModuleConfiguration}
 * （带 {@code @ConditionalOnProperty(video.enabled=true)}）。属性缺失时配置类被跳过，
 * 控制器却仍要实例化 → {@code UnsatisfiedDependencyException} → <b>整个若依应用启动失败</b>。
 * 也就是说文档里写的「默认关闭」当时等于「默认起不来」。</p>
 *
 * <p>离线验证，不需要数据库 / Redis / ComfyUI。</p>
 */
class VideoCreationControllerGatingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(VideoModuleConfiguration.class, VideoCreationController.class);

    @Test
    @DisplayName("video.enabled 未配置（默认）：上下文安全启动，控制器不注册")
    void disabledByDefaultStartsCleanly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(VideoCreationController.class);
            assertThat(context).doesNotHaveBean(org.dromara.ai.video.service.WorkflowContractRegistry.class);
        });
    }

    @Test
    @DisplayName("video.enabled=false：同样安全启动")
    void explicitlyDisabledStartsCleanly() {
        runner.withPropertyValues("video.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(VideoCreationController.class);
        });
    }
}
