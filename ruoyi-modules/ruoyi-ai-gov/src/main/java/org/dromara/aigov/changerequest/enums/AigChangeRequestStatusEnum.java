package org.dromara.aigov.changerequest.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变更单状态（{@code aig_change_request.status}）。
 *
 * <p><b>为什么 CONFLICTED 要单独一态</b>：批准时发现"现场已经和申请时不一样了"——
 * 这既不是"已生效"（APPLIED），也不是"被驳回"（REJECTED，那是审批人的判断），
 * 而是**系统拒绝执行**。混进 REJECTED 会让人以为是审批人不同意的。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigChangeRequestStatusEnum {

    /**
     * 待批准
     */
    PENDING("PENDING", "待批准"),

    /**
     * 已批准并生效（批准即执行成功）
     */
    APPLIED("APPLIED", "已生效"),

    /**
     * 已驳回
     */
    REJECTED("REJECTED", "已驳回"),

    /**
     * 已撤回（申请人自己撤）
     */
    CANCELLED("CANCELLED", "已撤回"),

    /**
     * 批准时与现场冲突，未生效（需重新提交）
     */
    CONFLICTED("CONFLICTED", "冲突未生效");

    private final String code;

    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigChangeRequestStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigChangeRequestStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
