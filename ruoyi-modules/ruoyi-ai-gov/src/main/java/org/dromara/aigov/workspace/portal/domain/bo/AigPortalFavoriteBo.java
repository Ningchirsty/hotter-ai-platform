package org.dromara.aigov.workspace.portal.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收藏/取消收藏岗位入参（主文档线增量 5）。
 *
 * @author ai-gov
 */
@Data
public class AigPortalFavoriteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编码（必须对本人可见，服务层校验）
     */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 80, message = "岗位编码长度不能超过 80")
    private String roleCode;

    /**
     * true=收藏，false=取消收藏（默认收藏）
     */
    private Boolean favorite;

}
