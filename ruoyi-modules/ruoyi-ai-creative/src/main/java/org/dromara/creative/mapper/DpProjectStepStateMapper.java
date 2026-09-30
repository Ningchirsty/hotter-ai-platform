package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpProjectStepState;

/**
 * 项目步骤状态 Mapper（V0.2 D2）。
 *
 * <p><b>本表有一条纪律：删除必须物理删除。</b>表上有唯一键
 * {@code uk_dp_project_step(task_id, step_code)}，而 {@code del_flag} 是逻辑删除列——
 * 一旦留下 {@code del_flag='1'} 的软删行，这一步之后**任何**写入都会撞唯一键：
 * 重新跳过会 409，{@code moveStage} 的步骤状态同步也会 409，而 moveStage 是事务性的，
 * 会把整次阶段推进一起回滚。所以这里给两个写入点（跳过 / 阶段同步）提供物理删除，
 * 用来清掉历史软删行；历史由 {@code dp_stage_event} 承担。
 *
 * @author creative
 */
@Mapper
public interface DpProjectStepStateMapper extends BaseMapperPlus<DpProjectStepState, DpProjectStepState> {

    /**
     * 物理删除某一步的状态行（**含软删行**）。
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @return 删除行数（0 表示本来就没有这行）
     */
    @Delete("DELETE FROM dp_project_step_state WHERE task_id = #{taskId} AND step_code = #{stepCode}")
    int hardDelete(@Param("taskId") Long taskId, @Param("stepCode") String stepCode);
}
