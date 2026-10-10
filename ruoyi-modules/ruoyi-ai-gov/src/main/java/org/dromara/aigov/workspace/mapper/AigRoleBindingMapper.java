package org.dromara.aigov.workspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 岗位可见性绑定 Mapper。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由见 {@code AigScenarioMapper}。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigRoleBindingMapper extends BaseMapperPlus<AigRoleBinding, AigRoleBinding> {

}
