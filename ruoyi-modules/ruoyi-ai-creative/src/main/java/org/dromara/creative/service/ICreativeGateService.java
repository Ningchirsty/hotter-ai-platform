package org.dromara.creative.service;

import java.util.List;

/**
 * 视觉门服务。
 *
 * <p><b>它管什么</b>：把「视觉方案是否可以进入生产」变成一道<b>后端强制</b>的门，
 * 而不是前端按钮的禁用状态。门的结果由两部分组成：</p>
 * <ol>
 *     <li><b>机器可判定的准入项</b>（基因是否锁定、参考图是否齐备、分镜是否锁定…）——
 *     不满足连提交都不允许，直接给出可读原因；</li>
 *     <li><b>人工确认</b>——通过后建一张审批卡（复用 cp_interaction_card），
 *     由人点「确认」或「打回」；只有人确认了才写 {@code VISUAL_GATE_PASS} 事件，
 *     出图才被放行。</li>
 * </ol>
 *
 * <p><b>为什么要留事件而不是只看阶段</b>：阶段会被后续动作推走（出图中、排版中…），
 * 单靠阶段无法回答「它到底过没过门」。因此以 {@code dp_stage_event} 里的
 * {@code VISUAL_GATE_PASS} 为权威凭据。</p>
 *
 * @author creative
 */
public interface ICreativeGateService {

    /**
     * 门禁准入项。
     *
     * @param code   编码
     * @param label  名称
     * @param level  BLOCK（硬性）/ CONDITION（建议）
     * @param passed 是否满足
     * @param detail 说明（满足也要说明「依据是什么」）
     */
    record GateItem(String code, String label, String level, boolean passed, String detail) {
    }

    /**
     * 门禁评估结果。
     *
     * @param items        准入项
     * @param blocked      未满足的硬性项
     * @param submittable  是否可提交人工确认（硬性项全满足）
     * @param passed       是否已通过（存在 VISUAL_GATE_PASS 事件）
     * @param cardStatus   审批卡状态（PENDING/RESOLVED/BLOCKED/CLOSED；无卡为 null）
     * @param cardId       审批卡ID（无卡为 null）
     * @param stage        当前视觉阶段
     * @param stageDesc    阶段描述
     */
    record GateEvaluation(List<GateItem> items, List<String> blocked, boolean submittable,
                          boolean passed, String cardStatus, Long cardId,
                          String stage, String stageDesc) {
    }

    /**
     * 评估门禁（只读，不建卡、不改状态）。
     *
     * @param taskId 项目ID
     * @return 评估结果
     */
    GateEvaluation evaluate(Long taskId);

    /**
     * 提交视觉门审核（硬性项未满足时拒绝并列出原因）。
     *
     * @param taskId 项目ID
     * @return 提交后的评估结果
     */
    GateEvaluation submit(Long taskId);

    /**
     * 处理视觉门（人确认或打回）。
     *
     * @param taskId  项目ID
     * @param option  CONFIRM（通过）/ BLOCK（打回）
     * @param comment 意见
     * @return 处理后的评估结果
     */
    GateEvaluation review(Long taskId, String option, String comment);

    /**
     * 是否已通过视觉门（出图放行的唯一凭据）。
     *
     * @param taskId 项目ID
     * @return 是否通过
     */
    boolean hasPassed(Long taskId);

    /**
     * 出图前的强制检查：未过门时抛业务异常，并说明缺什么、下一步该做什么。
     *
     * @param taskId 项目ID
     */
    void requireCanProduce(Long taskId);

}
