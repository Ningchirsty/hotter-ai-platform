package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 电话明文查看入参（SPEC-P3 §2.1 {@code POST /recruit/candidates/{id}/phone-view}、
 * SPEC-P4 §2.1 {@code POST /talent/profiles/{id}/phone-view}）。
 *
 * <p><b>用途必填，但刻意不用 Bean Validation 校验非空</b>：{@link #purpose} 为空时由<b>服务层</b>拒绝，
 * 并在拒绝前写入一条审计（结果 {@code denied}，§15.2「敏感访问可审计」）。
 * 若在此处加 {@code @NotBlank}，参数校验框架会先行返回 400，服务层那条「被拒绝的敏感访问」审计
 * 将<b>永远不可达</b>——而被拒绝的尝试恰恰是最需要留痕的。
 * 该口径与附件下载（{@code AttachmentDownloadBo}）、简历下载（{@code ResumeDownloadBo}）一致。</p>
 *
 * <p>长度上限仍保留在此处：超长用途属纯入参格式问题，不涉及「拒绝留痕」语义。</p>
 *
 * @author hr-talent
 */
@Data
public class PhoneViewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查看用途/事由（非空校验在服务层完成并写 {@code denied} 审计；仅记录该文本，不记录电话明文）
     */
    @Size(max = 255, message = "查看用途长度不能超过 255")
    private String purpose;

}
