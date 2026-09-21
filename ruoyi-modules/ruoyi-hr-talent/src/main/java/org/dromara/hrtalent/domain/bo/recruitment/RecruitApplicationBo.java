package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitApplication;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应聘记录业务对象 hr_recruit_application（SPEC-P3 §2.2 / §3.2）。
 *
 * <p><b>不接受</b>前端写入 {@code current_stage} / {@code current_status}：阶段只能经流转接口变更，
 * 且每次变更必须落一条阶段历史；{@code version} 在编辑场景必填，用于乐观锁校验。</p>
 *
 * <p>本对象只承载「本次应聘的业务快照」，不含人才基础信息（姓名、电话、邮箱等），
 * 与 §7.6.1「创建应聘记录不复制人才基础信息」一致。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitApplication.class, reverseConvertGenerate = false)
public class RecruitApplicationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录ID（编辑场景由路径变量 {@code /recruit/applications/{id}} 注入）
     */
    private Long applicationId;

    /**
     * 人才主档ID（新增必填）
     */
    @NotNull(message = "人才主档不能为空", groups = {AddGroup.class})
    private Long talentId;

    /**
     * 岗位执行项ID（新增必填）
     */
    @NotNull(message = "应聘岗位不能为空", groups = {AddGroup.class})
    private Long jobId;

    /**
     * 本次应聘使用的简历版本ID（不随人才最新简历变更）
     */
    private Long resumeId;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 来源方式（channel/referral/import 等稳定编码）
     */
    @Size(max = 32, message = "来源方式长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String sourceType;

    /**
     * 期望薪资低值（低值不得大于高值）
     */
    @DecimalMin(value = "0", message = "期望薪资低值不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资高值
     */
    @DecimalMin(value = "0", message = "期望薪资高值不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal expectedSalaryMax;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 下次跟进时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 应聘（投递）时间，为空时服务端取当前时间
     */
    private LocalDateTime applyTime;

    /**
     * 乐观锁版本号（编辑时必填，取自详情返回值）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试", groups = {EditGroup.class})
    private Integer version;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
