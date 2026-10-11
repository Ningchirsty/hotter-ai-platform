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
 * ②**分页拉取**到短页为止（页大小 200），offset 正确推进；③**同域 assetId 去重**
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
