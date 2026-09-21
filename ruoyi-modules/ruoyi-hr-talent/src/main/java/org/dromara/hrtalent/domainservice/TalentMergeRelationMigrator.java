package org.dromara.hrtalent.domainservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hrtalent.mapper.TalentMergeRelationMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人才合并关系迁移领域服务实现（SPEC-P4 §2.5、设计文档 §8.18、§21.7）。
 *
 * <p><b>本类只做两件事</b>：统计关系数量、按 {@code talent_id} 批量改归属。
 * 所有 SQL 集中在 {@link TalentMergeRelationMapper}，本类不含任何删除逻辑
 * （设计文档 §8.18「合并操作不直接物理删除任何资料」）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentMergeRelationMigrator implements ITalentMergeRelationMigrator {

    /**
     * 关系表名：应聘记录。
     */
    public static final String TABLE_APPLICATION = "hr_recruit_application";

    /**
     * 关系表名：简历版本。
     */
    public static final String TABLE_RESUME = "hr_talent_resume";

    /**
     * 关系表名：教育经历。
     */
    public static final String TABLE_EDUCATION = "hr_talent_education";

    /**
     * 关系表名：工作经历。
     */
    public static final String TABLE_WORK = "hr_talent_work";

    /**
     * 关系表名：项目经历。
     */
    public static final String TABLE_PROJECT = "hr_talent_project";

    /**
     * 关系表名：业务附件（按应聘记录归属的计数）。
     */
    public static final String TABLE_ATTACHMENT = "hr_recruit_attachment";

    /**
     * 关系表名：人才级附件（{@code biz_type = 'talent'} 的子集统计，
     * 与按应聘记录归属的 {@link #TABLE_ATTACHMENT} 分开计数，避免数量重复计算）。
     */
    public static final String TABLE_TALENT_ATTACHMENT = "hr_recruit_attachment_talent";

    /**
     * 关系表名：人才池成员。
     */
    public static final String TABLE_POOL_MEMBER = "hr_talent_pool_member";

    /**
     * 关系表名：标签关系。
     */
    public static final String TABLE_PROFILE_TAG = "hr_talent_profile_tag";

    /**
     * 关系表名：跟进记录。
     */
    public static final String TABLE_FOLLOW_UP = "hr_talent_follow_up";

    /**
     * 关系表名：分组成员。
     */
    public static final String TABLE_GROUP_MEMBER = "hr_talent_group_member";

    /**
     * 关系表名：共享授权。
     */
    public static final String TABLE_SCOPE_GRANT = "hr_talent_scope_grant";

    /**
     * 关系表名：关键字段变更历史。
     */
    public static final String TABLE_PROFILE_CHANGE = "hr_talent_profile_change";

    /**
     * 合并关系迁移 Mapper。
     */
    private final TalentMergeRelationMapper talentMergeRelationMapper;

    @Override
    public TalentMergeRelationSnapshot snapshot(Long talentId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (talentId == null) {
            return new TalentMergeRelationSnapshot(TalentMergeRelationSnapshot.Kind.BEFORE, counts);
        }
        counts.put(TABLE_APPLICATION, talentMergeRelationMapper.countApplications(talentId));
        counts.put(TABLE_RESUME, talentMergeRelationMapper.countResumes(talentId));
        counts.put(TABLE_EDUCATION, talentMergeRelationMapper.countEducations(talentId));
        counts.put(TABLE_WORK, talentMergeRelationMapper.countWorks(talentId));
        counts.put(TABLE_PROJECT, talentMergeRelationMapper.countProjects(talentId));
        counts.put(TABLE_ATTACHMENT, talentMergeRelationMapper.countAttachments(talentId));
        counts.put(TABLE_TALENT_ATTACHMENT, talentMergeRelationMapper.countTalentAttachments(talentId));
        counts.put(TABLE_POOL_MEMBER, talentMergeRelationMapper.countPoolMembers(talentId));
        counts.put(TABLE_PROFILE_TAG, talentMergeRelationMapper.countProfileTags(talentId));
        counts.put(TABLE_FOLLOW_UP, talentMergeRelationMapper.countFollowUps(talentId));
        counts.put(TABLE_GROUP_MEMBER, talentMergeRelationMapper.countGroupMembers(talentId));
        counts.put(TABLE_SCOPE_GRANT, talentMergeRelationMapper.countScopeGrants(talentId));
        counts.put(TABLE_PROFILE_CHANGE, talentMergeRelationMapper.countProfileChanges(talentId));
        return new TalentMergeRelationSnapshot(TalentMergeRelationSnapshot.Kind.BEFORE, counts);
    }

    @Override
    public Map<String, Long> migrate(Long keepTalentId, Long mergedTalentId, Long operatorId) {
        if (keepTalentId == null || mergedTalentId == null || keepTalentId.equals(mergedTalentId)) {
            throw new IllegalArgumentException("合并关系迁移的主档ID非法");
        }
        Map<String, Long> moved = new LinkedHashMap<>();
        // 1. 先移简历版本，再移引用简历的经历（经历上的 resume_id 需要按版本号回填到保留主档）
        moved.put(TABLE_RESUME, (long) talentMergeRelationMapper.moveResumes(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_EDUCATION,
            (long) talentMergeRelationMapper.moveEducations(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_WORK,
            (long) talentMergeRelationMapper.moveWorks(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_PROJECT,
            (long) talentMergeRelationMapper.moveProjects(keepTalentId, mergedTalentId, operatorId));
        // 2. 应聘记录（其附件随之跟随）
        moved.put(TABLE_APPLICATION,
            (long) talentMergeRelationMapper.moveApplications(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_TALENT_ATTACHMENT,
            (long) talentMergeRelationMapper.moveTalentAttachments(keepTalentId, mergedTalentId, operatorId));
        // 3. 人才池成员、标签、分组（存在唯一键冲突时跳过冲突行，保留主档已有关系优先）
        moved.put(TABLE_POOL_MEMBER,
            (long) talentMergeRelationMapper.movePoolMembers(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_PROFILE_TAG,
            (long) talentMergeRelationMapper.moveProfileTags(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_GROUP_MEMBER,
            (long) talentMergeRelationMapper.moveGroupMembers(keepTalentId, mergedTalentId, operatorId));
        // 4. 跟进记录、共享授权、关键字段变更历史
        moved.put(TABLE_FOLLOW_UP,
            (long) talentMergeRelationMapper.moveFollowUps(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_SCOPE_GRANT,
            (long) talentMergeRelationMapper.moveScopeGrants(keepTalentId, mergedTalentId, operatorId));
        moved.put(TABLE_PROFILE_CHANGE,
            (long) talentMergeRelationMapper.moveProfileChanges(keepTalentId, mergedTalentId, operatorId));
        log.info("人才合并关系迁移完成, keepTalentId={}, mergedTalentId={}, operatorId={}, moved={}",
            keepTalentId, mergedTalentId, operatorId, moved);
        return moved;
    }

}
