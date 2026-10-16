package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;

import java.util.List;

/**
 * 能力「配置完整度」聚合查询（能力清单卡片 / 看板）。
 *
 * <p>只读；不写任何表。规则与口径见 {@link org.dromara.aigov.domain.vo.AigCapabilityOverviewVo}。</p>
 *
 * @author ai-gov
 */
public interface IAigCapabilityOverviewService {

    /**
     * 聚合查询能力概览。
     *
     * @param bo             基础筛选（能力编码 / 名称 / 状态；可空）
     * @param requiredTags   标签筛选（逗号分隔模糊匹配；可空）
     * @param issueCode      只看带某类问题的能力（如 {@code NO_MODEL_CANDIDATE}；可空）
     * @param candidateState 候选状态筛选：{@code HAS} / {@code NO}（可空）
     * @return 概览列表（有问题的在前）
     */
    List<AigCapabilityOverviewVo> overview(AigCapabilityBo bo, String requiredTags,
                                           String issueCode, String candidateState);

}
