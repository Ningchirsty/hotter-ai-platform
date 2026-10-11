package org.dromara.aigov.workspace.portal.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 品牌归属"尚未接入"时的实现（F-05：不为岗位可见性新造授权体系）。
 *
 * <p>返回空集且自报 {@link #available()} = false。含义必须读准：
 * <b>"按品牌定向的绑定暂时对谁都不生效"</b>，而不是"所有用户都没有品牌"。
 * 前者是可以解释的 fail-closed，后者会把一个"没接数据源"说成一个业务事实。</p>
 *
 * <p><b>与数据库实现互斥</b>：本 Bean 在 {@code aigov.user-brand.enabled=false}（含未配置）时装配；
 * 打开开关后由 {@link AigUserBrandDbResolver} 接管，两者不会同时存在。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "aigov.user-brand", name = "enabled",
    havingValue = "false", matchIfMissing = true)
public class AigUserBrandResolverNotYetAvailable implements AigUserBrandResolver {

    @Override
    public Set<Long> brandsOf(Long userId) {
        return Set.of();
    }

    @Override
    public boolean available() {
        return false;
    }

}
