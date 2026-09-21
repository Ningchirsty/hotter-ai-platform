package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitPlanItemStatusLog;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemStatusLogVo;

/**
 * 月度计划任务状态变更日志 Mapper（设计文档 §7.1.5 / §21.15）。
 *
 * <p><b>追加型</b>：业务调用方只允许 {@code insert} 与只读查询，
 * 不得更新或删除日志行（{@code del_flag} 仅供系统一致性修复使用）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitPlanItemStatusLogMapper extends BaseMapperPlus<RecruitPlanItemStatusLog, RecruitPlanItemStatusLogVo> {

}
