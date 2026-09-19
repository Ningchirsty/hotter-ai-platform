package org.dromara.talent.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.talent.domain.vo.ResumeImportPreviewVo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 简历导入确认业务对象。
 * <p>
 * 确认时以 {@code talent} 内的值为准（用户可修改预览候选值），
 * 服务端<b>不回填</b>未提交字段的抽取结果，因此前端必须先把预览值写进表单再提交。
 *
 * @author talent
 */
@Data
public class ResumeImportConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导入凭证（来自 {@link ResumeImportPreviewVo#importToken}）
     */
    @NotBlank(message = "导入凭证不能为空")
    private String importToken;

    /**
     * 人才主档信息（执行与新增完全一致的校验与建档路径）
     */
    @NotNull(message = "人才信息不能为空")
    @Valid
    private TlTalentBo talent;

}
