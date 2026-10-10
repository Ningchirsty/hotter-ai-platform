package org.dromara.aigov.workspace.launch.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 启动记录（按任务查；主文档线增量 4：专业台桥接与回跳用）。
 *
 * <h3>它给专业页什么</h3>
 * <p>专业台（创作/视频等页面）拿到 URL query 里的 {@code taskId} 后，需要回答两个问题：
 * "这个任务是从哪个岗位的哪张卡片启动的？"、"返回时该回到哪里？"。答案就在这里：
 * 岗位编码/名称 + 卡片编码 + 目标类型。</p>
 *
 * <h3>为什么只给"自己发起的"</h3>
 * <p>查询按登录用户过滤（见服务实现）。不这么做，任何登录用户只要猜到 taskId 就能看到
 * "别人从哪张卡片启动的"——它不是敏感数据，但没有理由把它做成一个可枚举的接口。</p>
 *
 * <h3>刻意不带的东西</h3>
 * <p>不带请求摘要、幂等键与任何内部错误码：那些是排查用的事实，不是给专业页展示的。</p>
 *
 * @author ai-gov
 */
@Data
public class AigLaunchRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 启动记录ID
     */
    private Long launchId;

    /**
     * 平台任务ID
     */
    private Long taskId;

    /**
     * 平台任务编号
     */
    private String taskNo;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 岗位名称（回跳入口上直接显示，避免专业页再查一次）
     */
    private String roleName;

    /**
     * 卡片编码
     */
    private String actionCode;

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
     * 启动时间
     */
    private LocalDateTime committedAt;

}
