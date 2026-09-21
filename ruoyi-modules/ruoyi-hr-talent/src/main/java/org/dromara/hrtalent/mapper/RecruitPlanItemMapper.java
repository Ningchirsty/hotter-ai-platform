package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemVo;

/**
 * 月度招聘计划任务 Mapper（SPEC-P2 §3.2）。
 *
 * <p><b>刻意没有</b>「公司 + 岗位 + 月份」唯一约束对应的任何查询捷径：
 * 新增任务一律独立插入，相似性只用于提示（设计文档 §9.5 / §7.1.1）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitPlanItemMapper extends BaseMapperPlus<RecruitPlanItem, RecruitPlanItemVo> {

}
