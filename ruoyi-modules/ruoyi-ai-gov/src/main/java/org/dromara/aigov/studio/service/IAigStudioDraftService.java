package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSubmitBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftSubmitVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioMetadataVo;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;
import org.dromara.aigov.studio.domain.vo.AigStudioValidateVo;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * Agent Studio 草稿读写服务（专题 C §C2.1、§C7；增量 S2）。
 *
 * <h3>本层只做"草稿读写"，不做测试、不碰发布</h3>
 * <p>沙箱测试与门槛（S5）刻意不在本层：草稿服务一旦能顺手调用模型，
 * "只是打开训练台"就可能变成一次计费调用。发布更不在本层——
 * 正式版本只能由 {@code /aigov/agent/release/advance} 那台状态机产生。</p>
 *
 * <h3>三条不变式（都在实现里被强制）</h3>
 * <ol>
 *     <li><b>内容没变就不产生新修订</b>：否则每次点保存都多一条历史，版本记录会被噪声淹掉；</li>
 *     <li><b>修订不可变</b>：回滚不修改历史，而是产生一条内容相同、来源为 {@code ROLLBACK} 的新修订；</li>
 *     <li><b>编辑必须带 CAS 版本</b>：{@code expectedRevision} 与库中不一致就报错，
 *         绝不静默覆盖别人的修改。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigStudioDraftService {

    /**
     * 创建草稿，并同时产生第 1 个修订（内容为空时由服务端填标准骨架）。
     *
     * @param bo      入参
     * @param actorId 操作用户ID（成为草稿责任人）
     * @return 草稿ID
     */
    Long createDraft(AigStudioDraftCreateBo bo, Long actorId);

    /**
     * 读取草稿详情（含"是否有未提交改动"的服务端判定）。
     *
     * @param draftId 草稿ID
     * @return 详情
     */
    AigStudioDraftDetailVo getDraft(Long draftId);

    /**
     * 保存草稿（CAS）：内容变化时产生新修订，内容未变时原样返回。
     *
     * @param bo      入参（必须带 {@code expectedRevision}）
     * @param actorId 操作用户ID（必须是草稿责任人）
     * @return 保存后的详情（{@code revisionCreated} 表示本次是否产生新修订）
     */
    AigStudioDraftDetailVo saveDraft(AigStudioDraftSaveBo bo, Long actorId);

    /**
     * 列出草稿的修订（按修订号倒序，最新在前）。
     *
     * @param draftId 草稿ID
     * @return 修订列表
     */
    List<AigStudioRevisionVo> listRevisions(Long draftId);

    /**
     * 读取某个修订的内容快照（用于 Diff 与回滚确认）。
     *
     * @param revisionId 修订ID
     * @return 修订
     */
    AigStudioRevisionVo getRevision(Long revisionId);

    /**
     * 回滚到某个历史修订：<b>不修改历史</b>，而是产生一条来源为 {@code ROLLBACK} 的新修订。
     *
     * @param draftId          草稿ID
     * @param targetRevisionNo 目标修订号（历史里的那一版）
     * @param expectedRevision 期望的当前修订号（CAS）
     * @param actorId          操作用户ID（必须是草稿责任人）
     * @return 回滚后的详情
     */
    AigStudioDraftDetailVo rollback(Long draftId, Integer targetRevisionNo, Integer expectedRevision, Long actorId);

    /**
     * 把一份已经算好的内容落成一条<b>新修订</b>（带 CAS），供"采纳变更提案"复用。
     *
     * <p><b>为什么由草稿服务提供这个入口</b>：写修订的三件事（内容没变就不产生修订、
     * 修订号 +1、CAS 冲突检测）只能有一处实现。提案服务自己写一遍，就会出现
     * "两条路径对同一个草稿的并发语义不一样"——那是最难查的一类不一致。</p>
     *
     * <p><b>它不产生"提案"语义</b>：这里只负责把内容落库成修订；
     * "这条修订来自被采纳的提案"由调用方以 {@code source=COPILOT_ACCEPTED} 声明。</p>
     *
     * @param draftId          草稿ID
     * @param expectedRevision 期望的当前修订号（CAS）
     * @param content          已应用完改动的内容（服务端会自行序列化 + 规范化 + 算哈希）
     * @param source           修订来源
     * @param summary          修订说明
     * @param actorId          操作用户ID（必须是草稿责任人）
     * @return 落库后的草稿详情（{@code revisionCreated} 表示是否真的产生了新修订）
     */
    AigStudioDraftDetailVo applyRevision(Long draftId, Integer expectedRevision, AigStudioDraftContent content,
                                         AigStudioRevisionSourceEnum source, String summary, Long actorId);

    /**
     * 归档草稿（终态；归档后不可再编辑）。
     *
     * @param draftId 草稿ID
     * @param actorId 操作用户ID（必须是草稿责任人）
     */
    void archive(Long draftId, Long actorId);

    /**
     * 分页查询草稿清单（服务层填充状态描述与"是否有未提交改动"）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigStudioDraftVo> queryPage(AigStudioDraftQueryBo bo, PageQuery pageQuery);

    /**
     * 预检草稿当前内容（专题 C §C7 "本地静态校验"）：<b>只读校验，不产生版本、不发布</b>。
     *
     * <p>一次把所有问题列全，而不是抛第一个错——预检是给正在编辑的人看的，
     * 他需要知道哪里不合格才能改。</p>
     *
     * @param draftId 草稿ID
     * @return 预检结论
     */
    AigStudioValidateVo validateDraft(Long draftId);

    /**
     * 训练台元数据（设计 §9.2 {@code GET /aigov/studio/metadata}）：类别与 Prompt 分节的
     * <b>服务端唯一词表来源</b>，供前端渲染，避免前端各自枚举导致漂移。
     *
     * @return 元数据
     */
    AigStudioMetadataVo metadata();

    /**
     * 提交草稿：把当前内容固化成一条 <b>DRAFT</b> 的 Agent 版本（专题 C §C7 的"提交"）。
     *
     * <p><b>它只做到 DRAFT 为止</b>：不推进发布状态、不跑沙箱、不做审批。后续的
     * 沙箱/黄金用例/人工批准/灰度仍由既有 {@code /aigov/agent/release/advance} 状态机走——
     * 训练台提供第二条写状态通道的话，五道门槛就形同虚设。</p>
     *
     * <p><b>提交前必须先过预检</b>：内容不合格就直接拒绝并列出问题，
     * 不允许"先提交、后面再补"（那会让一份不合格内容进入发布流程）。</p>
     *
     * @param draftId 草稿ID
     * @param bo      入参（版本号可空，为空时由服务端生成）
     * @param actorId 操作用户ID（必须是草稿责任人）
     * @return 提交结果（含新建/复用的 Agent 与版本号）
     */
    AigStudioDraftSubmitVo submitDraft(Long draftId, AigStudioDraftSubmitBo bo, Long actorId);

}
