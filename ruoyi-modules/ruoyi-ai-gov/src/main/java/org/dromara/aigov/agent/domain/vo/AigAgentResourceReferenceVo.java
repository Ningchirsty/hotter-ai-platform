package org.dromara.aigov.agent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 「谁在用这份资源」——绑定行 + 它属于哪个 Agent 版本（设计 §6.4「引用该版本的 Agent」）。
 *
 * <p><b>为什么值得单独一个视图</b>：退役一个知识版本、或停用一个工具之前，
 * 维护人真正要回答的是"**停了会影响谁**"。绑定表只给 {@code agentVersionId}，
 * 运维拿到一串 ID 无法判断影响面——于是退役变成"先停掉再看谁来报错"。
 * 这里把 Agent 编码/名称/版本一并解析出来（解析属于 Agent 模块自己的事，
 * 所以放在本模块而不是让知识侧去读 Agent 的表）。</p>
 *
 * <p>{@code enabled=N} 的行也照样返回并如实标注：停用中的绑定**不会**被采用，
 * 但它仍然是"这条资源曾经给过谁"的记录——把它藏起来，人会以为"没人用过"。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAgentResourceReferenceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定ID */
    private Long bindingId;

    /** Agent 版本ID */
    private Long agentVersionId;

    /** Agent ID */
    private Long agentId;

    /** Agent 编码（版本或 Agent 已删除时为空） */
    private String agentCode;

    /** Agent 名称（版本或 Agent 已删除时为空） */
    private String agentName;

    /** Agent 版本号 */
    private String agentVersion;

    /** 版本发布状态（供"这个引用是不是在线上"的判断） */
    private String releaseStatus;

    /** 绑定是否启用（Y/N） */
    private String enabled;

    /** 限定公司（空=不限） */
    private Long companyId;

    /** 限定品牌（空=不限） */
    private Long brandId;

    /** 限定场景（空=不限） */
    private String scenarioCode;

    /** 限定角色（逗号分隔的 role_key，空=不限） */
    private String roleScope;

    /** 绑定创建时间 */
    private LocalDateTime createTime;

}
