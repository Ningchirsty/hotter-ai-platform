package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 快捷指令草案视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioActionDraft.class)
public class AigStudioActionDraftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    private Long actionDraftId;

    /**
     * 草案编码
     */
    private String draftCode;

    /**
     * 草案名称
     */
    private String draftName;

    /**
     * 指令编码
     */
    private String actionCode;

    /**
     * 指令名称
     */
    private String actionName;

    /**
     * 启动方式（QUICK/FORM/STUDIO/NAVIGATION）
     */
    private String launchMode;

    /**
     * 启动目标类型
     */
    private String targetType;

    /**
     * 启动目标引用
     */
    private String targetRef;

    /**
     * Agent 类别
     */
    private String agentCategory;

    /**
     * 指令目标
     */
    private String objective;

    /**
     * 产出要求
     */
    private String outputContract;

    /**
     * 所需能力
     */
    private String providerCapability;

    /**
     * 允许工具（逗号分隔）
     */
    private String allowedTools;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

}
