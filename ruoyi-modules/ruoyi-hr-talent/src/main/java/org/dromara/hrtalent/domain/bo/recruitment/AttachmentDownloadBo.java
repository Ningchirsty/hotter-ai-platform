package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 附件预览/下载业务对象。
 *
 * <p><b>用途必填</b>（设计文档 §11.1、§15.2）：预览与下载都必须记录操作人、对象、用途、
 * 时间、IP 与结果，用途为空一律拒绝，并写入一条 {@code denied} 审计。</p>
 *
 * <p>本对象<b>有意不标注 Bean Validation 注解</b>：若由参数校验框架先行拦截，
 * 拒绝动作将无法留下审计记录，故用途的非空校验统一在服务层完成。</p>
 *
 * @author hr-talent
 */
@Data
public class AttachmentDownloadBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 操作事由/用途（必填，最长 255，服务层校验并写审计）
     */
    private String purpose;

}
