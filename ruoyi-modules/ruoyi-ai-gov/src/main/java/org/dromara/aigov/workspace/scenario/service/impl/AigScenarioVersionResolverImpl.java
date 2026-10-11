package org.dromara.aigov.workspace.scenario.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.dromara.aigov.workspace.portal.helper.AigVersionPick;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioVersionResolver;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 场景版本解析实现（增量 13）。
 *
 * <p>取该场景的**最新 STABLE 版本**：多个 STABLE 并存时按版本号比较（{@link AigVersionPick}，
 * 数字段比较而非字符串比较——{@code 1.10.0} 必须赢过 {@code 1.9.0}）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigScenarioVersionResolverImpl implements IAigScenarioVersionResolver {

    private final AigScenarioMapper scenarioMapper;
    private final AigScenarioVersionMapper scenarioVersionMapper;

    @Override
    public AigScenarioVersion stableVersion(String scenarioCode) {
        if (StringUtils.isBlank(scenarioCode)) {
            return null;
        }
        AigScenario scenario = scenarioMapper.selectOne(Wrappers.<AigScenario>lambdaQuery()
            .eq(AigScenario::getScenarioCode, scenarioCode.trim())
            .last("limit 1"));
        if (scenario == null) {
            return null;
        }
        List<AigScenarioVersion> versions = scenarioVersionMapper.selectList(
            Wrappers.<AigScenarioVersion>lambdaQuery()
                .eq(AigScenarioVersion::getScenarioId, scenario.getScenarioId()));
        AigScenarioVersion best = null;
        if (versions != null) {
            for (AigScenarioVersion version : versions) {
                if (version == null
                    || AigReleaseStatusEnum.find(version.getReleaseStatus()) != AigReleaseStatusEnum.STABLE) {
                    continue;
                }
                if (best == null || AigVersionPick.compare(version.getVersion(), best.getVersion()) > 0) {
                    best = version;
                }
            }
        }
        return best;
    }

}
