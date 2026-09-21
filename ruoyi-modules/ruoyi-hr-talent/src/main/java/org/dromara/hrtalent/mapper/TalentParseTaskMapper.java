package org.dromara.hrtalent.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentParseTask;
import org.dromara.hrtalent.domain.vo.talent.TalentParseTaskVo;

/**
 * 简历解析任务 Mapper（SPEC-P4 §2.1）。
 *
 * <p>授权约定：调用方先经人才主档服务完成资源级鉴权，再按 {@code task_id} 读取任务；
 * 本 Mapper 不实现授权规则（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
public interface TalentParseTaskMapper extends BaseMapperPlus<TalentParseTask, TalentParseTaskVo> {
}
