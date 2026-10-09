package org.dromara.aigov.token.config;

import org.dromara.aigov.token.controller.AigServiceTokenController;
import org.dromara.aigov.token.filter.AigServiceTokenFilter;
import org.dromara.aigov.token.mapper.AigServiceTokenMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 服务令牌能力的<b>装配</b>测试（不是逻辑测试）。
 *
 * <p><b>为什么必须有这一类测试</b>：这次真的踩到了。{@code AigServiceTokenProperties} 一开始
 * 只写了 {@code @ConfigurationProperties}、没写 {@code @Component}——而本模块没有
 * {@code @ConfigurationPropertiesScan}，于是这个"配置类"根本不是 Bean。后果不是某个功能不好用，
 * 而是<b>注入它的管理接口让整个后端启动失败</b>。单测抓不到：单测里配置类是手工 {@code new} 的。</p>
 *
 * <p>更阴的一点是"开关关着反而起不来"：{@code AigServiceTokenWebConfig} 带
 * {@code @ConditionalOnProperty}，默认关闭时它整类不生效——把它当作配置类的注册来源，
 * 等于把"能不能启动"绑在了"功能开没开"上。所以这里两个状态都测。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigServiceTokenWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(AigServiceTokenMapper.class, () -> mock(AigServiceTokenMapper.class));

    @Test
    @DisplayName("★ 开关关闭（默认值）时容器也必须能起来，且配置类与接口都是 Bean")
    void contextStartsWithFeatureDisabled() {
        runner.withUserConfiguration(TokenPackageScan.class)
            .withPropertyValues("aigov.service-token.enabled=false")
            .run(context -> {
                assertNull(context.getStartupFailure(),
                    "默认关闭时容器起不来 = 上线即故障：" + context.getStartupFailure());
                assertNotNull(context.getBean(AigServiceTokenProperties.class),
                    "配置类必须任何开关状态下都是 Bean（管理接口要注入它）");
                assertNotNull(context.getBean(AigServiceTokenController.class),
                    "管理接口自身也必须能建出来（它的依赖必须都存在）");
                assertFalse(context.getBean(AigServiceTokenProperties.class).isEnabled());
                assertEquals(0, context.getBeansOfType(FilterRegistrationBean.class).size(),
                    "开关关闭时不得注册过滤器");
            });
    }

    @Test
    @DisplayName("★ 开关打开时：注册过滤器，路径与顺序都符合约定")
    void filterIsRegisteredOnlyWhenEnabled() {
        runner.withUserConfiguration(TokenPackageScan.class)
            .withPropertyValues("aigov.service-token.enabled=true")
            .run(context -> {
                assertNull(context.getStartupFailure(), "开关打开时容器起不来：" + context.getStartupFailure());

                FilterRegistrationBean<?> registration =
                    context.getBean("aigServiceTokenFilter", FilterRegistrationBean.class);
                assertEquals(List.of("/aigov/*"), List.copyOf(registration.getUrlPatterns()),
                    "默认只覆盖 /aigov/*（不默认全站）");
                assertEquals(Ordered.HIGHEST_PRECEDENCE + 100, registration.getOrder(),
                    "顺序要早于业务过滤器，又不抢最前面");
                assertEquals(AigServiceTokenFilter.class, registration.getFilter().getClass());
            });
    }

    @Test
    @DisplayName("★ 约定守卫：模块内每个 @ConfigurationProperties 类都必须是 Bean")
    void everyConfigurationPropertiesClassIsABean() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));

        int checked = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents("org.dromara.aigov")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            checked++;
            assertNotNull(AnnotationUtils.findAnnotation(type, Component.class),
                type.getName() + " 带 @ConfigurationProperties 但不是 Bean（缺 @Component）："
                    + "本模块没有 @ConfigurationPropertiesScan，注入它的类会在启动时失败");
        }
        assertTrue(checked >= 8, "扫描到的配置类太少（" + checked + "），可能是扫描包写错了，别让这条守卫变成空跑");
    }

    /**
     * 只扫描本能力所在的包，避免把整个模块的 Bean 都拖进这个轻量上下文。
     *
     * <p>注意写的是<b>包名</b>而不是 {@code basePackageClasses}：后者只扫描"该类所在包及其子包"，
     * 会漏掉同级的 {@code token.controller} / {@code token.service}——
     * 那会让这个装配测试变成"看起来在测、实际什么都没装配"。</p>
     */
    @Configuration
    @ComponentScan("org.dromara.aigov.token")
    static class TokenPackageScan {
    }

}
