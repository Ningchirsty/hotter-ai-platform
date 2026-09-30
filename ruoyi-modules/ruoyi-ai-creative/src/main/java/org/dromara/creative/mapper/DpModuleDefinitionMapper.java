package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpModuleDefinition;

/**
 * 模块定义 Mapper（V0.2 R21）。
 *
 * @author creative
 */
public interface DpModuleDefinitionMapper extends BaseMapperPlus<DpModuleDefinition, DpModuleDefinition> {

    /**
     * 数同交付类型、同模块编码的定义——**把逻辑删除的行也算进来**（V0.2 R29）。
     *
     * <p><b>为什么必须绕开逻辑删除</b>：{@code uk_dp_module_def (delivery_type, module_code)}
     * 是只建在这两列上的唯一键，<b>不含 del_flag</b>。所以软删过的模块编码会永久占位：
     * R29 真机验收里重建同编码模块，页面看到的是 MySQL 原始报错
     * 「数据库中已存在该记录，请联系管理员确认」——用户完全不知道"是谁占了"，
     * 也不知道该怎么办。这里先查出来，好给一句能照做的提示。</p>
     *
     * @param deliveryType 交付类型
     * @param moduleCode   模块编码
     * @return 命中行数（含 del_flag='1' 的历史行）
     */
    @Select("select count(*) from dp_module_definition "
        + "where delivery_type = #{deliveryType} and module_code = #{moduleCode}")
    Long countIncludingDeleted(@Param("deliveryType") String deliveryType,
                               @Param("moduleCode") String moduleCode);
}
