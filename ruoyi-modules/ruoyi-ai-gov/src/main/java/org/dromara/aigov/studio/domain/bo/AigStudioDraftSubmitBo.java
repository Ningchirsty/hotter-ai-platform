package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交训练草稿入参（把草稿固化成一条 DRAFT Agent 版本）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本号（可空）。
     *
     * <p><b>为空时</b>服务端按 {@code 0.1.<当前修订号>} 生成一个草稿版本号；
     * 若该号已被占用则**明确报错要求显式指定**，而不是自动跳到下一个——
     * "悄悄换一个版本号"会让调用方以为发布的还是它要的那个版本。</p>
     */
    @Size(max = 32, message = "版本号长度不能超过 32")
    private String version;

    /**
     * 版本说明（写入 aig_agent_version.remark）
     */
    @Size(max = 500, message = "版本说明长度不能超过 500")
    private String remark;

}
