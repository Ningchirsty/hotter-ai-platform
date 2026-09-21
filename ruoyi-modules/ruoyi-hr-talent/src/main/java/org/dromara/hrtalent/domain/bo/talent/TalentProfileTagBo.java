package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才标签关系业务对象 hr_talent_profile_tag
 * （SPEC-P4 §2.3 {@code PUT /talent/profiles/{id}/tags}）。
 *
 * <p><b>全量覆盖语义</b>：{@link #tagIds} 表示操作完成后该人才<b>应具备</b>的标签集合，
 * 服务层按差集逻辑删除已移除的关系、补齐新增的关系；重复挂同一标签按幂等处理。</p>
 *
 * <p><b>敏感约束（设计文档 §7.6.4）</b>：来源不是人工（{@code manual}）时，
 * 不允许挂敏感标签——背调失败、健康、家庭、年龄等敏感内容不得自动生成
 * 可被普通用户检索的标签。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentProfileTagBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（由路径参数回填）
     */
    private Long talentId;

    /**
     * 操作后应具备的标签ID集合（空集合表示清空全部标签）
     */
    @Size(max = 50, message = "单个人才最多挂 50 个标签")
    private Long[] tagIds;

    /**
     * 来源类型（manual手工/resume解析/system系统等稳定编码，默认 manual）
     */
    private String sourceType;

}
