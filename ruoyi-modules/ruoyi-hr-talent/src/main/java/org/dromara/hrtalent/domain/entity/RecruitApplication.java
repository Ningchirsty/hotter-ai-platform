package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应聘记录对象 hr_recruit_application（SPEC-P3 §3.2 / 设计文档 §9.2、§7.5）。
 *
 * <p><b>业务定位</b>：一条应聘记录 = 一名人才对某个岗位执行项的一次应聘，形成独立的流程闭环；
 * 同一人才可以有多次应聘记录，流程互不覆盖（§7.5）。</p>
 *
 * <p><b>快照原则</b>：本表只保存「本次应聘」的业务快照（岗位、期望薪资、来源渠道、招聘负责人等），
 * <b>不复制</b>人才基础信息（姓名、电话、邮箱等一律回查 {@code hr_talent_profile}，§7.6.1）。</p>
 *
 * <p><b>并发控制</b>：{@link #version} 为乐观锁字段（§9.6），阶段流转必须携带版本号；
 * 每次阶段变化都要写一条 {@code hr_recruit_stage_log}，历史只追加不覆盖。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐列一致；主键列为 {@code application_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_application")
public class RecruitApplication extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录ID（主键）
     */
    @TableId(value = "application_id")
    private Long applicationId;

    /**
     * 应聘编号（业务编号，唯一，由 RecruitBusinessNoGenerator 生成）
     */
    private String applicationNo;

    /**
     * 人才主档ID（一人一档，不复制人才基础信息）
     */
    private Long talentId;

    /**
     * 岗位执行项ID（本次应聘的业务快照归属）
     */
    private Long jobId;

    /**
     * 本次应聘使用的简历版本ID（不随人才最新简历变更）
     */
    private Long resumeId;

    /**
     * 当前阶段（new/resume_review/invite/first_interview/second_interview/background/offer/pending_arrival/arrived）
     */
    private String currentStage;

    /**
     * 应聘结果（processing/passed/rejected/withdrawn/paused/talent_pool）
     */
    private String currentStatus;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 来源方式（channel/referral/import 等稳定编码）
     */
    private String sourceType;

    /**
     * 期望薪资低值（金额，低值不得大于高值）
     */
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资高值
     */
    private BigDecimal expectedSalaryMax;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 联系日期（§14 跟进联系留痕）
     */
    private LocalDate contactDate;

    /**
     * 下次跟进时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 计划报到日期（录用后约定报到日期，进入待报到阶段的必要资料，§7.2）
     */
    private LocalDate planArrivalDate;

    /**
     * 录用邀约日期（§8.7 录用邀约环节）
     */
    private LocalDate offerDate;

    /**
     * 邀约结果（accepted/rejected 等稳定编码，进入待报到阶段的必要资料，§7.2）
     */
    private String offerResult;

    /**
     * 实际到岗日期（登记报到后写入，是到岗统计的唯一事实来源）
     */
    private LocalDate arrivalDate;

    /**
     * 未报到原因（§8.4）
     */
    private String noArrivalReason;

    /**
     * 应聘（投递）时间
     */
    private LocalDateTime applyTime;

    /**
     * 进入当前阶段时间
     */
    private LocalDateTime stageEnterTime;

    /**
     * 淘汰/撤回原因
     */
    private String rejectReason;

    /**
     * 乐观锁版本号（§9.6，阶段流转必须校验）
     */
    @Version
    private Integer version;

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
