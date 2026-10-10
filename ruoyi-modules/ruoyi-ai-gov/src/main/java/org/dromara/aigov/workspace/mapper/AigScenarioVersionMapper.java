package org.dromara.aigov.workspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 场景包版本 Mapper。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由见 {@link AigScenarioMapper}。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigScenarioVersionMapper
    extends BaseMapperPlus<AigScenarioVersion, AigScenarioVersion> {

}
