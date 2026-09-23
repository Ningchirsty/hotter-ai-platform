package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.vo.CpTaskVo;

/**
 * 内容生产任务 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpTaskMapper extends BaseMapperPlus<CpTask, CpTaskVo> {

    /**
     * 取某前缀下已用过的最大任务号，<b>包含已逻辑删除的行</b>。
     *
     * <p><b>为什么必须绕开逻辑删除</b>：{@code uk_cp_task_no} 是只建在 {@code task_no} 上的
     * 唯一索引，<b>不含</b> {@code del_flag}；而 MyBatis-Plus 的通用查询会自动追加
     * {@code del_flag = '0'}。用通用方法取 max 时，一旦当天创建的任务都被软删除，
     * max 就变成 null，序号从 0001 重新开始，插入立刻撞唯一索引报
     * {@code Duplicate entry}——现象是「当天把任务删完之后，当天再也建不了新任务」。
     * 这与 {@code cp_output_check} 的单号问题完全同类（同一坑踩了两次），
     * 故这里用原生 {@code @Select}（不走逻辑删除注入）保证看得见软删除行。</p>
     *
     * @param prefix 任务号前缀，形如 {@code CT20260923}
     * @return 最大任务号；当天无记录时返回 null
     */
    @Select("select max(task_no) from cp_task where task_no like concat(#{prefix}, '%')")
    String selectMaxTaskNoIncludeDeleted(@Param("prefix") String prefix);

}
