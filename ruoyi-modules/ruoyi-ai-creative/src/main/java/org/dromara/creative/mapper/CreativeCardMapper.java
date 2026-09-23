package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 互动确认卡（cp_interaction_card）的最小读写。
 *
 * <p><b>为什么视觉工厂直接写这张表</b>：视觉门的人工确认项要落在「人确认」这件事上，
 * 而全平台承载它的就是 cp_interaction_card（内容模块的冲突/缺料/例外卡都用它，
 * 成品不一致时也会自动建卡）。内容模块只暴露了「查询 + 处理」两个方法，没有「建卡」入口，
 * 因此这里用最小 SQL 建卡——<b>与 {@code cp_task.visual_stage} 同一做法：只碰必需的列，
 * 不复制实体、不改对方代码</b>。处理动作仍然调用内容模块的
 * {@code IContentCardService.resolve}，以复用它的闸门重算逻辑。</p>
 *
 * <p>字段语义按内容模块既有约定：{@code gate_level=BLOCK} 且 {@code blocking=N} 表示
 * 「需要人确认，但在人明确选择『阻断』之前不影响任务状态」——视觉门正处于这个语义。</p>
 *
 * @author creative
 */
@Mapper
public interface CreativeCardMapper {

    /**
     * 建一张审批卡（幂等由调用方保证：先查再建）。
     *
     * @param card 卡片字段（键与列一一对应）
     * @return 影响行数
     */
    @Insert("""
        INSERT INTO cp_interaction_card
          (card_id, task_id, card_type, field_code, title, question, evidence_json, impact_json,
           options_json, gate_level, blocking, assignee_id, assignee_name, due_at, status,
           create_dept, create_by, create_time, del_flag)
        VALUES
          (#{cardId}, #{taskId}, 'APPROVAL', #{fieldCode}, #{title}, #{question}, #{evidenceJson},
           #{impactJson}, #{optionsJson}, 'BLOCK', 'N', #{assigneeId}, #{assigneeName}, #{dueAt},
           'PENDING', #{deptId}, #{userId}, NOW(), '0')
        """)
    int insertApprovalCard(Map<String, Object> card);

    /**
     * 查该任务下某字段编码最新的未处理卡。
     *
     * @param taskId    项目ID
     * @param fieldCode 字段编码（视觉门固定为 visual_gate）
     * @return 卡片ID；没有则 null
     */
    @Select("""
        SELECT card_id FROM cp_interaction_card
         WHERE task_id = #{taskId} AND field_code = #{fieldCode} AND status = 'PENDING' AND del_flag = '0'
         ORDER BY card_id DESC LIMIT 1
        """)
    Long selectPendingCardId(@Param("taskId") Long taskId, @Param("fieldCode") String fieldCode);

    /**
     * 查该任务下某字段编码最新一张卡的状态（不限状态）。
     *
     * @param taskId    项目ID
     * @param fieldCode 字段编码
     * @return 状态（PENDING/RESOLVED/BLOCKED/CLOSED）；没有则 null
     */
    @Select("""
        SELECT status FROM cp_interaction_card
         WHERE task_id = #{taskId} AND field_code = #{fieldCode} AND del_flag = '0'
         ORDER BY card_id DESC LIMIT 1
        """)
    String selectLatestCardStatus(@Param("taskId") Long taskId, @Param("fieldCode") String fieldCode);

    /**
     * 处理审批卡（人工确认或打回）。
     *
     * <p><b>为什么不复用内容模块的 {@code IContentCardService.resolve}</b>：它的
     * {@code CONFIRM} 语义是「采用某个<b>候选事实值</b>」，会去 {@code cp_fact_snapshot}
     * 里找该字段的候选行——审批卡没有候选值，必然报「该字段已无候选值」。
     * 强行借道只会把审批记成「补充资料」，让卡片历史变得不可读。</p>
     *
     * <p>因此这里只做卡片自身的状态流转（与内容模块同一张表、同一套状态枚举），
     * 而<b>闸门重算是复用的</b>（打回后调内容模块的 recheckAndApply，让内容任务状态同步为待确认）。</p>
     *
     * @param cardId  卡片ID
     * @param status  目标状态（RESOLVED 通过 / BLOCKED 打回）
     * @param option  处理选项（CONFIRM / BLOCK，写入 resolved_option 便于回溯）
     * @param comment 意见
     * @param userId  处理人
     * @return 影响行数（0 表示卡已被别人处理过）
     */
    @Update("""
        UPDATE cp_interaction_card
           SET status = #{status}, resolved_option = #{option}, resolved_by = #{userId},
               resolved_at = NOW(), remark = #{comment}, update_by = #{userId}, update_time = NOW()
         WHERE card_id = #{cardId} AND status = 'PENDING' AND del_flag = '0'
        """)
    int resolveApprovalCard(@Param("cardId") Long cardId,
                            @Param("status") String status,
                            @Param("option") String option,
                            @Param("comment") String comment,
                            @Param("userId") Long userId);

}
