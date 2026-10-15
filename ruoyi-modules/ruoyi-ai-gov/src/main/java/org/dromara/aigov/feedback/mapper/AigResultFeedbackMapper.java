package org.dromara.aigov.feedback.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.feedback.domain.AigResultFeedback;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 结果反馈 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigResultFeedbackMapper
    extends BaseMapperPlus<AigResultFeedback, AigResultFeedback> {
}
