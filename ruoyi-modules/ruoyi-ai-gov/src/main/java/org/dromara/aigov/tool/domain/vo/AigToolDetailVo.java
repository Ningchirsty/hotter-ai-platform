package org.dromara.aigov.tool.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具契约详情（设计 §6.5：「工具卡片显示用途、版本、输入输出、数据范围、操作类型、责任人和接入状态」）。
 *
 * <p><b>为什么列表视图不够、要单独一个详情</b>：列表只给"能绑定的版本"这一列事实
 * （编码/版本/操作类型/是否可执行/权限点/等级上限），而注册一个工具的人和申请绑定的人
 * 真正要核对的是<b>契约本身</b>：它收什么、回什么、超时多久、重试几次、数据策略怎么写。
 * 把 {@code inputSchema/outputSchema} 这类长文本塞进列表响应，会让"看一眼有哪些工具"
 * 变成把每个版本的契约全文都拉一遍。</p>
 *
 * <p><b>刻意带上 {@code executorRef}</b>：它是"该注册哪个执行器实现"的唯一线索——
 * 只有 {@code executable=false} 而没有引用名，接线的人只能去翻代码找 Bean 名字。</p>
 *
 * @author ai-gov
 */
@Data
public class AigToolDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具编码 */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 用途说明（可空） */
    private String description;

    /** 责任人（可空） */
    private Long ownerId;

    /** 数据策略说明（不含密钥，可空） */
    private String dataPolicy;

    /** 版本号 */
    private String version;

    /** 执行器引用（可空=仅登记、待接入） */
    private String executorRef;

    /** 操作类型 */
    private String operationType;

    /** 操作类型描述 */
    private String operationTypeDesc;

    /** 是否必须人工确认 */
    private Boolean requiresConfirmation;

    /** 是否已接入执行器（false=已登记 待接入，不可执行） */
    private Boolean executable;

    /** 调用所需权限点（可空=不额外要求） */
    private String permissionPoint;

    /** 数据等级上限（可空=不限） */
    private String dataLevelMax;

    /** 输入 Schema（JSON 文本，可空） */
    private String inputSchema;

    /** 输出 Schema（JSON 文本，可空） */
    private String outputSchema;

    /** 超时（毫秒） */
    private Integer timeoutMs;

    /** 最大尝试次数 */
    private Integer maxAttempts;

    /** 备注 */
    private String remark;

}
