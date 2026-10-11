package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户↔品牌归属视图（④；管理端用）。
 *
 * @author ai-gov
 */
@Data
public class AigUserBrandVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long userBrandId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 品牌ID（业务主数据 ID）
     */
    private Long brandId;

    /**
     * 记录状态（0正常 1停用）：停用 = 暂时不参与可见性判定
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
