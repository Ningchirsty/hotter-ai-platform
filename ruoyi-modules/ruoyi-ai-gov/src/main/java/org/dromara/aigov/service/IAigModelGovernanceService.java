package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.bo.AigModelProviderBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * AI 模型治理服务。
 *
 * <p><b>关于 {@code sai_*} 的读写边界（阶段1 有意调整，务请知悉）</b>：</p>
 * <ul>
 *     <li>查询：跨表只读 {@code sai_model_config} / {@code sai_model_provider}，任何响应都不含 {@code api_key}；</li>
 *     <li>治理属性：读写 {@code aig_model_governance}（{@link #saveGovernance}）；</li>
 *     <li>新增模型：<b>唯一一处写 {@code sai_model_config} 的地方</b>
 *         （{@link #createModel}）。原设计约定「治理层只读 sai_*」，但实际部署中
 *         snail-ai 服务端未必就绪，届时没有任何途径登记模型，治理页只能看着空清单。
 *         故补上该入口，并把它限制在「只 INSERT、列白名单、不含 api_key」的范围内；
 *         模型主数据的修改与下架仍不经过治理层。</li>
 *     <li>供应商：内置 7 家是 snail-ai 种子数据，接入自建服务/新厂商时需要新增供应商
 *         （{@link #createProvider}），同样只写名称/标识/说明/图标/启停，不含任何密钥。
 *         供应商表没有密钥列，这是刻意的。</li>
 * </ul>
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

    /**
     * 新增模型：登记模型主数据（{@code sai_model_config}）并同时写入首份治理属性
     * （{@code aig_model_governance}），两者在同一事务内，避免产生无治理属性的孤儿模型。
     *
     * @param bo 新增模型参数
     * @return 新模型ID
     */
    Long createModel(AigModelCreateBo bo);

    /**
     * 供应商下拉选项（仅启用项，不含任何凭据）。
     *
     * @return 供应商列表
     */
    List<AigModelProviderVo> listProviders();

    /**
     * 供应商管理列表（含停用项，附各供应商下已登记模型数量）。
     *
     * @return 供应商列表
     */
    List<AigModelProviderVo> listAllProviders();

    /**
     * 新增供应商：{@code sai_model_provider} 内置的 7 家是种子数据，接入自建推理服务/内部网关/
     * 新云厂商时需要先有供应商才能登记模型，故开放此入口。只写名称/标识/说明/图标/启停，不涉及任何密钥。
     *
     * @param bo 供应商参数
     * @return 新供应商ID
     */
    Long createProvider(AigModelProviderBo bo);

    /**
     * 修改供应商：只允许名称/说明/图标/启停；标识一旦被模型引用就不再变动。
     *
     * @param bo 供应商参数（id 必填）
     * @return 影响行数
     */
    int updateProvider(AigModelProviderBo bo);

    /**
     * 连通性测试：按部署类型/适配器分流探测（本地执行者自检 / snail-ai 链路 / OpenAI 兼容端点），
     * 并把结果写入 {@code aig_model_governance.health_status} 与 {@code health_time}。
     *
     * @param modelId 模型ID
     * @return 测试结果（不含密钥）
     */
    AigModelTestVo testConnection(Long modelId);

}
