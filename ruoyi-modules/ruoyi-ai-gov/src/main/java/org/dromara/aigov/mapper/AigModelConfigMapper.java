package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelProviderBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;

import java.util.List;

/**
 * 模型与供应商主数据写入 Mapper（{@code sai_model_config} / {@code sai_model_provider}）。
 *
 * <p><b>与 {@link AigModelViewMapper} 的分工</b>：那个只读、负责联表查询；
 * 本接口只承担「新增模型」「新增/修改供应商」这两件写事，两个接口都不越界，
 * 便于审查「谁动了 sai_* 表」。</p>
 *
 * <p><b>安全约束（必须遵守）</b>：</p>
 * <ol>
 *     <li>除 {@code api_key} 外，语句中的列<b>全部显式为白名单</b>；</li>
 *     <li>{@code api_key} 是<b>唯一</b>允许写的敏感列，且只允许
 *         {@link #updateModelApiKey} 单列更新，绝不与其它列混在一次 UPDATE 里。
 *         写入值必须是<b>密文</b>——由 {@code AigModelSecretCipher} 按 snail-ai 的
 *         SM4 口径产出。明文一旦落库，snail-ai 运行时解密失败，模型会静默不可用；</li>
 *     <li>本接口只做 INSERT、供应商的有限 UPDATE（名称/说明/图标/启停），
 *         以及 {@code api_key} 的单列 UPDATE；模型主数据的其它列<b>不提供 UPDATE / DELETE</b>，
 *         修改与下架走 snail-ai 或治理属性；</li>
 *     <li>读 {@code api_key} 的只有 {@link #selectTestTarget(Long)}：连通性测试必须拿到
 *         平台侧密钥才能发请求，结果只进 {@code AigModelTestTargetVo}（服务端内部类型），
 *         绝不出现在任何对外响应或日志里。至于「是否已配置密钥」，由
 *         {@code AigModelViewMapper} 在 SQL 内算成布尔位，不把原值带进 Java。</li>
 * </ol>
 *
 * <p>⛔ 不要在本接口新增读取 {@code api_key} 原值的语句，也不要把 {@code api_key}
 * 加进别的 UPDATE。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigModelConfigMapper {

    /**
     * 新增模型主数据。
     * <p>自增主键回填到 {@code bo.id}。此处刻意不加 {@code @Param}：
     * 单个对象参数下 MyBatis 可直接用 {@code keyProperty="id"} 回填，写法最不易出错。</p>
     *
     * @param bo 新增模型参数（{@code apiKey} 须为密文或 null，由 Service 负责加密）
     * @return 影响行数
     */
    int insertModel(AigModelCreateBo bo);

    /**
     * 写入/清除模型密钥（<b>只更新 {@code api_key} 一列</b>）。
     *
     * @param modelId 模型ID
     * @param apiKey  密文密钥；传 {@code null} 表示清除
     * @return 影响行数
     */
    int updateModelApiKey(@Param("modelId") Long modelId, @Param("apiKey") String apiKey);

    /**
     * 按模型标识统计（唯一性校验）。
     *
     * @param modelKey 模型标识
     * @return 已存在的条数
     */
    int countByModelKey(@Param("modelKey") String modelKey);

    /**
     * 按供应商ID统计（存在性校验）。
     *
     * @param providerId 供应商ID
     * @return 已存在的条数
     */
    int countProvider(@Param("providerId") Long providerId);

    /**
     * 供应商下拉选项（仅启用的）。
     *
     * @return 供应商列表
     */
    List<AigModelProviderVo> selectProviderOptions();

    /**
     * 供应商管理列表（含停用，附各供应商下已登记模型数量）。
     *
     * @return 供应商列表
     */
    List<AigModelProviderVo> selectAllProviders();

    /**
     * 新增供应商。
     * <p>自增主键回填到 {@code bo.id}。</p>
     *
     * @param bo 供应商参数
     * @return 影响行数
     */
    int insertProvider(AigModelProviderBo bo);

    /**
     * 修改供应商的名称/说明/图标/启停（不改标识）。
     *
     * @param bo 供应商参数（id 必填）
     * @return 影响行数
     */
    int updateProvider(AigModelProviderBo bo);

    /**
     * 按供应商标识统计（唯一性校验）。
     *
     * @param providerKey 供应商标识
     * @param excludeId   需要排除的供应商ID（修改时排除自身），可为 null
     * @return 已存在的条数
     */
    int countByProviderKey(@Param("providerKey") String providerKey, @Param("excludeId") Long excludeId);

    /**
     * 按供应商名称统计（唯一性校验）。
     *
     * @param providerName 供应商名称
     * @param excludeId    需要排除的供应商ID（修改时排除自身），可为 null
     * @return 已存在的条数
     */
    int countByProviderName(@Param("providerName") String providerName, @Param("excludeId") Long excludeId);

    /**
     * 连通性测试所需的配置快照（含平台侧密钥，仅服务端内部使用）。
     *
     * @param modelId 模型ID
     * @return 测试目标，模型不存在时返回 null
     */
    AigModelTestTargetVo selectTestTarget(@Param("modelId") Long modelId);

}
