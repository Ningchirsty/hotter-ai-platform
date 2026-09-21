package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;

/**
 * 月度计划任务与应聘记录关联 Mapper（SPEC-P2 §0）。
 *
 * <p><b>P2 占位</b>：{@code hr_recruit_application} 实体在 P3 才存在，本阶段只提供
 * Mapper 以便 P3 直接接入，<b>不实现</b>任何与应聘记录的联动逻辑。
 * 候选人类出参也不使用本表，故未绑定 VO 泛型，直接复用实体自身。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitPlanApplicationRelMapper
    extends BaseMapperPlus<RecruitPlanApplicationRel, RecruitPlanApplicationRel> {

}
