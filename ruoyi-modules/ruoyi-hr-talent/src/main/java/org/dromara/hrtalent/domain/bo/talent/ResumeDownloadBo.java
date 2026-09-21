package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 简历受控下载业务对象（SPEC-P4 §2.1 / 设计文档 §11.1、§15.2）。
 *
 * <p><b>用途必填</b>：简历下载必须先经人才资源级鉴权，再写审计，最后才流式返回内容；
 * 用途（{@code purpose}）为空一律拒绝，并写入一条 {@code denied} 审计。</p>
 *
 * <p>本对象<b>有意不标注 Bean Validation 注解</b>：若由参数校验框架先行拦截，
 * 拒绝动作将无法留下审计记录，故非空校验统一在服务层完成（与
 * {@code AttachmentDownloadBo} 一致）。</p>
 *
 * @author hr-talent
 */
@Data
public class ResumeDownloadBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下载事由/用途（必填，最长 255，服务层校验并写审计）
     */
    private String purpose;

}
