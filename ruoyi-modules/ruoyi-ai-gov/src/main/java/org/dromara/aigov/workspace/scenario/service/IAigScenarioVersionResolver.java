package org.dromara.aigov.workspace.scenario.service;

import org.dromara.aigov.workspace.domain.AigScenarioVersion;

/**
 * 场景版本解析（增量 13；用户拍板：执行时按 {@code scenarioCode} 解析当时的 STABLE 版本）。
 *
 * <p><b>为什么按 STABLE 解析</b>：平台任务只记 {@code scenarioCode}，不记版本。执行时取该场景
 * <b>最新的 STABLE 版本</b>，与 {@code AigLaunchChecklist#scenarioUsable}（"只有 STABLE 算可用"）
 * 同一口径。代价是"确认的版本"与"执行的版本"可能不同——这是选这条路线时已经接受的取舍；
 * 若要改成"锁定确认的那一版"，需要把版本记到任务上（那是另一条路线）。</p>
 *
 * @author ai-gov
 */
public interface IAigScenarioVersionResolver {

    /**
     * 取某场景当前最新的 STABLE 版本。
     *
     * @param scenarioCode 场景编码（可空）
     * @return 版本行；场景不存在或没有 STABLE 版本时返回 null
     */
    AigScenarioVersion stableVersion(String scenarioCode);

}
