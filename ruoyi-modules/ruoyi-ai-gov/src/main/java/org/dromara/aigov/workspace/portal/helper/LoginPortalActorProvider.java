package org.dromara.aigov.workspace.portal.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.mapper.AigPortalDeptMapper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 基于 Sa-Token 登录态的门户用户解析（主文档线增量 2）。
 *
 * <h3>组织范围</h3>
 * <p>取 {@code LoginHelper.getDeptId()} + {@code sys_dept.ancestors} 的祖级链，
 * 交给纯函数 {@link AigOrgScopeResolver} 解析。理由见该类的注释：岗位绑定常挂在集团/子公司层级，
 * 只按本部门精确匹配会让"给华东子公司发布的岗位"对华东下每个部门都不可见。</p>
 *
 * <h3>品牌集今天恒为空——这是刻意的</h3>
 * <p>本仓目前<b>没有任何"用户属于哪个品牌"的数据源</b>（F-05 已冻：不为岗位可见性新造授权体系，
 * 品牌归属属于业务侧主数据）。因此这里返回空集，含义是
 * <b>"按品牌定向的绑定暂时对谁都不生效"</b>——fail-closed。
 * 若将来业务侧提供了品牌归属，改动点只有这一个方法；
 * 判定规则（命中品牌即可见）已被 {@code AigRoleVisibilityResolverTest} 钉住，不需要重写。</p>
 *
 * <p><b>取不到登录人返回 null</b>，由服务层拒绝，不在这里编造身份。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginPortalActorProvider implements AigPortalActorProvider {

    private final AigPortalDeptMapper deptMapper;

    @Override
    public AigPortalActor currentActor() {
        Long userId;
        Long deptId;
        try {
            userId = LoginHelper.getUserId();
            deptId = LoginHelper.getDeptId();
        } catch (Exception e) {
            // 无登录上下文对门户是异常路径：只记 debug，真正的拒绝在服务层
            log.debug("门户取不到登录用户：{}", e.getClass().getSimpleName());
            return null;
        }
        if (userId == null) {
            return null;
        }
        String ancestors = null;
        if (deptId != null) {
            try {
                ancestors = deptMapper.selectAncestors(deptId);
            } catch (Exception e) {
                // 组织查询失败时不要让整个门户 500：范围退化成"只有本部门"，
                // 表现是"按上级组织定向的岗位暂时看不到"，比整页打不开可控
                log.warn("门户读取部门祖级失败，按本部门处理：deptId={} 异常={}", deptId,
                    e.getClass().getSimpleName());
            }
        }
        return new AigPortalActor(userId, deptId, AigOrgScopeResolver.resolve(deptId, ancestors), Set.of());
    }

}
