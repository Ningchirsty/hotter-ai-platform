package org.dromara.aigov.tool.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具版本视图（设计 §9.2 {@code GET /aigov/tools}：版本、操作类型、接入与权限状态）。
 *
 * <p><b>为什么必须有 {@link #executable}</b>：设计 §4.1 要求"未接入验证的工具应显示『已登记 待接入』，
 * 并说明它是否影响发布"。把这一判断放在服务端（由"执行器是否已注册"决定）而不是让前端从
 * {@code executorRef} 是否为空去猜——空字符串、写错引用都会让前端猜错，而猜错的后果是
 * 界面把不可用的工具显示成可用。</p>
 *
 * @author ai-gov
 */
@Data
public class AigToolDefinitionVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具编码 */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 版本号 */
    private String version;

    /** 操作类型 */
    private String operationType;

    /** 操作类型描述 */
    private String operationTypeDesc;

    /** 是否必须人工确认 */
    private Boolean requiresConfirmation;

    /** 是否已接入执行器（false=已登记 待接入，不可执行） */
    private Boolean executable;

    /** 调用所需权限点（可空） */
    private String permissionPoint;

    /** 数据等级上限（可空） */
    private String dataLevelMax;

}
