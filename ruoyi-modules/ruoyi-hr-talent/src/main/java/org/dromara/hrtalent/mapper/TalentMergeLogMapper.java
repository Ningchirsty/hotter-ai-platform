package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentMergeLog;
import org.dromara.hrtalent.domain.vo.talent.TalentMergeLogVo;

/**
 * 人才合并日志 Mapper（SPEC-P4 §2.5）。
 *
 * <p>合并快照是「不可撤销凭证」，本 Mapper <b>只允许插入与按条件查询</b>：
 * 合并日志不提供更新与删除入口（逻辑删除同样不用于本表，避免快照被抹除）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentMergeLogMapper extends BaseMapperPlus<TalentMergeLog, TalentMergeLogVo> {
}
