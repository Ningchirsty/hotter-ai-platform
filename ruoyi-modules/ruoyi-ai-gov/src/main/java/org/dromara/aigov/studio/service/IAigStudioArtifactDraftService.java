package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.bo.AigStudioActionDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioChildAgentDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioSkillDraftBo;
import org.dromara.aigov.studio.domain.vo.AigStudioActionDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioArtifactGenerateVo;
import org.dromara.aigov.studio.domain.vo.AigStudioChildAgentDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioSkillDraftVo;
import org.dromara.aigov.studio.enums.AigStudioArtifactKindEnum;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 草案生成器服务（附件 07 增量 7／ST-005/006/007）。
 *
 * <h3>它保证的核心不变式</h3>
 * <ol>
 *     <li><b>草案不改任何草稿</b>：创建草案只写草案表。只有 {@link #generate} 会写草稿，
 *         且它写的是"一条带 CAS 的新修订"（复用训练台唯一的写修订入口）；</li>
 *     <li><b>生成物必须可提交</b>：八分节齐全、类别与能力已校验——否则"可提交内容"
 *         只是一句自我感觉；</li>
 *     <li><b>预览与生成同源</b>：{@link #preview} 与 {@link #generate} 走同一段生成逻辑，
 *         不会出现"预览看的是 A、生成的是 B"；</li>
 *     <li><b>不静默丢字段</b>：草案里在草稿内容中没有槽位的项（父 Agent、启动方式与启动目标）
 *         以及留空待补的分节，都出现在 {@code notes} 里。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigStudioArtifactDraftService {

    /**
     * 新增技能草案（不改任何草稿）。
     *
     * @param bo      入参
     * @param actorId 操作用户ID
     * @return 草案ID
     */
    Long createSkillDraft(AigStudioSkillDraftBo bo, Long actorId);

    /**
     * 新增子 Agent 草案。
     *
     * @param bo      入参
     * @param actorId 操作用户ID
     * @return 草案ID
     */
    Long createChildAgentDraft(AigStudioChildAgentDraftBo bo, Long actorId);

    /**
     * 新增快捷指令草案。
     *
     * @param bo      入参
     * @param actorId 操作用户ID
     * @return 草案ID
     */
    Long createActionDraft(AigStudioActionDraftBo bo, Long actorId);

    /**
     * 技能草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigStudioSkillDraftVo> querySkillDraftPage(AigStudioArtifactDraftQueryBo bo,
                                                          PageQuery pageQuery);

    /**
     * 子 Agent 草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigStudioChildAgentDraftVo> queryChildAgentDraftPage(AigStudioArtifactDraftQueryBo bo,
                                                                    PageQuery pageQuery);

    /**
     * 快捷指令草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigStudioActionDraftVo> queryActionDraftPage(AigStudioArtifactDraftQueryBo bo,
                                                            PageQuery pageQuery);

    /**
     * 预览生成物（<b>不写任何表</b>）：看清这份草案会生成一份什么样的可提交内容。
     *
     * @param kind    草案类型
     * @param draftId 草案ID
     * @return 生成结果（不含目标草稿与修订）
     */
    AigStudioArtifactGenerateVo preview(AigStudioArtifactKindEnum kind, Long draftId);

    /**
     * 把生成物落成目标训练草稿的一条新修订（{@code source=DRAFT_GENERATED}）。
     *
     * @param kind             草案类型
     * @param draftId          草案ID
     * @param targetDraftId    目标训练草稿ID
     * @param expectedRevision 期望的目标草稿当前修订号（写草稿必须带 CAS）
     * @param actorId          操作用户ID（必须是目标草稿责任人）
     * @return 生成结果（含目标草稿与修订号）
     */
    AigStudioArtifactGenerateVo generate(AigStudioArtifactKindEnum kind, Long draftId, Long targetDraftId,
                                         Integer expectedRevision, Long actorId);

}
