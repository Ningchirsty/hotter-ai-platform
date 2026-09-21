package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.enums.DuplicateMatchLevelEnum;
import org.dromara.hrtalent.enums.DuplicateStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 疑似重复案件视图对象 hr_talent_duplicate_case（SPEC-P4 §2.5 GET /talent/duplicates）。
 *
 * <p><b>只返回可展示摘要</b>：不返回电话/邮箱明文与任何哈希；两侧均为脱敏或摘要字段。
 * 字典标签与用户昵称一律用 {@code @Translation} 回填（全仓惯例）。</p>
 *
 * <p><b>可见范围</b>：列表查询恒叠加 {@code TalentScopeDomainService} 解析出的可见人才范围，
 * 只有「主档A 或 主档B 至少一方可见」的案件才会返回。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentDuplicateCaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 重复案件ID
     */
    private Long caseId;

    /**
     * 主档A（待处理侧）人才ID
     */
    private Long sourceTalentId;

    /**
     * 主档B（比对侧）人才ID
     */
    private Long targetTalentId;

    /**
     * 匹配级别（strong/medium/weak）
     */
    private String matchLevel;

    /**
     * 匹配级别中文标签（由 {@link DuplicateMatchLevelEnum} 兜底转换，不依赖字典数据）
     */
    private String matchLevelLabel;

    /**
     * 匹配原因（命中规则说明）
     */
    private String matchReason;

    /**
     * 匹配得分
     */
    private BigDecimal matchScore;

    /**
     * 处理状态（pending/merged/not_same/ignored）
     */
    private String status;

    /**
     * 处理状态中文标签（由 {@link DuplicateStatusEnum} 兜底转换）
     */
    private String statusLabel;

    /**
     * 处理人用户ID
     */
    private Long handledBy;

    /**
     * 处理人昵称（由 {@link #handledBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "handledBy")
    private String handledByName;

    /**
     * 处理时间
     */
    private LocalDateTime handledTime;

    /**
     * 主档A 姓名
     */
    private String sourceTalentName;

    /**
     * 主档A 人才编号
     */
    private String sourceTalentNo;

    /**
     * 主档A 当前公司
     */
    private String sourceCurrentCompany;

    /**
     * 主档A 期望岗位
     */
    private String sourceExpectedPosition;

    /**
     * 主档A 归属部门名称快照
     */
    private String sourceOwnerDeptName;

    /**
     * 主档B 姓名
     */
    private String targetTalentName;

    /**
     * 主档B 人才编号
     */
    private String targetTalentNo;

    /**
     * 主档B 当前公司
     */
    private String targetCurrentCompany;

    /**
     * 主档B 期望岗位
     */
    private String targetExpectedPosition;

    /**
     * 主档B 归属部门名称快照
     */
    private String targetOwnerDeptName;

    /**
     * 确认/忽略/合并原因与备注（取案件 remark）
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
     * 由枚举兜底填充匹配级别中文标签（字典数据缺失时页面仍有中文可展示）。
     */
    public void fillEnumLabels() {
        DuplicateMatchLevelEnum level = DuplicateMatchLevelEnum.find(matchLevel);
        this.matchLevelLabel = level == null ? null : level.getDesc();
        DuplicateStatusEnum statusEnum = DuplicateStatusEnum.find(status);
        this.statusLabel = statusEnum == null ? null : statusEnum.getDesc();
    }

}
