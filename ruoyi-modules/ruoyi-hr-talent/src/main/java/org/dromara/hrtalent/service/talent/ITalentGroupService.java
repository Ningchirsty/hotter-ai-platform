package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentGroupBo;
import org.dromara.hrtalent.domain.bo.talent.TalentGroupQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupVo;

/**
 * 人才分组服务接口（SPEC-P4 §2.3 C 线，设计文档 §8.15）。
 *
 * <p><b>两类分组</b>：</p>
 * <ul>
 *     <li>{@code public} 公共分组：团队共同整理，由人才池管理员维护；</li>
 *     <li>{@code personal} 个人收藏：用户个人快速访问，
 *     <b>不改变人才数据权限</b>——加入个人收藏不会让任何人获得该人才的查看权，
 *     分组内容同样要叠加人才可见范围。</li>
 * </ul>
 *
 * <p><b>授权约束</b>：公共分组的可见范围统一经
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 判定；
 * 个人收藏只对本人可见可维护。实现方不得自行拼装授权规则（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
public interface ITalentGroupService {

    /**
     * 分页查询当前用户可见的分组（公共分组 + 自己的个人收藏）。
     *
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数
     * @return 分组分页结果
     */
    PageResult<TalentGroupVo> queryPage(TalentGroupQueryBo bo, PageQuery pageQuery);

    /**
     * 分组详情。
     *
     * @param groupId 分组ID
     * @return 分组详情
     */
    TalentGroupVo getDetail(Long groupId);

    /**
     * 新增分组。
     *
     * @param bo 分组入参
     * @return 新增的分组ID
     */
    Long create(TalentGroupBo bo);

    /**
     * 更新分组。
     *
     * @param bo 分组入参
     */
    void update(TalentGroupBo bo);

    /**
     * 逻辑删除分组（只删除分组本身与成员关系，<b>不删除人才主档</b>）。
     *
     * @param groupId 分组ID
     */
    void remove(Long groupId);

    /**
     * 分页查询分组成员（叠加人才可见范围）。
     *
     * @param groupId   分组ID
     * @param pageQuery 分页参数
     * @return 成员分页结果
     */
    PageResult<TalentGroupMemberVo> queryMembers(Long groupId, PageQuery pageQuery);

    /**
     * 加入分组。
     *
     * <p><b>幂等</b>：{@code (group_id, talent_id)} 已存在时返回已有关系（必要时恢复已移出的关系），
     * 不抛异常。</p>
     *
     * @param groupId  分组ID
     * @param talentId 人才主档ID
     * @return 成员关系（新建或已存在）
     */
    TalentGroupMemberVo addMember(Long groupId, Long talentId);

    /**
     * 移出分组成员（只结束关系，不删除人才主档）。
     *
     * @param groupId  分组ID
     * @param memberId 成员关系ID
     */
    void removeMember(Long groupId, Long memberId);

}
