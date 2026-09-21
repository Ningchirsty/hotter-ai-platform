package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpBo;
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentFollowUpVo;

/**
 * 人才跟进 服务接口（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p><b>访问前置</b>：所有方法在执行前都必须经
 * {@code TalentScopeDomainService} 校验该人才对当前用户可见，
 * 可见范围规则不在本接口实现内另写。</p>
 *
 * <p><b>与招聘阶段历史分开</b>（设计文档 §8.16）：跟进记录只写
 * {@code hr_talent_follow_up}，<b>不</b>写 {@code hr_recruit_stage_log}，
 * 也不触发应聘阶段流转。</p>
 *
 * @author hr-talent
 */
public interface ITalentFollowUpService {

    /**
     * 分页查询某位人才的跟进记录。
     *
     * @param talentId  人才主档ID
     * @param bo        检索条件，可为 null
     * @param pageQuery 分页参数
     * @return 跟进记录分页结果
     */
    PageResult<TalentFollowUpVo> queryPage(Long talentId, TalentFollowUpQueryBo bo, PageQuery pageQuery);

    /**
     * 查询单条跟进记录详情。
     *
     * @param followId 跟进记录ID
     * @return 跟进记录
     */
    TalentFollowUpVo getDetail(Long followId);

    /**
     * 新增跟进记录。
     *
     * @param talentId 人才主档ID（路径参数，权威值）
     * @param bo       跟进入参
     * @return 新增的跟进记录ID
     */
    Long create(Long talentId, TalentFollowUpBo bo);

    /**
     * 更新跟进记录。
     *
     * @param bo 跟进入参（必须含 followId）
     */
    void update(TalentFollowUpBo bo);

    /**
     * 逻辑删除跟进记录。
     *
     * @param followIds 跟进记录ID集合
     */
    void remove(Long[] followIds);

    /**
     * 校验沟通摘要内容：只允许与招聘相关的沟通要点，禁止与招聘无关的高度敏感个人信息。
     *
     * <p>阈值与关键词表由实现类维护；本方法供服务内部与单测直接调用，校验失败抛中文提示。</p>
     *
     * @param summary 沟通摘要，可为空
     */
    void validateSummary(String summary);

}
