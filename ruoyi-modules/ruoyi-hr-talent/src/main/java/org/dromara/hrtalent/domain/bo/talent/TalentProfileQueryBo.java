package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才主档组合检索业务对象（SPEC-P3 §2.1 {@code GET /talent/profiles}）。
 *
 * <p>只承载检索条件，不承载写字段；可见范围条件由
 * {@code TalentScopeDomainService} 统一生成，本对象<b>不</b>接受任何可见范围参数。</p>
 *
 * <p><b>安全约束</b>：{@link #phone} 只用于服务端计算标准化哈希后做<b>精确</b>匹配，
 * 绝不参与模糊匹配，也不会被回显。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentProfileQueryBo implements Serializable {

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
     * 电话（服务端标准化后按哈希精确匹配，不做模糊匹配）
     */
    private String phone;

    /**
     * 邮箱（服务端小写标准化后按哈希精确匹配）
     */
    private String email;

    /**
     * 人才生命周期状态（字典 talent_status 编码，精确匹配）
     */
    private String talentStatus;

    /**
     * 当前所在城市（模糊匹配）
     */
    private String currentCity;

    /**
     * 期望工作城市（模糊匹配）
     */
    private String expectedCity;

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
     * 所属行业（模糊匹配）
     */
    private String industry;

    /**
     * 人才归属（负责人）用户ID
     */
    private Long ownerId;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 可见范围（字典 talent_visibility_type 编码，精确匹配）
     */
    private String visibilityType;

    /**
     * 数据分级（字典 recruit_data_level 编码，精确匹配）
     */
    private String dataLevel;

    /**
     * 主档来源（manual/resume_import/application 等稳定编码）
     */
    private String sourceType;

    /**
     * 协助人用户ID（命中 assistant_ids 逗号串中的任一元素）
     */
    private Long assistantId;

    /**
     * 工作年限下限（含）
     */
    private Integer workYearsBegin;

    /**
     * 工作年限上限（含）
     */
    private Integer workYearsEnd;

    /**
     * 创建时间起（含，{@code yyyy-MM-dd}）
     */
    private LocalDate createDateBegin;

    /**
     * 创建时间止（含，{@code yyyy-MM-dd}）
     */
    private LocalDate createDateEnd;

    /**
     * 是否包含已归档人才（默认 false，归档人才不参与日常检索）
     */
    private Boolean includeArchived;

    /**
     * 是否只查未被任何应聘记录引用的人才
     */
    private Boolean onlyWithoutApplication;

}
