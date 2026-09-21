package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentTag;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才标签字典业务对象 hr_talent_tag（SPEC-P4 §2.3 {@code GET/POST/PUT /talent/tags}）。
 *
 * <p><b>敏感标签约束（设计文档 §7.6.4、§8.15）</b>：</p>
 * <ul>
 *     <li>标签名称命中敏感/歧视性词库时，服务层一律拒绝创建与修改；</li>
 *     <li>{@link #sensitiveFlag} 为 {@code 1} 的标签只能由集团级人才管理员维护；</li>
 *     <li>背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签。</li>
 * </ul>
 * <p>{@code tag_code} 由服务端生成，不接受前端写入。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentTag.class, reverseConvertGenerate = false)
public class TalentTagBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID（编辑时必填）
     */
    @NotNull(message = "标签ID不能为空", groups = {EditGroup.class})
    private Long tagId;

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 100, message = "标签名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String tagName;

    /**
     * 标签分类（字典 talent_tag_category 编码：skill/job_direction/industry/experience/language/certificate/other）
     */
    @NotBlank(message = "标签分类不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 32, message = "标签分类长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String tagCategory;

    /**
     * 是否敏感标签（0否 1是；标记为 1 需集团级人才管理员权限）
     */
    @Pattern(regexp = "^(0|1)?$", message = "敏感标记只能为 0 或 1", groups = {AddGroup.class, EditGroup.class})
    private String sensitiveFlag;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 状态（active启用/disabled停用等稳定编码）
     */
    @Size(max = 32, message = "状态长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
