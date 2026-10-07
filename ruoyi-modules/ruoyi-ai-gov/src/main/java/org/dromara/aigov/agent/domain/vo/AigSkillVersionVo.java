package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigSkillVersion;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Skill 版本列表视图（裁掉 Schema 与工具策略长文本，要看走详情）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigSkillVersion.class)
public class AigSkillVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Skill 版本ID
     */
    private Long skillVersionId;

    /**
     * 所属 Skill
     */
    private Long skillId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * 需要的 Provider 能力编码
     */
    private String providerCapability;

    /**
     * 是否允许外部调用（Y/N）
     */
    private String allowExternal;

    /**
     * 来源 Package 版本（第三方 Package 带入；内置 Skill 为空）
     */
    private Long packageVersionId;

    /**
     * 人工批准人
     */
    private Long approvedBy;

    /**
     * 人工批准时间
     */
    private LocalDateTime approvedAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 版本说明
     */
    private String remark;

}
