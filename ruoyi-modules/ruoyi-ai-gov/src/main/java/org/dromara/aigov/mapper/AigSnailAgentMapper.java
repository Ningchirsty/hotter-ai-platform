package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.domain.vo.AigSnailAgentVo;

import java.util.List;

/**
 * snail-ai Agent 的<b>只读</b> Mapper（{@code sai_agent}）——「模型 ↔ Agent」映射的来源。
 *
 * <p><b>只读，且不提供任何写方法</b>：Agent 的创建/编辑属于 snail-ai。
 * 治理层需要这张表，只是因为「走 snail-ai 时实际跑哪个模型」记在它上面；
 * 让它可写会把「谁动了 sai_* 表」这件事说不清（与 {@link AigModelConfigMapper} 同一口径：
 * 每个跨模块 Mapper 的职责范围写在类注释里）。</p>
 *
 * <p><b>刻意不在 SQL 里过滤 {@code status}</b>：把该模型下的 Agent 全部取回来，
 * 由调用方分类，才能给出「有 2 个 Agent，但都被禁用了」这种可执行的报错；
 * 在 SQL 里过滤掉会让失败原因退化成「一个都没有」，运维只能靠猜。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigSnailAgentMapper {

    /**
     * 按关联的对话模型ID列出该模型下的全部 Agent（不过滤状态，按 id 升序）。
     *
     * @param chatModelId 关联的对话模型ID（{@code sai_model_config.id}）
     * @return Agent 列表；无匹配返回空列表
     */
    @Select("select id, name, chat_model_id as chatModelId, app_id as appId, status "
        + "from sai_agent where chat_model_id = #{chatModelId} order by id")
    List<AigSnailAgentVo> selectByChatModelId(@Param("chatModelId") Long chatModelId);

}
