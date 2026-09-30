package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Insert;
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

    /**
     * 读取交付类型（V0.2 D2：步骤状态同步与投影都要按场景选步骤配置）。
     *
     * @param taskId 项目ID
     * @return 交付类型编码；不存在返回 null
     */
    @Select("SELECT deliverable_type FROM cp_task WHERE task_id = #{taskId}")
    String selectDeliverableType(@Param("taskId") Long taskId);

    /**
     * 读取项目是否被删除（V0.2 R22 模块规划：已删除的项目不该还能规划模块）。
     *
     * <p><b>为什么不用 Map 一次查回多列</b>：R22 第一版写了
     * {@code SELECT ... deliverable_type AS deliveryType ...} 返回 Map，运行时
     * {@code meta.get("deliveryType")} 取到 null，{@code String.valueOf(null)} 变成字符串 "null"，
     * 于是模块库按 {@code delivery_type='null'} 去查，**静默返回 0 条**——真机验收当场抓到
     * （模块库 0 条、保存报「模块编码不在模块库里」）。教训：ResultSet→Map 的列名/别名不值得赌，
     * 标量列各查各的，类型交给方法签名保证。</p>
     *
     * @param taskId 项目ID
     * @return '0' 存在 / '1' 已删除；项目不存在返回 null
     */
    @Select("SELECT del_flag FROM cp_task WHERE task_id = #{taskId}")
    String selectDelFlag(@Param("taskId") Long taskId);

    /**
     * 已软删的项目清单（V0.2 R26：批量清理已删项目素材用）。
     *
     * <p>返回**具体 VO**而不是 Map：R22 踩过"Map 取列取到 null → 字符串 null"的坑，
     * 列名交给映射器解析，字段名由类型保证。</p>
     *
     * @return 已删除项目（按删除时间倒序）
     */
    @Select("SELECT task_id AS taskId, task_name AS taskName, deliverable_type AS deliverableType, "
        + "update_time AS updateTime FROM cp_task WHERE del_flag = '1' ORDER BY update_time DESC")
    List<org.dromara.creative.domain.vo.DeletedTaskVo> selectDeletedTasks();

    /**
     * 写一条视觉阶段事件（V0.2 R28：模块计划确认这类"人的动作"要留痕）。
     *
     * <p>为什么不复用 {@code moveStage}：那不是阶段变更；写阶段机会污染阶段历史
     * （from=to=当前阶段），所以这里只落事件行。</p>
     *
     * @param taskId     项目ID
     * @param eventType  事件类型
     * @param fromStage  原阶段（可为 null）
     * @param toStage    目标阶段（可为 null）
     * @param action     动作编码
     * @param detailJson 明细 JSON
     * @return 影响行数
     */
    @Insert("INSERT INTO dp_stage_event (task_id, event_type, from_stage, to_stage, action, detail_json, "
        + "actor_id, actor_name, create_time) VALUES (#{taskId}, #{eventType}, #{fromStage}, #{toStage}, "
        + "#{action}, #{detailJson}, "
        + "(SELECT user_id FROM sys_user WHERE user_name = CURRENT_USER()), "
        + "(SELECT nick_name FROM sys_user WHERE user_name = CURRENT_USER()), NOW())")
    int insertEvent(@Param("taskId") Long taskId, @Param("eventType") String eventType,
                    @Param("fromStage") String fromStage, @Param("toStage") String toStage,
                    @Param("action") String action, @Param("detailJson") String detailJson);

}
