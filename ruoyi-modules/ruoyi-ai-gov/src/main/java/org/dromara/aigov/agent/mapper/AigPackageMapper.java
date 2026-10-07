package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.vo.AigPackageVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Package 主定义 Mapper。
 *
 * <p>泛型第二参数是列表视图 {@link AigPackageVo}，供 {@code selectVoPage/selectVoById} 使用；
 * 实体级方法（{@code selectById/insert/updateById}）不受它影响，仍返回/接收实体。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigPackageMapper extends BaseMapperPlus<AigPackage, AigPackageVo> {
}
