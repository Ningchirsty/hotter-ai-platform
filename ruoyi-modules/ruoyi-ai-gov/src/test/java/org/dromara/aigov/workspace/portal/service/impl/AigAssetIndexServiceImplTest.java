package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.portal.domain.AigAssetIndex;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.aigov.workspace.portal.mapper.AigAssetIndexMapper;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产聚合索引测试（增量 11）。
 *
 * <p>钉住重建的四件事：①**先删该用户该域**（否则各域删掉的资产会在索引里变成幽灵行）；
 * ②**分页拉取**直到空页（页大小 200）——<b>不靠"短页"判结束</b>
 * （真实端口自设单次上限 50，短页会被误判成到底，把一个域只同步前 50 条）；③**同域 assetId 去重**
 * （唯一键是 user+domain+assetId，重复行会撞键）；④非法入参不落库。
 * 读取侧钉住：映射字段正确、userId 非法时拒绝。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAssetIndexServiceImplTest {

    private AigAssetIndexMapper indexMapper;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigAssetIndex.class);
    }

    @BeforeEach
    void setUp() {
        indexMapper = mock(AigAssetIndexMapper.class);
        when(indexMapper.insert(any(AigAssetIndex.class))).thenReturn(1);
    }

    @Test
    @DisplayName("重建：先删该用户该域，再分页拉取写入；短页即停")
    void rebuildDeletesThenPages() {
        List<MyAssetDTO> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            MyAssetDTO dto = new MyAssetDTO();
            dto.setAssetId((long) i);
            dto.setDomain("IMAGE");
            dto.setName("a" + i);
            dto.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i));
            many.add(dto);
        }

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(
            List.of(pagingPort("IMAGE", many)), indexMapper);

        int written = service.rebuildForUser(9L);

        assertEquals(201, written);
        verify(indexMapper).deleteByUserAndDomain(9L, "IMAGE");
        verify(indexMapper, times(201)).insert(any(AigAssetIndex.class));

        ArgumentCaptor<AigAssetIndex> captor = ArgumentCaptor.forClass(AigAssetIndex.class);
        verify(indexMapper, times(201)).insert(captor.capture());
        AigAssetIndex first = captor.getAllValues().get(0);
        assertEquals(9L, first.getUserId());
        assertEquals("IMAGE", first.getDomain());
        assertEquals(0L, first.getAssetId());
        assertEquals("a0", first.getName());
    }

    @Test
    @DisplayName("★ 端口自设单次上限（50 < 同步页大小 200）时不能把短页当成到底：全量仍要拉完")
    void rebuildFollowsPortCapInsteadOfTreatingShortPageAsEnd() {
        List<MyAssetDTO> many = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            MyAssetDTO dto = new MyAssetDTO();
            dto.setAssetId((long) i);
            dto.setName("a" + i);
            many.add(dto);
        }
        List<Integer> offsets = new ArrayList<>();
        MyAssetPort capped = new MyAssetPort() {
            @Override
            public String domain() {
                return "CONTENT";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                offsets.add(offset);
                // 真实端口（内容/视频）自设单次上限 50，调用方请求 200 也只能拿到 50
                int cappedLimit = Math.min(limit, 50);
                if (offset >= many.size()) {
                    return List.of();
                }
                return many.subList(offset, Math.min(many.size(), offset + cappedLimit));
            }
        };

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(capped), indexMapper);

        int written = service.rebuildForUser(9L);

        assertEquals(120, written, "被截断成 50 条就是「少同步」缺陷");
        verify(indexMapper, times(120)).insert(any(AigAssetIndex.class));
        assertEquals(List.of(0, 50, 100, 120), offsets, "offset 必须按实际返回条数推进，直到空页");
    }

    @Test
    @DisplayName("★ 实现忽略 offset（每页都是同一批）时必须停：不空转到页数上限")
    void rebuildStopsWhenPortIgnoresOffset() {
        MyAssetDTO a = new MyAssetDTO();
        a.setAssetId(1L);
        MyAssetDTO b = new MyAssetDTO();
        b.setAssetId(2L);
        List<Integer> calls = new ArrayList<>();
        MyAssetPort broken = new MyAssetPort() {
            @Override
            public String domain() {
                return "VIDEO";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                calls.add(offset);
                return List.of(a, b);
            }
        };

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(broken), indexMapper);

        int written = service.rebuildForUser(9L);

        assertEquals(2, written, "同一批资产只写一次");
        assertEquals(2, calls.size(), "第一页有新增、第二页无新增即停，不空转到 500 页");
    }

    @Test
    @DisplayName("重建：同域重复 assetId 只写一行（唯一键是 user+domain+assetId）")
    void rebuildDeduplicatesAssetIds() {
        MyAssetDTO one = new MyAssetDTO();
        one.setAssetId(5L);
        MyAssetDTO dup = new MyAssetDTO();
        dup.setAssetId(5L);

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(
            List.of(pagingPort("VIDEO", List.of(one, dup))), indexMapper);

        int written = service.rebuildForUser(9L);

        assertEquals(1, written);
        verify(indexMapper, times(1)).insert(any(AigAssetIndex.class));
    }

    @Test
    @DisplayName("重建：非法 userId 直接拒绝，不删不写")
    void rebuildRejectsInvalidUser() {
        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(), indexMapper);

        assertThrows(ServiceException.class, () -> service.rebuildForUser(0L));

        verify(indexMapper, never()).deleteByUserAndDomain(anyLong(), any());
        verify(indexMapper, never()).insert(any(AigAssetIndex.class));
    }

    @Test
    @DisplayName("读取：索引行映射成门户视图；userId 非法时拒绝")
    void pageMapsIndexRows() {
        AigAssetIndex row = new AigAssetIndex();
        row.setIndexId(1L);
        row.setDomain("CONTENT");
        row.setAssetId(401L);
        row.setAssetType("PDF");
        row.setName("mine.pdf");
        row.setSizeBytes(10L);
        row.setTaskId(1L);
        row.setAssetTime(LocalDateTime.of(2026, 2, 1, 0, 0));
        Page<AigAssetIndex> page = new Page<>();
        page.setRecords(List.of(row));
        page.setTotal(1L);
        when(indexMapper.selectPage(any(), any())).thenReturn(page);

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(), indexMapper);
        PageResult<AigPortalMyAssetVo> result = service.pageAssets(9L, "content", new PageQuery());

        assertEquals(1, result.getRows().size());
        var vo = new ArrayList<>(result.getRows()).get(0);
        assertEquals("CONTENT", vo.getDomain(), "域编码读取时应归一化为大写");
        assertEquals(401L, vo.getAssetId());
        assertEquals("mine.pdf", vo.getName());

        assertThrows(ServiceException.class, () -> service.pageAssets(0L, null, new PageQuery()));
    }

    @Test
    @DisplayName("★ 增量：只补水位线之上的新增，翻到已同步区间即停（不再翻旧数据）")
    void syncNewOnlyInsertsAssetsAboveWatermark() {
        when(indexMapper.selectMaxAssetId(9L, "IMAGE")).thenReturn(5L);
        List<Integer> offsets = new ArrayList<>();
        List<MyAssetDTO> all = List.of(dto(10L), dto(8L), dto(5L), dto(3L), dto(1L));
        MyAssetPort port = new MyAssetPort() {
            @Override
            public String domain() {
                return "IMAGE";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                offsets.add(offset);
                if (offset >= all.size()) {
                    return List.of();
                }
                return all.subList(offset, Math.min(all.size(), offset + limit));
            }
        };

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(port), indexMapper);

        int written = service.syncNewForUser(9L);

        assertEquals(2, written, "水位线 5 之上只有 10 与 8");
        assertEquals(List.of(0), offsets, "翻到已同步区间就该停，不必再拉第二页");
        ArgumentCaptor<AigAssetIndex> captor = ArgumentCaptor.forClass(AigAssetIndex.class);
        verify(indexMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(10L, 8L), captor.getAllValues().stream().map(AigAssetIndex::getAssetId).toList());
        verify(indexMapper, never()).deleteByUserAndDomain(anyLong(), any());
    }

    @Test
    @DisplayName("★ 增量：跨页补齐（水位线在中部时不能只看第一页）")
    void syncNewCrossesPages() {
        when(indexMapper.selectMaxAssetId(9L, "VIDEO")).thenReturn(50L);
        List<MyAssetDTO> all = new ArrayList<>();
        for (int i = 100; i >= 1; i--) {
            all.add(dto((long) i));
        }
        List<Integer> offsets = new ArrayList<>();
        MyAssetPort port = new MyAssetPort() {
            @Override
            public String domain() {
                return "VIDEO";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                offsets.add(offset);
                int capped = Math.min(limit, 50);
                if (offset >= all.size()) {
                    return List.of();
                }
                return all.subList(offset, Math.min(all.size(), offset + capped));
            }
        };

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(List.of(port), indexMapper);

        int written = service.syncNewForUser(9L);

        assertEquals(50, written, "100..51 共 50 条新增");
        assertEquals(List.of(0, 50), offsets, "第二页第一条就是水位线 50，翻到这里停");
    }

    @Test
    @DisplayName("★ 增量：该域还没有水位线时退回全量（不能拿水位线猜，否则一条都补不进来）")
    void syncNewFallsBackToFullWhenNoWatermark() {
        when(indexMapper.selectMaxAssetId(9L, "CONTENT")).thenReturn(null);
        MyAssetDTO one = dto(7L);
        MyAssetDTO two = dto(6L);

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(
            List.of(pagingPort("CONTENT", List.of(one, two))), indexMapper);

        int written = service.syncNewForUser(9L);

        assertEquals(2, written);
        verify(indexMapper).deleteByUserAndDomain(9L, "CONTENT");
        verify(indexMapper, times(2)).insert(any(AigAssetIndex.class));
    }

    @Test
    @DisplayName("★ 增量：没有新增时一行不写（也不删）")
    void syncNewWritesNothingWhenNothingNew() {
        when(indexMapper.selectMaxAssetId(9L, "IMAGE")).thenReturn(10L);
        MyAssetDTO old = dto(10L);

        AigAssetIndexServiceImpl service = new AigAssetIndexServiceImpl(
            List.of(pagingPort("IMAGE", List.of(old))), indexMapper);

        int written = service.syncNewForUser(9L);

        assertEquals(0, written);
        verify(indexMapper, never()).insert(any(AigAssetIndex.class));
        verify(indexMapper, never()).deleteByUserAndDomain(anyLong(), any());
        assertThrows(ServiceException.class, () -> service.syncNewForUser(0L));
    }

    private static MyAssetDTO dto(Long assetId) {
        MyAssetDTO dto = new MyAssetDTO();
        dto.setAssetId(assetId);
        return dto;
    }

    /**
     * 一个"内存数据集 + offset/limit 分页"的假端口，模拟各域实现。
     *
     * @param domain 域编码
     * @param all    全部资产（按 id 倒序不重要，分页只看 offset/limit）
     * @return 端口
     */
    private static MyAssetPort pagingPort(String domain, List<MyAssetDTO> all) {
        return new MyAssetPort() {
            @Override
            public String domain() {
                return domain;
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                if (offset >= all.size()) {
                    return List.of();
                }
                int end = Math.min(all.size(), offset + limit);
                return all.subList(offset, end);
            }
        };
    }

}
