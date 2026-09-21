package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人才疑似重复案件对象 hr_talent_duplicate_case（设计文档 §8.18、§9.2）。
 *
 * <p><b>业务定位</b>：查重分级本身由 {@code TalentDuplicateDomainService} 计算（不落库），
 * 本表只承载「已落库的疑似记录 + 处置流转」：待处理 → 已确认（待合并）/ 已忽略 / 已合并。</p>
 *
 * <p><b>强/中/弱口径</b>：{@code match_level} 取 {@code strong} / {@code medium} / {@code weak}
 * （见 {@code DuplicateMatchLevelEnum}）；强匹配必须阻止静默新增，弱匹配仅提示。</p>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code case_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_duplicate_case")
public class TalentDuplicateCase extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 重复案件ID（主键）
     */
    @TableId(value = "case_id")
    private Long caseId;

    /**
     * 主档A（待处理侧，疑似新增的一方）人才ID
     */
    private Long sourceTalentId;

    /**
     * 主档B（比对侧，系统中已有的一方）人才ID
     */
    private Long targetTalentId;

    /**
     * 匹配级别（strong/medium/weak，见 DuplicateMatchLevelEnum）
     */
    private String matchLevel;

    /**
     * 匹配原因（命中规则说明，如「电话命中」「姓名命中 + 当前公司命中」）
     */
    private String matchReason;

    /**
     * 匹配得分（0~100，仅用于排序展示，不作为合并依据）
     */
    private BigDecimal matchScore;

    /**
     * 处理状态（pending/merged/not_same/ignored，见 DuplicateStatusEnum，字典 talent_duplicate_status）
     */
    private String status;

    /**
     * 处理人用户ID
     */
    private Long handledBy;

    /**
     * 处理时间
     */
    private LocalDateTime handledTime;

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
