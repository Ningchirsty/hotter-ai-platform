package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent / Skill / Package 版本的发布状态（设计 §5.4、§6.3-6/7）。
 *
 * <p><b>为什么发布状态落在「版本」上、而不是落在 Agent 上</b>：设计 §5.4 的门槛是
 * 「通过 Manifest 校验、沙箱运行、黄金用例、人工批准、小范围灰度」——这些全部是
 * <b>逐版本</b>过的。父表若再存一份「这个 Agent 处于什么状态」，就会与版本状态不一致：
 * 一个 Agent 完全可能同时有 STABLE 的 v1 和刚进 DRAFT 的 v2。
 * 因此父表只有记录状态（0正常/1停用），发布状态只在这里。</p>
 *
 * <p>{@link #ARCHIVED} 是唯一终态：归档之后不再有任何出边（要再发布就出新版本）。
 * {@link #DISABLED} 刻意**不是**终态——停用是可以被撤销的（回滚后重新启用目标版本），
 * 若把它当终态，回滚就只能靠直接改库。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigReleaseStatusEnum {

    /**
     * 草稿：刚创建，尚未通过任何校验
     */
    DRAFT("DRAFT", "草稿", false),

    /**
     * 已校验：Manifest / 输入输出 Schema 校验通过
     */
    VALIDATED("VALIDATED", "已校验（Manifest/Schema 通过）", false),

    /**
     * 沙箱已跑通：在沙箱项目运行过脱敏黄金用例，未触碰正式资产
     */
    SANDBOX_TESTED("SANDBOX_TESTED", "沙箱已跑通", false),

    /**
     * 候选：已通过审批，仅绑定范围内的品牌/部门/测试项目可用（灰度中）
     */
    CANDIDATE("CANDIDATE", "候选（灰度中，仅绑定范围可见）", false),

    /**
     * 稳定：灰度达标，正式发布
     */
    STABLE("STABLE", "稳定（已正式发布）", false),

    /**
     * 已停用：出过问题或主动下线；可重新启用，也可归档
     */
    DISABLED("DISABLED", "已停用", false),

    /**
     * 已归档：终态
     */
    ARCHIVED("ARCHIVED", "已归档", true);

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否终态
     */
    private final boolean terminal;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigReleaseStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigReleaseStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
