package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 候选资产状态（设计 §9.2「SUCCEEDED 只代表 Provider 返回成功；候选资产仍需 QA/人工审核」）。
 *
 * <p><b>核心不变式：自动流程只筛除、不放行。</b>见 {@link #isAutoAssignable()}——
 * 自动 QA 可以给出 {@link #REJECTED}（明确不合格）或维持 {@link #CANDIDATE}（供人选），
 * 但<b>永远不能</b>置 {@link #APPROVED}。理由不是谨慎，而是责任：
 * 「选定哪一张」是有业务后果的决定，必须有人担；让自动流程放行，
 * 一旦选错就找不到责任人，而这类错误（选了一张产品不一致的图去投放）代价很高。
 * 因此 {@code APPROVED} 只能由人工操作产生，且必须记录 {@code selected_by}。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigCandidateStatusEnum {

    /**
     * 候选：自动 QA 未筛除，等待人工选定
     */
    CANDIDATE("CANDIDATE", "候选（待人工选定）"),

    /**
     * 已选定：只能由人工产生
     */
    APPROVED("APPROVED", "已选定（必须人工）"),

    /**
     * 已筛除：自动 QA 或人工都可以筛除
     */
    REJECTED("REJECTED", "已筛除");

    /**
     * 允许自动流程写入的状态集合。
     * <p>刻意<b>不含</b> {@link #APPROVED}——见类注释。</p>
     */
    private static final Set<AigCandidateStatusEnum> AUTO_ASSIGNABLE =
        EnumSet.of(CANDIDATE, REJECTED);

    /**
     * 编码
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 该状态能否由自动流程写入（自动 QA / 编排器）。
     *
     * @return 可以返回 true；{@link #APPROVED} 恒为 false
     */
    public boolean isAutoAssignable() {
        return AUTO_ASSIGNABLE.contains(this);
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigCandidateStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigCandidateStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
