package org.dromara.content.service;

import org.dromara.content.helper.ContentGateEngine;

/**
 * 闸门重算服务：按闸门规则重算任务状态并落库。
 *
 * <p><b>为什么单独成一个服务</b>：解析完成、卡片处理、人工确认之后都要重算闸门。
 * 若把重算写在任务服务里，卡片服务就要反向依赖任务服务，形成循环；独立出来后
 * 两者都只依赖本服务，且「状态怎么变」只有一处实现。</p>
 *
 * @author content
 */
public interface IContentTaskGateService {

    /**
     * 只做判定、不落库。
     * <p>供详情接口使用：GET 不应产生写副作用。</p>
     *
     * @param taskId 任务ID
     * @return 判定结论
     */
    ContentGateEngine.GateResult evaluate(Long taskId);

    /**
     * 重算并落库任务状态。
     *
     * @param taskId 任务ID
     * @return 判定结论
     */
    ContentGateEngine.GateResult recheckAndApply(Long taskId);

}
