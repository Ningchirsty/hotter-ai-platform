package org.dromara.aigov.service.invoker;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatSyncResponse;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigGovProperties;
import org.dromara.aigov.domain.vo.AigSnailAgentVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.helper.SnailAiAppVerifier;
import org.dromara.aigov.mapper.AigSnailAgentMapper;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * <p><b>「模型 ↔ Agent」精确映射（阶段2 已落地）</b>：snail-ai OpenAPI 聊天入口收的是
 * <b>Agent</b>（{@code agentId}），不是 {@code model_key}。因此本调用器<b>不</b>使用任何静态
 * agentId，而是<b>按路由选中的模型反查 Agent</b>：{@code sai_agent.chat_model_id = 本次模型ID}。</p>
 * <ul>
 *     <li>查到（取启用中、id 最小的那个）→ 用它发起调用，因此**实际执行的模型与治理层选中的模型一致**；</li>
 *     <li>查不到 → <b>明确失败</b>并说明怎么办（在 snail-ai 里给该模型建 Agent，或把该模型改为直连部署类型）。
 *         绝不退回到「随便找个 Agent 跑」——那正是「配的是 A、跑的是 B」的来源，
 *         而且失败时调用方看不出任何异常；</li>
 *     <li>一个模型对应多个 Agent → 取 id 最小者并记 WARN：模型一致了，但 Agent 自带的
 *         instruction/skill/RAG 也会影响输出，因此映射最好保持一对一。</li>
 * </ul>
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
     * {@code sai_agent.status} 的活跃值（1-活跃 2-非活跃 3-已废弃 4-已禁用）。
     */
    private static final int ACTIVE_STATUS = 1;

    /**
     * snail-ai OpenAPI 客户端（snail-ai.enabled=false 时不存在，故用 ObjectProvider 软依赖）。
     */
    private final ObjectProvider<OpenApiChatClient> chatClientProvider;

    /**
     * 治理层配置（snail-ai 开关 / openId / 超时 / 应用作用域）。
     */
    private final AigGovProperties properties;

    /**
     * snail-ai Agent 只读 Mapper：模型 ↔ Agent 映射的来源。
     */
    private final AigSnailAgentMapper snailAgentMapper;

    /**
     * 客户端身份核对（app-id / token 与 {@code sai_app} 是否一致）。
     *
     * <p>身份的作用域过滤也取自它，保证「过滤用谁」与「校验谁」是同一个值。</p>
     */
    private final SnailAiAppVerifier appVerifier;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        // 集团共享 / 外部企业服务走 snail-ai；本地私有由本地调用器承担。
        //
        // EXTERNAL_API 已让给 OpenAiCompatibleInvoker 直连，不再绕集团链路：
        // snail-ai 的聊天入口收的是 agentId，实际执行的模型由 Agent 决定，
        // 与治理台登记的 model_key / api_endpoint 无关。继续认领它只会造成
        // 「治理台配的是 A、实际跑的是 B」这种最难查的错配。
        // 这样切分后，同一种部署类型只有一个调用器认领，不依赖 Bean 装配顺序。
        return deploymentType == AigDeploymentTypeEnum.GROUP
            || deploymentType == AigDeploymentTypeEnum.EXTERNAL_ENTERPRISE;
    }

    @Override
    public boolean available() {
        // 只管「这条通道本身能不能用」：客户端在、开关开。
        // 「本次这个模型在 snail-ai 里有没有对应的 Agent」是**逐次调用**的事实，
        // 由 invoke 内部判定并给出可读原因——本方法没有模型参数，在这里猜只会把
        // 路由阶段变成「静默排除」，用户看不到为什么。
        return chatClientProvider.getIfAvailable() != null && properties.isEnabled();
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
        // 先核对客户端身份（app-id / token 与 sai_app 是否一致）。
        // 放在这里而不是启动期：该通道默认关闭，且 sai_* 表未必每个环境都导入；
        // 而身份错了的典型表现是「发出去了但没人应答」或服务端鉴权失败，报错落在别处、很难定位。
        List<String> identityProblems = appVerifier.verify();
        if (!identityProblems.isEmpty()) {
            return ModelInvokeResult.failure("snail-ai 客户端身份核对未通过："
                + SnailAiAppVerifier.describe(identityProblems), 0L);
        }
        // 模型 → Agent：查不到就失败，绝不换一个模型跑（见类注释）
        AgentPick pick = resolveAgent(request);
        if (pick.error() != null) {
            return ModelInvokeResult.failure(pick.error(), 0L);
        }
        Long agentId = pick.agentId();
        long start = System.currentTimeMillis();
        try {
            // 图片型载荷必须显式拒绝：snail-ai OpenAPI 只收文本 content，
            // 「忽略图片照常发文本」会让模型在没见过图的情况下给出结论，
            // 调用方无法区分「真的比对过」与「只是没报错」。
            String imageError = ModelImagePayload.requireUnsupported(request.getPayload(), "snail-ai");
            if (imageError != null) {
                return ModelInvokeResult.failure(imageError, System.currentTimeMillis() - start);
            }
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
     * 一次「模型 → Agent」的选定结果：要么给出可用 Agent，要么给出可读的失败原因。
     *
     * @param agentId 选中的 Agent ID（失败时为 null）
     * @param error   失败原因（成功时为 null）
     */
    private record AgentPick(Long agentId, String error) {

        static AgentPick of(Long agentId) {
            return new AgentPick(agentId, null);
        }

        static AgentPick fail(String error) {
            return new AgentPick(null, error);
        }
    }

    /**
     * 按本次调用的模型反查应使用的 snail-ai Agent。
     *
     * <p>这是「模型 ↔ Agent 精确映射」的全部实现：判据是 {@code sai_agent.chat_model_id}
     * 与本次 {@code modelId} 相等。任何一步不成立都返回可读原因，而不是换一个 Agent 继续——
     * 后者会让「配的是 A、跑的是 B」重新出现，且调用方看不出异常。</p>
     *
     * @param request 调用请求
     * @return 选定的 Agent 或失败原因
     */
    private AgentPick resolveAgent(ModelInvokeRequest request) {
        Long modelId = request.getModelId();
        if (modelId == null) {
            return AgentPick.fail("snail-ai 调用缺少模型ID（modelId）：模型 → Agent 映射以"
                + " sai_agent.chat_model_id 为准，没有模型ID就无法确定实际执行哪个模型。"
                + "若这是连通性探测，请让探测请求带上 modelId（对象：" + request.getModelKey() + "）");
        }
        List<AigSnailAgentVo> agents = snailAgentMapper.selectByChatModelId(modelId);
        if (agents == null || agents.isEmpty()) {
            return AgentPick.fail("模型 #" + modelId + "（" + StringUtils.blankToDefault(request.getModelKey(), "未知标识")
                + "）在 snail-ai 里没有关联的 Agent（sai_agent.chat_model_id 无匹配），"
                + "无法经集团链路执行。处置：在 snail-ai 里为该模型建一个 Agent，"
                + "或把该模型改用直连部署类型（EXTERNAL_API / LOCAL）");
        }

        // 作用域：只接受「本地执行(app_id 为空)」或「就是我方应用」的 Agent。
        // 否则请求可能被派给别的应用，表现为「发出去了但没人应答」。
        // 身份取自 appVerifier.effectiveAppId()——与上面的身份核对是同一个值，
        // 不会出现「过滤用 A、校验用 B」。
        String ownAppId = appVerifier.effectiveAppId();
        List<AigSnailAgentVo> scoped = new ArrayList<>();
        int foreignApp = 0;
        for (AigSnailAgentVo agent : agents) {
            if (StringUtils.isBlank(ownAppId)
                || StringUtils.isBlank(agent.getAppId())
                || ownAppId.equals(agent.getAppId())) {
                scoped.add(agent);
            } else {
                foreignApp++;
            }
        }

        List<AigSnailAgentVo> active = new ArrayList<>();
        int inactive = 0;
        for (AigSnailAgentVo agent : scoped) {
            // status: 1-活跃 2-非活跃 3-已废弃 4-已禁用
            if (agent.getStatus() != null && agent.getStatus() == ACTIVE_STATUS) {
                active.add(agent);
            } else {
                inactive++;
            }
        }
        if (active.isEmpty()) {
            return AgentPick.fail("模型 #" + modelId + "（" + StringUtils.blankToDefault(request.getModelKey(), "未知标识")
                + "）在 snail-ai 里没有**启用中**的 Agent，无法经集团链路执行：共匹配到 " + agents.size()
                + " 个 Agent，其中状态非活跃/已废弃/已禁用 " + inactive + " 个"
                + (foreignApp > 0 ? "、作用域属于其它应用 " + foreignApp + " 个" : "")
                + "。处置：把其中一个设为活跃（status=1）"
                + (foreignApp > 0
                    ? "，或确认「本客户端身份（app-id=" + ownAppId + "）」与它在同一作用域"
                    : ""));
        }

        // 一对一最稳妥：Agent 自带的 instruction/skill/RAG 同样会影响输出，
        // 多对一时「用哪个 Agent」治理层无从得知，因此取 id 最小者并留痕（不静默）。
        // 排序在 Java 里做，不依赖「SQL 恰好按 id 排序」——那是另一处实现细节，
        // 一旦有人改了 SQL 或换了调用方，「取哪个」就会悄悄变。
        active.sort(Comparator.comparing(AigSnailAgentVo::getId, Comparator.nullsLast(Long::compareTo)));
        AigSnailAgentVo chosen = active.get(0);
        if (active.size() > 1) {
            log.warn("模型 #{} 在 snail-ai 里关联了 {} 个启用中的 Agent（{}），本次取 id 最小的 #{}（{}）；"
                    + "建议保持一对一映射：Agent 自带的系统指令/技能/RAG 也会影响输出",
                modelId, active.size(), describeIds(active), chosen.getId(), chosen.getName());
        }
        log.debug("snail-ai 模型 → Agent 映射命中, modelId={}, agentId={}, appId={}, 候选数={}",
            modelId, chosen.getId(), chosen.getAppId(), active.size());
        return AgentPick.of(chosen.getId());
    }

    /**
     * 列出候选 Agent 的 id（仅用于日志，不含可能较长的名称）。
     *
     * @param agents 候选
     * @return 形如 {@code 12,15} 的文本
     */
    private static String describeIds(List<AigSnailAgentVo> agents) {
        StringBuilder sb = new StringBuilder();
        for (AigSnailAgentVo agent : agents) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(agent.getId());
        }
        return sb.toString();
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
        // 双保险：即便上面的拒绝分支被绕过，也绝不把 base64 序列化进 content
        Map<String, Object> textPayload = ModelImagePayload.withoutImages(payload);
        if (textPayload.isEmpty()) {
            return prompt;
        }
        return prompt + "\n" + JsonUtils.toJsonString(textPayload);
    }

}
