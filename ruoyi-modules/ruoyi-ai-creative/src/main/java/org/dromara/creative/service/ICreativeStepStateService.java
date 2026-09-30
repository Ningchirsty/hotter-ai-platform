package org.dromara.creative.service;

import org.dromara.creative.domain.vo.ProjectStepStateVo;

import java.util.List;

/**
 * 步骤状态的人为动作（V0.2 R36：只做「跳过 / 取消跳过」）。
 *
 * <p><b>为什么单独一个服务</b>：步骤状态此前只有两条写入路径——阶段推进（{@code moveStage} 经
 * {@code CreativeStepStateWriter}）与"没落库就按阶段推导"。而"跳过"既不是阶段推进、
 * 也不是投影能算出来的（它是人的决定），塞进上面任一条都会把那个类的语义搞混。</p>
 *
 * <p><b>SKIPPED 的含义（R36 定义）</b>：人显式跳过了一个**可选且无闸门**的步骤。
 * 它不是"没走到"（PENDING），也不是"做完了"（DONE）：</p>
 * <ul>
 *   <li>必填步骤（配置 {@code required='1'}）**不允许跳过**——那是流程要求，不是可选项；</li>
 *   <li>有闸门的步骤**不允许跳过**——跳过等于绕过门禁（闸门是这套系统的硬边界）；</li>
 *   <li>已经做完的步骤（投影为 DONE）不允许"跳过"——那会把"已完成"说成"跳过"；</li>
 *   <li>必须给原因（留痕），写进 {@code dp_project_step_state.remark} 并写一条
 *       {@code STEP_SKIPPED} 阶段事件（谁、何时、为什么，操作日志里查得到）；</li>
 *   <li>可以取消：删掉该行后回到"按阶段投影"，并写 {@code STEP_SKIP_CANCELLED} 事件。</li>
 * </ul>
 *
 * <p><b>与进度口径的关系</b>：跳过的步骤从进度分母里去掉（不算完成、也不永远压着进度），
 * 判据在 {@code CreativeStepProjection#progress}。</p>
 *
 * <p><b>不改变闸门判定</b>：任何闸门都不看步骤状态（它看阶段与事实），本轮也没有动闸门配置。</p>
 *
 * @author creative
 */
public interface ICreativeStepStateService {

    /**
     * 跳过某一步（可选且无闸门时才允许）。
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @param reason   跳过原因（必填，2~200 字）
     * @return 更新后的步骤状态列表
     */
    List<ProjectStepStateVo> skip(Long taskId, String stepCode, String reason);

    /**
     * 取消跳过（回到按阶段投影的状态）。
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @return 更新后的步骤状态列表
     */
    List<ProjectStepStateVo> cancelSkip(Long taskId, String stepCode);
}
