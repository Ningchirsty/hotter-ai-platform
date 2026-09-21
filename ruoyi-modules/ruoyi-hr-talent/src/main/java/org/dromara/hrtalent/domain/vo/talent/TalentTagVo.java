package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.enums.TalentTagCategoryEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才标签视图对象 hr_talent_tag（SPEC-P4 §2.3 C 线）。
 *
 * <p><b>敏感标签展示约束（设计文档 §7.6.4）</b>：{@link #sensitiveFlag} 为 {@code 1} 的标签
 * 只对集团级人才管理员可见，普通用户的标签字典查询结果中不包含敏感标签，
 * 避免「背调失败 / 健康 / 家庭 / 年龄」类内容被当作可检索标签使用。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentTag.class)
public class TalentTagVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 标签编码（业务编号，唯一）
     */
    private String tagCode;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签分类（字典 talent_tag_category 编码）
     */
    private String tagCategory;

    /**
     * 标签分类标签（字典 talent_tag_category）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "tagCategory", other = "talent_tag_category")
    private String tagCategoryLabel;

    /**
     * 是否敏感标签（0否 1是）
     */
    private String sensitiveFlag;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 状态（active/disabled 等稳定编码）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 标签分类中文兜底（字典 talent_tag_category 未配置时保证页面有中文可展示）。
     *
     * @return 中文分类名，未知/空编码返回 null
     */
    public String getTagCategoryText() {
        TalentTagCategoryEnum category = TalentTagCategoryEnum.find(tagCategory);
        return category == null ? null : category.getDesc();
    }

}
