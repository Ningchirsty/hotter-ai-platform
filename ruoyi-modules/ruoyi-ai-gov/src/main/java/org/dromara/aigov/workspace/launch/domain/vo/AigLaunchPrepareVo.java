package org.dromara.aigov.workspace.launch.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * prepare 的结果（主文档线增量 3）。
 *
 * <p>它把"这次启动会变成什么"提前摊开给用户看：目标是什么、会不会建任务、还要补哪些输入。
 * 界面上"确认"按钮之前显示的就是这些——用户确认的必须是**将要执行的那件事**，
 * 而不是一个笼统的"启动"。</p>
 *
 * @author ai-gov
 */
@Data
public class AigLaunchPrepareVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 票据ID（commit 时原样回传）
     */
    private String ticketId;

    /**
     * 票据过期时刻
     */
    private LocalDateTime expiresAt;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 岗位版本（用户确认的是这一版的配置）
     */
    private Long roleVersionId;

    /**
     * 卡片编码
     */
    private String actionCode;

    /**
     * 卡片标题
     */
    private String title;

    /**
     * 启动方式
     */
    private String launchMode;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标引用
     */
    private String targetRef;

    /**
     * STUDIO 的专业页跳转键
     */
    private String studioRouteKey;

    /**
     * 本次是否会创建平台任务（NAVIGATION 不会）
     */
    private Boolean willCreateTask;

    /**
     * 任务类型（回显）
     */
    private String taskType;

    /**
     * 业务域（回显）
     */
    private String projectType;

    /**
     * 业务项目ID（回显）
     */
    private Long projectId;

    /**
     * 卡片要求但本次没给的上下文键（让用户知道还差什么）
     */
    private List<String> missingContextKeys;

    /**
     * 阻止启动的问题码（通过时为空；有值时不应发放票据）
     */
    private List<String> problems;

    /**
     * 是否通过（problems 为空）
     */
    private Boolean passed;

    /**
     * 同一次启动已经存在时的启动记录ID（幂等重放；不为空说明**不需要**再提交）
     */
    private Long existingLaunchId;

    /**
     * 同一次启动已经对应的任务ID
     */
    private Long existingTaskId;

    /**
     * 同一次启动已经对应的任务编号
     */
    private String existingTaskNo;

}
