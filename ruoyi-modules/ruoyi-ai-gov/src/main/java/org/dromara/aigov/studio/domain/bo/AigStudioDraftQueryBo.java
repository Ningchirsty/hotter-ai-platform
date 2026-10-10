package org.dromara.aigov.studio.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 训练草稿查询入参。
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 训练对象编码（模糊）
     */
    private String agentCode;

    /**
     * 草稿状态（{@code AigStudioDraftStatusEnum} 的 code）
     */
    private String status;

    /**
     * 归属组织（部门ID）
     */
    private Long orgId;

    /**
     * 责任人
     */
    private Long ownerId;

}
