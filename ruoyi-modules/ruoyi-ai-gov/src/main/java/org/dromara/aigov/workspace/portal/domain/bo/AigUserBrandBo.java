package org.dromara.aigov.workspace.portal.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户↔品牌归属编辑入参（④）。
 *
 * @author ai-gov
 */
@Data
public class AigUserBrandBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 品牌ID（业务主数据 ID）
     */
    @NotNull(message = "品牌ID不能为空")
    private Long brandId;

    /**
     * 备注
     */
    private String remark;

}
