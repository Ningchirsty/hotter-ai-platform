package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门户里的能力卡片（主文档线增量 2）。
 *
 * <h3>为什么不用管理台那个 {@code AigRoleActionVo}</h3>
 * <p>管理台的卡片视图带 {@code actionId}、{@code enabled}、清单哈希等"管理视角"字段。
 * 门户是<b>对外契约</b>：员工只需要"能启动这张卡片"的信息。
 * 两者共用一个 VO 会诱使门户的字段随着管理台增长——某天把内部字段顺手带出去，
 * 而这是那种"多一个字段没人报错"的泄漏。分开写，门户就有自己的边界。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalActionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 卡片编码（启动时回传，用于幂等与审计）
     */
    private String actionCode;

    /**
     * 所属分类
     */
    private String categoryCode;

    /**
     * 卡片标题（业务名）
     */
    private String title;

    /**
     * 卡片说明（适用事项/所需资料/输出内容）
     */
    private String description;

    /**
     * 启动方式（QUICK/FORM/STUDIO/NAVIGATION）
     */
    private String launchMode;

    /**
     * 目标类型（SCENARIO/QUICK_CAPABILITY/NAVIGATION）
     */
    private String targetType;

    /**
     * 目标引用（SCENARIO 时是 {@code scenario://code@version}；NAVIGATION 时是 routeKey）
     */
    private String targetRef;

    /**
     * STUDIO 模式的专业页跳转键
     */
    private String studioRouteKey;

    /**
     * 所需上下文键（逗号分隔）
     */
    private String requiredContext;

    /**
     * 排序（同分类内）
     */
    private Integer sortOrder;

}
