package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 岗位包版本的发布流转守卫（附件 §4.2「RoleVersion 发布：DRAFT/TESTING/PUBLISHED/DISABLED」）。
 *
 * <h3>为什么单独一个纯函数</h3>
 * <p>流转写错不会报错——只会在某天出现"某个岗位对员工可见了，但没人知道它是怎么上去的"。
 * 而岗位包一旦发布，员工就照着它用。所以允许的边必须是一张<b>写死的表</b>，
 * 逐条可读、逐条可测，而不是散在服务层的 if 里。</p>
 *
 * <h3>四条边的理由</h3>
 * <ul>
 *     <li>{@code DRAFT → TESTING}：先给测试账号预览，再对员工开放——测试是发布的前一步，不是可选项。</li>
 *     <li>{@code TESTING → PUBLISHED}：只有从"已验证过"的状态才能对员工开放。</li>
 *     <li>{@code {DRAFT,TESTING,PUBLISHED} → DISABLED}：任何已经存在的东西都要能被叫停。</li>
 *     <li>{@code DISABLED → {TESTING,PUBLISHED}}：停用是<b>可撤销</b>的（附件只要求"禁止新用户获得该版本"）。
 *         重新启用到 PUBLISHED 是允许的——它此前已经发布过；要到 TESTING 也允许（想再验一遍）。</li>
 * </ul>
 *
 * <h3>刻意<b>不</b>允许的</h3>
 * <ul>
 *     <li>{@code PUBLISHED → DRAFT}：发布过的版本不能"退回草稿"——那会让"这个版本当时是什么"变得无法回答。
 *         要改就出新版本（版本不可变的同一口径）。</li>
 *     <li>{@code X → X}：同状态不是流转，写事件流只会多一条"其实没变"的记录。</li>
 * </ul>
 *
 * @author ai-gov
 */
public final class AigRoleReleaseTransition {

    /**
     * 允许的边（from → 可去的集合）
     */
    private static final Map<AigRoleReleaseStatusEnum, Set<AigRoleReleaseStatusEnum>> ALLOWED;

    static {
        Map<AigRoleReleaseStatusEnum, Set<AigRoleReleaseStatusEnum>> edges = new LinkedHashMap<>();
        edges.put(AigRoleReleaseStatusEnum.DRAFT, Set.of(
            AigRoleReleaseStatusEnum.TESTING, AigRoleReleaseStatusEnum.DISABLED));
        edges.put(AigRoleReleaseStatusEnum.TESTING, Set.of(
            AigRoleReleaseStatusEnum.PUBLISHED, AigRoleReleaseStatusEnum.DISABLED));
        edges.put(AigRoleReleaseStatusEnum.PUBLISHED, Set.of(
            AigRoleReleaseStatusEnum.DISABLED));
        edges.put(AigRoleReleaseStatusEnum.DISABLED, Set.of(
            AigRoleReleaseStatusEnum.TESTING, AigRoleReleaseStatusEnum.PUBLISHED));
        ALLOWED = Collections.unmodifiableMap(edges);
    }

    private AigRoleReleaseTransition() {
    }

    /**
     * 这次流转是否允许。
     *
     * @param from 源状态（可空）
     * @param to   目标状态（可空）
     * @return 允许返回 true
     */
    public static boolean canTransition(AigRoleReleaseStatusEnum from, AigRoleReleaseStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 从某状态能去哪（用于报错文案：让人当场知道能做什么，而不是只被告知"不行"）。
     *
     * @param from 源状态
     * @return 可去的状态集合；源状态为空或非法时返回空集合
     */
    public static Set<AigRoleReleaseStatusEnum> allowedFrom(AigRoleReleaseStatusEnum from) {
        if (from == null) {
            return Set.of();
        }
        return ALLOWED.getOrDefault(from, Set.of());
    }

    /**
     * 人读的"允许去哪"（形如 {@code TESTING/PUBLISHED/DISABLED}）。
     *
     * @param from 源状态
     * @return 说明文本
     */
    public static String describeAllowed(AigRoleReleaseStatusEnum from) {
        if (from == null) {
            return "（当前状态未知）";
        }
        Set<AigRoleReleaseStatusEnum> targets = allowedFrom(from);
        if (targets.isEmpty()) {
            return "没有可执行的流转";
        }
        StringBuilder text = new StringBuilder();
        for (AigRoleReleaseStatusEnum item : targets) {
            if (text.length() > 0) {
                text.append('/');
            }
            text.append(item.getCode());
        }
        return text.toString();
    }

    /**
     * 该状态是否对员工可见（只有 PUBLISHED 可见）。
     *
     * <p>刻意集中在这一处判断：把"能不能看到"散到各处，迟早会出现
     * "TESTING 的岗位被员工看到"这种只有用户能发现的问题。</p>
     *
     * @param status 状态
     * @return 对员工可见返回 true
     */
    public static boolean visibleToEmployees(AigRoleReleaseStatusEnum status) {
        return status == AigRoleReleaseStatusEnum.PUBLISHED;
    }

}
