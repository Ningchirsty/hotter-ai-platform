package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Package 清单查询条件（设计 §6.1）。
 *
 * @author ai-gov
 */
@Data
public class AigPackageQueryBo {

    /**
     * Package 编码（精确）
     */
    private String packageCode;

    /**
     * Package 名称（模糊）
     */
    private String packageName;

    /**
     * 类型（AGENT/SKILL/MIXED）
     */
    private String packageType;

    /**
     * 发布方（模糊）
     */
    private String publisher;

    /**
     * 来源类型（UPLOAD/TRUSTED_SOURCE）
     */
    private String sourceType;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

}
