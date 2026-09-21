package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 候选人列表查询业务对象（SPEC-P3 §2.1 {@code GET /recruit/candidates}）。
 *
 * <p>候选人 = 存在应聘记录的人才，因此查询条件分两组：人才主档字段与最近一次应聘字段。
 * 可见范围条件由 {@code TalentScopeDomainService} 统一生成，本对象<b>不</b>接受可见范围参数。</p>
 *
 * @author hr-talent
 */
@Data
public class CandidateQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才编号（模糊匹配）
     */
    private String talentNo;

    /**
     * 姓名（模糊匹配）
     */
    private String name;

    /**
     * 电话（服务端标准化后按哈希精确匹配）
     */
    private String phone;

    /**
     * 邮箱（服务端小写标准化后按哈希精确匹配）
     */
    private String email;

    /**
     * 人才生命周期状态（字典 talent_status 编码）
     */
    private String talentStatus;

    /**
     * 最近一次应聘的阶段编码（字典 recruit_candidate_stage）
     */
    private String stage;

    /**
     * 最近一次应聘的结果编码（字典 recruit_application_result）
     */
    private String status;

    /**
     * 应聘岗位执行项ID
     */
    private Long jobId;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 当前所在城市（模糊匹配）
     */
    private String currentCity;

    /**
     * 期望岗位（模糊匹配）
     */
    private String expectedPosition;

    /**
     * 当前公司（模糊匹配）
     */
    private String currentCompany;

    /**
     * 最高学历（字典编码，精确匹配）
     */
    private String highestEducation;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 数据分级（字典 recruit_data_level 编码）
     */
    private String dataLevel;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 期望薪资下限（含）
     */
    private java.math.BigDecimal expectedSalaryMin;

    /**
     * 期望薪资上限（含）
     */
    private java.math.BigDecimal expectedSalaryMax;

}
