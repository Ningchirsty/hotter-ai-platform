package org.dromara.aigov.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.vo.AigModelVo;

import java.util.Collection;
import java.util.List;

/**
 * 模型主数据只读查询 Mapper（跨模块只读视图）。
 * <p><b>只读</b>：只做 {@code select}，绝不修改 snail-ai 的 {@code sai_*} 表。</p>
 * <p>为什么单独一个 Mapper：治理层的 5 个实体 Mapper 泛型被固定为
 * {@code BaseMapperPlus<实体, 实体Vo>}，无法承载「sai_model_config 为主表 LEFT JOIN
 * aig_model_governance」的联表 VO（{@link AigModelVo}）。本接口只承担这一件事。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigModelViewMapper {

    /**
     * 分页查询模型清单（sai_model_config 主表 LEFT JOIN aig_model_governance）。
     *
     * @param page 分页对象
     * @param bo   查询条件（keyword/modelType/isEnabled/deploymentType/lifecycleStatus/status）
     * @return 模型视图分页
     */
    IPage<AigModelVo> selectModelPage(IPage<AigModelVo> page, @Param("bo") AigModelGovernanceBo bo);

    /**
     * 按模型ID批量查询模型视图（含治理属性），供绑定列表回填与路由引擎使用。
     *
     * @param modelIds 模型ID集合
     * @return 模型视图列表
     */
    List<AigModelVo> selectModelListByIds(@Param("modelIds") Collection<Long> modelIds);

    /**
     * 按模型ID查询单个模型视图（含治理属性）。
     *
     * @param modelId 模型ID
     * @return 模型视图，不存在返回 null
     */
    AigModelVo selectModelById(@Param("modelId") Long modelId);

}
