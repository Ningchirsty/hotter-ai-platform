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

    // ------------------------------------------------------------------
    // 上传资料并识别（v1 人工测试反馈 详情页与审核 1.3）
    //
    // 原文：「闸门结论版块**除了手动录入信息之外还应该有上传信息自动识别录入的功能**」；
    // 裁定：「在闸门里做上传+识别」。
    //
    // <b>为什么复用内容侧那条链，而不是在视觉门里另写一套识别</b>：
    //   内容任务页本来就有「上传资料 → 触发解析 → 解析结果**以待确认落库** → 人工确认」，
    //   事实行还带溯源（source_file_id / source_locator / source_excerpt / confidence）。
    //   视觉门这一侧加的是**入口与状态**：能上传、能触发、能看见"识别到几条待确认、去哪确认"；
    //   识别本身仍由内容侧的文档解析能力做——两套识别一定会给出两个答案。
    //
    // <b>权限口径</b>：用 `creative:project:upload`（设计师本来就有，语义就是"给这个项目补资料"），
    //   而不是内容侧的 `content:task:edit`——后者是品牌侧写权限，
    //   而内测里"同一账号同时拥有品牌侧与设计侧全部权限"本身就是被反馈过的问题（S7）。
    // ------------------------------------------------------------------

    /**
     * 视觉门里的一份资料（上传 + 识别状态）。
     *
     * @param fileId          附件ID
     * @param fileName        文件名
     * @param fileExt         扩展名
     * @param fileSize        字节数
     * @param parseStatus     解析状态码（PENDING/PARSING/DONE/FAILED/SKIPPED）
     * @param parseStatusDesc 解析状态中文
     * @param parseMessage    解析失败/跳过的原因（成功时为 null）
     * @param createTime      上传时间
     */
    record GateMaterial(Long fileId, String fileName, String fileExt, Long fileSize,
                        String parseStatus, String parseStatusDesc, String parseMessage,
                        java.time.LocalDateTime createTime) {
    }

    /**
     * 视觉门「上传资料并识别」这一块的现状。
     *
     * @param files          已上传的资料（含各自的解析状态）
     * @param pendingFacts   识别出来但**还没人工确认**的事实条数（人要去确认的就是这些）
     * @param confirmedFacts 已确认的事实条数（会进开工包）
     * @param parseDoneAt    最近一次解析完成时间（空＝还没跑过解析）
     */
    record GateMaterials(java.util.List<GateMaterial> files, long pendingFacts, long confirmedFacts,
                         java.time.LocalDateTime parseDoneAt) {
    }

    /**
     * 读取「上传资料并识别」这一块的现状。
     *
     * @param taskId 项目ID
     * @return 现状（资料列表 + 待确认/已确认条数 + 最近解析时间）
     */
    GateMaterials materials(Long taskId);

    /**
     * 上传一份资料（供识别用）。
     *
     * <p>落到内容侧同一张附件表（`cp_task_file`，来源标 {@code UPLOAD}），解析状态从
     * {@code PENDING} 开始——**识别要人点「识别」才跑**，免得传一个文件就自动触发一次模型调用。</p>
     *
     * @param taskId 项目ID
     * @param file   文件（文档或图片，≤ 20MB）
     * @return 刚上传的那一份（含初始解析状态）
     */
    GateMaterial uploadMaterial(Long taskId, org.springframework.web.multipart.MultipartFile file);

    /**
     * 触发识别（异步，走内容侧的文档解析链路）。
     *
     * @param taskId 项目ID
     * @return 异步作业ID
     */
    Long triggerMaterialParse(Long taskId);

}
