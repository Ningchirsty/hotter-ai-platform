package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;

import java.util.List;

/**
 * 模型主数据写入 Mapper（{@code sai_model_config}）。
 *
 * <p><b>与 {@link AigModelViewMapper} 的分工</b>：那个只读、负责联表查询；
 * 本接口只承担「新增模型」这一件写事，两个接口都不越界，便于审查
 * 「谁动了 sai_* 表」。</p>
 *
 * <p><b>安全约束（必须遵守）</b>：</p>
 * <ol>
 *     <li>语句中的列<b>全部显式为白名单</b>，绝不出现 {@code api_key}——治理层从不
 *         读写该列，密钥一律只登记引用（{@code aig_model_governance.secret_ref}）；</li>
 *     <li>本接口<b>只做 INSERT 与只读校验</b>，不提供 UPDATE / DELETE：
 *         修改与下架走 snail-ai 或治理属性，避免治理层意外改写模型主数据。</li>
 * </ol>
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
     * @param bo 新增模型参数
     * @return 影响行数
     */
    int insertModel(AigModelCreateBo bo);

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

}
