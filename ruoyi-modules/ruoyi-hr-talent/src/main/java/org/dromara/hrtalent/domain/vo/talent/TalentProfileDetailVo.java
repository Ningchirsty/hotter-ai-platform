package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人才主档详情视图对象 hr_talent_profile（SPEC-P3 §2.1 {@code GET /talent/profiles/{id}}）。
 *
 * <p>在列表 {@link TalentProfileVo} 的基础上补齐详情字段（出生日期、年龄快照、期望薪资、
 * 归属与合并信息等）。</p>
 *
 * <p><b>安全约束</b>：</p>
 * <ul>
 *     <li>仍然<b>不返回</b>电话/邮箱密文列，只返回脱敏串；</li>
 *     <li>电话明文只能经 {@code POST /recruit/candidates/{id}/phone-view} 获取并写审计；</li>
 *     <li>本对象不做 MapStruct 自动映射，由服务层显式逐字段装配，避免密文字段被顺手带出。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TalentProfileDetailVo extends TalentProfileVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 曾用名（或英文名）
     */
    private String formerName;

    /**
     * 出生日期（优先保存出生日期）
     */
    private LocalDate birthDate;

    /**
     * 年龄快照（仅导入原值，不反推出生日期）
     */
    private Integer ageSnapshot;

    /**
     * 期望薪资下限
     */
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资上限
     */
    private BigDecimal expectedSalaryMax;

    /**
     * 当前简历版本ID
     */
    private Long currentResumeId;

    /**
     * 是否已存在应聘记录（仅无应聘记录的人才可删除）
     */
    private Boolean hasApplication;

    /**
     * 被合并到的目标主档ID（非空表示已合并）
     */
    private Long mergedToId;

    /**
     * 最近跟进时间
     */
    private LocalDateTime lastFollowTime;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 备注
     */
    private String remark;

}
