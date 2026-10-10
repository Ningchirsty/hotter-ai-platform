package org.dromara.aigov.workspace.portal.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalArtifactVo;

/**
 * 门户"我的产物"只读查询（主文档线增量 6）。
 *
 * <p><b>为什么单独一个 Mapper</b>：产物台账 {@code aig_task_artifact} 自己不带"归属"——
 * 它的 {@code create_by} 是**生产方**（执行器/调度器），不是员工。真正决定"这份产物是谁的"是
 * 任务的 {@code create_by}，所以必须与 {@code aig_task} 联表。而
 * {@code BaseMapperPlus<实体,实体>} 承担不了联表 VO，本接口只做这一件事（与
 * {@code AigModelViewMapper} 同一分工）。</p>
 *
 * <p><b>只读</b>：只有 select，不写任何表。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigPortalArtifactMapper {

    /**
     * 分页查询"我的产物"。
     *
     * <p>口径写死在 SQL 里（不交给调用方）：只能查**自己提交的任务**下、**通过校验**的产物。
     * 参数里没有 userId，userId 由服务层从登录态取——调用方无法指定"查谁的"。</p>
     *
     * @param page   分页对象
     * @param userId 任务提交人（登录态）
     * @param taskId 任务ID（可空：只查某个任务的产物）
     * @return 分页结果
     */
    IPage<AigPortalArtifactVo> selectMyArtifactPage(IPage<AigPortalArtifactVo> page,
                                                    @Param("userId") Long userId,
                                                    @Param("taskId") Long taskId);

}
