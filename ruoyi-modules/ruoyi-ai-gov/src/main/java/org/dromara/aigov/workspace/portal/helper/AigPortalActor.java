package org.dromara.aigov.workspace.portal.helper;

import java.util.Set;

/**
 * 门户的"当前用户"（主文档线增量 2）。
 *
 * <p><b>为什么把组织范围与品牌集一起传进来，而不是在服务里现查</b>：可见性判定要能被
 * 逐条单测（F-06 要求"运行时可见性必须自己实现并有测试"）。把数据来源压成一个值对象，
 * 判定就退化成一次纯函数调用（见 {@link AigRoleVisibilityResolver}）。</p>
 *
 * @param userId   用户ID
 * @param deptId   所在部门（可空）
 * @param orgIds   组织范围：本部门 + 全部祖级（见 {@link AigOrgScopeResolver}）
 * @param brandIds 所属品牌（今天恒为空集：没有用户↔品牌数据源，见 {@link AigUserBrandResolver}）
 * @author ai-gov
 */
public record AigPortalActor(Long userId, Long deptId, Set<Long> orgIds, Set<Long> brandIds) {

    /**
     * 组织范围（null 安全）。
     *
     * @return 组织范围
     */
    public Set<Long> orgScope() {
        return orgIds == null ? Set.of() : orgIds;
    }

    /**
     * 品牌集（null 安全）。
     *
     * @return 品牌集
     */
    public Set<Long> brands() {
        return brandIds == null ? Set.of() : brandIds;
    }

}
