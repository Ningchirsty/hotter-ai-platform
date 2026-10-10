package org.dromara.aigov.workspace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位能力卡片视图（主文档线增量 1b）。
 *
 * @author ai-gov
 */
@Data
public class AigRoleActionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 卡片ID
     */
    private Long actionId;

    /**
     * 卡片编码
     */
    private String actionCode;

    /**
     * 所属分类
     */
    private String categoryCode;

    /**
     * 卡片标题
     */
    private String title;

    /**
     * 卡片说明
     */
    private String description;

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

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

}
