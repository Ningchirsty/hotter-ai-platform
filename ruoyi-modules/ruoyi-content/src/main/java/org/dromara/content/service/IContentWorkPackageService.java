package org.dromara.content.service;

import org.dromara.content.domain.vo.CpWorkPackageVo;

/**
 * 设计开工包服务。
 *
 * <p>对应设计文档 §7.3：让平面「无需重新翻找、核对和追问基础资料」。
 * 开工包冻结的是**已确认**的产品事实，以及缺口与替代方案、不可修改项。</p>
 *
 * @author content
 */
public interface IContentWorkPackageService {

    /**
     * 生成开工包（草稿）。
     * <p>仅在闸门判定为「可开工」或「条件开工」时允许生成；否则抛出可读的阻断说明。</p>
     *
     * @param taskId 任务ID
     * @return 开工包ID
     */
    Long generate(Long taskId);

    /**
     * 签发开工包。
     *
     * @param packageId 开工包ID
     */
    void issue(Long packageId);

    /**
     * 查询任务的最新开工包。
     *
     * @param taskId 任务ID
     * @return 开工包，未生成返回 null
     */
    CpWorkPackageVo getByTask(Long taskId);

}
