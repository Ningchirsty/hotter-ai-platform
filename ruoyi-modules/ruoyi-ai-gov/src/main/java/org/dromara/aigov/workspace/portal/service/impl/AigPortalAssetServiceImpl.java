package org.dromara.aigov.workspace.portal.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalAssetGroupVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.service.IAigPortalAssetService;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 门户「我的资产」聚合实现（增量 8）。
 *
 * <h3>三条取舍</h3>
 * <ol>
 *     <li><b>排序/分页不跨域</b>：只按域分栏、每域取最近 N 条。跨域全局分页要么内存归并
 *         （受数据量上限约束），要么建聚合索引（引入同步）；在拿到真实产品需求前不做。</li>
 *     <li><b>域不是"有数据才出现"</b>：接了提供方但你没数据 → 该栏出现且为空；
 *         没接这个域 → 该栏不出现。两者含义不同，不能都显示成"空"。</li>
 *     <li><b>不吞异常</b>：某个域的查询失败就让整个接口失败（500），而不是把那一栏显示成空——
 *         把"查挂了"显示成"你没有资产"是最难排查的静默降级。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigPortalAssetServiceImpl implements IAigPortalAssetService {

    /**
     * 每个域最多取多少条（聚合页只展示"最近几条"，真要全量去各域自己的入口）
     */
    private static final int PER_DOMAIN_LIMIT = 10;

    /**
     * 分栏顺序（不在表里的域排到最后，按编码稳定排序）
     */
    private static final List<String> DOMAIN_ORDER = List.of("IMAGE", "VIDEO", "CONTENT");

    private final List<MyAssetPort> assetPorts;

    @Override
    public List<AigPortalAssetGroupVo> myAssets(AigPortalActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
        List<MyAssetPort> ports = new ArrayList<>(assetPorts == null ? List.of() : assetPorts);
        ports.sort(Comparator
            .comparingInt((MyAssetPort port) -> orderOf(port.domain()))
            .thenComparing(port -> StringUtils.blankToDefault(port.domain(), "")));

        List<AigPortalAssetGroupVo> groups = new ArrayList<>();
        // 同一域出现多个提供方时只认第一个（排序已确定，结果稳定），并在日志里提醒配置重复
        Set<String> seen = new LinkedHashSet<>();
        for (MyAssetPort port : ports) {
            String domain = port.domain() == null ? null : port.domain().trim().toUpperCase();
            if (StringUtils.isBlank(domain)) {
                log.warn("跳过没有声明域的资产提供方：{}", port.getClass().getName());
                continue;
            }
            if (!seen.add(domain)) {
                log.warn("同一域出现了多个资产提供方，只取第一个：domain={}, 被跳过={}",
                    domain, port.getClass().getName());
                continue;
            }
            AigPortalAssetGroupVo group = new AigPortalAssetGroupVo();
            group.setDomain(domain);
            for (MyAssetDTO dto : port.listMyAssets(actor.userId(), PER_DOMAIN_LIMIT)) {
                if (dto == null) {
                    continue;
                }
                group.getItems().add(toVo(domain, dto));
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * 域排序值。
     *
     * @param domain 域编码
     * @return 排序值
     */
    private static int orderOf(String domain) {
        String normalized = domain == null ? "" : domain.trim().toUpperCase();
        int index = DOMAIN_ORDER.indexOf(normalized);
        return index < 0 ? DOMAIN_ORDER.size() : index;
    }

    /**
     * 跨域视图 → 门户视图。
     *
     * @param domain 域编码（以提供方声明的为准）
     * @param dto    跨域视图
     * @return 门户视图
     */
    private static AigPortalMyAssetVo toVo(String domain, MyAssetDTO dto) {
        AigPortalMyAssetVo vo = new AigPortalMyAssetVo();
        vo.setAssetId(dto.getAssetId());
        vo.setAssetType(dto.getAssetType());
        vo.setSourceKind(dto.getSourceKind());
        vo.setName(dto.getName());
        vo.setMimeType(dto.getMimeType());
        vo.setSizeBytes(dto.getSizeBytes());
        vo.setTaskId(dto.getTaskId());
        vo.setCreateTime(dto.getCreateTime());
        return vo;
    }

}
