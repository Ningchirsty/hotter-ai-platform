package org.dromara.aigov.execution;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
import org.dromara.aigov.agent.enums.AigAgentResourceTypeEnum;
import org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper;
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
    private final AigAgentResourceBindingMapper resourceBindingMapper;
    private final org.dromara.aigov.workspace.launch.mapper.AigLaunchRecordMapper launchRecordMapper;
    private final IAigAgentRegistryService registryService;
    private final KnowledgePort knowledgePort;
    private final ISubAgentGraphResolver subAgentGraphResolver;
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
        // §7.2：有效数据等级取各来源最高值（任务等级 × 版本最低等级）
        String effectiveDataLevel = AigEffectivePolicy.strictestDataLevel(dataLevel, config.getMinimumLevel());
        if (effectiveDataLevel == null) {
            effectiveDataLevel = dataLevel;
        }
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
        // §7.2：有效知识范围 = 版本声明 ∩ 启用的 KNOWLEDGE 绑定（绑定只做收窄；无绑定不额外收窄）
        List<String> allowedCollections = AigEffectivePolicy.intersect(config.getKnowledgeRefs(),
            knowledgeBindings(version.getAgentVersionId()));
        if (!allowedCollections.isEmpty() && StringUtils.isNotBlank(businessInput)) {
            KnowledgePort.RetrievalResult retrieval = knowledgePort.retrieve(new KnowledgePort.RetrievalQuery(
                businessInput, Set.of(), Set.copyOf(allowedCollections), DEFAULT_MAX_CITATIONS));
            for (KnowledgePort.Citation citation : retrieval.citations()) {
                citations.add(new AigExecutionCitation(citation.chunkId(), citation.versionId(),
                    citation.documentId(), citation.sourceName(), citation.locator(),
                    citation.excerptRef(), citation.dataLevel()));
            }
            knowledgeBlock = AigKnowledgePromptBlock.render(citations, retrieval.noSupport(),
                retrieval.reason());
        }

        // ★ D05：子 Agent 顺序编排——展开并校验（最大深度、禁止循环），结果冻结进计划。
        //   超限/循环一律拒绝，而不是截断或等运行时超时。
        List<Long> subAgentVersionIds = List.of();
        if (!config.getSubAgentVersionIds().isEmpty()) {
            int maxDepth = config.getMaxDepth() == null ? 1 : config.getMaxDepth();
            AigSubAgentOrchestration.Result orchestration = AigSubAgentOrchestration.plan(
                version.getAgentVersionId(), subAgentGraphResolver::childrenOf, maxDepth);
            if (!orchestration.accepted()) {
                throw new ServiceException("子 Agent 编排被拒绝：" + orchestration.rejectionReason());
            }
            subAgentVersionIds = orchestration.steps().stream()
                .map(AigSubAgentOrchestration.Step::versionId)
                .toList();
        }

        // ★ D05：顺序流程——校验声明的节点序列（类型可识别、必须有输出且输出在最后、不超步数上限）
        List<AigFlowNode> flowNodes = List.of();
        if (!config.getFlowNodes().isEmpty()) {
            int maxSteps = config.getMaxFlowSteps() == null ? 20 : config.getMaxFlowSteps();
            AigSequentialFlow.Result flow = AigSequentialFlow.validate(config.getFlowNodes(), maxSteps);
            if (!flow.accepted()) {
                throw new ServiceException("流程编排被拒绝：" + flow.reason());
            }
            flowNodes = flow.nodes();
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
            .dataLevel(effectiveDataLevel)
            .allowExternal(allowExternal)
            .maxCost(maxCost)
            .maxToolCalls(config.getMaxToolCalls())
            .maxDepth(config.getMaxDepth())
            .knowledgeRefs(config.getKnowledgeRefs())
            .toolRefs(config.getToolRefs())
            .subAgentVersionIds(subAgentVersionIds)
            .authorizationContext(authorizationContext(taskId))
            .flowNodes(flowNodes)
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
        // §7.2：任意禁止条件优先——版本声明与 v2 dataPolicy 任一方禁止即禁止
        return AigEffectivePolicy.externalAllowed(versionAllows, config.getAllowExternal());
    }

    private static String sha256(String text) {
        return DigestUtil.sha256Hex(text == null ? "" : text);
    }

    /**
     * 冻结授权上下文：从启动记录读"这次运行以谁的身份、在哪个岗位卡片下发生"（设计 §7.1/§7.2）。
     *
     * <p>取不到（任务不是由岗位卡片启动、或启动记录已不存在）时返回 null——表示"没有岗位上下文"，
     * 而不是"身份未知可以放宽"。</p>
     *
     * @param taskId 任务ID（可空）
     * @return 授权上下文；无法确定时 null
     */
    private AigAuthorizationContext authorizationContext(Long taskId) {
        if (taskId == null) {
            return null;
        }
        org.dromara.aigov.workspace.launch.domain.AigLaunchRecord record = launchRecordMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                org.dromara.aigov.workspace.launch.domain.AigLaunchRecord>()
                .eq(org.dromara.aigov.workspace.launch.domain.AigLaunchRecord::getTaskId, taskId)
                .last("limit 1"));
        if (record == null) {
            return null;
        }
        return new AigAuthorizationContext(record.getUserId(), record.getOrgId(), record.getRoleCode(),
            record.getRoleVersionId(), record.getActionCode());
    }

    /**
     * 取某版本"启用的知识绑定编码"（用于把声明范围收窄，设计 §7.2）。
     * @param agentVersionId 版本ID
     * @return 绑定编码（无绑定返回空列表 = 不额外收窄）
     */
    private List<String> knowledgeBindings(Long agentVersionId) {
        List<AigAgentResourceBinding> rows = resourceBindingMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AigAgentResourceBinding>()
                .eq(AigAgentResourceBinding::getAgentVersionId, agentVersionId)
                .eq(AigAgentResourceBinding::getResourceType, AigAgentResourceTypeEnum.KNOWLEDGE.getCode())
                .eq(AigAgentResourceBinding::getEnabled, "Y"));
        List<String> codes = new ArrayList<>();
        for (AigAgentResourceBinding row : rows) {
            if (StringUtils.isNotBlank(row.getResourceCode())) {
                codes.add(row.getResourceCode().trim());
            }
        }
        return codes;
    }

}
