package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 候选人新增入参（SPEC-P3 §2.1 {@code POST /recruit/candidates}）。
 *
 * <p><b>两种入库方式</b>（设计文档 §7.6.3）：</p>
 * <ol>
 *     <li><b>新建主档</b>：{@link #talentId} 为空 → 服务端先查重，通过后在同一事务内创建
 *     {@code hr_talent_profile}，并可选地创建首条应聘记录；</li>
 *     <li><b>复用已有主档</b>：{@link #talentId} 非空 → 不再创建主档，只追加应聘记录
 *     （这是「疑似重复」时的推荐处置）。</li>
 * </ol>
 *
 * <p><b>不得静默创建重复人员</b>：新建主档时若命中强/中匹配且未显式确认
 * （{@link #duplicateAck} 不为 {@code true}），服务端拒绝创建并返回命中摘要。</p>
 *
 * <p>为兼容「先确认不是同一人、再原样提交」的用法，本对象继承 {@link TalentProfileBo}
 * 的全部主档字段；复用分支只读取 {@link #talentId} 与应聘字段。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CandidateCreateBo extends TalentProfileBo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 复用已有主档ID；为空表示新建主档
     */
    private Long talentId;

    /**
     * 是否已确认「不是同一人」：命中强/中匹配时必须显式传 true 才能继续创建
     */
    private Boolean duplicateAck;

    /**
     * 是否创建首条应聘记录（默认 true；为 false 时只入库人才主档）
     */
    private Boolean createApplication;

    /**
     * 应聘岗位执行项ID（创建应聘记录时必填）
     */
    private Long jobId;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 期望薪资下限（应聘业务快照，缺省回落人才主档值）
     */
    @PositiveOrZero(message = "期望薪资下限不能为负数")
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资上限（应聘业务快照，缺省回落人才主档值）
     */
    @PositiveOrZero(message = "期望薪资上限不能为负数")
    private BigDecimal expectedSalaryMax;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 来源方式（channel/referral/import 等稳定编码）
     */
    private String sourceType;

    /**
     * 应聘备注
     */
    private String applicationRemark;

}
