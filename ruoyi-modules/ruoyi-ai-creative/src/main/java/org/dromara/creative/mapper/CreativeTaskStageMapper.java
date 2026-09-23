package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 视觉阶段读写（{@code cp_task.visual_stage}）。
 *
 * <p><b>为什么用最小的专表 Mapper，而不改内容模块的实体</b>：内容模块的
 * {@code CpTask} 实体不认识 {@code visual_stage}，而 MyBatis-Plus 的
 * {@code updateById} 默认忽略 null 字段——所以内容模块的编辑动作不会把这一列覆盖掉，
 * 两边可以安全共存。反过来，如果让视觉工厂直接复用内容模块的全量更新，
 * 就会把内容模块不该管的列一起写进去，风险更大。</p>
 *
 * <p>写入只发生在 {@code CreativeProjectService.moveStage} 一处，
 * 且必须同时落一条 {@code dp_stage_event}。</p>
 *
 * @author creative
 */
@Mapper
public interface CreativeTaskStageMapper {

    /**
     * 读取单个项目的视觉阶段。
     *
     * @param taskId 项目ID（cp_task.task_id）
     * @return 含 taskId / visualStage 的行；项目不存在返回 null
     */
    @Select("SELECT task_id AS taskId, visual_stage AS visualStage FROM cp_task WHERE task_id = #{taskId}")
    Map<String, Object> selectStage(@Param("taskId") Long taskId);

    /**
     * 批量读取视觉阶段（列表页用）。
     *
     * <p>刻意不写 {@code foreach} 批查：MyBatis 的注解 SQL 只有在字符串<b>以</b>
     * {@code <script>} 开头时才按 XML 解析，写成文本块会踩到前导换行导致
     * 「SQL 里混进 script 标签」的坑；而列表页一屏最多几十条，逐条走主键查询（亚毫秒级）
     * 换来的是「不可能解析错」。真的出现 N+1 瓶颈时，再改成 XML 映射文件。</p>
     *
     * @param taskIds 项目ID集合
     * @return 每行含 taskId / visualStage（顺序与入参一致，缺失项跳过）
     */
    default List<Map<String, Object>> selectStages(Collection<Long> taskIds) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        if (taskIds == null) {
            return rows;
        }
        for (Long taskId : taskIds) {
            if (taskId == null) {
                continue;
            }
            Map<String, Object> row = selectStage(taskId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 更新视觉阶段。
     *
     * @param taskId 项目ID
     * @param stage  目标阶段编码
     * @return 影响行数
     */
    @Update("UPDATE cp_task SET visual_stage = #{stage}, update_time = NOW() WHERE task_id = #{taskId}")
    int updateStage(@Param("taskId") Long taskId, @Param("stage") String stage);

    /**
     * 读取项目名称（生产中心列表需要展示「属于哪个项目」）。
     *
     * @param taskId 项目ID
     * @return 项目名称；不存在返回 null
     */
    @Select("SELECT task_name FROM cp_task WHERE task_id = #{taskId}")
    String selectTaskName(@Param("taskId") Long taskId);

}
