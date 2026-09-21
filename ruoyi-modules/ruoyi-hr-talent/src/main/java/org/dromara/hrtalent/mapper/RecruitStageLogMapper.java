package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitStageLog;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStageLogVo;

/**
 * 应聘阶段历史 Mapper 接口（SPEC-P3 §2.2）。
 *
 * <p><b>只追加</b>：业务侧只允许 {@code insert} 与 {@code selectList}；
 * 任何阶段的变更都必须留下历史，禁止通过本 Mapper 覆盖或物理删除历史记录。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitStageLogMapper extends BaseMapperPlus<RecruitStageLog, RecruitStageLogVo> {

}
