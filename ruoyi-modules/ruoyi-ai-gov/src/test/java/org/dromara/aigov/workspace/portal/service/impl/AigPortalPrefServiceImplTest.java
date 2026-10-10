package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.portal.domain.AigUserWorkspacePref;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalDefaultRoleBo;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalFavoriteBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalPrefVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.mapper.AigUserWorkspacePrefMapper;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作台偏好服务测试（增量 5）。
 *
 * <p>守三件事：①**收藏前必须可见**（否则偏好表变成"验证岗位编码"的探针，且用户能收藏打不开的岗位）；
 * ②**写入范围恒为当前用户本人**（不接受调用方指定用户）；③**无组织用 0 哨兵**——
 * 写 NULL 会让唯一键失效，那个坑本仓已经踩过两次。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalPrefServiceImplTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    private AigUserWorkspacePrefMapper prefMapper;
    private IAigPortalService portalService;
    private AigPortalPrefServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigUserWorkspacePref.class);
    }

    @BeforeEach
    void setUp() {
        prefMapper = mock(AigUserWorkspacePrefMapper.class);
        portalService = mock(IAigPortalService.class);
        service = new AigPortalPrefServiceImpl(prefMapper, portalService);
        when(portalService.listMyRoles(any())).thenReturn(List.of(role("R1"), role("R2")));
    }

    @Test
    @DisplayName("没有偏好行时返回空收藏与空默认岗位（不抛异常）")
    void emptyPrefIsEmptyNotError() {
        when(prefMapper.selectOne(any())).thenReturn(null);

        AigPortalPrefVo vo = service.getPref(ACTOR);

        assertEquals(List.of(), vo.getFavorites());
        assertNull(vo.getDefaultRoleCode());
    }

    @Test
    @DisplayName("收藏前必须可见：不可见的岗位既不入库也不回显")
    void favoriteRequiresVisibility() {
        when(prefMapper.selectOne(any())).thenReturn(null);
        AigPortalFavoriteBo bo = new AigPortalFavoriteBo();
        bo.setRoleCode("NOT_VISIBLE");

        assertThrows(ServiceException.class, () -> service.toggleFavorite(bo, ACTOR));
        verify(prefMapper, never()).insert(any(AigUserWorkspacePref.class));
        verify(prefMapper, never()).updateById(any(AigUserWorkspacePref.class));
    }

    @Test
    @DisplayName("首次收藏会建行，且写入范围是当前用户本人")
    void firstFavoriteCreatesOwnRow() {
        when(prefMapper.selectOne(any())).thenReturn(null);
        AigPortalFavoriteBo bo = new AigPortalFavoriteBo();
        bo.setRoleCode("R1");

        AigPortalPrefVo vo = service.toggleFavorite(bo, ACTOR);

        assertEquals(List.of("R1"), vo.getFavorites());
        ArgumentCaptor<AigUserWorkspacePref> captor = ArgumentCaptor.forClass(AigUserWorkspacePref.class);
        verify(prefMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getUserId(), "只写当前用户自己那一行");
        assertEquals(102L, captor.getValue().getOrgId());
    }

    @Test
    @DisplayName("没有部门时写 0 哨兵：写 NULL 会让唯一键失效（同一用户可存出多份偏好）")
    void noDeptUsesSentinelOrg() {
        AigPortalActor noDept = new AigPortalActor(9L, null, Set.of(), Set.of());
        when(prefMapper.selectOne(any())).thenReturn(null);
        AigPortalFavoriteBo bo = new AigPortalFavoriteBo();
        bo.setRoleCode("R1");

        service.toggleFavorite(bo, noDept);

        ArgumentCaptor<AigUserWorkspacePref> captor = ArgumentCaptor.forClass(AigUserWorkspacePref.class);
        verify(prefMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getOrgId(), "无部门必须写 0，不能写 NULL");
    }

    @Test
    @DisplayName("再次收藏是更新而不是新建；取消收藏后会从清单里去掉")
    void existingRowIsUpdated() {
        AigUserWorkspacePref existing = new AigUserWorkspacePref();
        existing.setPrefId(5L);
        existing.setUserId(9L);
        existing.setOrgId(102L);
        existing.setFavoritesJson("[\"R2\"]");
        when(prefMapper.selectOne(any())).thenReturn(existing);

        AigPortalFavoriteBo add = new AigPortalFavoriteBo();
        add.setRoleCode("R1");
        AigPortalPrefVo afterAdd = service.toggleFavorite(add, ACTOR);
        assertEquals(List.of("R2", "R1"), afterAdd.getFavorites());
        verify(prefMapper).updateById(existing);
        verify(prefMapper, never()).insert(any(AigUserWorkspacePref.class));

        AigPortalFavoriteBo remove = new AigPortalFavoriteBo();
        remove.setRoleCode("R2");
        remove.setFavorite(false);
        assertEquals(List.of("R1"), service.toggleFavorite(remove, ACTOR).getFavorites());
    }

    @Test
    @DisplayName("默认岗位：留空即清空（用户必须能去掉默认），设置时同样要求可见")
    void defaultRoleCanBeSetAndCleared() {
        AigUserWorkspacePref existing = new AigUserWorkspacePref();
        existing.setPrefId(5L);
        existing.setUserId(9L);
        existing.setOrgId(102L);
        when(prefMapper.selectOne(any())).thenReturn(existing);

        AigPortalDefaultRoleBo set = new AigPortalDefaultRoleBo();
        set.setRoleCode("R2");
        assertEquals("R2", service.setDefaultRole(set, ACTOR).getDefaultRoleCode());

        AigPortalDefaultRoleBo clear = new AigPortalDefaultRoleBo();
        clear.setRoleCode("  ");
        assertNull(service.setDefaultRole(clear, ACTOR).getDefaultRoleCode());

        AigPortalDefaultRoleBo invisible = new AigPortalDefaultRoleBo();
        invisible.setRoleCode("NOT_VISIBLE");
        ServiceException e = assertThrows(ServiceException.class, () -> service.setDefaultRole(invisible, ACTOR));
        assertTrue(e.getMessage().contains("不存在"));
        assertNotNull(existing.getPrefId());
    }

    private static AigPortalRoleVo role(String code) {
        AigPortalRoleVo vo = new AigPortalRoleVo();
        vo.setRoleCode(code);
        return vo;
    }

}
