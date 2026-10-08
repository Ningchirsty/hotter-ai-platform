package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;

/**
 * 模型健康探测（M-003）。
 *
 * <p>把"人记得点 {@code POST /aigov/model/{id}/test}"变成"到点自动探测一轮"。</p>
 *
 * @author ai-gov
 */
public interface IAigModelHealthProbeService {

    /**
     * 探测一轮：挑出需要复测的模型，逐个调连通性测试并把结果落回治理表。
     *
     * <p><b>不抛异常</b>：单个模型探测失败只计入 skipped 并继续后面的模型——
     * 一轮里某个供应商超时不代表其余模型不该测。</p>
     *
     * @return 本轮结果汇总
     */
    AigModelHealthProbeVo probeOnce();

}
