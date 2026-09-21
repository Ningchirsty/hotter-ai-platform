package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 人才合并关系转移 Mapper（SPEC-P4 §2.5、设计文档 §8.18、§21.7）。
 *
 * <p><b>为什么需要本 Mapper</b>：合并要在单个事务内把「被合并主档」的十余张关系表改归属到
 * 「保留主档」。这些表分属其他交付线（简历、经历、人才池、标签、跟进、分组、授权），
 * <b>其专属 Mapper 由对应交付线持有</b>；本 Mapper 只提供「按 talent_id 批量改归属」的最小 SQL，
 * 避免为改一行归属而在他人 Mapper 里塞合并专用方法，也避免新建同表 Mapper。</p>
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li><b>只有 UPDATE 与 COUNT</b>：本 Mapper 不存在任何 {@code DELETE}，
 *     合并<b>绝不物理删除</b>任何资料（§8.18、§21.7）；</li>
 *     <li><b>逻辑删除</b>：自定义 SQL <b>不会</b>经过 MyBatis-Plus 的 {@code @TableLogic} 拦截，
 *     因此每条语句都<b>显式</b>带 {@code del_flag = '0'}，避免把已逻辑删除的历史关系一并改归属；</li>
 *     <li><b>审计字段</b>：自定义 SQL 同样<b>不会</b>触发 {@code InjectionMetaObjectHandler} 的
 *     {@code update_by}/{@code update_time} 自动填充，因此每条改归属语句都<b>显式</b>写入
 *     {@code update_by = #{operatorId}} 与 {@code update_time = NOW(3)}；
 *     操作人同时进入合并快照与敏感审计，保证「谁在何时把这条关系改到保留主档」可追溯
 *     （与 {@code TalentGroupMemberMapper}、{@code TalentProfileTagMapper} 的既有自定义 SQL 写法一致）；</li>
 *     <li><b>唯一键冲突处理（跳过语义）</b>：{@code hr_talent_pool_member} / {@code hr_talent_profile_tag} /
 *     {@code hr_talent_group_member} 的 {@code (业务键, talent_id)} 唯一约束可能因两边同时在
 *     同一人才池/分组而冲突，统一用 {@code NOT EXISTS} <b>跳过冲突行</b>
 *     （保留主档已有关系优先，<b>不做物理删除、不做覆盖归并</b>）；
 *     被跳过行数 = 迁移前数量 − 实际迁移行数，在合并快照
 *     {@code relation_count_json} 的 {@code before}/{@code moved} 差值中如实体现，便于日后对账；</li>
 *     <li>简历/经历的自引用外键（{@code resume_id} / {@code work_id}）必须<b>与改归属在同一条语句内
 *     一起回填</b>：子查询只按业务键（{@code version_no} / {@code company_name + start_date}）匹配
 *     保留主档侧记录，且子行与目标记录分属不同主档，不构成自引用。
 *     <b>回填失败的边界</b>：匹配不到保留侧对应记录时<b>保留原值</b>（fail-safe：不置空、不删除），
 *     此时该引用可能仍指向「已随本次迁移归属到保留侧」的旧版本/旧经历记录 ——
 *     因记录本身未被删除、引用不悬空，属可接受边界（不丢数据优先）。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentMergeRelationMapper {

    /* ------------------------------------------------------------------ 关系数量统计（合并快照用） ------------------------------------------------------------------ */

    /**
     * 统计某主档持有的应聘记录数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_recruit_application WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countApplications(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的简历版本数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_resume WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countResumes(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的教育经历数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_education WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countEducations(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的工作经历数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_work WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countWorks(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的项目经历数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_project WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countProjects(@Param("talentId") Long talentId);

    /**
     * 统计某主档名下应聘记录关联的业务附件数（附件按 {@code biz_type + biz_id} 归属应聘记录）。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("""
        SELECT COUNT(*) FROM hr_recruit_attachment a
         WHERE a.del_flag = '0'
           AND a.biz_type = 'application'
           AND EXISTS (SELECT 1 FROM hr_recruit_application p
                        WHERE p.application_id = a.biz_id
                          AND p.talent_id = #{talentId}
                          AND p.del_flag = '0')
        """)
    long countAttachments(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的「人才级」附件数（{@code biz_type = 'talent'}，{@code biz_id} 为人才ID）。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_recruit_attachment"
        + " WHERE biz_type = 'talent' AND biz_id = #{talentId} AND del_flag = '0'")
    long countTalentAttachments(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的人才池成员关系数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_pool_member WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countPoolMembers(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的标签关系数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_profile_tag WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countProfileTags(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的跟进记录数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_follow_up WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countFollowUps(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的分组成员关系数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_group_member WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countGroupMembers(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的共享授权数（含已撤销授权，保证快照完整）。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_scope_grant WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countScopeGrants(@Param("talentId") Long talentId);

    /**
     * 统计某主档持有的关键字段变更历史数。
     *
     * @param talentId 人才主档ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM hr_talent_profile_change WHERE talent_id = #{talentId} AND del_flag = '0'")
    long countProfileChanges(@Param("talentId") Long talentId);

    /* ------------------------------------------------------------------ 关系改归属 ------------------------------------------------------------------ */

    /**
     * 转移应聘记录（改归属；同一人才允许多次应聘，无唯一键冲突）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @return 影响行数
     */
    /**
     * 转移应聘记录（改归属；同一人才允许多次应聘，无唯一键冲突）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_recruit_application SET talent_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE talent_id = #{mergedTalentId} AND del_flag = '0'")
    int moveApplications(@Param("keepTalentId") Long keepTalentId,
                         @Param("mergedTalentId") Long mergedTalentId,
                         @Param("operatorId") Long operatorId);

    /**
     * 转移简历版本（改归属）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_resume SET talent_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE talent_id = #{mergedTalentId} AND del_flag = '0'")
    int moveResumes(@Param("keepTalentId") Long keepTalentId,
                    @Param("mergedTalentId") Long mergedTalentId,
                    @Param("operatorId") Long operatorId);

    /**
     * 转移教育经历（改归属），同时把 {@code resume_id} 回填为保留主档同版本号的简历。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("""
        UPDATE hr_talent_education e
           SET e.talent_id = #{keepTalentId},
               e.resume_id = (SELECT r2.resume_id FROM hr_talent_resume r2
                               WHERE r2.talent_id = #{keepTalentId}
                                 AND r2.del_flag = '0'
                                 AND r2.version_no = (SELECT r.version_no FROM hr_talent_resume r
                                                       WHERE r.resume_id = e.resume_id)),
               e.update_by = #{operatorId},
               e.update_time = NOW(3)
         WHERE e.talent_id = #{mergedTalentId} AND e.del_flag = '0'
        """)
    int moveEducations(@Param("keepTalentId") Long keepTalentId,
                       @Param("mergedTalentId") Long mergedTalentId,
                       @Param("operatorId") Long operatorId);

    /**
     * 转移工作经历（改归属），同时把 {@code resume_id} 回填为保留主档同版本号的简历。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("""
        UPDATE hr_talent_work w
           SET w.talent_id = #{keepTalentId},
               w.resume_id = (SELECT r2.resume_id FROM hr_talent_resume r2
                               WHERE r2.talent_id = #{keepTalentId}
                                 AND r2.del_flag = '0'
                                 AND r2.version_no = (SELECT r.version_no FROM hr_talent_resume r
                                                       WHERE r.resume_id = w.resume_id)),
               w.update_by = #{operatorId},
               w.update_time = NOW(3)
         WHERE w.talent_id = #{mergedTalentId} AND w.del_flag = '0'
        """)
    int moveWorks(@Param("keepTalentId") Long keepTalentId,
                  @Param("mergedTalentId") Long mergedTalentId,
                  @Param("operatorId") Long operatorId);

    /**
     * 转移项目经历（改归属），同时回填 {@code resume_id} 与 {@code work_id}。
     *
     * <p>{@code work_id} 按「公司名 + 入职日期」匹配保留主档侧的工作经历；
     * 找不到对应记录时保持原值（不置空、不删除）。</p>
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("""
        UPDATE hr_talent_project p
           SET p.talent_id = #{keepTalentId},
               p.resume_id = (SELECT r2.resume_id FROM hr_talent_resume r2
                               WHERE r2.talent_id = #{keepTalentId}
                                 AND r2.del_flag = '0'
                                 AND r2.version_no = (SELECT r.version_no FROM hr_talent_resume r
                                                       WHERE r.resume_id = p.resume_id)),
               p.work_id = (SELECT w2.work_id FROM hr_talent_work w2
                             WHERE w2.talent_id = #{keepTalentId}
                               AND w2.del_flag = '0'
                               AND w2.company_name = (SELECT w.company_name FROM hr_talent_work w
                                                       WHERE w.work_id = p.work_id)
                               AND w2.start_date = (SELECT w.start_date FROM hr_talent_work w
                                                     WHERE w.work_id = p.work_id)
                             LIMIT 1),
               p.update_by = #{operatorId},
               p.update_time = NOW(3)
         WHERE p.talent_id = #{mergedTalentId} AND p.del_flag = '0'
        """)
    int moveProjects(@Param("keepTalentId") Long keepTalentId,
                     @Param("mergedTalentId") Long mergedTalentId,
                     @Param("operatorId") Long operatorId);

    /**
     * 转移「人才级」附件（{@code biz_type = 'talent'}）的归属。
     *
     * <p>挂在应聘记录上的附件会随应聘记录改归属自动跟随（见 {@link #moveApplications}），
     * 本方法只处理直接把人才主档当业务对象的附件。</p>
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_recruit_attachment SET biz_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE biz_type = 'talent' AND biz_id = #{mergedTalentId} AND del_flag = '0'")
    int moveTalentAttachments(@Param("keepTalentId") Long keepTalentId,
                              @Param("mergedTalentId") Long mergedTalentId,
                              @Param("operatorId") Long operatorId);

    /**
     * 转移人才池成员关系（改归属）；同一人才池已有保留主档成员关系时跳过冲突行。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_pool_member m SET m.talent_id = #{keepTalentId},"
        + " m.update_by = #{operatorId}, m.update_time = NOW(3)"
        + " WHERE m.talent_id = #{mergedTalentId} AND m.del_flag = '0'"
        + " AND NOT EXISTS (SELECT 1 FROM (SELECT pool_id FROM hr_talent_pool_member"
        + " WHERE talent_id = #{keepTalentId} AND del_flag = '0') k WHERE k.pool_id = m.pool_id)")
    int movePoolMembers(@Param("keepTalentId") Long keepTalentId,
                        @Param("mergedTalentId") Long mergedTalentId,
                        @Param("operatorId") Long operatorId);

    /**
     * 转移标签关系（改归属）；同一标签已有保留主档关系时跳过冲突行。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_profile_tag t SET t.talent_id = #{keepTalentId},"
        + " t.update_by = #{operatorId}, t.update_time = NOW(3)"
        + " WHERE t.talent_id = #{mergedTalentId} AND t.del_flag = '0'"
        + " AND NOT EXISTS (SELECT 1 FROM (SELECT tag_id FROM hr_talent_profile_tag"
        + " WHERE talent_id = #{keepTalentId} AND del_flag = '0') k WHERE k.tag_id = t.tag_id)")
    int moveProfileTags(@Param("keepTalentId") Long keepTalentId,
                        @Param("mergedTalentId") Long mergedTalentId,
                        @Param("operatorId") Long operatorId);

    /**
     * 转移跟进记录（改归属）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_follow_up SET talent_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE talent_id = #{mergedTalentId} AND del_flag = '0'")
    int moveFollowUps(@Param("keepTalentId") Long keepTalentId,
                      @Param("mergedTalentId") Long mergedTalentId,
                      @Param("operatorId") Long operatorId);

    /**
     * 转移分组成员关系（改归属）；同一分组已有保留主档成员关系时跳过冲突行。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_group_member m SET m.talent_id = #{keepTalentId},"
        + " m.update_by = #{operatorId}, m.update_time = NOW(3)"
        + " WHERE m.talent_id = #{mergedTalentId} AND m.del_flag = '0'"
        + " AND NOT EXISTS (SELECT 1 FROM (SELECT group_id FROM hr_talent_group_member"
        + " WHERE talent_id = #{keepTalentId} AND del_flag = '0') k WHERE k.group_id = m.group_id)")
    int moveGroupMembers(@Param("keepTalentId") Long keepTalentId,
                         @Param("mergedTalentId") Long mergedTalentId,
                         @Param("operatorId") Long operatorId);

    /**
     * 转移共享授权（改归属）；授权有效期与级别原样保留（§8.19：共享只扩大查看范围）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_scope_grant SET talent_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE talent_id = #{mergedTalentId} AND del_flag = '0'")
    int moveScopeGrants(@Param("keepTalentId") Long keepTalentId,
                        @Param("mergedTalentId") Long mergedTalentId,
                        @Param("operatorId") Long operatorId);

    /**
     * 转移关键字段变更历史（改归属，保证合并后仍可追溯被合并主档的历史）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID（显式写入 update_by）
     * @return 影响行数
     */
    @Update("UPDATE hr_talent_profile_change SET talent_id = #{keepTalentId},"
        + " update_by = #{operatorId}, update_time = NOW(3)"
        + " WHERE talent_id = #{mergedTalentId} AND del_flag = '0'")
    int moveProfileChanges(@Param("keepTalentId") Long keepTalentId,
                           @Param("mergedTalentId") Long mergedTalentId,
                           @Param("operatorId") Long operatorId);

}
