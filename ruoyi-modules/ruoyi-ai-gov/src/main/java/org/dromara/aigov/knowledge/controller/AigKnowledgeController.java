package org.dromara.aigov.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.vo.AigAgentResourceReferenceVo;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeCollectionBo;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeDocumentBo;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeRetrievalTestBo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeChunkVo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeDocumentRegisterVo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeRetrievalTestVo;
import org.dromara.aigov.knowledge.retrieval.KnowledgePort;
import org.dromara.aigov.knowledge.service.IAigKnowledgeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 知识中心接口（设计 §6.4、§9.2）。
 *
 * <h3>四个动作、四种权限</h3>
 * <ul>
 *     <li>{@code collection:list} / {@code collection:create}：看集合 vs 建集合；</li>
 *     <li>{@code document:list}：看文档与版本详情（含处理状态与失败阶段）；</li>
 *     <li>{@code document:upload}：登记文档与版本（不改"能不能被检索"）；</li>
 *     <li>{@code document:manage}：推进/失败/退役处理（直接改变"这份资料能不能被检索"）；</li>
 *     <li>{@code retrieval:test}：只检索不生成的验证入口。</li>
 * </ul>
 *
 * <p>上传与处理分开授权：把"登记一份资料"与"让它进入检索"绑在同一个权限上，
 * 会让"只想上传待审"的人顺手把未审资料推成可检索。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/knowledge")
public class AigKnowledgeController extends BaseController {

    private final IAigKnowledgeService knowledgeService;

    private final KnowledgePort knowledgePort;

    /**
     * 知识集合清单。
     *
     * @return 集合清单
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_COLLECTION_LIST)
    @GetMapping("/collections")
    public R<List<AigKnowledgeCollection>> collections() {
        return R.ok(knowledgeService.listCollections());
    }

    /**
     * 新建知识集合。
     *
     * @param bo 入参
     * @return 集合ID
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_COLLECTION_CREATE)
    @RepeatSubmit
    @PostMapping("/collections")
    public R<Long> createCollection(@RequestBody @Validated AigKnowledgeCollectionBo bo) {
        return R.ok(knowledgeService.createCollection(bo, LoginHelper.getUserId()));
    }

    /**
     * 知识文档清单（可按集合过滤）。
     *
     * @param collectionId 集合ID（可空）
     * @return 文档清单
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_LIST)
    @GetMapping("/documents")
    public R<List<AigKnowledgeDocument>> documents(@RequestParam(required = false) Long collectionId) {
        return R.ok(knowledgeService.listDocuments(collectionId));
    }

    /**
     * 登记知识文档与版本（处理管道从 UPLOADED 开始）。
     *
     * @param bo 入参
     * @return 登记结果（含重复文件提示）
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_UPLOAD)
    @RepeatSubmit
    @PostMapping("/documents")
    public R<AigKnowledgeDocumentRegisterVo> registerDocument(
        @RequestBody @Validated AigKnowledgeDocumentBo bo) {
        return R.ok(knowledgeService.registerDocument(bo, LoginHelper.getUserId()));
    }

    /**
     * 取文档版本详情（来源、处理状态、失败阶段）。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_LIST)
    @GetMapping("/document-versions/{versionId:\\d+}")
    public R<AigKnowledgeDocumentVersion> documentVersion(
        @NotNull(message = "版本ID不能为空") @PathVariable Long versionId) {
        return R.ok(knowledgeService.getVersion(versionId));
    }

    /**
     * 推进一个处理阶段（幂等：同一幂等键重复调用只推进一次）。
     *
     * @param versionId      版本ID
     * @param idempotencyKey 幂等键
     * @return 推进后的状态
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_MANAGE)
    @PostMapping("/document-versions/{versionId:\\d+}/advance")
    public R<String> advance(
        @NotNull(message = "版本ID不能为空") @PathVariable Long versionId,
        @RequestParam String idempotencyKey) {
        return R.ok(knowledgeService.advanceProcessing(versionId, idempotencyKey).getCode());
    }

    /**
     * 记录一次处理失败（写清失败阶段与原因）。
     *
     * @param versionId      版本ID
     * @param idempotencyKey 幂等键（可空）
     * @param errorCode      失败分类（可空）
     * @param errorMessage   可读原因（可空）
     * @return 失败后的状态
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_MANAGE)
    @PostMapping("/document-versions/{versionId:\\d+}/fail")
    public R<String> fail(
        @NotNull(message = "版本ID不能为空") @PathVariable Long versionId,
        @RequestParam(required = false) String idempotencyKey,
        @RequestParam(required = false) String errorCode,
        @RequestParam(required = false) String errorMessage) {
        return R.ok(knowledgeService.failProcessing(versionId, idempotencyKey, errorCode, errorMessage)
            .getCode());
    }

    /**
     * 退役一个版本（不再被新任务检索，历史结果仍可追溯）。
     *
     * @param versionId 版本ID
     * @return 退役后的状态
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_MANAGE)
    @PostMapping("/document-versions/{versionId:\\d+}/retire")
    public R<String> retire(
        @NotNull(message = "版本ID不能为空") @PathVariable Long versionId) {
        return R.ok(knowledgeService.retire(versionId).getCode());
    }

    /**
     * 「谁在引用这个知识集合」（设计 §6.4「引用该版本的 Agent」）。
     *
     * <p>退役之前先看影响面：只给一串绑定 ID，运维没法判断"停了会影响谁"，
     * 退役就变成"先停掉再看谁来报错"。</p>
     *
     * <p><b>权限沿用 {@code aig:knowledge:document:list}</b>：返回的是"哪个 Agent 引用了这个集合"
     * 这类元数据（Agent 编码/名称/版本/范围），不含任何资料正文；能看文档清单的人就该能看影响面。
     * 停用中的绑定也一并返回（如实标注），否则会被读成"没人用过"。</p>
     *
     * @param collectionId 集合ID
     * @return 引用清单
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_DOCUMENT_LIST)
    @GetMapping("/collections/{collectionId:\\d+}/references")
    public R<List<AigAgentResourceReferenceVo>> references(
        @NotNull(message = "集合ID不能为空") @PathVariable Long collectionId) {
        return R.ok(knowledgeService.listReferencingAgents(collectionId));
    }

    /**
     * 列出某版本的分段预览（设计 §6.4：「解析预览、分段」）。
     *
     * <p><b>权限沿用 {@code aig:knowledge:retrieval:test}</b>：本接口给的是
     * {@code text_excerpt}（受限长度预览），与"检索测试返回的引用预览"是同一类东西——
     * 能跑检索测试的人本来就看得到命中片段的预览。**正文仍在受控存储引用里**，不经此接口暴露；
     * 再单独开一个"只有管理员能勾"的权限点，只会让"谁能看预览"更难回答。</p>
     *
     * @param versionId 版本ID
     * @param limit     最多返回几条（可空=默认 50，最多 200）
     * @return 分段预览（按段序升序）
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_RETRIEVAL_TEST)
    @GetMapping("/document-versions/{versionId:\\d+}/chunks")
    public R<List<AigKnowledgeChunkVo>> chunks(
        @NotNull(message = "版本ID不能为空") @PathVariable Long versionId,
        @RequestParam(required = false) Integer limit) {
        return R.ok(knowledgeService.listChunks(versionId, limit));
    }

    /**
     * 知识检索测试（只检索、不生成回答）。
     *
     * <p>把命中的片段与来源如实返回；没有命中时回 {@code noSupport=true} 与原因，
     * 让人能区分"知识没检索到"与"模型答不好"。</p>
     *
     * @param bo 入参
     * @return 检索结果
     */
    @SaCheckPermission(AigConstants.PERM_KNOWLEDGE_RETRIEVAL_TEST)
    @PostMapping("/retrieval-tests")
    public R<AigKnowledgeRetrievalTestVo> retrievalTest(
        @RequestBody @Validated AigKnowledgeRetrievalTestBo bo) {
        KnowledgePort.RetrievalResult result = knowledgePort.retrieve(new KnowledgePort.RetrievalQuery(
            bo.getQuery(),
            bo.getVersionIds() == null ? Set.of() : Set.copyOf(bo.getVersionIds()),
            bo.getCollectionCodes() == null ? Set.of() : Set.copyOf(bo.getCollectionCodes()),
            // 维护测试入口由权限点管控，按显式选择的集合检索；正式执行路径才带身份
            bo.getMaxChunks(), null));
        AigKnowledgeRetrievalTestVo vo = new AigKnowledgeRetrievalTestVo();
        vo.setNoSupport(result.noSupport());
        vo.setReason(result.reason());
        for (KnowledgePort.Citation citation : result.citations()) {
            AigKnowledgeRetrievalTestVo.Item item = new AigKnowledgeRetrievalTestVo.Item();
            item.setChunkId(citation.chunkId());
            item.setVersionId(citation.versionId());
            item.setDocumentId(citation.documentId());
            item.setSourceName(citation.sourceName());
            item.setLocator(citation.locator());
            item.setExcerptRef(citation.excerptRef());
            item.setDataLevel(citation.dataLevel());
            vo.getCitations().add(item);
        }
        return R.ok(vo);
    }

}
