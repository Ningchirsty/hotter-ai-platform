package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.domain.AigUserWorkspacePref;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalDefaultRoleBo;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalFavoriteBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalPrefVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigWorkspaceFavorites;
import org.dromara.aigov.workspace.portal.mapper.AigUserWorkspacePrefMapper;
import org.dromara.aigov.workspace.portal.service.IAigPortalPrefService;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台偏好实现（主文档线增量 5）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigPortalPrefServiceImpl implements IAigPortalPrefService {

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 无组织时的哨兵值（**不能**用 NULL：唯一键不约束 NULL，会让"无组织"的用户存出多份偏好）
     */
    private static final Long NO_ORG_SENTINEL = 0L;

    private final AigUserWorkspacePrefMapper prefMapper;
    private final IAigPortalService portalService;

    @Override
    public AigPortalPrefVo getPref(AigPortalActor actor) {
        requireActor(actor);
        return toVo(findRow(actor));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPortalPrefVo toggleFavorite(AigPortalFavoriteBo bo, AigPortalActor actor) {
        requireActor(actor);
        if (bo == null || StringUtils.isBlank(bo.getRoleCode())) {
            throw new ServiceException("岗位编码不能为空");
        }
        String roleCode = bo.getRoleCode().trim();
        boolean favorite = bo.getFavorite() == null || bo.getFavorite();
        // ★收藏前必须确认"这个岗位对本人可见"：否则偏好表会变成"验证岗位编码是否存在"的探针，
        // 而且用户能收藏一个永远打不开的岗位（点了没反应）
        assertVisible(roleCode, actor);
        AigUserWorkspacePref row = findRow(actor);
        List<String> favorites = AigWorkspaceFavorites.decode(row == null ? null : row.getFavoritesJson());
        List<String> updated = AigWorkspaceFavorites.toggle(favorites, roleCode, favorite);
        row = upsert(row, actor);
        row.setFavoritesJson(AigWorkspaceFavorites.encode(updated));
        persist(row);
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigPortalPrefVo setDefaultRole(AigPortalDefaultRoleBo bo, AigPortalActor actor) {
        requireActor(actor);
        String roleCode = bo == null || StringUtils.isBlank(bo.getRoleCode()) ? null : bo.getRoleCode().trim();
        if (roleCode != null) {
            assertVisible(roleCode, actor);
        }
        AigUserWorkspacePref row = upsert(findRow(actor), actor);
        // 留空即清空：否则用户没有办法去掉默认岗位，只能换一个
        row.setDefaultRoleCode(roleCode);
        persist(row);
        return toVo(row);
    }

    /**
     * 读当前用户在当前组织下的偏好行。
     *
     * @param actor 当前用户
     * @return 偏好行；没有返回 null
     */
    private AigUserWorkspacePref findRow(AigPortalActor actor) {
        return prefMapper.selectOne(Wrappers.<AigUserWorkspacePref>lambdaQuery()
            .eq(AigUserWorkspacePref::getUserId, actor.userId())
            .eq(AigUserWorkspacePref::getOrgId, orgOf(actor))
            .last("limit 1"));
    }

    /**
     * 取（或创建）偏好行。
     *
     * @param row   已有行（可空）
     * @param actor 当前用户
     * @return 待写入的行
     */
    private AigUserWorkspacePref upsert(AigUserWorkspacePref row, AigPortalActor actor) {
        if (row != null) {
            return row;
        }
        AigUserWorkspacePref created = new AigUserWorkspacePref();
        created.setUserId(actor.userId());
        created.setOrgId(orgOf(actor));
        created.setStatus(STATUS_NORMAL);
        created.setFavoritesJson(AigWorkspaceFavorites.encode(List.of()));
        return created;
    }

    /**
     * 落库（新建或更新）。
     *
     * @param row 行
     */
    private void persist(AigUserWorkspacePref row) {
        if (row.getPrefId() == null) {
            prefMapper.insert(row);
            return;
        }
        prefMapper.updateById(row);
    }

    /**
     * 组织（无组织写 0 哨兵）。
     *
     * @param actor 当前用户
     * @return 组织ID
     */
    private Long orgOf(AigPortalActor actor) {
        return actor.deptId() == null ? NO_ORG_SENTINEL : actor.deptId();
    }

    /**
     * 断言岗位对本人可见。
     *
     * @param roleCode 岗位编码
     * @param actor    当前用户
     */
    private void assertVisible(String roleCode, AigPortalActor actor) {
        List<AigPortalRoleVo> roles = portalService.listMyRoles(actor);
        boolean visible = roles.stream().anyMatch(role -> roleCode.equals(role.getRoleCode()));
        if (!visible) {
            // 与门户其它接口同一句话：不确认"这个岗位存在但你没权限"
            throw new ServiceException("岗位不存在或当前没有对你开放的版本：" + roleCode);
        }
    }

    /**
     * 行 → 偏好视图。
     *
     * @param row 行（可空）
     * @return 偏好
     */
    private AigPortalPrefVo toVo(AigUserWorkspacePref row) {
        AigPortalPrefVo vo = new AigPortalPrefVo();
        vo.setFavorites(row == null
            ? new ArrayList<>() : AigWorkspaceFavorites.decode(row.getFavoritesJson()));
        vo.setDefaultRoleCode(row == null ? null : row.getDefaultRoleCode());
        return vo;
    }

    /**
     * 工作台偏好必须有登录用户。
     *
     * @param actor 用户
     */
    private void requireActor(AigPortalActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
    }

}
