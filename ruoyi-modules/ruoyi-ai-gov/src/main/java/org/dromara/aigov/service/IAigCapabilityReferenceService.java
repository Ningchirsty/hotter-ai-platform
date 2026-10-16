package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigCapabilityReferenceVo;

import java.util.List;

/**
 * 「谁在引用这个能力」查询（只读；Q6 影响面）。
 *
 * @author ai-gov
 */
public interface IAigCapabilityReferenceService {

    /**
     * 查询引用某个能力的地方。
     *
     * @param capabilityCode 能力编码（必填）
     * @return 引用清单（Agent 版本 → 历史任务 → 训练台草稿）；能力编码为空返回空列表
     */
    List<AigCapabilityReferenceVo> listReferences(String capabilityCode);

}
