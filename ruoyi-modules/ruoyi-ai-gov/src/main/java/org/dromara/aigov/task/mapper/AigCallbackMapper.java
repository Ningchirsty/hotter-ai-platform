package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigCallback;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 回调账本 Mapper（追加型）。
 *
 * <p>(provider_code, event_id) 唯一，是回调幂等的最后一道防线：
 * 即使服务层的「先查后插」在并发下判断失误，重复投递也会在插入时被数据库拒绝。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigCallbackMapper extends BaseMapperPlus<AigCallback, AigCallback> {

}
