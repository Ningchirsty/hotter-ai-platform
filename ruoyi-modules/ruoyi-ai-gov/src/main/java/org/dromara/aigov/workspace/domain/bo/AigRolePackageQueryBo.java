package org.dromara.aigov.workspace.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位包清单查询入参（主文档线增量 1b）。
 *
 * @author ai-gov
 */
@Data
public class AigRolePackageQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编码（模糊）
     */
    private String roleCode;

    /**
     * 岗位名称（模糊）
     */
    private String roleName;

    /**
     * 发布状态（精确；DRAFT/TESTING/PUBLISHED/DISABLED）
     */
    private String releaseStatus;

}
