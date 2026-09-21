package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才跟进记录业务对象 hr_talent_follow_up（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p><b>服务端权威字段</b>：跟进记录ID、跟进人、以下次要字段均由服务端维护，前端传入无效：
 * 主键不接受前端写入；{@code followerId} 缺省时回落为当前登录用户。</p>
 *
 * <p><b>沟通摘要约束</b>（设计文档 §8.16）：{@link #summary} 只允许记录与招聘有关的沟通要点，
 * <b>不得</b>保存与招聘无关的高度敏感个人信息（身份证号、银行卡号、健康/病史、家庭与婚姻状况、
 * 精确住址、宗教信仰等）。服务层在落库前做长度与内容双重校验并给出中文提示；
 * 本对象只做长度上限约束，内容判定由服务层完成。</p>
 *
 * <p><b>边界</b>：跟进只记录人才关系维护动作，<b>不</b>参与应聘阶段流转，也不写入
 * {@code hr_recruit_stage_log}（设计文档 §8.16）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentFollowUp.class, reverseConvertGenerate = false)
public class TalentFollowUpBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 跟进记录ID（编辑时必填，主键由服务端管理）
     */
    @NotNull(message = "跟进记录ID不能为空", groups = {EditGroup.class})
    private Long followId;

    /**
     * 人才主档ID（新增时必填；列表路径参数会覆盖为路径值）
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class})
    private Long talentId;

    /**
     * 联系时间（为空时回落为当前时间）
     */
    private LocalDateTime contactTime;

    /**
     * 联系方式（phone电话/wechat微信/email邮件/onsite面谈等稳定编码）
     */
    @Size(max = 32, message = "联系方式长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String contactMethod;

    /**
     * 联系结果（取 {@code ContactResultEnum} 的稳定编码，字典 talent_contact_result）
     */
    @Size(max = 32, message = "联系结果长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String contactResult;

    /**
     * 意向变化（如「期望城市由北京改为上海」）
     */
    @Size(max = 500, message = "意向变化长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String intentChange;

    /**
     * 跟进摘要（不得写入与招聘无关的高度敏感个人信息；长度上限与 DDL
     * {@code summary varchar(1000)} 一致）
     */
    @Size(max = 1000, message = "跟进摘要长度不能超过 1000", groups = {AddGroup.class, EditGroup.class})
    private String summary;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextContactTime;

    /**
     * 跟进人用户ID（为空时服务端回落为当前登录用户）
     */
    private Long followerId;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
