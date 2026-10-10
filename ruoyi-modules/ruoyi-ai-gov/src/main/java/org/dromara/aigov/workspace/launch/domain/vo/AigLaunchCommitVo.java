package org.dromara.aigov.workspace.launch.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * commit 的结果（主文档线增量 3）。
 *
 * <p>{@link #replayed} 说明这次是不是**幂等重放**（同一个幂等键又提交了一次）。
 * 界面必须能区分"这次真的启动了"和"你上次已经启动过了，这是同一次"——
 * 否则用户会以为按钮没生效而反复点。</p>
 *
 * @author ai-gov
 */
@Data
public class AigLaunchCommitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 启动记录ID
     */
    private Long launchId;

    /**
     * 平台任务ID（NAVIGATION 为空）
     */
    private Long taskId;

    /**
     * 平台任务编号
     */
    private String taskNo;

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
     * 启动状态（COMMITTED/FAILED）
     */
    private String launchStatus;

    /**
     * 是否是幂等重放
     */
    private Boolean replayed;

    /**
     * 启动时间
     */
    private LocalDateTime committedAt;

    /**
     * 阻止启动的问题（码 + 可直接展示的文案；通过时为空）
     *
     * <p><b>为什么用 200 + problems 而不是抛异常</b>：这些是**业务拒绝**（票据过期、配额耗尽、
     * 权限/输入不对），前端需要拿到稳定错误码与文案来决定怎么提示；而真正的系统故障仍会抛异常
     * 走全局处理。代价是调用方必须看 {@link #passed} —— 所以它与问题一起返回，不能只看 HTTP 状态。</p>
     */
    private List<AigLaunchProblemVo> problems;

    /**
     * 是否通过（problems 为空）
     */
    private Boolean passed;

}
