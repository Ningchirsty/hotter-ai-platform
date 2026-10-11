package org.dromara.ai.image.api;

import org.dromara.ai.asset.MyAssetRowMapper;
import org.dromara.ai.image.service.ImageInspirationTenantResolver;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图像域的「我的素材」只读端口（{@link MyAssetPort} 的图像实现）。
 *
 * <h3>归属口径</h3>
 * <p>直接复用图像域自己的查询 {@code ImageTaskRepository#listOwnedAssets}：
 * 条件恒为 {@code tenant_id + user_id + del_flag='0'}，按 id 倒序。
 * 这里**不另写 SQL**——"谁的素材"这条规则只能有一处实现，聚合层不应该有第二份。</p>
 *
 * <p>与模块同条件装配（{@code image.enabled=true}）：图像模块没上线时本 Bean 不存在，
 * 门户聚合结果少一组，而不是启动失败。</p>
 *
 * @author ai-gov
 */
@Service
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageMyAssetPort implements MyAssetPort {

    /**
     * 单次最多取多少条（聚合层只是"最近几条"，实现方仍要设上限）
     */
    private static final int MAX_LIMIT = 50;

    private final ImageTaskRepository repository;
    private final ImageInspirationTenantResolver tenantResolver;

    public ImageMyAssetPort(ImageTaskRepository repository, ImageInspirationTenantResolver tenantResolver) {
        this.repository = repository;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public String domain() {
        return "IMAGE";
    }

    @Override
    public List<MyAssetDTO> listMyAssets(long userId, int limit) {
        if (userId <= 0) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        // 租户由本模块自己的解析器从登录账号反查（LoginUser 没有租户字段）
        String tenantId = tenantResolver.resolve(userId);
        return repository.listOwnedAssets(tenantId, userId, 0, capped).stream()
            .map(row -> MyAssetRowMapper.toDto(domain(), row))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

}
