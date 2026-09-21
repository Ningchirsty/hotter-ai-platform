package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitBackground;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘背调记录业务对象 hr_recruit_background。
 * <p>入参只覆盖背调业务字段：主键由服务端维护，删除标志、创建人/时间等通用字段不在本 BO 内。</p>
 *
 * <p><b>结论、原因分类与枚举</b>：{@code result} 取 {@code BackgroundResultEnum} 的稳定编码，
 * {@code status} 取 {@code BackgroundStatusEnum} 的稳定编码，{@code failureReasonCode}
 * 必须是字典编码、<b>不得传中文</b>，均在本 BO 之外由服务层校验，避免裸字符串写入。</p>
 *
 * <p><b>敏感入参</b>：{@link #detailCipher} 是背调敏感说明的明文入参，服务端落库前加密；
 * 禁止把该字段写入日志或审计明细（设计文档 §15.1、§21.9）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitBackground.class, reverseConvertGenerate = false)
public class RecruitBackgroundBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 背调记录ID（编辑时必填）
     */
    @NotNull(message = "背调记录ID不能为空", groups = {EditGroup.class})
    private Long backgroundId;

    /**
     * 应聘记录ID（新增时必填；编辑时忽略，不允许改挂到其它应聘记录）
     */
    @NotNull(message = "应聘记录ID不能为空", groups = {AddGroup.class})
    private Long applicationId;

    /**
     * 是否已获得候选人授权（0否 1是），空值按「否」处理
     */
    @Pattern(regexp = "^(0|1)?$", message = "是否已获得候选人授权只能为 0 或 1", groups = {AddGroup.class, EditGroup.class})
    private String authorizedFlag;

    /**
     * 授权时间
     */
    private LocalDateTime authorizeTime;

    /**
     * 背调负责人用户ID
     */
    private Long checkerId;

    /**
     * 背调完成时间
     */
    private LocalDateTime checkTime;

    /**
     * 背调开始日期
     */
    private LocalDate checkStartDate;

    /**
     * 背调结束日期
     */
    private LocalDate checkEndDate;

    /**
     * 背调结论（{@code BackgroundResultEnum} 的 code：pending/pass/fail/waived），空值按待背调处理
     */
    private String result;

    /**
     * 未通过原因编码（字典编码，不存中文；result=fail 时必填）
     */
    @Size(max = 64, message = "未通过原因编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String failureReasonCode;

    /**
     * 背调核查项清单
     */
    @Size(max = 500, message = "核查项长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String checkItems;

    /**
     * 背调敏感说明（明文入参，落库加密；禁止写日志与审计明细）
     */
    private String detailCipher;

    /**
     * 免背调授权原因（result=waived 时必填）
     */
    @Size(max = 500, message = "免背调原因长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String waiveReason;

    /**
     * 背调状态（{@code BackgroundStatusEnum} 的 code：draft/checking/finished/cancelled），空值按草稿处理
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
