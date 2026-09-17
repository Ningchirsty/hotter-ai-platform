package org.dromara.ai.video.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块装配守卫。
 *
 * <p>存在的理由是一次真实的生产事故：{@code VideoCreationController} 通过构造器注入了
 * {@code VideoTierResolutions}，但该类当时只是 {@code VideoProperties} 里的一个嵌套属性对象，
 * <b>没有注册成 Spring bean</b>。单元测试全部通过（它们直接 new 对象），CI 也是绿的，
 * 直到部署时才以
 * {@code APPLICATION FAILED TO START ... required a bean of type 'VideoTierResolutions'}
 * 崩溃——一次本可在测试阶段拦住的故障。</p>
 *
 * <p>因此这里做三件事：</p>
 * <ol>
 *   <li>断言 {@code VideoTierResolutions} 被声明为 {@code @ConfigurationProperties}
 *       并已列入 {@code @EnableConfigurationProperties}（bean 能存在的充要条件）；</li>
 *   <li>断言配置类的 bean 工厂方法可被调用且不返回 null（防止方法签名与调用方脱节）；</li>
 *   <li>断言「从属性对象取出的档位表」与「独立实例」行为一致
 *       （防止两条路径各自演化出错）。</li>
 * </ol>
 */
class VideoModuleWiringTest {

    @Test
    @DisplayName("装配：VideoTierResolutions 必须是可注入的 @ConfigurationProperties bean")
    void tierResolutionsIsAConfigurationPropertiesBean() {
        ConfigurationProperties annotation =
            VideoTierResolutions.class.getAnnotation(ConfigurationProperties.class);
        assertNotNull(annotation,
            "VideoTierResolutions 必须标注 @ConfigurationProperties，否则无法作为 bean 注入"
                + "（曾因此导致生产启动失败）");
        assertEquals("video.tier-resolutions", annotation.prefix(),
            "前缀应为 video.tier-resolutions");

        EnableConfigurationProperties enabled =
            VideoModuleConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertNotNull(enabled, "配置类必须声明 @EnableConfigurationProperties");
        assertTrue(Arrays.asList(enabled.value()).contains(VideoTierResolutions.class),
            "VideoTierResolutions 必须列入 @EnableConfigurationProperties，"
                + "否则控制器注入时会报 No qualifying bean；当前为 " + Arrays.toString(enabled.value()));
        assertTrue(Arrays.asList(enabled.value()).contains(VideoModuleConfiguration.VideoProperties.class),
            "VideoProperties 也必须列入");
    }

    @Test
    @DisplayName("装配：配置类的 bean 工厂方法可调用且返回非 null")
    void beanFactoryMethodsProduceInstances() {
        VideoModuleConfiguration configuration = new VideoModuleConfiguration();
        VideoModuleConfiguration.VideoProperties properties = new VideoModuleConfiguration.VideoProperties();

        assertNotNull(configuration.h3TemplatePreparer(new com.fasterxml.jackson.databind.ObjectMapper(),
            properties), "h3TemplatePreparer bean 不得为 null");
        assertNotNull(properties.getTierResolutions(), "VideoProperties 必须携带档位表");
        assertNotNull(properties.getTierResolutions().getDurations(), "时长矩阵不得为 null");
    }

    @Test
    @DisplayName("装配：属性对象与独立实例的档位/时长矩阵一致")
    void propertiesAndStandaloneInstanceAgree() {
        VideoTierResolutions fromProperties =
            new VideoModuleConfiguration.VideoProperties().getTierResolutions();
        VideoTierResolutions standalone = new VideoTierResolutions();

        assertEquals(standalone.tierNames(), fromProperties.tierNames(),
            "两条路径的档位集合必须一致");
        for (String tier : standalone.tierNames()) {
            assertEquals(standalone.durationsOf(tier), fromProperties.durationsOf(tier),
                tier + " 的时长矩阵必须一致");
            assertEquals(standalone.of(tier), fromProperties.of(tier),
                tier + " 的分辨率必须一致");
        }
    }

    @Test
    @DisplayName("装配：档位与时长矩阵的具体取值（防止误改默认值）")
    void defaultMatrixIsExactlyAsDesigned() {
        VideoTierResolutions tiers = new VideoTierResolutions();
        assertEquals(List.of("高清 · 1080P", "流畅 · 720P", "标清 · 480P"),
            List.copyOf(tiers.tierNames()), "档位顺序应稳定（前端按此渲染）");
        assertEquals(List.of("5 秒"), tiers.durationsOf("高清 · 1080P"));
        assertEquals(List.of("5 秒", "10 秒"), tiers.durationsOf("流畅 · 720P"));
        assertEquals(List.of("5 秒", "10 秒", "20 秒"), tiers.durationsOf("标清 · 480P"));
        // 分辨率必须与原 1080P 行为一致（已上线，不能变）
        assertEquals(new VideoTierResolutions.Resolution(1920, 1088, 1920, 1080, 1920),
            tiers.of("高清 · 1080P"));
    }
}
