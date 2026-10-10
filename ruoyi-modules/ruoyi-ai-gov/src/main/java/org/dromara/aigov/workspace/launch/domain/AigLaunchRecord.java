package org.dromara.aigov.workspace.launch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位启动记录 {@code aig_launch_record}（主文档线增量 3：Launch Resolver）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_launch_record.sql} 的列注释为准</b>。</p>
 *
 * <p>它把"员工点了哪张卡片"变成一条可回答的事实：谁、按哪个岗位版本、
 * 启动到哪、对应哪个平台任务。没有它，"卡片点了没反应"只能靠猜。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_launch_record")
public class AigLaunchRecord extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 启动记录ID
     */
    @TableId(value = "launch_id")
    private Long launchId;

    /**
     * 启动时的组织（幂等作用域的一段）
     */
    private Long orgId;

    /**
     * 启动人
     */
    private Long userId;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 岗位版本
     */
    private Long roleVersionId;

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
     * 请求内容摘要（同一幂等键换内容=冲突）
     */
    private String requestDigest;

    /**
     * 幂等键
     */
    private String idempotencyKey;

    /**
     * 业务域
     */
    private String projectType;

    /**
     * 业务项目ID
     */
    private Long projectId;

    /**
     * 平台任务ID（NAVIGATION 类为空）
     */
    private Long taskId;

    /**
     * 平台任务编号
     */
    private String taskNo;

    /**
     * 启动状态（COMMITTED/FAILED）
     */
    private String launchStatus;

    /**
     * 失败时的错误码
     */
    private String errorCode;

    /**
     * 启动时间
     */
    private LocalDateTime committedAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
