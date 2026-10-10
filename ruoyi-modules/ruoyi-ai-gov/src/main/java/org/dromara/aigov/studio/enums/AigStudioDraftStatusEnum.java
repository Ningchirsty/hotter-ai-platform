package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent Studio 训练草稿的状态（专题 C §C2.1、§C7）。
 *
 * <p><b>它刻意<b>不</b>与发布状态同构</b>：草稿的 {@code SUBMITTED} 只表示
 * 「这份内容已经固化成了一条 {@code aig_agent_version}（DRAFT）」，<b>不等于</b>已校验、已跑沙箱、
 * 已批准、已发布。真正的发布状态只存在于 {@code aig_agent_version.release_status}
 * （{@link org.dromara.aigov.agent.enums.AigReleaseStatusEnum}），并只由
 * {@code /aigov/agent/release/advance} 那台状态机推进。</p>
 *
 * <p>把两套状态合成一个字段，会让页面把"我提交了"显示成"我发布了"——这正是本项目
 * 反复在堵的"看着生效"的一类。所以这里只有三个值：编辑中、已提交、已归档。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioDraftStatusEnum {

    /**
     * 编辑中：可以继续改，改到有意义的节点产生新修订
     */
    EDITING("EDITING", "编辑中", false),

    /**
     * 已提交：内容已固化为 DRAFT 版本；草稿仍可继续编辑（会产生后续修订），
     * 但「未发布改动」的判据变成 contentHash 与 lastPublishedHash 是否相等
     */
    SUBMITTED("SUBMITTED", "已提交", false),

    /**
     * 已归档：终态；要再训练就新建草稿
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
    public static AigStudioDraftStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigStudioDraftStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
