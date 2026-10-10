package org.dromara.aigov.workspace.portal.helper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户的<b>组织范围</b>解析（主文档线增量 2；附件 §10.2 "岗位按组织可见"）。
 *
 * <h3>为什么"我在哪个部门"不等于"我能看到哪些部门定的岗位"</h3>
 * <p>岗位绑定通常挂在集团/子公司层级（F-05：治理层的组织树只有 {@code sys_dept} 前两级），
 * 而员工挂在下级部门。若只按 `dept_id` 精确匹配，一份"给华东子公司看的岗位包"
 * 对华东下的每个具体部门都不可见——配置的人会以为"发布了但没人看到"。
 * 所以范围=**本部门 + 全部祖级**（RuoYi 的 {@code sys_dept.ancestors} 就是逗号分隔的祖级链）。</p>
 *
 * <h3>为什么把祖级解析做成纯函数</h3>
 * <p>它有两个不易发现的失效方式：①把空串/`"0"` 这类占位当成一个真实部门，于是"范围"里
 * 混进一个不存在的 id；②token 解析出错时静默返回空集，于是**所有人都看不到任何岗位**——
 * 这类问题不会报错，只会表现为"发布了却没人看得见"。所以逐条钉住。</p>
 *
 * @author ai-gov
 */
public final class AigOrgScopeResolver {

    /**
     * 组织树里表示"无父级"的占位值：它不是一个真实部门，不能进范围
     */
    private static final long NO_PARENT = 0L;

    private AigOrgScopeResolver() {
    }

    /**
     * 解析组织范围（本部门 + 全部祖级）。
     *
     * @param deptId      本部门ID（可空）
     * @param ancestorsCsv 祖级列表（逗号分隔，来自 {@code sys_dept.ancestors}；可空）
     * @return 组织范围（不可变集合）；入参都为空时返回空集
     */
    public static Set<Long> resolve(Long deptId, String ancestorsCsv) {
        Set<Long> scope = new LinkedHashSet<>();
        if (deptId != null && deptId > NO_PARENT) {
            scope.add(deptId);
        }
        if (ancestorsCsv == null || ancestorsCsv.isBlank()) {
            return frozen(scope);
        }
        for (String raw : ancestorsCsv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            long parsed;
            try {
                parsed = Long.parseLong(token);
            } catch (NumberFormatException e) {
                // 脏 token 直接忽略：把它当成部门ID会让范围变成"猜出来的"
                continue;
            }
            if (parsed > NO_PARENT) {
                scope.add(parsed);
            }
        }
        return frozen(scope);
    }

    /**
     * 冻结成不可变集合，并**保留插入顺序**（本仓库有一条已付代价的教训：
     * 依赖 {@code Set.of}/{@code Set.copyOf} 的迭代顺序会让同一个东西在不同机器上显示成不同样子）。
     *
     * @param scope 集合
     * @return 不可变集合
     */
    private static Set<Long> frozen(Set<Long> scope) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(scope));
    }

}
