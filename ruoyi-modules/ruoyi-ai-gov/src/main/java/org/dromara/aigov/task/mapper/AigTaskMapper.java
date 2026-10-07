package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 统一任务 Mapper。
 *
 * <p>无独立 VO：任务的对外视图尚未定型（前台列表、排障视图、统计口径各不相同），
 * 先按实体直接读写。泛型第二参数给实体本身是本仓既有写法
 * （见 {@code DpGateItemMapper} 等），避免为了满足类型参数造一个空 VO。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskMapper extends BaseMapperPlus<AigTask, AigTask> {

}
