package org.dromara.aigov.mapper;

import org.dromara.aigov.domain.AigPolicyDecisionLog;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 策略决策账本 Mapper。
 *
 * <p><b>为什么用 {@code BaseMapperPlus<AigPolicyDecisionLog, AigPolicyDecisionLog>}（T == V）
 * 而不是像其它表那样配一个 VO</b>：本表是<b>只写账本</b>，当前没有任何按 VO 读取页面的需求。
 * 配一个 VO 就要额外维护 {@code @AutoMapper} 与两向转换器，而它一条数据都不会被用到——
 * 那是纯负担。{@code AigMapperVoConverterCoverageTest} 对 {@code T == V} 是<b>显式跳过</b>的
 * （不经过 MapStruct 转换），所以这个写法不会削弱那条守卫。</p>
 *
 * <p>将来若治理台要加"决策查询"页面，再引入 VO 即可，届时守卫测试会正常要求转换器生成。</p>
 *
 * @author ai-gov
 */
public interface AigPolicyDecisionLogMapper
    extends BaseMapperPlus<AigPolicyDecisionLog, AigPolicyDecisionLog> {
}
