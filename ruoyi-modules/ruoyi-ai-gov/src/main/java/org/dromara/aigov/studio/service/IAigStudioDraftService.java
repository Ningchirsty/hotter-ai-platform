package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;

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
     * 归档草稿（终态；归档后不可再编辑）。
     *
     * @param draftId 草稿ID
     * @param actorId 操作用户ID（必须是草稿责任人）
     */
    void archive(Long draftId, Long actorId);

}
