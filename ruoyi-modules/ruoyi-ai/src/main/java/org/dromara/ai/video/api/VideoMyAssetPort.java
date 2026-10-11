package org.dromara.ai.video.api;

import org.dromara.ai.asset.MyAssetRowMapper;
import org.dromara.ai.video.cloud.VideoCloudTenantResolver;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 视频域的「我的素材」只读端口（{@link MyAssetPort} 的视频实现）。
 *
 * <h3>归属口径</h3>
 * <p>直接复用视频域自己的查询 {@code VideoTaskRepository#listOwnedAssets}：
 * 条件恒为 {@code tenant_id + user_id + del_flag='0'}，按 id 倒序。
 * 与图像域一致，<b>不另写 SQL</b>。</p>
 *
 * <p>租户复用本模块已有的 {@code VideoCloudTenantResolver}（与视频入口同一套约定）；
 * 模块未上线（{@code video.enabled != true}）时本 Bean 不存在，门户少一组而不是启动失败。</p>
 *
 * @author ai-gov
 */
@Service
@ConditionalOnProperty(prefix = "video", name = "enabled", havingValue = "true")
public class VideoMyAssetPort implements MyAssetPort {

    /**
     * 单次最多取多少条
     */
    private static final int MAX_LIMIT = 50;

    private final VideoTaskRepository repository;
    private final JdbcTemplate jdbc;

    public VideoMyAssetPort(VideoTaskRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public String domain() {
        return "VIDEO";
    }

    @Override
    public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
        if (userId <= 0) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        int from = Math.max(0, offset);
        String tenantId = VideoCloudTenantResolver.resolve(jdbc, userId);
        return repository.listOwnedAssets(tenantId, userId, from, capped).stream()
            .map(row -> MyAssetRowMapper.toDto(domain(), row))
            .filter(Objects::nonNull)
            .toList();
    }

}
