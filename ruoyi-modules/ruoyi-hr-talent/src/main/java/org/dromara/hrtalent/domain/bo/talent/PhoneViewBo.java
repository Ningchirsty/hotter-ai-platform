package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 电话明文查看入参（SPEC-P3 §2.1 {@code POST /recruit/candidates/{id}/phone-view}）。
 *
 * <p><b>用途必填</b>：{@link #purpose} 为空时服务端直接拒绝，并写入审计结果 {@code denied}；
 * 非空时先写审计（{@code phone_view} / {@code talent}）再返回电话明文。</p>
 *
 * @author hr-talent
 */
@Data
public class PhoneViewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查看用途/事由（必填，审计留痕；仅记录该文本，不记录电话明文）
     */
    @NotBlank(message = "查看用途不能为空")
    @Size(max = 255, message = "查看用途长度不能超过 255")
    private String purpose;

}
