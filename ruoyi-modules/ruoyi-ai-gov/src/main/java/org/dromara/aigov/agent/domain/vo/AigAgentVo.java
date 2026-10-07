package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigAgent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 列表视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigAgent.class)
public class AigAgentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 编码
     */
    private String agentCode;

    /**
     * 名称
     */
    private String agentName;

    /**
     * 类别
     */
    private String category;

    /**
     * 负责人
     */
    private Long ownerId;

    /**
     * 是否平台内置（Y/N）
     */
    private String builtin;

    /**
     * 说明
     */
    private String description;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

}
