package org.dromara.aigov.workspace.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.portal.domain.AigUserBrand;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 用户↔品牌归属 Mapper（④）。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由同 {@code AigUserWorkspacePrefMapper}：
 * 本处只需要数据访问，不为 VO 好看而多加一层转换器。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigUserBrandMapper extends BaseMapperPlus<AigUserBrand, AigUserBrand> {

}
