package org.dromara.aigov.tool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具版本 aig_tool_definition_version（设计 §7.5、§8.2）。
 *
 * <p>{@link #executorRef} 为空表示"仅登记、待接入"：设计 §4.1 要求这类资源如实标注，
 * 不得显示为可用。是否可执行由"执行器是否已注册"决定，而不是由这一列非空决定。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_tool_definition_version")
public class AigToolDefinitionVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具版本ID */
    @TableId(value = "tool_version_id")
    private Long toolVersionId;

    /** 所属工具 */
    private Long toolId;

    /** 版本号 */
    private String version;

    /** 执行器引用（已注册的执行器键；空=待接入） */
    private String executorRef;

    /** 操作类型 */
    private String operationType;

    /** 输入 Schema */
    private String inputSchema;

    /** 输出 Schema */
    private String outputSchema;

    /** 超时（毫秒） */
    private Integer timeoutMs;

    /** 最大尝试次数 */
    private Integer maxAttempts;

    /** 调用所需权限点 */
    private String permissionPoint;

    /** 允许处理的数据等级上限 */
    private String dataLevelMax;

    /** 记录状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;

}
