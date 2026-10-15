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
 *     <li><b>不靠"短页"判结束</b>：各域实现会<b>自设单次上限</b>（内容/视频端口是 50，而这里的
 *         同步页大小是 200），"返回条数少于请求条数"会被误判成"已经到底"——那会把一个用户某个域
 *         只同步前 50 条，而且<b>一声不响</b>。结束条件改为两条：①空页；
 *         ②本页没有带来任何新资产（实现忽略了 offset 的信号，也是防死循环的兜底）。
 *         "少同步"是索引类缺陷里最难发现的一种：界面只是"少了几个资产"，
 *         没人会怀疑是同步逻辑，所以宁可多请求一页空页。</li>
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
     * 同步时每页拉多少条（<b>只是"我希望一次拉多少"</b>：实现可自行下调）
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
            total += rebuildDomain(userId, port, domain);
        }
        log.info("资产索引重建完成：userId={}, 写入 {} 行", userId, total);
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncNewForUser(long userId) {
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
            Long watermark = indexMapper.selectMaxAssetId(userId, domain);
            if (watermark == null || watermark <= 0L) {
                // 还没有水位线：不能"从 0 开始只补新增"（那等于什么都没做），退回该域全量
                total += rebuildDomain(userId, port, domain);
                continue;
            }
            total += syncNewDomain(userId, port, domain, watermark);
        }
        log.info("资产索引增量同步完成：userId={}, 新增 {} 行", userId, total);
        return total;
    }

    /**
     * 全量重建一个域：先删该用户该域旧行，再分页拉取写入。
     *
     * @param userId 用户ID
     * @param port   该域的资产提供方
     * @param domain 归一化后的域编码
     * @return 写入行数
     */
    private int rebuildDomain(long userId, MyAssetPort port, String domain) {
        indexMapper.deleteByUserAndDomain(userId, domain);
        int written = 0;
        Set<Long> seenAssetIds = new LinkedHashSet<>();
        int offset = 0;
        int pages = 0;
        while (pages < MAX_SYNC_PAGES) {
            List<MyAssetDTO> batch = port.listMyAssets(userId, offset, SYNC_PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            int addedOnThisPage = 0;
            for (MyAssetDTO dto : batch) {
                if (dto == null || dto.getAssetId() == null) {
                    continue;
                }
                if (!seenAssetIds.add(dto.getAssetId())) {
                    continue;
                }
                indexMapper.insert(toEntity(userId, domain, dto));
                written++;
                addedOnThisPage++;
            }
            offset += batch.size();
            pages++;
            if (addedOnThisPage == 0) {
                // 本页全是已见过的资产：实现要么忽略了 offset（再翻也是同一页），
                // 要么返回了重复数据。两种情况下继续翻都不会有新数据，停。
                log.warn("资产索引同步本页无新增，停止翻页（实现可能忽略 offset）：userId={}, domain={}, offset={}",
                    userId, domain, offset);
                break;
            }
        }
        if (pages >= MAX_SYNC_PAGES) {
            log.warn("重建资产索引达到页数上限（{} 页 × {} 条），可能未同步完：userId={}, domain={}",
                MAX_SYNC_PAGES, SYNC_PAGE_SIZE, userId, domain);
        }
        return written;
    }

    /**
     * 增量同步一个域：只补比水位线更大的资产。
     *
     * @param userId    用户ID
     * @param port      该域的资产提供方
     * @param domain    归一化后的域编码
     * @param watermark 水位线（该域已索引的最大 asset_id）
     * @return 新增写入行数
     */
    private int syncNewDomain(long userId, MyAssetPort port, String domain, long watermark) {
        int written = 0;
        Set<Long> seenAssetIds = new LinkedHashSet<>();
        int offset = 0;
        int pages = 0;
        while (pages < MAX_SYNC_PAGES) {
            List<MyAssetDTO> batch = port.listMyAssets(userId, offset, SYNC_PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            boolean reachedWatermark = false;
            int addedOnThisPage = 0;
            for (MyAssetDTO dto : batch) {
                if (dto == null || dto.getAssetId() == null) {
                    continue;
                }
                if (dto.getAssetId() <= watermark) {
                    // 已经翻到"同步过"的区间：端口按资产 ID 倒序，后面的只会更旧
                    reachedWatermark = true;
                    continue;
                }
                if (!seenAssetIds.add(dto.getAssetId())) {
                    continue;
                }
                indexMapper.insert(toEntity(userId, domain, dto));
                written++;
                addedOnThisPage++;
            }
            offset += batch.size();
            pages++;
            if (reachedWatermark) {
                break;
            }
            if (addedOnThisPage == 0) {
                log.warn("资产索引增量同步本页无新增，停止翻页（实现可能忽略 offset）：userId={}, domain={}, offset={}",
                    userId, domain, offset);
                break;
            }
        }
        if (pages >= MAX_SYNC_PAGES) {
            log.warn("增量同步资产索引达到页数上限（{} 页 × {} 条），可能未同步完：userId={}, domain={}",
                MAX_SYNC_PAGES, SYNC_PAGE_SIZE, userId, domain);
        }
        return written;
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
