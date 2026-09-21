package org.dromara.hrtalent.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentProfileChange;
import org.dromara.hrtalent.domain.vo.talent.CandidateVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;

import java.util.List;

/**
 * 人才主档 Mapper 接口（SPEC-P3 §2.1）。
 *
 * <p><b>可见范围</b>：候选人视图与变更历史两处自定义 SQL 都<b>必须</b>接收
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService#visibleTalentWrapper()}
 * 生成的条件包装器，本 Mapper <b>不</b>自行实现任何授权规则（设计文档 §11.1）。</p>
 *
 * <p><b>避坑</b>：{@code hr_talent_profile} 的实体属性名与表列名不同
 * （如 {@code delFlag} → {@code del_flag}），因此连接条件的列名一律使用
 * {@code hr_talent_profile.del_flag}，而包装器内引用别名 {@code p} 的列。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentProfileMapper extends BaseMapperPlus<TalentProfile, TalentProfileVo> {

    /**
     * 查询当前用户可见的人才ID列表（设计文档 §21.14）。
     *
     * <p><b>这是「套可见范围」的唯一接法</b>：{@code wrapper} 必须是
     * {@code TalentScopeDomainService#visibleTalentWrapper()} 原样返回的对象——
     * {@code ${ew.customSqlSegment}} 自带 {@code WHERE} 且参数绑定作用在同一个 wrapper 上；
     * <b>禁止</b>把别的 wrapper 的 {@code getCustomSqlSegment()} 搬到本 wrapper 上（会前导
     * {@code WHERE} 重复 + 参数错位），也<b>禁止</b>在 Mapper/Service 里另写一套授权规则（§11.1）。</p>
     *
     * @param wrapper 可见范围条件包装器（由 TalentScopeDomainService 生成）
     * @return 可见的人才主档ID列表
     */
    @Select("SELECT p.talent_id FROM hr_talent_profile p ${ew.customSqlSegment}")
    List<Long> selectVisibleTalentIds(@Param(Constants.WRAPPER) Wrapper<TalentProfile> wrapper);

    /**
     * 候选人列表：存在应聘记录的人才视图（一人一档，不另建候选人主表）。
     *
     * <p>只返回最近一次应聘（按 {@code apply_time}、{@code application_id} 倒序取第一条）的聚合字段，
     * 电话/邮箱只返回脱敏列（由服务层解密后脱敏填充）。</p>
     *
     * @param page      分页参数
     * @param wrapper   可见范围与检索条件包装器（由 TalentScopeDomainService 生成）
     * @param stage     最近一次应聘的阶段编码，可为空
     * @param status    最近一次应聘的结果编码，可为空
     * @param jobId     最近一次应聘的岗位ID，可为空
     * @param recruiterId 最近一次应聘的招聘负责人用户ID，可为空
     * @return 候选人分页结果
     */
    @Select("""
        <script>
        SELECT p.talent_id             AS talentId,
               p.talent_no             AS talentNo,
               p.name                  AS name,
               p.former_name           AS formerName,
               p.gender                AS gender,
               p.highest_education     AS highestEducation,
               p.phone_cipher          AS phoneCipher,
               p.backup_phone_cipher   AS backupPhoneCipher,
               p.email_cipher          AS emailCipher,
               p.current_city          AS currentCity,
               p.expected_city         AS expectedCity,
               p.current_company       AS currentCompany,
               p.current_position      AS currentPosition,
               p.expected_position     AS expectedPosition,
               p.work_years            AS workYears,
               p.industry              AS industry,
               p.owner_id              AS ownerId,
               p.owner_dept_id         AS ownerDeptId,
               p.owner_dept_name       AS ownerDeptName,
               p.assistant_ids         AS assistantIds,
               p.talent_status         AS talentStatus,
               p.status_reason         AS statusReason,
               p.status_expire_date    AS statusExpireDate,
               p.visibility_type       AS visibilityType,
               p.data_level            AS dataLevel,
               p.source_type           AS sourceType,
               p.source_channel_id     AS sourceChannelId,
               p.resume_update_time    AS resumeUpdateTime,
               p.version               AS version,
               p.create_time           AS createTime,
               p.update_time           AS updateTime,
               agg.application_count      AS applicationCount,
               a.application_id           AS latestApplicationId,
               a.application_no           AS latestApplicationNo,
               a.job_id                   AS latestJobId,
               a.current_stage            AS latestStage,
               a.current_status           AS latestStatus,
               a.recruiter_id             AS latestRecruiterId,
               a.contact_date             AS latestContactDate,
               a.apply_time               AS latestApplyTime,
               a.stage_enter_time         AS latestStageEnterTime,
               a.plan_arrival_date        AS latestPlanArrivalDate,
               a.arrival_date             AS latestArrivalDate,
               a.expected_salary_min      AS latestExpectedSalaryMin,
               a.expected_salary_max      AS latestExpectedSalaryMax,
               a.version                  AS latestVersion
          FROM hr_talent_profile p
          JOIN (SELECT a.talent_id, COUNT(*) AS application_count
                  FROM hr_recruit_application a
                 WHERE a.del_flag = '0'
                   <if test="stage != null and stage != ''">
                   AND a.current_stage = #{stage}
                   </if>
                   <if test="status != null and status != ''">
                   AND a.current_status = #{status}
                   </if>
                   <if test="jobId != null">
                   AND a.job_id = #{jobId}
                   </if>
                   <if test="recruiterId != null">
                   AND a.recruiter_id = #{recruiterId}
                   </if>
                 GROUP BY a.talent_id) agg ON agg.talent_id = p.talent_id
          LEFT JOIN hr_recruit_application a
                 ON a.application_id = (SELECT a2.application_id
                                          FROM hr_recruit_application a2
                                         WHERE a2.talent_id = p.talent_id
                                           AND a2.del_flag = '0'
                                           <if test="stage != null and stage != ''">
                                           AND a2.current_stage = #{stage}
                                           </if>
                                           <if test="status != null and status != ''">
                                           AND a2.current_status = #{status}
                                           </if>
                                           <if test="jobId != null">
                                           AND a2.job_id = #{jobId}
                                           </if>
                                           <if test="recruiterId != null">
                                           AND a2.recruiter_id = #{recruiterId}
                                           </if>
                                         ORDER BY a2.apply_time DESC, a2.application_id DESC
                                         LIMIT 1)
         ${ew.customSqlSegment}
        </script>
        """)
    IPage<CandidateVo> selectCandidatePage(IPage<CandidateVo> page,
                                           @Param(Constants.WRAPPER) Wrapper<TalentProfile> wrapper,
                                           @Param("stage") String stage,
                                           @Param("status") String status,
                                           @Param("jobId") Long jobId,
                                           @Param("recruiterId") Long recruiterId);

    /**
     * 人才重复预检：按强/中/弱三组条件一次性捞取候选命中项（设计文档 §21.15）。
     *
     * <p>只返回可展示摘要字段，不返回密文与哈希；级别判定在服务层用 Java 完成，
     * 避免在 SQL 里堆叠复杂的 OR 分支导致索引失效。</p>
     *
     * @param params 规范化后的预检参数
     * @return 命中的人才摘要列表（上限 50 条）
     */
    @Select("""
        <script>
        SELECT p.talent_id         AS talentId,
               p.talent_no         AS talentNo,
               p.name              AS name,
               p.phone_cipher      AS phoneCipher,
               p.email_cipher      AS emailCipher,
               p.current_company   AS currentCompany,
               p.current_city      AS currentCity,
               p.expected_position AS expectedPosition,
               p.talent_status     AS talentStatus,
               p.owner_id          AS ownerId,
               p.owner_dept_id     AS ownerDeptId,
               p.phone_hash        AS phoneHash,
               p.email_hash        AS emailHash,
               p.create_time       AS createTime
          FROM hr_talent_profile p
         WHERE p.del_flag = '0'
           AND p.talent_status &lt;&gt; 'merged'
           AND (
             <trim prefixOverrides="OR ">
             <if test="params.phoneHash != null and params.phoneHash != ''">
               OR p.phone_hash = #{params.phoneHash}
             </if>
             <if test="params.emailHash != null and params.emailHash != ''">
               OR p.email_hash = #{params.emailHash}
             </if>
             <if test="params.name != null and params.name != '' and params.currentCompany != null and params.currentCompany != ''">
               OR (p.name = #{params.name} AND p.current_company = #{params.currentCompany})
             </if>
             <if test="params.name != null and params.name != '' and params.schoolName != null and params.schoolName != ''">
               OR (p.name = #{params.name} AND EXISTS (
                     SELECT 1 FROM hr_talent_education e
                      WHERE e.talent_id = p.talent_id AND e.del_flag = '0' AND e.school_name = #{params.schoolName}))
             </if>
             <if test="params.name != null and params.name != '' and params.resumeHash != null and params.resumeHash != ''">
               OR (p.name = #{params.name} AND EXISTS (
                     SELECT 1 FROM hr_talent_resume r
                      WHERE r.talent_id = p.talent_id AND r.del_flag = '0' AND r.file_hash = #{params.resumeHash}))
             </if>
             <if test="params.name != null and params.name != '' and params.expectedPosition != null and params.expectedPosition != ''">
               OR (p.name = #{params.name} AND LOWER(TRIM(p.expected_position)) = LOWER(TRIM(#{params.expectedPosition})))
             </if>
             </trim>
           )
         ORDER BY p.create_time DESC, p.talent_id DESC
         LIMIT 50
        </script>
        """)
    List<PrecheckRow> selectPrecheckRows(@Param("params") DuplicatePrecheckParams params);

    /**
     * 重复预检原始命中行（只含摘要与哈希，供服务层分级使用，不直接返回给前端）。
     *
     * <p>使用传统 JavaBean 而非 record：MyBatis 默认走无参构造 + setter 映射，
     * 避免不同版本对 record 构造器映射的差异带来运行期风险。</p>
     *
     * @author hr-talent
     */
    @lombok.Data
    class PrecheckRow implements java.io.Serializable {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        /**
         * 人才主档ID
         */
        private Long talentId;

        /**
         * 人才编号
         */
        private String talentNo;

        /**
         * 姓名
         */
        private String name;

        /**
         * 电话密文（出参拦截器自动解密）
         */
        private String phoneCipher;

        /**
         * 邮箱密文（出参拦截器自动解密）
         */
        private String emailCipher;

        /**
         * 当前公司
         */
        private String currentCompany;

        /**
         * 当前所在城市
         */
        private String currentCity;

        /**
         * 期望岗位
         */
        private String expectedPosition;

        /**
         * 人才状态编码
         */
        private String talentStatus;

        /**
         * 负责人用户ID
         */
        private Long ownerId;

        /**
         * 归属部门ID
         */
        private Long ownerDeptId;

        /**
         * 电话哈希（仅用于服务层分级比对）
         */
        private String phoneHash;

        /**
         * 邮箱哈希（仅用于服务层分级比对）
         */
        private String emailHash;

        /**
         * 创建时间（用于人工判断新旧）
         */
        private java.time.LocalDateTime createTime;

    }

    /**
     * 人才关键字段变更历史（按人才ID分页）。
     *
     * <p>可见范围由调用方先经 {@link #selectVisibleTalentIds} 解析为人才ID集合后以
     * {@code IN} 条件传入，避免在自定义 SQL 里重复实现授权规则。</p>
     *
     * @param page    分页参数
     * @param wrapper 变更历史与人才ID条件包装器（仅含 talentId 等业务条件）
     * @return 变更历史分页结果
     */
    @Select("""
        SELECT c.change_id    AS changeId,
               c.talent_id    AS talentId,
               c.change_type  AS changeType,
               c.before_json  AS beforeJson,
               c.after_json   AS afterJson,
               c.operator_id  AS operatorId,
               c.operate_time AS operateTime,
               c.remark       AS remark
          FROM hr_talent_profile_change c
         WHERE c.del_flag = '0'
           ${ew.customSqlSegment}
         ORDER BY c.operate_time DESC, c.change_id DESC
        """)
    IPage<TalentProfileChangeVo> selectChangePage(IPage<TalentProfileChangeVo> page,
                                                  @Param(Constants.WRAPPER) Wrapper<TalentProfileChange> wrapper);

}
