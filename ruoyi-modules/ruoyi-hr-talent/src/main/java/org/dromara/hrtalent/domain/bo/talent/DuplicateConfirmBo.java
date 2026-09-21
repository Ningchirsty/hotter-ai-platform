package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 确认疑似重复业务对象（SPEC-P4 §2.5 POST /talent/duplicates/{id}/confirm）。
 *
 * <p><b>语义</b>：人工确认「这两条是同一人」，把案件推进到待合并状态
 * （{@code status = pending} 且 {@code confirmed_flag = '1'}），
 * 后续由集团人才管理员调用合并接口完成关系转移；确认本身<b>不改动任何人才资料</b>。</p>
 *
 * <p>确认人必须填写确认依据（§8.18「显示高风险提示，允许授权用户确认」），
 * 用于审计追溯与后续合并的 {@code merge_reason}。</p>
 *
 * @author hr-talent
 */
@Data
public class DuplicateConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 确认结论：是否为同一人（true 同一人 / false 非同一人）
     */
    @NotNull(message = "请明确是否为同一人")
    private Boolean samePerson;

    /**
     * 确认依据（必填，用于审计追溯）
     */
    private String reason;

    /**
     * 下一次复核提醒日期（可空，仅作提示信息保存到备注）
     */
    private LocalDate reviewDate;

}
