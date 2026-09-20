package org.dromara.content.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 互动确认卡查询业务对象。
 *
 * @author content
 */
@Data
public class ContentCardQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务号（模糊）
     */
    @Size(max = 32, message = "任务号长度不能超过 32")
    private String taskNo;

    /**
     * 卡片类型
     */
    @Size(max = 16, message = "卡片类型长度不能超过 16")
    private String cardType;

    /**
     * 卡片状态
     */
    @Size(max = 16, message = "卡片状态长度不能超过 16")
    private String status;

    /**
     * 闸门等级
     */
    @Size(max = 16, message = "闸门等级长度不能超过 16")
    private String gateLevel;

    /**
     * 责任人
     */
    private Long assigneeId;

    /**
     * 是否只看待我处理（按当前登录人过滤）
     */
    private Boolean mineOnly;

    /**
     * 是否只看阻断项
     */
    private Boolean blockingOnly;

    /**
     * 分页参数容器
     */
    private Map<String, Object> params;

}
