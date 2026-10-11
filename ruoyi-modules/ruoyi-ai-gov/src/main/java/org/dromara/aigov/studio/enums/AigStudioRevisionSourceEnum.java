package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 草稿修订的来源（专题 C §C4 提案式修改、§C7.1 回滚）。
 *
 * <p><b>为什么要记来源而不是只记"谁改的"</b>：一条修订是"人工敲进去的"还是"采纳了培训助手的提案"
 * 还是"从包导入"还是"回滚回来的"，决定了出问题时该找谁、以及这条修订能不能被信任。
 * 只记 {@code author_id} 的话，三者看起来一模一样。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioRevisionSourceEnum {

    /**
     * 人工编辑（P0 唯一会产生的来源）
     */
    MANUAL("MANUAL", "人工编辑"),

    /**
     * 采纳培训助手的提案（P1；提案本身落在 aig_studio_change_proposal）
     */
    COPILOT_ACCEPTED("COPILOT_ACCEPTED", "采纳培训助手提案"),

    /**
     * 从 Package 导入（复用既有 Package V1 安装产物）
     */
    IMPORT_PACKAGE("IMPORT_PACKAGE", "从 Package 导入"),

    /**
     * 回滚到某个历史修订（不修改历史修订本身，而是产生一条内容相同的新修订）
     */
    ROLLBACK("ROLLBACK", "回滚"),

    /**
     * 由「草案生成器」从 Skill/子 Agent/快捷指令草案生成（附件 07 增量 7）。
     *
     * <p><b>为什么与 {@link #MANUAL} 分开</b>：生成物是"一份可提交内容"，但它是机器按草案
     * 字段填出来的，不是人敲的。合并成一个来源后，"这条修订是人写的还是生成器填的"
     * 就再也答不出来——而这正是事后复盘"哪类草案生成的版本质量更差"的唯一线索。</p>
     */
    DRAFT_GENERATED("DRAFT_GENERATED", "由草案生成");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigStudioRevisionSourceEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigStudioRevisionSourceEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
