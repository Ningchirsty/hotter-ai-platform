package org.dromara.aigov.workspace.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户 ↔ 品牌归属数据源配置（{@code aigov.user-brand.*}；④）。
 *
 * <p><b>默认关闭</b>：数据源要求 {@code aig_user_brand} 表已建、且运维已登记归属。
 * 关着时走 {@code AigUserBrandResolverNotYetAvailable}（空集），含义是
 * "按品牌定向的绑定暂时对谁都不生效"——fail-closed，而不是"所有人都没有品牌"。</p>
 *
 * <p>（本仓没有 {@code @ConfigurationPropertiesScan}，配置类必须自己带 {@code @Component}。）</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.user-brand")
public class AigUserBrandProperties {

    /**
     * 是否启用"用户↔品牌归属"数据源（默认关闭；打开前必须先执行 aig_user_brand.sql）
     */
    private boolean enabled = false;

}
