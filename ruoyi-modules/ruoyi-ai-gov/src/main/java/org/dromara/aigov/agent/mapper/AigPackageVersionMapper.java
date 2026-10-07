package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.vo.AigPackageVersionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Package 版本 Mapper。
 *
 * <p>发布状态的推进不走本 Mapper 的 {@code updateById}（那会整行覆盖），
 * 而是用「按当前状态做条件更新」，见服务层说明。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigPackageVersionMapper extends BaseMapperPlus<AigPackageVersion, AigPackageVersionVo> {
}
