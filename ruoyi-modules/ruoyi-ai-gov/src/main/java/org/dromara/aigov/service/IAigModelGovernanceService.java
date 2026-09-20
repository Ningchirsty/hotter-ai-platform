package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * AI 模型治理服务。
 * <p>模型主数据来自 snail-ai 的 {@code sai_model_config}，本服务<b>只读</b>该表，
 * 只写 {@code aig_model_governance}；任何响应都不含 {@code api_key}。</p>
 *
 * @author ai-gov
 */
public interface IAigModelGovernanceService {

    /**
     * 模型清单（跨表：{@code sai_model_config} 主表 LEFT JOIN {@code aig_model_governance}）。
     * <p>{@code apiEndpoint} / {@code secretRef} 仅在有 {@code aig:model:secret} 权限时下发。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigModelVo> list(AigModelGovernanceBo bo, PageQuery pageQuery);

    /**
     * 单个模型详情（含治理属性）。
     *
     * @param modelId 模型ID
     * @return 模型视图
     */
    AigModelVo getDetail(Long modelId);

    /**
     * 登记/更新模型治理属性（按 modelId upsert）。
     *
     * @param bo 治理参数
     * @return 治理记录ID
     */
    Long saveGovernance(AigModelGovernanceBo bo);

}
