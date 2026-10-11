package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.domain.AigAssetIndex;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.aigov.workspace.portal.mapper.AigAssetIndexMapper;
import org.dromara.aigov.workspace.portal.service.IAigAssetIndexService;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资产聚合索引实现（增量 11）。
 *
 * <h3>三条取舍</h3>
 * <ol>
 *     <li><b>重建 = 先删该用户该域、再分页拉取写入</b>（同一事务）：这样各域删掉的资产会从索引里
 *         消失，不会留下永远删不掉的幽灵行。事务保证读者看到的是旧的一份或新的一份，
 *         而不是中间的空档。</li>
 *     <li><b>页式拉取有上限</b>：既防实现方"永远返回满页"造成死循环，也防一次重建把内存吃穿；
 *         到上限会 WARN 留痕（而不是静默少同步）。</li>
 *     <li><b>同一域内按 assetId 去重</b>：唯一键是 {@code (user_id, domain, asset_id)}，
 *         实现方若返回重复行，重复插入会撞唯一键——去重比捕获异常更清楚。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigAssetIndexServiceImpl implements IAigAssetIndexService {

    /**
     * 同步时每页拉多少条
     */
    private static final int SYNC_PAGE_SIZE = 200;

    /**
     * 同步页数上限（防"永远返回满页"把重建变成死循环）
     */
    private static final int MAX_SYNC_PAGES = 500;

    /**
     * 分栏/同步顺序
     */
    private static final List<String> DOMAIN_ORDER = List.of("IMAGE", "VIDEO", "CONTENT");

    private final List<MyAssetPort> assetPorts;
    private final AigAssetIndexMapper indexMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuildForUser(long userId) {
        if (userId <= 0) {
            throw new ServiceException("用户ID不能为空");
        }
        int total = 0;
        for (MyAssetPort port : sortedPorts()) {
            String domain = normalize(port.domain());
            if (domain == null) {
                log.warn("跳过没有声明域的资产提供方：{}", port.getClass().getName());
                continue;
            }
            indexMapper.deleteByUserAndDomain(userId, domain);
            Set<Long> seenAssetIds = new LinkedHashSet<>();
            int offset = 0;
            int pages = 0;
            while (pages < MAX_SYNC_PAGES) {
                List<MyAssetDTO> batch = port.listMyAssets(userId, offset, SYNC_PAGE_SIZE);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (MyAssetDTO dto : batch) {
                    if (dto == null || dto.getAssetId() == null) {
                        continue;
                    }
                    if (!seenAssetIds.add(dto.getAssetId())) {
                        continue;
                    }
                    indexMapper.insert(toEntity(userId, domain, dto));
                    total++;
                }
                offset += batch.size();
                pages++;
                if (batch.size() < SYNC_PAGE_SIZE) {
                    break;
                }
            }
            if (pages >= MAX_SYNC_PAGES) {
                log.warn("重建资产索引达到页数上限（{} 页 × {} 条），可能未同步完：userId={}, domain={}",
                    MAX_SYNC_PAGES, SYNC_PAGE_SIZE, userId, domain);
            }
        }
        log.info("资产索引重建完成：userId={}, 写入 {} 行", userId, total);
        return total;
    }

    @Override
    public PageResult<AigPortalMyAssetVo> pageAssets(long userId, String domain, PageQuery pageQuery) {
        if (userId <= 0) {
            throw new ServiceException("用户ID不能为空");
        }
        String normalized = normalize(domain);
        LambdaQueryWrapper<AigAssetIndex> wrapper = Wrappers.<AigAssetIndex>lambdaQuery()
            .eq(AigAssetIndex::getUserId, userId)
            .eq(normalized != null, AigAssetIndex::getDomain, normalized)
            // 时间倒序；同一时间用主键兜底，保证翻页顺序稳定（不随物理顺序漂移）
            .orderByDesc(AigAssetIndex::getAssetTime)
            .orderByDesc(AigAssetIndex::getIndexId);
        IPage<AigAssetIndex> page = indexMapper.selectPage(
            pageQuery == null ? new PageQuery().build() : pageQuery.build(), wrapper);
        List<AigPortalMyAssetVo> rows = new ArrayList<>();
        for (AigAssetIndex row : page.getRecords()) {
            rows.add(toVo(row));
        }
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 按固定域顺序排列提供方（同一域多个提供方时只留第一个，结果不随 Bean 顺序漂移）。
     *
     * @return 提供方列表
     */
    private List<MyAssetPort> sortedPorts() {
        List<MyAssetPort> ports = new ArrayList<>(assetPorts == null ? List.of() : assetPorts);
        ports.sort(Comparator
            .comparingInt((MyAssetPort port) -> orderOf(port.domain()))
            .thenComparing(port -> StringUtils.blankToDefault(port.domain(), "")));
        return ports;
    }

    /**
     * 归一化域编码。
     *
     * @param domain 域编码
     * @return 大写去空白；空返回 null
     */
    private static String normalize(String domain) {
        return StringUtils.isBlank(domain) ? null : domain.trim().toUpperCase();
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
     * 跨域视图 → 索引行。
     *
     * @param userId 用户ID
     * @param domain 域编码
     * @param dto    跨域视图
     * @return 索引行
     */
    private static AigAssetIndex toEntity(long userId, String domain, MyAssetDTO dto) {
        AigAssetIndex entity = new AigAssetIndex();
        entity.setUserId(userId);
        entity.setDomain(domain);
        entity.setAssetId(dto.getAssetId());
        entity.setAssetType(dto.getAssetType());
        entity.setSourceKind(dto.getSourceKind());
        entity.setName(dto.getName());
        entity.setMimeType(dto.getMimeType());
        entity.setSizeBytes(dto.getSizeBytes());
        entity.setTaskId(dto.getTaskId());
        entity.setAssetTime(dto.getCreateTime());
        return entity;
    }

    /**
     * 索引行 → 门户视图。
     *
     * @param entity 索引行
     * @return 门户视图
     */
    private static AigPortalMyAssetVo toVo(AigAssetIndex entity) {
        AigPortalMyAssetVo vo = new AigPortalMyAssetVo();
        vo.setDomain(entity.getDomain());
        vo.setAssetId(entity.getAssetId());
        vo.setAssetType(entity.getAssetType());
        vo.setSourceKind(entity.getSourceKind());
        vo.setName(entity.getName());
        vo.setMimeType(entity.getMimeType());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setTaskId(entity.getTaskId());
        vo.setCreateTime(entity.getAssetTime());
        return vo;
    }

}
