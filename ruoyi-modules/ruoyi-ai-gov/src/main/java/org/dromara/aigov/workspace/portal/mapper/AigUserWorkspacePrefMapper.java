package org.dromara.aigov.workspace.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.portal.domain.AigUserWorkspacePref;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 工作台偏好 Mapper。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由见 {@code AigScenarioMapper}：
 * 本增量只需要数据访问，不为 VO 好看而多加一层转换器。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigUserWorkspacePrefMapper extends BaseMapperPlus<AigUserWorkspacePref, AigUserWorkspacePref> {

}
