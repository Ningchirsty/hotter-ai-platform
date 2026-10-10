package org.dromara.aigov.workspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 场景包 Mapper。
 *
 * <p><b>为什么泛型第二参数是实体自己（T == V）</b>：目前没有任何接口需要一个"裁剪过的场景视图"，
 * 造一个没人用的 VO 只会多一份要跟着改的死代码。<b>当列表接口需要脱敏/裁剪字段时再加 VO</b>——
 * 那时本模块的守卫测试（{@code AigMapperVoConverterCoverageTest}）会要求 {@code @AutoMapper}
 * 与生成的转换器存在，不会漏。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigScenarioMapper extends BaseMapperPlus<AigScenario, AigScenario> {

}
