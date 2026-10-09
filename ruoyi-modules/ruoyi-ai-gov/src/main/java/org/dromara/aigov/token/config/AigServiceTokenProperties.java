package org.dromara.aigov.token.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务令牌（机器身份）配置：{@code aigov.service-token.*}
 *
 * <p><b>默认关闭</b>：不配置时本能力完全不生效，行为与之前一模一样。
 * 认证相关的能力"默认打开"是不可接受的——它会让一个配置遗漏变成权限面变化。</p>
 *
 * <p><b>{@code @Component} 不能省</b>：本模块没有 {@code @ConfigurationPropertiesScan}，
 * 全靠组件扫描注册配置类（本模块其它 12 个 Properties 类都是 {@code @Component}）。
 * 少了它，注入本类的管理接口会在启动时找不到 Bean——
 * 而"默认关闭"时 {@code AigServiceTokenWebConfig} 因条件注解不生效，
 * 连它上面的 {@code @EnableConfigurationProperties} 也一起不生效，
 * 于是<b>开关关着反而起不来</b>。{@code AigServiceTokenWiringTest} 专门守这一条。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.service-token")
public class AigServiceTokenProperties {

    /**
     * 是否启用服务令牌认证（默认 false）
     */
    private boolean enabled = false;

    /**
     * 承载服务令牌的请求头名（默认 {@code X-Service-Token}）。
     * 刻意**不复用** {@code Authorization}：那会与人的会话令牌混淆，
     * 排查"这次调用到底是谁"时最容易出错的地方就是两者同源。
     */
    private String headerName = "X-Service-Token";

    /**
     * 生效路径（默认只覆盖 {@code /aigov/*}）。
     * 不默认全站：认证是全局性的，先限定在治理层的路径上，扩大范围应当是显式决定。
     */
    private List<String> pathPatterns = List.of("/aigov/*");

    /**
     * 服务身份会话的超时（秒），默认 300（5 分钟）。
     *
     * <p>这个值不是"会话能活多久"的业务选择，而是<b>堆积量的闸门</b>：适配器每次认证都会新建一个
     * Sa-Token 会话（实测 {@code isShare=true} 不能复用），所以稳态会话数 ≈ 该身份每秒请求数 ×
     * 本值。300 秒意味着 1 QPS 的调用方最多留 300 个很小（~KB 级）的会话。</p>
     */
    private long sessionTimeoutSeconds = 300L;

}
