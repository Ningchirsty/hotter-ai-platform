package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 草稿修订 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioRevisionMapper extends BaseMapperPlus<AigStudioRevision, AigStudioRevisionVo> {

}
