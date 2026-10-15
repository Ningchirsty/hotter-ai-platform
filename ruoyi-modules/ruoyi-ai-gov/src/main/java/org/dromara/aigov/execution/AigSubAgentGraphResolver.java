package org.dromara.aigov.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 子 Agent 图解析实现（设计 §6.5）：从版本配置 {@code config_json} 读取
 * {@code subAgentRefs[].agentVersionId}。
 *
 * <p>只认 v2 配置（与执行计划同一口径）：旧格式没有子 Agent 声明，返回空列表。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigSubAgentGraphResolver implements ISubAgentGraphResolver {

    private final AigAgentVersionMapper agentVersionMapper;

    private final JsonMapper jsonMapper;

    @Override
    public List<Long> childrenOf(Long agentVersionId) {
        if (agentVersionId == null) {
            return List.of();
        }
        AigAgentVersion version = agentVersionMapper.selectById(agentVersionId);
        if (version == null) {
            return List.of();
        }
        AigExecutionConfig config = AigExecutionConfig.parse(version.getConfigJson(), jsonMapper);
        return config == null ? List.of() : config.getSubAgentVersionIds();
    }

}
