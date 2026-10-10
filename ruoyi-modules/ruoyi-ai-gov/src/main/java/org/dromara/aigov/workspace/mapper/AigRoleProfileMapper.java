package org.dromara.aigov.workspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 岗位定义 Mapper。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由见 {@code AigScenarioMapper}。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigRoleProfileMapper extends BaseMapperPlus<AigRoleProfile, AigRoleProfile> {

}
