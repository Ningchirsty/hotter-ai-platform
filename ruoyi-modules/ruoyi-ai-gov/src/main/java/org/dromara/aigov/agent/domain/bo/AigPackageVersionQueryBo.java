package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Package 版本清单查询条件。
 *
 * @author ai-gov
 */
@Data
public class AigPackageVersionQueryBo {

    /**
     * 所属 Package
     */
    private Long packageId;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * Manifest 扫描结论（PASS/REJECT/PENDING）
     */
    private String scanResult;

    /**
     * 版本号（模糊）
     */
    private String version;

}
