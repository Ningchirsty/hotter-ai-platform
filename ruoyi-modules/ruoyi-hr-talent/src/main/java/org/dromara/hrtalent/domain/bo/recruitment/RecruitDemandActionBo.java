package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招聘需求动作入参业务对象（SPEC-P2 §3.1 POST /recruit/demands/{id}/actions/{action}）。
 *
 * <p>动作集合：{@code submit} / {@code confirm} / {@code pause} / {@code resume} / {@code complete} / {@code close}。
 * 其中 {@code pause} / {@code resume} / {@code close} <b>必须</b>填写 {@code reason}
 * （业务规则见 SPEC-P2 §4.1）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitDemandActionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 操作原因（暂停、复开、关闭必填）
     */
    @Size(max = 500, message = "操作原因长度不能超过 500")
    private String reason;

    /**
     * 乐观锁版本号（可选；传入时参与并发校验，未传则跳过版本比对）
     */
    private Integer version;

    /**
     * 备注（追加到需求备注字段，可为空）
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
