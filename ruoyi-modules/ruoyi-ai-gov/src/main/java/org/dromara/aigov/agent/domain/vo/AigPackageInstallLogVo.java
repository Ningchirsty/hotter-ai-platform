package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigPackageInstallLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Package 安装日志视图（账本行，只读）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigPackageInstallLog.class)
public class AigPackageInstallLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Long logId;

    /**
     * Package 版本ID
     */
    private Long packageVersionId;

    /**
     * 动作
     */
    private String action;

    /**
     * 操作人
     */
    private Long operatorId;

    /**
     * 结果
     */
    private String result;

    /**
     * 说明
     */
    private String detail;

    /**
     * 证据引用
     */
    private String evidenceRef;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
