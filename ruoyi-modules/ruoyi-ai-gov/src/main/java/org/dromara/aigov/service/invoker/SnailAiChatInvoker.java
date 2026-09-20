package org.dromara.aigov.service.invoker;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatSyncResponse;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigGovProperties;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * snail-ai 调用器（集团共享 / 外部部署走 snail-ai server）。
 *
 * <p><b>字段来源已实测确认</b>（{@code snail-ai-commons-core-1.1.1.jar} 的 javap 结果）：</p>
 * <pre>
 * OpenApiChatRequest : agentId / openId / conversationId / content / attachments /
 *                      disabledMcpServerIds / disabledSkillIds / deepPlanEnabled /
 *                      webSearchEnabled / sid / timeout
 * OpenApiChatSyncResponse : conversationId / content / durationMs
 * OpenApiChatClient  : chatSync(OpenApiChatRequest) → Result&lt;OpenApiChatSyncResponse&gt;
 * Result             : status==1 表示成功（ok 置 1，fail 置 0）
 * </pre>
 *
 * <p><b>架构错配（阶段1 已知限制，非缺陷）</b>：snail-ai OpenAPI 聊天入口收的是
 * <b>Agent</b>（{@code agentId}），不是 {@code model_key}。也就是说走 snail-ai 时
 * <b>实际执行的模型由 Agent 决定</b>，治理层无法用它精确指定某个 {@code sai_model_config} 模型。
 * 路由引擎仍按 {@code sai_model_config} 选模型（用于「能不能用/等级够不够」的治理判定），
 * 但真正执行时以 Agent 绑定的模型为准；模型级精确路由需要后续建立「模型 ↔ Agent」映射，
 * 属于阶段2。</p>
 *
 * <p><b>tokensUsed / cost 恒为 null</b>：{@code OpenApiChatSyncResponse} 不返回 token 用量
 * 与费用，这里不编造数字，审计中这两列写 null。</p>
 *
 * <p><b>条件加载说明</b>：本类用 {@code @ConditionalOnClass(OpenApiChatClient.class)}
 * 而非 {@code @ConditionalOnBean}。原因是 {@code @ConditionalOnBean} 在被组件扫描的
 * {@code @Component} 上于「扫描期」求值，此时 snail-ai 的自动配置（{@code SnailAiConfig}，
 * 属 deferred 导入）尚未注册 {@code OpenApiChatClient} Bean，条件恒为 false，
 * 会导致本调用器永远不加载（正是 SPEC §9 担心的「snail-ai 关闭时不可用 / 开启时也不可用」）。
 * 因此这里以「类存在」为准，再在 {@link #available()} 中做运行期 Bean 与配置可用性判定。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(OpenApiChatClient.class)
public class SnailAiChatInvoker implements ModelInvoker {

    /**
     * snail-ai {@code Result.status} 的成功值（ok 置 1，fail 置 0）。
     */
    private static final int SUCCESS_STATUS = 1;

    /**
     * snail-ai OpenAPI 客户端（snail-ai.enabled=false 时不存在，故用 ObjectProvider 软依赖）。
     */
    private final ObjectProvider<OpenApiChatClient> chatClientProvider;

    /**
     * 治理层配置（snail-ai Agent / openId / 超时）。
     */
    private final AigGovProperties properties;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        // 外部 / 集团共享走 snail-ai；本地私有由 LocalRuleModelInvoker 承担
        return deploymentType != null && deploymentType != AigDeploymentTypeEnum.LOCAL;
    }

    @Override
    public boolean available() {
        return chatClientProvider.getIfAvailable() != null
            && properties.isEnabled()
            && properties.getAgentId() != null
            && properties.getAgentId() > 0;
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        if (request == null) {
            return ModelInvokeResult.failure("调用请求为空", 0L);
        }
        OpenApiChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return ModelInvokeResult.failure("snail-ai 调用参数未确认或客户端未加载（OpenApiChatClient 不存在）", 0L);
        }
        if (!properties.isEnabled()) {
            return ModelInvokeResult.failure("snail-ai 调用未启用（aigov.snail-ai.enabled=false）", 0L);
        }
        Long agentId = properties.getAgentId();
        if (agentId == null || agentId <= 0) {
            return ModelInvokeResult.failure("snail-ai Agent 未配置（aigov.snail-ai.agent-id）", 0L);
        }
        long start = System.currentTimeMillis();
        try {
            OpenApiChatRequest chatRequest = new OpenApiChatRequest();
            chatRequest.setAgentId(agentId);
            chatRequest.setOpenId(properties.getOpenId());
            chatRequest.setContent(buildContent(request));
            chatRequest.setTimeout(properties.getTimeoutMs());
            Result<OpenApiChatSyncResponse> result = client.chatSync(chatRequest);
            long elapsed = System.currentTimeMillis() - start;
            if (result == null) {
                return ModelInvokeResult.failure("snail-ai 返回为空", elapsed);
            }
            if (result.getStatus() != SUCCESS_STATUS || result.getData() == null) {
                return ModelInvokeResult.failure("snail-ai 调用失败：" + StringUtils.blankToDefault(result.getMessage(), "未知错误"), elapsed);
            }
            OpenApiChatSyncResponse data = result.getData();
            // tokens/cost 该响应结构不返回，保持 null（不编造）
            ModelInvokeResult invokeResult = ModelInvokeResult.success(data.getContent(),
                data.getDurationMs() == null ? elapsed : data.getDurationMs());
            invokeResult.setModelVersion(request.getModelKey());
            return invokeResult;
        } catch (Exception e) {
            // SPI 约定：不向外抛异常
            log.error("snail-ai 调用异常, capabilityCode={}, modelId={}, agentId={}",
                request.getCapabilityCode(), request.getModelId(), agentId, e);
            return ModelInvokeResult.failure("snail-ai 调用异常：" + e.getClass().getSimpleName(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 组装 snail-ai 的 {@code content}：提示词 + 结构化载荷。
     *
     * @param request 调用请求
     * @return content 文本
     */
    private String buildContent(ModelInvokeRequest request) {
        Map<String, Object> payload = request.getPayload();
        String prompt = StringUtils.blankToDefault(request.getPrompt(), "");
        if (payload == null || payload.isEmpty()) {
            return prompt;
        }
        return prompt + "\n" + JsonUtils.toJsonString(payload);
    }

}
