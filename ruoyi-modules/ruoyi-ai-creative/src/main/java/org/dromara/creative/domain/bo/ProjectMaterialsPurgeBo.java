package org.dromara.creative.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 清理项目素材的请求（V0.2 R25）。
 *
 * @author creative
 */
@Data
public class ProjectMaterialsPurgeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 二次确认：必须与项目名**逐字相同**（服务端校验）
     */
    private String confirmName;

    /**
     * 项目**还没被删除**时是否强制清理（页面会在二次确认后传 true）
     */
    private Boolean force;

    /**
     * 批量清理的确认口令（逐字输入「清理素材」）；只用批量端点时传
     */
    private String confirmText;

    /**
     * 批量清理选中的项目ID
     */
    private java.util.List<Long> taskIds;
}
