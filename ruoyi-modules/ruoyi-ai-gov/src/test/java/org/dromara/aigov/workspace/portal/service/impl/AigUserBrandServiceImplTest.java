package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.portal.domain.AigUserBrand;
import org.dromara.aigov.workspace.portal.domain.bo.AigUserBrandBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigUserBrandVo;
import org.dromara.aigov.workspace.portal.mapper.AigUserBrandMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户↔品牌归属管理测试（④）。
 *
 * <p>钉住三件事：①**登记是幂等的**（同一对 user+brand 不会插出第二条；停用的行会被恢复）；
 * ②**撤销是停用而不是删除**（避免唯一键被"已删但仍在"的行挡住）；
 * ③非法入参当场拒绝（不落库）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigUserBrandServiceImplTest {

    private AigUserBrandMapper mapper;
    private AigUserBrandServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigUserBrand.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(AigUserBrandMapper.class);
        service = new AigUserBrandServiceImpl(mapper);
    }

    @Test
    @DisplayName("登记已存在的启用行：返回原ID，不插新行")
    void grantExistingNormalIsIdempotent() {
        when(mapper.selectOne(any())).thenReturn(row(55L, 9L, 7L, "0"));

        Long id = service.grant(bo(9L, 7L));

        assertEquals(55L, id);
        verify(mapper, never()).insert(any(AigUserBrand.class));
    }

    @Test
    @DisplayName("登记已存在但停用的行：把同一行改回启用（而不是插新行）")
    void grantRevivesDisabledRow() {
        when(mapper.selectOne(any())).thenReturn(row(55L, 9L, 7L, "1"));

        Long id = service.grant(bo(9L, 7L));

        assertEquals(55L, id);
        ArgumentCaptor<AigUserBrand> captor = ArgumentCaptor.forClass(AigUserBrand.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(55L, captor.getValue().getUserBrandId());
        assertEquals("0", captor.getValue().getStatus());
        verify(mapper, never()).insert(any(AigUserBrand.class));
    }

    @Test
    @DisplayName("登记不存在的行：插入启用行")
    void grantInsertsNewRow() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(AigUserBrand.class))).thenAnswer(inv -> {
            inv.<AigUserBrand>getArgument(0).setUserBrandId(77L);
            return 1;
        });

        Long id = service.grant(bo(9L, 7L));

        assertEquals(77L, id);
        ArgumentCaptor<AigUserBrand> captor = ArgumentCaptor.forClass(AigUserBrand.class);
        verify(mapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals(7L, captor.getValue().getBrandId());
        assertEquals("0", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("非法入参当场拒绝，不落库")
    void invalidInputIsRejected() {
        assertThrows(ServiceException.class, () -> service.grant(null));
        assertThrows(ServiceException.class, () -> service.grant(bo(null, 7L)));
        assertThrows(ServiceException.class, () -> service.grant(bo(0L, 7L)));
        assertThrows(ServiceException.class, () -> service.grant(bo(9L, null)));
        assertThrows(ServiceException.class, () -> service.grant(bo(9L, -1L)));
        verify(mapper, never()).insert(any(AigUserBrand.class));
    }

    @Test
    @DisplayName("撤销=停用（不是删除）；已停用时幂等；不存在时拒绝")
    void revokeDisablesInsteadOfDeleting() {
        when(mapper.selectById(55L)).thenReturn(row(55L, 9L, 7L, "0"));

        service.revoke(55L);

        ArgumentCaptor<AigUserBrand> captor = ArgumentCaptor.forClass(AigUserBrand.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("1", captor.getValue().getStatus());
        verify(mapper, never()).deleteById(any());

        // 已停用：不重复写
        when(mapper.selectById(55L)).thenReturn(row(55L, 9L, 7L, "1"));
        service.revoke(55L);
        verify(mapper, never()).deleteById(any());

        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.revoke(99L));
        assertThrows(ServiceException.class, () -> service.revoke(null));
    }

    @Test
    @DisplayName("查某人的归属：映射字段；userId 为空时拒绝")
    void listByUserMapsFields() {
        when(mapper.selectList(any())).thenReturn(List.of(row(55L, 9L, 7L, "0")));

        List<AigUserBrandVo> rows = service.listByUser(9L);

        assertEquals(1, rows.size());
        assertEquals(55L, rows.get(0).getUserBrandId());
        assertEquals(9L, rows.get(0).getUserId());
        assertEquals(7L, rows.get(0).getBrandId());
        assertEquals("0", rows.get(0).getStatus());

        assertThrows(ServiceException.class, () -> service.listByUser(null));
    }

    private static AigUserBrandBo bo(Long userId, Long brandId) {
        AigUserBrandBo bo = new AigUserBrandBo();
        bo.setUserId(userId);
        bo.setBrandId(brandId);
        return bo;
    }

    private static AigUserBrand row(Long id, Long userId, Long brandId, String status) {
        AigUserBrand entity = new AigUserBrand();
        entity.setUserBrandId(id);
        entity.setUserId(userId);
        entity.setBrandId(brandId);
        entity.setStatus(status);
        return entity;
    }

}
