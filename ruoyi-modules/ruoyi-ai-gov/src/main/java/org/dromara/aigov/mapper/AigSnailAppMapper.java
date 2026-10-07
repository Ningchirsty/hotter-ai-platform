package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.domain.vo.AigSnailAppVo;

/**
 * snail-ai 客户端应用（{@code sai_app}）的<b>只读</b> Mapper——配置核对用。
 *
 * <p><b>只读，不提供任何写方法</b>：应用与凭据的创建/轮换属于 snail-ai。
 * 治理层读它，只是为了把「配置里的 app-id 与 token 到底对不对」从人肉比对变成一次查询。</p>
 *
 * <p><b>token 的原值不进入 Java</b>：是否一致在 SQL 里算成布尔位返回
 * （与 {@code AigModelViewMapper}「是否已配置密钥」同一口径）。这样「谁读过凭据原值」
 * 这件事不会因为一次体检而多出一条路径。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigSnailAppMapper {

    /**
     * 按应用标识查一行，并在库内比较 token。
     *
     * <p>{@code app_id} 上有唯一键（{@code uk_app_id}），因此最多一行。</p>
     *
     * @param appId 应用标识（配置里的 {@code snail-ai.app-id}）
     * @param token 配置里的 {@code snail-ai.token}；为空时比对结果恒为 0
     * @return 行；不存在返回 null
     */
    @Select("select id, app_id as appId, app_name as appName, status, "
        + "case when token = #{token} then 1 else 0 end as tokenMatched "
        + "from sai_app where app_id = #{appId}")
    AigSnailAppVo selectByAppId(@Param("appId") String appId, @Param("token") String token);

}
