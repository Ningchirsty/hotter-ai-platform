package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.domain.vo.AigReleaseEventVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 版本发布事件 Mapper（追加型账本：只 insert 与查询）。
 *
 * @author ai-gov
 */
@Mapper
public interface AigReleaseEventMapper extends BaseMapperPlus<AigReleaseEvent, AigReleaseEventVo> {
}
