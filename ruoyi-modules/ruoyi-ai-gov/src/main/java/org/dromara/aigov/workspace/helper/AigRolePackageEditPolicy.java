package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.common.core.utils.StringUtils;

/**
 * 岗位包编辑与流转的<b>可判定规则</b>（主文档线增量 1b）。
 *
 * <h3>为什么把这些从服务层提到一个纯类里</h3>
 * <p>这三条规则的失效方式都是"不报错"：
 * <ul>
 *     <li>允许改一份已发布版本 —— 员工当时看到的配置从此无法回答；</li>
 *     <li>允许把没校验通过的版本放出去 —— 员工侧表现是"卡片点了没反应"；</li>
 *     <li>CAS 判定写得过松（比如空串也算匹配）—— 并发编辑被静默覆盖，谁改的都不知道。</li>
 * </ul>
 * 它们在服务层里只是几个 {@code if}，单测要拉起整个 Spring 上下文才跑得到。
 * 提成纯函数后可以逐条钉住（见 {@code AigRolePackageEditPolicyTest}），
 * 服务层只剩"读库、调用、写库"。</p>
 *
 * @author ai-gov
 */
public final class AigRolePackageEditPolicy {

    private AigRolePackageEditPolicy() {
    }

    /**
     * 该状态的版本还能不能改清单/卡片。
     *
     * <p>只有 DRAFT 可改：其余状态意味着"这份配置已经被答应给谁看过"。</p>
     *
     * @param status 当前状态（可空）
     * @return 可编辑返回 true
     */
    public static boolean isEditable(AigRoleReleaseStatusEnum status) {
        return status == AigRoleReleaseStatusEnum.DRAFT;
    }

    /**
     * 流转到该目标前是否必须先通过校验。
     *
     * <p>TESTING 与 PUBLISHED 都要：测试账号看到的也是"平台给出的能力"，
     * 一份点不动的卡片在测试阶段同样会浪费一轮排查。DISABLED 不需要——
     * 叫停一个配错的东西不该先证明它是对的。</p>
     *
     * @param target 目标状态（可空）
     * @return 需要校验返回 true
     */
    public static boolean requiresValidationPass(AigRoleReleaseStatusEnum target) {
        return target == AigRoleReleaseStatusEnum.TESTING || target == AigRoleReleaseStatusEnum.PUBLISHED;
    }

    /**
     * CAS 是否匹配（覆盖草稿时的"我读到的是哪一版"）。
     *
     * <p>刻意要求两侧都非空白：把"期望为空"当成匹配，等于给并发编辑开了一个后门——
     * 不带期望值的调用方会永远成功，CAS 就形同虚设。</p>
     *
     * @param expected 调用方给出的期望哈希（可空）
     * @param actual   库中当前哈希（可空）
     * @return 匹配返回 true
     */
    public static boolean casMatches(String expected, String actual) {
        if (StringUtils.isBlank(expected) || StringUtils.isBlank(actual)) {
            return false;
        }
        return expected.trim().equals(actual.trim());
    }

}
