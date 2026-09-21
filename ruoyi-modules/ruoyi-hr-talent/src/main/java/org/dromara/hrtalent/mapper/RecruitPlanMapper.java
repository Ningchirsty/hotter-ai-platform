package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanVo;

/**
 * 公司月度招聘计划表头 Mapper（SPEC-P2 §3.2）。
 *
 * <p>表头按 {@code (company_dept_id, plan_month)} 唯一，因此按公司与月份加载表头时
 * 使用 {@code selectOne} 是安全的（数据库唯一索引兜底）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitPlanMapper extends BaseMapperPlus<RecruitPlan, RecruitPlanVo> {

}
