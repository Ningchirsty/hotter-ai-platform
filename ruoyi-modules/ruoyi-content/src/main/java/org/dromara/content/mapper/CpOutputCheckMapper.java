package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpOutputCheck;
import org.dromara.content.domain.vo.CpOutputCheckVo;

/**
 * 成品一致性检查 Mapper 接口
 *
 * <p>无需 XML：字段与列名一一对应，MyBatis-Plus 通用方法即可覆盖；
 * 新增列（如后续的差异定位坐标）也会被自动映射，不必同步改映射文件。</p>
 *
 * @author content
 */
@Mapper
public interface CpOutputCheckMapper extends BaseMapperPlus<CpOutputCheck, CpOutputCheckVo> {

    /**
     * 取某前缀下已用过的最大检查单号，<b>包含已逻辑删除的行</b>。
     *
     * <p><b>为什么必须绕开逻辑删除</b>：{@code uk_cp_output_check_no} 是只建在
     * {@code check_no} 上的唯一索引，<b>不含</b> {@code del_flag}；而 MyBatis-Plus 的
     * 通用查询会自动追加 {@code del_flag = '0'}。用通用方法取 max 时，一旦当天所有记录
     * 都被软删除，max 就变成 null，序号从 0001 重新开始，插入立刻撞唯一索引报
     * {@code Duplicate entry}——表现为「删过一次检查之后，再也发不起新检查」。
     * 这里用原生 {@code @Select}（不走逻辑删除注入）保证看得见软删除行。</p>
     *
     * @param prefix 单号前缀，形如 {@code CK20260923}
     * @return 最大单号；当天无记录时返回 null
     */
    @Select("select max(check_no) from cp_output_check where check_no like concat(#{prefix}, '%')")
    String selectMaxCheckNoIncludeDeleted(@Param("prefix") String prefix);

}
