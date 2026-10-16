package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.domain.vo.AigCapabilityReferenceVo;
import org.dromara.aigov.service.IAigCapabilityReferenceService;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「谁在引用这个能力」实现（只读）。
 *
 * <h3>三种来源与它们回答的问题</h3>
 * <ol>
 *     <li><b>Agent 版本</b>（{@code aig_agent_version}）：声明了要用的能力 —— 改策略/停用会直接影响它，
 *         其中"已发布"的版本风险最高；</li>
 *     <li><b>历史任务</b>（{@code aig_task}）：真的调用过这个能力 —— 删除能力后这些历史记录会指向
 *         一个不存在的编码，影响追溯（不是"重新跑就跑不动"那么轻）；</li>
 *     <li><b>训练台草稿</b>（{@code aig_studio_draft}）：正在写着要用它 —— 改了之后它会在预检/提交时失败，
 *         属于"提前告知"的价值。</li>
 * </ol>
 *
 * <h3>两个实现上的取舍</h3>
 * <ul>
 *     <li><b>Agent 版本两处都查</b>：能力编码既可能在独立列 {@code provider_capability}，
 *         也可能写在 {@code config_json} 里（历史写法）。只查一处会漏掉一半引用，
 *         而"影响面漏了"比"多查一次"的代价大得多，所以两处都查、按版本ID去重；</li>
 *     <li><b>任务与草稿只回计数与标题样例</b>：历史任务可能有上千条，确认框不需要逐条列出；
 *         给出"共 N 条"就足以支撑"要不要继续"的决定。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigCapabilityReferenceServiceImpl implements IAigCapabilityReferenceService {

    /**
     * 引用类型：Agent 版本声明。
     */
    private static final String TYPE_AGENT_VERSION = "AGENT_VERSION";

    /**
     * 引用类型：历史任务。
     */
    private static final String TYPE_TASK = "TASK";

    /**
     * 引用类型：训练台草稿。
     */
    private static final String TYPE_DRAFT = "DRAFT";

    /**
     * 发布中的状态标记（用于把"在跑的版本"排在前面并提示风险）。
     */
    private static final String RELEASE_PUBLISHED = "RELEASED";

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigAgentMapper agentMapper;

    private final AigTaskMapper taskMapper;

    private final AigStudioDraftMapper studioDraftMapper;

    @Override
    public List<AigCapabilityReferenceVo> listReferences(String capabilityCode) {
        if (StringUtils.isBlank(capabilityCode)) {
            return List.of();
        }
        String code = capabilityCode.trim();
        List<AigCapabilityReferenceVo> result = new ArrayList<>();

        // ① Agent 版本：独立列 + config_json 两处都查（去重）
        List<AigAgentVersion> versions = agentVersionMapper.selectList(new LambdaQueryWrapper<AigAgentVersion>()
            .eq(AigAgentVersion::getProviderCapability, code)
            .or()
            .like(AigAgentVersion::getConfigJson, code));
        Set<Long> seen = new LinkedHashSet<>();
        List<AigAgentVersion> distinct = new ArrayList<>();
        for (AigAgentVersion version : versions) {
            if (version.getAgentVersionId() != null && seen.add(version.getAgentVersionId())) {
                distinct.add(version);
            }
        }
        if (!distinct.isEmpty()) {
            List<Long> agentIds = new ArrayList<>();
            for (AigAgentVersion version : distinct) {
                if (version.getAgentId() != null) {
                    agentIds.add(version.getAgentId());
                }
            }
            List<AigAgent> agents = agentIds.isEmpty() ? List.of() : agentMapper.selectByIds(agentIds);
            for (AigAgentVersion version : distinct) {
                result.add(toAgentVersionRef(version, agents, code));
            }
        }

        // ② 历史任务：数量级可能是上千，只回计数（诚实标注"含已结束任务"）
        Long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getCapabilityCode, code));
        if (taskCount != null && taskCount > 0) {
            AigCapabilityReferenceVo vo = new AigCapabilityReferenceVo();
            vo.setRefType(TYPE_TASK);
            vo.setCount(taskCount.intValue());
            vo.setRefLabel("历史任务 " + taskCount + " 条（含已结束；删除能力后这些记录的编码将指向不存在的定义）");
            vo.setDetail("aig_task.capability_code");
            result.add(vo);
        }

        // ③ 训练台草稿：正在写着要用它
        Long draftCount = studioDraftMapper.selectCount(new LambdaQueryWrapper<AigStudioDraft>()
            .like(AigStudioDraft::getContentJson, code));
        if (draftCount != null && draftCount > 0) {
            AigCapabilityReferenceVo vo = new AigCapabilityReferenceVo();
            vo.setRefType(TYPE_DRAFT);
            vo.setCount(draftCount.intValue());
            vo.setRefLabel("训练台草稿 " + draftCount + " 份引用了该能力（改动后它们会在预检/提交时报错）");
            vo.setDetail("aig_studio_draft.content_json");
            result.add(vo);
        }

        log.info("能力引用查询, capabilityCode={}, agentVersions={}, tasks={}, drafts={}",
            code, distinct.size(), taskCount, draftCount);
        return result;
    }

    /**
     * 组装一行 Agent 版本引用。
     *
     * @param version       版本
     * @param agents        Agent 主数据（用于编码/名称）
     * @param capabilityCode 能力编码（判断声明来源）
     * @return 引用行
     */
    private AigCapabilityReferenceVo toAgentVersionRef(AigAgentVersion version, List<AigAgent> agents,
                                                       String capabilityCode) {
        AigAgent agent = null;
        for (AigAgent candidate : agents) {
            if (candidate != null && candidate.getAgentId() != null
                && candidate.getAgentId().equals(version.getAgentId())) {
                agent = candidate;
                break;
            }
        }
        // 声明来源：独立列命中就是"显式声明"，否则说明写在 config_json 里
        boolean declaredInColumn = capabilityCode.equalsIgnoreCase(
            version.getProviderCapability() == null ? "" : version.getProviderCapability().trim());
        AigCapabilityReferenceVo vo = new AigCapabilityReferenceVo();
        vo.setRefType(TYPE_AGENT_VERSION);
        vo.setAgentId(version.getAgentId());
        vo.setAgentCode(agent == null ? null : agent.getAgentCode());
        vo.setAgentName(agent == null ? null : agent.getAgentName());
        vo.setAgentVersionId(version.getAgentVersionId());
        vo.setAgentVersion(version.getVersion());
        vo.setReleaseStatus(version.getReleaseStatus());
        vo.setDetail(declaredInColumn ? "显式声明 provider_capability" : "声明在 config_json 里");
        String name = agent == null ? ("Agent#" + version.getAgentId()) : agent.getAgentName();
        vo.setRefLabel("Agent「" + name + "」版本 " + version.getVersion()
            + "（" + releaseLabel(version.getReleaseStatus()) + "）");
        return vo;
    }

    /**
     * 发布状态 → 人话（在跑的版本风险最高，必须显式写出来）。
     *
     * @param releaseStatus 发布状态（可空）
     * @return 说明
     */
    private String releaseLabel(String releaseStatus) {
        if (StringUtils.isBlank(releaseStatus)) {
            return "状态未知";
        }
        return RELEASE_PUBLISHED.equalsIgnoreCase(releaseStatus.trim()) ? "已发布" : releaseStatus.trim();
    }

}
