package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 重复人才治理分页查询业务对象（SPEC-P4 §2.5 GET /talent/duplicates）。
 *
 * <p>所有条件均为可选；<b>不接受前端传入任何可见范围字段</b>——结果集恒由
 * {@code TalentScopeDomainService} 生成的可见人才范围收敛（§11.1、§21.14）。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentDuplicateQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理状态（pending/merged/not_same/ignored，字典 talent_duplicate_status）
     */
    private String status;

    /**
     * 匹配级别（strong/medium/weak）
     */
    private String matchLevel;

    /**
     * 主档A（待处理侧）人才ID
     */
    private Long sourceTalentId;

    /**
     * 主档B（比对侧）人才ID
     */
    private Long targetTalentId;

    /**
     * 人才姓名（同时匹配主档A与主档B的姓名，模糊）
     */
    private String name;

    /**
     * 人才编号（同时匹配主档A与主档B的编号，模糊）
     */
    private String talentNo;

    /**
     * 疑似记录创建日期起（含）
     */
    private LocalDate createDateBegin;

    /**
     * 疑似记录创建日期止（含）
     */
    private LocalDate createDateEnd;

}
