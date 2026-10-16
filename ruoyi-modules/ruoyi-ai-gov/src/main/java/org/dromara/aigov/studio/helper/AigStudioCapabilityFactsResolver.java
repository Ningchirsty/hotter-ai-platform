package org.dromara.aigov.studio.helper;

import lombok.RequiredArgsConstructor;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;
import org.dromara.aigov.service.IAigCapabilityOverviewService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把"草稿声明的能力编码"查成 {@link AigStudioCapabilityFacts}。
 *
 * <p><b>为什么复用能力概览服务，而不是直接查 AigCapability / AigCapabilityModel / AigRoutePolicy</b>：
 * 就绪度是一套口径（什么算"缺候选"、什么算"没策略"、什么算"健康"）。如果训练台自己再查一遍，
 * 就会出现"治理台卡片说缺策略、训练台预检说没问题"这类自相矛盾，而且两处口径以后必然继续漂移。</p>
 *
 * <p><b>查询按编码收敛</b>：概览的编码过滤是 LIKE，所以结果里再按编码**精确**取一条
 * （编码含相同片段的其它能力不能被当成它）。</p>
 *
 * @author ai-gov
 */
@Component
@RequiredArgsConstructor
public class AigStudioCapabilityFactsResolver {

    private final IAigCapabilityOverviewService capabilityOverviewService;

    /**
     * 查能力事实。
     *
     * @param capabilityCode 能力编码（可空）
     * @return 事实快照；编码为空时返回 {@code null}（"没声明能力"由预检的 CAPABILITY_MISSING 负责）
     */
    public AigStudioCapabilityFacts resolve(String capabilityCode) {
        if (StringUtils.isBlank(capabilityCode)) {
            return null;
        }
        String code = capabilityCode.trim();
        AigCapabilityBo bo = new AigCapabilityBo();
        bo.setCapabilityCode(code);
        List<AigCapabilityOverviewVo> rows = capabilityOverviewService.overview(bo, null, null, null);
        if (rows == null || rows.isEmpty()) {
            return AigStudioCapabilityFacts.missing(code);
        }
        for (AigCapabilityOverviewVo row : rows) {
            if (row != null && code.equalsIgnoreCase(row.getCapabilityCode())) {
                return AigStudioCapabilityFacts.of(code, row);
            }
        }
        return AigStudioCapabilityFacts.missing(code);
    }
}
