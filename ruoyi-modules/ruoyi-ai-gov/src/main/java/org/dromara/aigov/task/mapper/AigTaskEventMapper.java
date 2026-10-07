package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTaskEvent;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 任务事件 Mapper（追加型）。
 *
 * <p>只 insert 与按任务/时间查询。(task_id, sequence) 唯一——并发写入若重号，
 * 数据库直接拒绝，而不是留下两条顺序不明的事件。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskEventMapper extends BaseMapperPlus<AigTaskEvent, AigTaskEvent> {

}
