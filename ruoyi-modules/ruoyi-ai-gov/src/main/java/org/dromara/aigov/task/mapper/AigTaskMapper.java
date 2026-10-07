package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 统一任务 Mapper。
 *
 * <p>泛型第二参数是列表视图 {@link AigTaskVo}，供 {@code selectVoPage/selectVoById} 使用；
 * 实体级方法（{@code selectById/insert/updateById}）不受它影响，仍返回/接收实体。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskMapper extends BaseMapperPlus<AigTask, AigTaskVo> {

}
