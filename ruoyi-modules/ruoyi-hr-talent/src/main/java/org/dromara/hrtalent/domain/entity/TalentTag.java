package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才标签字典对象 hr_talent_tag（设计文档 §8.15、§9.2）。
 *
 * <p><b>定位</b>：标签表达<b>稳定、可检索</b>的人才特征（技能、岗位方向、行业、经验、语言、证书等），
 * 由标签字典统一维护，不允许各业务处自由拼字符串。</p>
 *
 * <p><b>敏感与歧视性约束（设计文档 §7.6.4、§8.15）</b>：</p>
 * <ul>
 *     <li>禁止随意创建敏感或歧视性标签；标签名称命中敏感/歧视词库时，服务层一律拒绝；</li>
 *     <li>{@link #sensitiveFlag} 为 {@code 1} 的标签属于受控标签，
 *     只能由集团级人才管理员（{@code TalentScopeDomainService#isGroupLevelAdmin()}）维护，
 *     且对普通用户不出现在标签字典查询结果中；</li>
 *     <li><b>背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签</b>：
 *     来源为解析/系统的自动打标不得挂敏感标签。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_tag")
public class TalentTag extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID（主键）
     */
    @TableId(value = "tag_id")
    private Long tagId;

    /**
     * 标签编码（业务编号，唯一，由服务端生成）
     */
    private String tagCode;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签分类（skill/job_direction/industry/experience/language/certificate/other，字典 talent_tag_category）
     */
    private String tagCategory;

    /**
     * 是否敏感标签（0否 1是，敏感标签展示与授权单独控制）
     */
    private String sensitiveFlag;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 状态（active启用/disabled停用等稳定编码）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
