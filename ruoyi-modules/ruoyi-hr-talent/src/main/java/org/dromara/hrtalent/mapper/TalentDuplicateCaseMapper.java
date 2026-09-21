package org.dromara.hrtalent.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentDuplicateCase;
import org.dromara.hrtalent.domain.vo.talent.TalentDuplicateCaseVo;

/**
 * 人才疑似重复案件 Mapper（SPEC-P4 §2.5）。
 *
 * <p><b>可见范围</b>：自定义 SQL {@link #selectCasePage} <b>必须</b>接收
 * {@code TalentScopeDomainService#visibleTalentWrapper()} 解析出的人才ID集合
 * （以 {@code EXISTS (... IN ...)} 形式内联），本 Mapper <b>不</b>自行实现任何授权规则（§11.1）。</p>
 *
 * <p><b>不新建同表 Mapper</b>：疑似案件表只有本 Mapper 一个写入口。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentDuplicateCaseMapper extends BaseMapperPlus<TalentDuplicateCase, TalentDuplicateCaseVo> {

    /**
     * 疑似重复案件分页查询（联表带出两侧人才的可展示摘要）。
     *
     * <p>业务条件由 {@code wrapper} 以 {@code ${ew.customSqlSegment}} 传入（禁止搬运别的 wrapper
     * 片段）；可见范围以 {@code visibleTalentIds} 集合内联，空集合返回空页。</p>
     *
     * @param page              分页参数
     * @param wrapper           业务条件包装器（状态、级别、姓名等）
     * @param visibleTalentIds  当前用户可见的人才主档ID集合（由 TalentScopeDomainService 解析）
     * @return 疑似重复案件分页结果
     */
    @Select("""
        <script>
        SELECT c.case_id               AS caseId,
               c.source_talent_id      AS sourceTalentId,
               c.target_talent_id      AS targetTalentId,
               c.match_level           AS matchLevel,
               c.match_reason          AS matchReason,
               c.match_score           AS matchScore,
               c.status                AS status,
               c.handled_by            AS handledBy,
               c.handled_time          AS handledTime,
               c.remark                AS remark,
               c.create_time           AS createTime,
               c.update_time           AS updateTime,
               s.name                  AS sourceTalentName,
               s.talent_no             AS sourceTalentNo,
               s.current_company       AS sourceCurrentCompany,
               s.expected_position     AS sourceExpectedPosition,
               s.owner_dept_name       AS sourceOwnerDeptName,
               t.name                  AS targetTalentName,
               t.talent_no             AS targetTalentNo,
               t.current_company       AS targetCurrentCompany,
               t.expected_position     AS targetExpectedPosition,
               t.owner_dept_name       AS targetOwnerDeptName
          FROM hr_talent_duplicate_case c
          LEFT JOIN hr_talent_profile s ON s.talent_id = c.source_talent_id
          LEFT JOIN hr_talent_profile t ON t.talent_id = c.target_talent_id
         WHERE c.del_flag = '0'
           AND (
             c.source_talent_id IN
             <foreach collection="visibleTalentIds" item="tid" open="(" separator="," close=")">#{tid}</foreach>
             OR c.target_talent_id IN
             <foreach collection="visibleTalentIds" item="tid" open="(" separator="," close=")">#{tid}</foreach>
           )
         ${ew.customSqlSegment}
         ORDER BY c.create_time DESC, c.case_id DESC
        </script>
        """)
    IPage<TalentDuplicateCaseVo> selectCasePage(IPage<TalentDuplicateCaseVo> page,
                                                @Param(Constants.WRAPPER) Wrapper<TalentDuplicateCase> wrapper,
                                                @Param("visibleTalentIds") java.util.Collection<Long> visibleTalentIds);

    /**
     * 把「保留主档 + 被合并主档」之间处于<b>未终结</b>状态的疑似重复案件批量置为已合并。
     *
     * <p><b>为什么用自定义 SQL 而不是 {@code LambdaUpdateWrapper}</b>：本语句的 OR 组合条件
     * 在注解 SQL 里语义更直观，且与合并其余批量改归属语句保持同一种实现风格；
     * 自定义 SQL 不触发 MyBatis-Plus 审计自动填充，故显式写入 {@code update_by}/{@code update_time}。</p>
     *
     * <p><b>覆盖状态</b>：{@code pending}（待人工判定）与 {@code confirmed}（已确认待合并）都是
     * 「未终结」状态，合并完成后都必须落到 {@code merged}，否则已确认案件会永久停留在 confirmed 无法闭环。
     * 已忽略（{@code ignored}）与已判定非同一人（{@code not_same}）的历史案件<b>不触碰</b>。</p>
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID
     * @return 影响行数
     */
    @Update("""
        UPDATE hr_talent_duplicate_case
           SET status = 'merged',
               handled_by = #{operatorId},
               handled_time = NOW(3),
               remark = CONCAT('merged:', #{mergedTalentId}, '->', #{keepTalentId}),
               update_by = #{operatorId},
               update_time = NOW(3)
         WHERE del_flag = '0'
           AND status IN ('pending', 'confirmed')
           AND ((source_talent_id = #{mergedTalentId} AND target_talent_id = #{keepTalentId})
             OR (source_talent_id = #{keepTalentId} AND target_talent_id = #{mergedTalentId}))
        """)
    int closePendingCases(@Param("keepTalentId") Long keepTalentId,
                          @Param("mergedTalentId") Long mergedTalentId,
                          @Param("operatorId") Long operatorId);

}
