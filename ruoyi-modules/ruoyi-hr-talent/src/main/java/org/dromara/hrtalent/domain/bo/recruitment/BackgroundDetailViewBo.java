package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 背调明细查看用途入参。
 *
 * <p>背调明细属「高度敏感」数据（设计文档 §15.1），不得无用途查看：调用方必须显式提供
 * {@link #purpose}，服务层先写敏感审计再返回明细（设计文档 §15.2、SPEC-P3 §3.6）。</p>
 *
 * <p><b>为什么这里不加 {@code @NotBlank}</b>：若在入参绑定阶段就拒绝，被拒绝的越权/无用途访问
 * 将不会留下任何审计痕迹。因此用途的必填校验放在服务层，先以 {@code denied} 结果写入
 * {@code hr_recruit_sensitive_audit}，再抛中文提示。</p>
 *
 * @author hr-talent
 */
@Data
public class BackgroundDetailViewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查看用途/事由（服务层校验必填，最长与审计表 purpose 列一致）
     */
    @Size(max = 255, message = "查看用途长度不能超过 255")
    private String purpose;

}
