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
 * 工具定义 aig_tool_definition（设计 §8.2）。
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_tool_definition")
public class AigToolDefinition extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具ID */
    @TableId(value = "tool_id")
    private Long toolId;

    /** 工具编码（唯一） */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 用途说明 */
    private String description;

    /** 责任人 */
    private Long ownerId;

    /** 数据策略说明（不含密钥） */
    private String dataPolicy;

    /** 记录状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;

}
