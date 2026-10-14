package org.dromara.aigov.execution;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.aigov.knowledge.retrieval.KnowledgePort;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 执行计划解析实现（设计 D01）。
 *
 * <h3>它解决什么</h3>
 * <p>通用任务执行过去只把 {@code agentVersionId} 透传给审计，Prompt 由调用方提供——
 * 于是「关联了某个版本」与「按该版本的配置执行」是两件事，正式运行完全可能用的是调用方
 * 临时拼的 Prompt。本解析器让执行从<b>冻结的版本配置</b>取系统 Prompt、能力与外发许可，
 * 并给出配置哈希，使"这次依据的是哪份配置"可复核。</p>
 *
 * <h3>三条保守原则</h3>
 * <ol>
 *     <li><b>只认显式 v2 配置</b>：非 v2 一律返回 {@code null}，退回旧适配器，不猜测；</li>
 *     <li><b>外发许可是取与的缩影</b>：版本 {@code allow_external} 与 v2 的
 *         {@code dataPolicy.allowExternal} 任一为否即为否，默认（空白）按否处理；</li>
 *     <li><b>执行前复查授权</b>：调用 {@code assertExecutionAuthorized}，绑定被撤销/到期后
 *         正式执行会被拒绝（D03）——发布时校验过不等于执行时仍成立。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigAgentExecutionResolver implements IAigAgentExecutionResolver {

    /**
     * 一次运行最多采用的检索片段数（设计 §7.4 的"最大片段数"由解析器给定）
     */
    private static final int DEFAULT_MAX_CITATIONS = 5;

    private final AigAgentVersionMapper agentVersionMapper;
    private final IAigAgentRegistryService registryService;
    private final KnowledgePort knowledgePort;
    private final JsonMapper jsonMapper;

    @Override
    public AigExecutionPlan resolve(Long taskId, Long agentVersionId, String dataLevel,
                                    String inputSnapshotJson, String businessInput, BigDecimal maxCost,
                                    AigExecutionPurposeEnum purpose) {
        if (agentVersionId == null) {
            return null;
        }
        AigAgentVersion version = agentVersionMapper.selectById(agentVersionId);
        if (version == null) {
            throw new ServiceException("Agent 版本不存在或已删除：" + agentVersionId);
        }
        AigExecutionConfig config = AigExecutionConfig.parse(version.getConfigJson(), jsonMapper);
        if (config == null) {
            // 旧格式：按既有适配器运行（不宣称新机制生效）
            return null;
        }
        if (StringUtils.isBlank(version.getPromptTemplate())) {
            // 声明了 v2 配置却没有冻结 Prompt：无法编译系统指令，退回旧路径而不是"执行一个空系统提示"
            log.warn("版本声明 v2 配置但没有冻结 prompt_template，按旧适配器运行, agentVersionId={}",
                agentVersionId);
            return null;
        }
        // D03：执行前复查版本授权（受限通道必须有启用中且在有效期内的绑定）
        registryService.assertExecutionAuthorized(agentVersionId);

        String capability = StringUtils.isNotBlank(config.getProviderCapability())
            ? config.getProviderCapability() : version.getProviderCapability();
        Boolean allowExternal = effectiveAllowExternal(version, config);
        String configHash = sha256(String.join("|",
            String.valueOf(version.getAgentVersionId()),
            StringUtils.blankToDefault(version.getVersion(), "-"),
            version.getPromptTemplate(),
            StringUtils.blankToDefault(capability, "-"),
            String.valueOf(allowExternal),
            StringUtils.blankToDefault(version.getConfigJson(), "")));
        String inputHash = sha256(StringUtils.blankToDefault(inputSnapshotJson, "")
            + "\n" + StringUtils.blankToDefault(businessInput, ""));
        String planId = sha256(configHash + ":" + (purpose == null ? "-" : purpose.getCode())
            + ":" + inputHash);

        // ★ C-3：知识检索在**解析阶段**完成并冻结进计划（设计 §7.1/§7.4）。
        //   检索带入授权范围（本版本的 knowledgeRefs）；只检索不生成；
        //   返回的引用来自服务端合法集合，因此可作为可核验来源。
        String knowledgeBlock = null;
        List<AigExecutionCitation> citations = new ArrayList<>();
        if (!config.getKnowledgeRefs().isEmpty() && StringUtils.isNotBlank(businessInput)) {
            KnowledgePort.RetrievalResult retrieval = knowledgePort.retrieve(new KnowledgePort.RetrievalQuery(
                businessInput, Set.of(), Set.copyOf(config.getKnowledgeRefs()), DEFAULT_MAX_CITATIONS));
            for (KnowledgePort.Citation citation : retrieval.citations()) {
                citations.add(new AigExecutionCitation(citation.chunkId(), citation.versionId(),
                    citation.documentId(), citation.sourceName(), citation.locator(),
                    citation.excerptRef(), citation.dataLevel()));
            }
            knowledgeBlock = AigKnowledgePromptBlock.render(citations, retrieval.noSupport(),
                retrieval.reason());
        }

        AigExecutionPlan plan = AigExecutionPlan.builder()
            .planId(planId)
            .taskId(taskId)
            .agentVersionId(version.getAgentVersionId())
            .purpose(purpose)
            .configHash(configHash)
            .systemPrompt(version.getPromptTemplate())
            .inputHash(inputHash)
            .capabilityCode(capability)
            .dataLevel(dataLevel)
            .allowExternal(allowExternal)
            .maxCost(maxCost)
            .maxToolCalls(config.getMaxToolCalls())
            .maxDepth(config.getMaxDepth())
            .knowledgeRefs(config.getKnowledgeRefs())
            .toolRefs(config.getToolRefs())
            .retrievedKnowledgeBlock(knowledgeBlock)
            .knowledgeCitations(citations)
            .build();
        log.info("解析 Agent 执行计划, taskId={}, agentVersionId={}, purpose={}, planId={}, configHash={}",
            taskId, agentVersionId, purpose == null ? null : purpose.getCode(),
            planId, configHash);
        return plan;
    }

    /**
     * 计算有效外发许可：版本声明与 v2 dataPolicy 取与，空白按禁止处理。
     *
     * @param version 版本
     * @param config  v2 配置
     * @return 有效许可（永不为 null）
     */
    private Boolean effectiveAllowExternal(AigAgentVersion version, AigExecutionConfig config) {
        boolean versionAllows = "Y".equalsIgnoreCase(
            version.getAllowExternal() == null ? null : version.getAllowExternal().trim());
        boolean configAllows = config.getAllowExternal() == null || config.getAllowExternal();
        return versionAllows && configAllows;
    }

    private static String sha256(String text) {
        return DigestUtil.sha256Hex(text == null ? "" : text);
    }

}
