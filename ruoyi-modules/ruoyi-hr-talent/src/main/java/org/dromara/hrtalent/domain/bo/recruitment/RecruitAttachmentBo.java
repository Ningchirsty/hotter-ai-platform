package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招聘业务附件上传业务对象 hr_recruit_attachment。
 *
 * <p>只承载「业务定位 + 附件元数据」入参：主键、版本号、当前版本标识、对象存储标识、
 * 上传人与上传时间一律由服务端权威生成，<b>不接受前端写入</b>。</p>
 *
 * <p>文件本体不放在本对象内，由 Controller 以 {@code multipart/form-data} 的 {@code file} 分段接收。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitAttachmentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务类型（application/interview/background/offer/talent 等稳定编码）
     */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    /**
     * 业务对象ID
     */
    @NotNull(message = "业务对象ID不能为空")
    private Long bizId;

    /**
     * 附件类型（字典 recruit_attachment_type 编码）；为空时按 other 处理，简历（resume）不被接受
     */
    @Size(max = 32, message = "附件类型长度不能超过 32")
    private String fileType;

    /**
     * 安全级别（字典 recruit_data_level 编码）；为空时按 sensitive 处理
     */
    @Size(max = 32, message = "安全级别长度不能超过 32")
    private String securityLevel;

    /**
     * 上传用途/事由（用于审计追溯，可为空）
     */
    @Size(max = 255, message = "用途长度不能超过 255")
    private String purpose;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
