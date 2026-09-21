package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.DuplicateConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.DuplicateIgnoreBo;
import org.dromara.hrtalent.domain.bo.talent.TalentDuplicateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeBo;
import org.dromara.hrtalent.domain.vo.talent.TalentDuplicateCaseVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergeLogVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergePreviewVo;

/**
 * 重复人才治理与合并服务（SPEC-P4 §2.5、设计文档 §8.18）。
 *
 * <p><b>本服务是人才模块风险最高的写入口</b>：{@link #merge} 会在单个数据库事务内
 * 把被合并主档的全部关系改归属到保留主档。设计约束：</p>
 * <ul>
 *     <li>只有集团人才管理员（或超级管理员）可以执行合并（§8.18、§7.6.1），
 *     判定必须走 {@code TalentScopeDomainService}，不得自写角色判断；</li>
 *     <li>合并必须校验两个主档的乐观锁版本、必须持分布式锁、必须在单事务内完成（§9.6、§11.1、§21.7）；</li>
 *     <li>合并<b>不物理删除</b>任何资料，必须写完整合并快照与审计记录（§8.18）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface ITalentDuplicateService {

    /**
     * 分页查询疑似重复案件（自动叠加人才可见范围）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 疑似重复案件分页结果
     */
    PageResult<TalentDuplicateCaseVo> queryPage(TalentDuplicateQueryBo bo, PageQuery pageQuery);

    /**
     * 合并预览：返回两侧主档摘要、冲突字段差异与关系数量（不落库、不改数据）。
     *
     * @param caseId 疑似重复案件ID
     * @return 合并预览结果
     */
    TalentMergePreviewVo preview(Long caseId);

    /**
     * 确认疑似重复案件（人工判断是否为同一人）。
     *
     * <p><b>状态口径</b>：{@code samePerson = true} → {@code DuplicateStatusEnum.CONFIRMED}
     * （已确认待合并，<b>不再</b>计入 {@code status = pending} 的待处理工作队列）；
     * {@code samePerson = false} → {@code NOT_SAME}（终结）。本方法<b>不改动任何人才资料</b>，
     * 只流转案件状态并记录确认人、确认时间与确认依据。</p>
     *
     * @param caseId 疑似重复案件ID
     * @param bo     确认入参
     */
    void confirm(Long caseId, DuplicateConfirmBo bo);

    /**
     * 忽略疑似重复案件（判定非同一人或暂不处理）。
     *
     * @param caseId 疑似重复案件ID
     * @param bo     忽略入参
     */
    void ignore(Long caseId, DuplicateIgnoreBo bo);

    /**
     * 事务合并两名人才（本模块最高风险写操作）。
     *
     * <p>执行顺序：权限校验（集团人才管理员）→ 版本校验 → 分布式锁（由 Controller 层
     * {@code @Lock4j} 保证）→ 同一事务内转移全部关系 → 应用冲突字段决策 →
     * 被合并主档标记 {@code merged} 并写 {@code merged_to_id} → 写合并快照与审计 →
     * 发布 {@code TalentMergedEvent}。</p>
     *
     * @param caseId 疑似重复案件ID，可为 null（表示直接按两个主档ID合并）
     * @param bo     合并入参（含两侧 ID 与乐观锁版本、冲突字段决策、合并原因）
     * @return 合并日志ID（合并快照主键）
     */
    Long merge(Long caseId, TalentMergeBo bo);

    /**
     * 分页查询合并日志（合并后不可普通撤销，只能查看快照）。
     *
     * @param keepTalentId   保留主档ID，可为空
     * @param mergedTalentId 被合并主档ID，可为空
     * @param pageQuery      分页参数
     * @return 合并日志分页结果
     */
    PageResult<TalentMergeLogVo> queryMergeLogs(Long keepTalentId, Long mergedTalentId, PageQuery pageQuery);

}
