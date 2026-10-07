package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 版本发布事件账本 aig_release_event（追加型，三类版本共用）。
 *
 * <p><b>它存在的理由不是「顺便留个日志」，而是某条不变式的唯一依据</b>：
 * 发布状态机允许 {@code DISABLED → STABLE}（停用必须可撤销，否则回滚只能靠直接改库），
 * 但状态机只能看到<b>当前状态</b>、看不到历史。若不核对历史，
 * 「先停用再启用」就能把从未发布过的版本推成 STABLE —— 一条绕道发布的后门。
 * 因此服务层必须能回答「这个版本曾经 STABLE 过吗」，
 * 而本表就是那个问题的唯一证据来源（{@code aig_package_install_log} 只覆盖 Package 版本，
 * Agent/Skill 版本此前没有任何发布记录）。</p>
 *
 * <p>与 {@code aig_package_install_log} 一样：<b>不继承 {@code BaseEntity}</b>
 * （DDL 里没有 create_by/create_time 等平台列，继承会让自动填充往不存在的列写，
 * INSERT 直接失败），<b>不带 del_flag</b>，时间列只有 {@link #operateTime}。</p>
 *
 * <p>{@link #passedGates} 记录的是<b>本次推进所依据</b>的门槛，不是「累计通过的门槛」：
 * 累计集合可以从前序事件推出来，而「这一次凭什么前进」记错了就再也还原不了
 * （例如把停用也记成「过了灰度」）。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_release_event")
public class AigReleaseEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发布事件ID
     */
    @TableId(value = "event_id")
    private Long eventId;

    /**
     * 对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    private String targetType;

    /**
     * 对象版本ID
     */
    private Long targetVersionId;

    /**
     * 源发布状态
     */
    private String fromStatus;

    /**
     * 目标发布状态
     */
    private String toStatus;

    /**
     * 本次推进所依据的门槛（逗号分隔；停用/归档这类运维动作为空）
     */
    private String passedGates;

    /**
     * 操作人（系统触发为空）
     */
    private Long operatorId;

    /**
     * 说明
     */
    private String detail;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
