package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才跟进记录对象 hr_talent_follow_up（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p><b>与招聘阶段历史分开</b>（设计文档 §8.16）：本表只记录「人才关系维护」性质的接触
 * （电话/微信/邮件/面谈、意向变化、下次联系时间等），
 * <b>绝不写入 {@code hr_recruit_stage_log}</b>，也不得由本表反推应聘阶段流转。</p>
 *
 * <p><b>敏感信息约束</b>（设计文档 §8.16）：{@link #summary} 只允许记录与招聘有关的沟通要点，
 * 不得记录与招聘无关的高度敏感个人信息（身份证号、银行卡号、健康/病史、家庭与婚姻、
 * 精确住址、宗教信仰等）。服务层在落库前做长度与内容校验并给出中文提示，
 * 本实体不做任何自动脱敏或改写。</p>
 *
 * <p><b>附件说明</b>：本表 DDL 未提供附件列（{@code attachment_ids} 缺口），
 * 因此实体不映射附件字段；跟进附件需先补列后再落地（见交付说明，不自行加列）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_follow_up")
public class TalentFollowUp extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 跟进记录ID（主键，DDL 为 {@code follow_id}）
     */
    @TableId(value = "follow_id")
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
     * 联系方式（phone电话/wechat微信/email邮件/onsite面谈等稳定编码）
     */
    private String contactMethod;

    /**
     * 联系结果（connected/no_answer/refused/interested/follow_up_later/invalid，
     * 取 {@code ContactResultEnum} 的稳定编码，字典 talent_contact_result）
     */
    private String contactResult;

    /**
     * 意向变化（§8.16，如「期望城市由北京改为上海」）
     */
    private String intentChange;

    /**
     * 跟进摘要（禁止写入电话明文等敏感信息，服务层做长度与内容校验）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
