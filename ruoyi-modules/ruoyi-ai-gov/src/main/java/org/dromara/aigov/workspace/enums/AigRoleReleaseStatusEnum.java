package org.dromara.aigov.workspace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 岗位包版本（RoleVersion）的发布状态（附件 §4.2）。
 *
 * <p><b>为什么不与 {@code AigReleaseStatusEnum} 复用</b>：那一套是<b>技术能力</b>的发布门槛
 * （Manifest/沙箱/黄金用例/人工批准/灰度），判的是"这个 Agent/Skill 能不能用"；
 * 岗位包判的是"这个岗位的卡片配置上没上架、给谁看"。两者证据来源与审批人都不同，
 * 混用一个状态字段会让"能力已 STABLE"看起来像"岗位已上架"。</p>
 *
 * <p>附件 §4.2 明确要求：不能把 Role 的 PUBLISHED、Agent 的 STABLE、Runtime 的 READY、
 * Execution 的 SUCCEEDED 混用为一个状态字段。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigRoleReleaseStatusEnum {

    /**
     * 草稿：可编辑，未对外
     */
    DRAFT("DRAFT", "草稿", false),

    /**
     * 测试中：可用测试账号预览，但不对员工开放
     */
    TESTING("TESTING", "测试中（仅预览）", false),

    /**
     * 已发布：对绑定范围内的员工可见
     */
    PUBLISHED("PUBLISHED", "已发布", false),

    /**
     * 已停用：禁止新用户获得该版本，历史任务不受影响；可重新启用
     */
    DISABLED("DISABLED", "已停用", false);

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
    public static AigRoleReleaseStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigRoleReleaseStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
