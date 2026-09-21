package org.dromara.hrtalent.domain.vo.talent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;
import org.dromara.hrtalent.enums.ContactResultEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才跟进记录视图对象 hr_talent_follow_up（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p>字典标签与用户昵称统一用 {@code @Translation} 回填；跟进人昵称经由只读属性
 * {@link #getFollowerIdText()} 取数，避免翻译静默失效。</p>
 *
 * <p><b>不含敏感字段</b>：跟进摘要本身要求不写入与招聘无关的高度敏感个人信息，
 * 本 VO 不再额外返回电话/邮箱等字段。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentFollowUp.class, reverseConvertGenerate = false)
public class TalentFollowUpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 跟进记录ID
     */
    private Long followId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 联系时间
     */
    private LocalDateTime contactTime;

    /**
     * 联系方式（phone/wechat/email/onsite 等稳定编码）
     */
    private String contactMethod;

    /**
     * 联系结果（字典 talent_contact_result 编码）
     */
    private String contactResult;

    /**
     * 联系结果中文标签（由 {@code ContactResultEnum} 兜底转换；字典未定义时也有中文可展示）
     */
    private String contactResultLabel;

    /**
     * 意向变化
     */
    private String intentChange;

    /**
     * 跟进摘要（服务层已保证不含与招聘无关的高度敏感个人信息）
     */
    private String summary;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextContactTime;

    /**
     * 跟进人用户ID
     */
    private Long followerId;

    /**
     * 跟进人昵称（由 {@link #getFollowerIdText()} 批量翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "followerIdText")
    private String followerName;

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
     * 跟进人用户ID入库原串（单个ID）。
     *
     * <p>只读属性：作为 {@link #followerName} 翻译的取数来源（翻译处理器按 getter 取值），
     * 并用 {@code @JsonIgnore} 避免重复输出。</p>
     *
     * @return 跟进人ID字符串，无跟进人时返回 null
     */
    @JsonIgnore
    public String getFollowerIdText() {
        return followerId == null ? null : String.valueOf(followerId);
    }

    /**
     * 联系结果中文标签。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getContactResultLabel() {
        ContactResultEnum item = ContactResultEnum.find(contactResult);
        return item == null ? null : item.getDesc();
    }

}
