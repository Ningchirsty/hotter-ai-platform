package org.dromara.aigov.workspace.portal.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 品牌归属"尚未接入"时的实现（F-05：不为岗位可见性新造授权体系）。
 *
 * <p>返回空集且自报 {@link #available()} = false。含义必须读准：
 * <b>"按品牌定向的绑定暂时对谁都不生效"</b>，而不是"所有用户都没有品牌"。
 * 前者是可以解释的 fail-closed，后者会把一个"没接数据源"说成一个业务事实。</p>
 *
 * <p>业务侧提供用户↔品牌归属后，用一个真实实现替换本 Bean 即可；判定规则不用动。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
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
