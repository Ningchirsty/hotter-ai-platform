package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanRolloverVo;

/**
 * 月度招聘计划结转记录 Mapper（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>结转幂等依赖本表唯一索引 {@code (source_item_id, target_month)}：
 * 先占位插入、再创建目标任务，重复或并发执行都会命中唯一索引被安全跳过。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitPlanRolloverMapper extends BaseMapperPlus<RecruitPlanRollover, RecruitPlanRolloverVo> {

}
