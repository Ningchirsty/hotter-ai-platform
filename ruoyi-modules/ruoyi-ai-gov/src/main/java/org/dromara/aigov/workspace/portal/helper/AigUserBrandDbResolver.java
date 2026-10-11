package org.dromara.aigov.workspace.portal.helper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.domain.AigUserBrand;
import org.dromara.aigov.workspace.portal.mapper.AigUserBrandMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库版用户↔品牌归属解析（④；{@code aigov.user-brand.enabled=true} 时装配）。
 *
 * <h3>两条刻意的取舍</h3>
 * <ol>
 *     <li><b>查询失败按"没有品牌"处理（fail-closed），不让门户 500</b>：可见性判定的输入缺一块时，
 *         正确方向是"少给一点"而不是"多给一点"；而门户打不开是比"按品牌定向的岗位暂时看不到"
 *         严重得多的故障。失败会 WARN 留痕，不静默。</li>
 *     <li><b>只认启用中的行</b>（{@code status='0'}）：停用是一条"暂时不参与判定"的表达，
 *         删除才用 {@code del_flag}（{@code @TableLogic} 已自动过滤）。</li>
 * </ol>
 *
 * <p>与默认实现的切换由 {@code aigov.user-brand.enabled} 决定：两个 Bean 的
 * {@code @ConditionalOnProperty} 互斥（true / false+matchIfMissing），不会同时装配。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigov.user-brand", name = "enabled", havingValue = "true")
public class AigUserBrandDbResolver implements AigUserBrandResolver {

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    private final AigUserBrandMapper userBrandMapper;

    @Override
    public Set<Long> brandsOf(Long userId) {
        if (userId == null || userId <= 0) {
            return Set.of();
        }
        try {
            List<AigUserBrand> rows = userBrandMapper.selectList(Wrappers.<AigUserBrand>lambdaQuery()
                .eq(AigUserBrand::getUserId, userId)
                .eq(AigUserBrand::getStatus, STATUS_NORMAL));
            Set<Long> brands = new LinkedHashSet<>();
            if (rows != null) {
                for (AigUserBrand row : rows) {
                    if (row != null && row.getBrandId() != null) {
                        brands.add(row.getBrandId());
                    }
                }
            }
            return brands;
        } catch (Exception e) {
            // fail-closed：宁可"按品牌定向的岗位暂时看不到"，也不要门户整页打不开
            log.warn("读取用户品牌归属失败，按「没有品牌」处理（fail-closed）：userId={}, 异常={}",
                userId, e.getClass().getSimpleName());
            return Set.of();
        }
    }

    @Override
    public boolean available() {
        return true;
    }

}
