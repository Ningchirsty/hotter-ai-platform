package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Package 安装日志 aig_package_install_log（设计 §6.3 安装流程的账本）。
 *
 * <p><b>刻意不继承 {@code BaseEntity}</b>，也不带 {@code del_flag}：这是一张
 * <b>追加型账本</b>（与 {@code aig_callback} 同类），一行一个动作，不允许改写或删除——
 * 否则「谁在什么时候把哪个版本放出去」这件事就可以被抹掉，而那正是审批链路的证据。</p>
 *
 * <p>⚠️ 这里有个容易踩的实现细节：本表在 DDL 里<b>没有</b> {@code create_by/create_time/
 * update_by/update_time} 这些平台列。若让实体继承 {@code BaseEntity}，
 * MyBatis-Plus 的自动填充会把 {@code create_by/create_time} 填成非空值，
 * 于是 INSERT 语句里会带上这两列 → <b>Unknown column，插入直接失败</b>
 * （默认 {@code FieldStrategy.NOT_NULL} 只是跳过 null 字段，填充过的字段不会被跳过）。
 * 因此本实体是独立类，字段与列一一对应。</p>
 *
 * <p>同理，时间列只有 {@link #operateTime}（不叫 create_time）：账本记的是
 * <b>动作发生的时间</b>，而不是「这条记录被创建的时间」。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_package_install_log")
public class AigPackageInstallLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * Package 版本ID
     */
    private Long packageVersionId;

    /**
     * 动作（UPLOAD/SCAN/SANDBOX/RUN_CASES/APPROVE/PUBLISH_CANDIDATE/PUBLISH_STABLE/DISABLE/ROLLBACK）
     */
    private String action;

    /**
     * 操作人（系统触发为空）
     */
    private Long operatorId;

    /**
     * 结果（PASS/FAIL/REJECT）
     */
    private String result;

    /**
     * 说明（拒绝/失败时必须写明原因）
     */
    private String detail;

    /**
     * 证据引用（评测报告/沙箱日志的对象键，不存副本）
     */
    private String evidenceRef;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
