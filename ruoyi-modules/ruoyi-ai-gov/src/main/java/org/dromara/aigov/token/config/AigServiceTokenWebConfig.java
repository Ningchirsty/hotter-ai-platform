package org.dromara.aigov.token.config;

import org.dromara.aigov.token.filter.AigServiceTokenFilter;
import org.dromara.aigov.token.service.IAigServiceLoginAdapter;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册服务令牌过滤器。
 *
 * <p><b>默认不注册</b>（{@code aigov.service-token.enabled=false}）：
 * 认证能力必须"显式打开"，这样一次配置遗漏不会变成权限面变化。</p>
 *
 * <p><b>顺序取 {@code HIGHEST_PRECEDENCE + 100}</b>：要早于业务过滤器（尽早确定身份），
 * 又刻意不抢在最前面（给 traceId/编码等基础设施过滤器留位置）。它与 Sa-Token 的校验
 * 不存在顺序竞争——后者是 MVC 拦截器，必然在过滤器之后。</p>
 *
 * <p><b>这里刻意不写 {@code @EnableConfigurationProperties}</b>：本配置类带条件注解，
 * 开关关闭时整个类不生效，挂在它上面的注册也会跟着失效——而配置类必须<b>任何开关状态下</b>
 * 都是 Bean（管理接口要注入它）。所以由 {@code AigServiceTokenProperties} 自己带
 * {@code @Component}（与模块内其它配置类一致），避免两处注册同一类型。</p>
 *
 * @author ai-gov
 */
@Configuration
@ConditionalOnProperty(prefix = "aigov.service-token", name = "enabled", havingValue = "true")
public class AigServiceTokenWebConfig {

    /**
     * 注册过滤器，并把生效范围限定在配置的路径上（默认 {@code /aigov/*}）。
     *
     * @param tokenService  令牌服务
     * @param loginAdapter  身份登录适配器
     * @param properties    配置
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<AigServiceTokenFilter> aigServiceTokenFilter(
        IAigServiceTokenService tokenService,
        IAigServiceLoginAdapter loginAdapter,
        AigServiceTokenProperties properties) {
        FilterRegistrationBean<AigServiceTokenFilter> bean = new FilterRegistrationBean<>(
            new AigServiceTokenFilter(tokenService, loginAdapter, properties));
        bean.addUrlPatterns(properties.getPathPatterns().toArray(new String[0]));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        bean.setName("aigServiceTokenFilter");
        return bean;
    }

}
