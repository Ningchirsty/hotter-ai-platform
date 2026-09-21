package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人才主档组合检索业务对象（SPEC-P3 §2.1 {@code GET /talent/profiles}；P4 §2.6 追加 §8.17 条件）。
 *
 * <p>只承载检索条件，不承载写字段；可见范围条件由
 * {@code TalentScopeDomainService} 统一生成，本对象<b>不</b>接受任何可见范围参数。</p>
 *
 * <p><b>安全约束</b>：{@link #phone} 只用于服务端计算标准化哈希后做<b>精确</b>匹配，
 * 绝不参与模糊匹配，也不会被回显；{@link #phoneTail4} 基于 {@code phone_tail4} 精确匹配，
 * 不解密全量比对。</p>
 *
 * <p><b>P4 增量说明</b>：§8.17 的增强条件以<b>纯追加</b>方式并入本对象（不改动、不重命名既有字段），
 * 既有调用方与前端契约保持向后兼容。</p>
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

    /* ------------------------------------------------------------------ P4 §8.17 检索增强（纯新增，向后兼容） ------------------------------------------------------------------ */

    /**
     * 手机号后四位（精确匹配 {@code phone_tail4}）。
     *
     * <p>基于 {@code hr_talent_profile.phone_tail4} 的明文后四位精确匹配，
     * <b>不解密全量比对</b>（设计文档 §8.17）。后四位是与列表脱敏展示同口径的部分信息，
     * 不使用「后四位哈希」——{@code 10^4} 种取值可被瞬间穷举，哈希提供不了保密性。</p>
     */
    private String phoneTail4;

    /**
     * 当前岗位（模糊匹配 {@code current_position}）
     */
    private String currentPosition;

    /**
     * 历史岗位（模糊匹配 {@code hr_talent_work.position_name}，命中任一工作经历即可）
     */
    private String historyPosition;

    /**
     * 人才标签ID集合（命中任一标签即可，走 {@code hr_talent_profile_tag}）
     */
    private List<Long> tagIds;

    /**
     * 专业（模糊匹配 {@code hr_talent_education.major}，命中任一教育经历即可）
     */
    private String major;

    /**
     * 毕业院校（模糊匹配 {@code hr_talent_education.school_name}，命中任一教育经历即可）
     */
    private String schoolName;

    /**
     * 来源渠道ID（精确匹配 {@code source_channel_id}）
     */
    private Long sourceChannelId;

    /**
     * 人才池ID（人才需为该池的有效成员，走 {@code hr_talent_pool_member}）
     */
    private Long poolId;

    /**
     * 最近联系时间起（含，匹配 {@code last_follow_time}）
     */
    private LocalDateTime lastFollowTimeBegin;

    /**
     * 最近联系时间止（含，匹配 {@code last_follow_time}）
     */
    private LocalDateTime lastFollowTimeEnd;

    /**
     * 是否存在当前简历（true=存在；false=不存在；null=不过滤）
     */
    private Boolean hasCurrentResume;

    /**
     * 当前简历解析状态（字典 talent_resume_parse_status 编码，精确匹配当前版本简历）
     */
    private String resumeParseStatus;

    /**
     * 资料完整度下限（<b>暂不支持</b>，传入非空值将被服务层拒绝）。
     *
     * <p>完整度是「由简历 / 教育 / 工作 / 标签等多表派生」的值（设计文档 §8.17），
     * 物化成列需要触发器或定时重算，极易与真实数据不一致；因此本期只在人才列表 / 详情
     * <b>计算并返回</b>完整度用于展示，<b>不作为筛选条件</b>。相关限制见
     * {@code docs/hr-talent/P2-决策与缺口台账.md}。</p>
     */
    private Integer minCompleteness;

}
