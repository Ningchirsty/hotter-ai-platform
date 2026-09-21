package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolMemberBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolVo;

/**
 * 人才池服务接口（SPEC-P4 §2.3 C 线，设计文档 §8.15）。
 *
 * <p><b>授权硬约束</b>：人才池的可见性与操作范围<b>必须</b>经
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 判定，
 * 实现方不得自行拼装授权规则（设计文档 §11.1）。</p>
 *
 * <p><b>业务硬约束</b>：</p>
 * <ul>
 *     <li>重复加入同一人才<b>返回已有关系</b>，不报错（幂等，设计文档 §8.15）；</li>
 *     <li>移出人才池只结束成员关系，<b>绝不删除人才主档</b>与任何简历、经历、标签数据；</li>
 *     <li>成员列表必须叠加人才可见范围，不可见的人才不出现在结果中。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface ITalentPoolService {

    /**
     * 分页查询当前用户可见的人才池。
     *
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数，可为空（为空时按默认分页）
     * @return 人才池分页结果
     */
    PageResult<TalentPoolVo> queryPage(TalentPoolQueryBo bo, PageQuery pageQuery);

    /**
     * 人才池详情。
     *
     * @param poolId 人才池ID
     * @return 人才池详情
     */
    TalentPoolVo getDetail(Long poolId);

    /**
     * 新增人才池（编码由服务端生成，创建者即默认池管理员）。
     *
     * @param bo 人才池入参
     * @return 新增的人才池ID
     */
    Long create(TalentPoolBo bo);

    /**
     * 更新人才池（仅池管理员、集团级人才管理员或超级管理员可操作）。
     *
     * @param bo 人才池入参
     */
    void update(TalentPoolBo bo);

    /**
     * 逻辑删除人才池（仅池管理员、集团级人才管理员或超级管理员可操作）。
     *
     * <p>删除池只结束运营范围：池成员关系被标记为已移出，
     * <b>不删除任何人才主档</b>。</p>
     *
     * @param poolIds 人才池ID数组
     */
    void remove(Long[] poolIds);

    /**
     * 分页查询池成员（叠加人才可见范围）。
     *
     * @param poolId      人才池ID
     * @param memberStatus 成员状态过滤，可为空
     * @param pageQuery   分页参数
     * @return 成员分页结果
     */
    PageResult<TalentPoolMemberVo> queryMembers(Long poolId, String memberStatus, PageQuery pageQuery);

    /**
     * 加入人才池。
     *
     * <p><b>幂等</b>：若该人才已在池中，直接返回已有成员关系（必要时把已移出的关系恢复为在池），
     * 不抛异常、不产生第二条记录。</p>
     *
     * @param poolId 人才池ID
     * @param bo     成员入参
     * @return 成员关系（新建或已存在）
     */
    TalentPoolMemberVo addMember(Long poolId, TalentPoolMemberBo bo);

    /**
     * 移出人才池成员。
     *
     * <p>只把成员状态置为「已移出」并记录移出时间与原因，
     * <b>不删除</b>人才主档与成员关系行（设计文档 §8.15）。</p>
     *
     * @param poolId  人才池ID
     * @param memberId 成员关系ID
     * @param reason  移出原因，可为空
     */
    void removeMember(Long poolId, Long memberId, String reason);

}
