package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.studio.domain.AigStudioChangeProposal;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalDecideBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalOperationBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalSourceEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.helper.AigStudioProposalPlanner;
import org.dromara.aigov.studio.mapper.AigStudioChangeProposalMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练草稿变更提案实现（增量 21／附件 07 增量 6）。
 *
 * <h3>三处刻意的控制</h3>
 * <ol>
 *     <li><b>创建提案不碰草稿</b>：只有 {@code aig_studio_change_proposal} 多一行。
 *         这样"Copilot 提议"永远不可能变成"Copilot 改了"；</li>
 *     <li><b>采纳时重新算一遍并比对哈希</b>：不信任提案里存的 {@code contentHashAfter}，
 *         而是从<b>当前</b>草稿内容重放操作、重算哈希，与提案存的比对。
 *         不一致说明提案提出后内容漂移了——拒绝，而不是"按老提案覆盖新内容"；</li>
 *     <li><b>写修订走草稿服务</b>：CAS + 修订号 + "内容没变不产生修订"只有一处实现
 *         （{@link IAigStudioDraftService#applyRevision}），提案服务不自己写一份。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigStudioProposalServiceImpl implements IAigStudioProposalService {

    private final AigStudioChangeProposalMapper proposalMapper;
    private final IAigStudioDraftService draftService;
    private final JsonMapper jsonMapper;

    @Override
    public void validateForCreate(Long draftId, AigStudioProposalCreateBo bo, Long actorId) {
        prepare(draftId, bo, actorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioChangeProposalVo create(Long draftId, AigStudioProposalCreateBo bo, Long actorId) {
        Prepared prepared = prepare(draftId, bo, actorId);

        AigStudioChangeProposal entity = new AigStudioChangeProposal();
        entity.setDraftId(draftId);
        entity.setBaseRevision(bo.getBaseRevision());
        entity.setSource(prepared.source().getCode());
        entity.setOperationsJson(writeJson(normalizedOperations(prepared.operations())));
        entity.setPreviewJson(writeJson(prepared.diff()));
        entity.setContentHashBefore(prepared.draft().getContentHash());
        entity.setContentHashAfter(prepared.afterHash());
        entity.setStatus(AigStudioProposalStatusEnum.PENDING.getCode());
        entity.setSummary(StringUtils.substring(bo.getSummary(), 0, 200));
        proposalMapper.insert(entity);
        log.info("已登记变更提案, proposalId={}, draftId={}, baseRevision={}, 改动数={}",
            entity.getProposalId(), draftId, bo.getBaseRevision(), prepared.diff().size());
        return toVo(proposalMapper.selectById(entity.getProposalId()));
    }

    /**
     * 创建提案前的全部校验与规划（<b>不落库</b>）。
     *
     * <p>被两处调用：{@link #create}（校验通过后落库）与 {@link #validateForCreate}
     * （只校验、不落库，供对话式 Copilot 在<b>调 create 之前</b>判定"模型越界"）。
     * 两处共用同一份实现，避免"校验过了、建的时候又说不行"的两套规则漂移。</p>
     *
     * @param draftId 草稿ID
     * @param bo      入参
     * @param actorId 操作人
     * @return 落库所需的全部已算好的东西
     */
    private Prepared prepare(Long draftId, AigStudioProposalCreateBo bo, Long actorId) {
        if (bo == null) {
            throw new ServiceException("提案入参不能为空");
        }
        if (bo.getBaseRevision() == null) {
            throw new ServiceException("提案必须声明基于哪个修订（baseRevision）");
        }
        AigStudioProposalSourceEnum source = AigStudioProposalSourceEnum.findOrDefault(bo.getSource());
        if (source == null) {
            throw new ServiceException("提案来源非法：" + bo.getSource());
        }

        AigStudioDraftDetailVo draft = draftService.getDraft(draftId);
        requireOwner(draft, actorId);
        requireEditable(draft);
        if (!bo.getBaseRevision().equals(draft.getLatestRevision())) {
            throw new ServiceException("提案基于的修订已不是当前修订：baseRevision=" + bo.getBaseRevision()
                + "，当前=" + draft.getLatestRevision() + "。请基于最新内容重新提案。");
        }

        AigStudioDraftContent content = parseContent(draft.getContentJson());
        List<AigStudioProposalPlanner.Operation> operations = toOperations(bo.getOperations());
        List<AigStudioProposalPlanner.DiffItem> diff = plan(content, operations);
        String afterHash = hashOf(content);
        if (afterHash.equals(draft.getContentHash())) {
            throw new ServiceException("提案没有产生任何内容变化：所给的值与当前内容一致");
        }
        return new Prepared(draft, source, operations, diff, afterHash);
    }

    /**
     * {@link #prepare} 的产出（落库只差这一步写）。
     *
     * @param draft     草稿详情
     * @param source    提案来源
     * @param operations 规范化前的操作清单
     * @param diff      改动预览
     * @param afterHash 改动后的内容哈希
     */
    private record Prepared(AigStudioDraftDetailVo draft, AigStudioProposalSourceEnum source,
                            List<AigStudioProposalPlanner.Operation> operations,
                            List<AigStudioProposalPlanner.DiffItem> diff, String afterHash) {
    }

    @Override
    public List<AigStudioChangeProposalVo> list(Long draftId) {
        // 先校验草稿存在（不存在的草稿返回空列表会让人以为"没有提案"，而不是"草稿ID错了"）
        draftService.getDraft(draftId);
        return proposalMapper.selectList(Wrappers.<AigStudioChangeProposal>lambdaQuery()
                .eq(AigStudioChangeProposal::getDraftId, draftId)
                .orderByDesc(AigStudioChangeProposal::getCreateTime)
                .orderByDesc(AigStudioChangeProposal::getProposalId))
            .stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioDraftDetailVo apply(Long proposalId, AigStudioProposalDecideBo bo, Long actorId) {
        if (bo == null || bo.getExpectedRevision() == null) {
            throw new ServiceException("采纳提案必须带期望修订号（expectedRevision）");
        }
        AigStudioChangeProposal proposal = requirePending(proposalId);
        AigStudioDraftDetailVo draft = draftService.getDraft(proposal.getDraftId());
        requireOwner(draft, actorId);
        requireEditable(draft);
        if (!proposal.getBaseRevision().equals(draft.getLatestRevision())) {
            throw new ServiceException("提案基于旧修订，已失效：baseRevision=" + proposal.getBaseRevision()
                + "，当前=" + draft.getLatestRevision() + "。请基于最新内容重新提案。");
        }

        AigStudioDraftContent content = parseContent(draft.getContentJson());
        List<AigStudioProposalPlanner.Operation> operations = readOperations(proposal.getOperationsJson());
        plan(content, operations);
        String afterHash = hashOf(content);
        if (!afterHash.equals(proposal.getContentHashAfter())) {
            throw new ServiceException("提案提出后草稿内容已变化，提案失效（内容哈希不一致）："
                + "proposal=" + proposal.getContentHashAfter() + "，实际=" + afterHash);
        }
        if (afterHash.equals(draft.getContentHash())) {
            throw new ServiceException("提案没有产生任何内容变化：与当前内容一致");
        }

        AigStudioDraftDetailVo updated = draftService.applyRevision(draft.getDraftId(),
            bo.getExpectedRevision(), content, AigStudioRevisionSourceEnum.COPILOT_ACCEPTED,
            "采纳变更提案 #" + proposalId
                + (StringUtils.isBlank(proposal.getSummary()) ? "" : "：" + proposal.getSummary()),
            actorId);
        if (!Boolean.TRUE.equals(updated.getRevisionCreated())) {
            throw new ServiceException("提案未产生新修订（内容与当前一致）");
        }
        markDecided(proposalId, AigStudioProposalStatusEnum.APPLIED, actorId, null);
        log.info("变更提案已采纳, proposalId={}, draftId={}, revision={}",
            proposalId, draft.getDraftId(), updated.getLatestRevision());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioChangeProposalVo reject(Long proposalId, AigStudioProposalDecideBo bo, Long actorId) {
        AigStudioChangeProposal proposal = requirePending(proposalId);
        AigStudioDraftDetailVo draft = draftService.getDraft(proposal.getDraftId());
        requireOwner(draft, actorId);
        markDecided(proposalId, AigStudioProposalStatusEnum.REJECTED, actorId,
            bo == null ? null : StringUtils.substring(bo.getReason(), 0, 200));
        log.info("变更提案已拒绝, proposalId={}, draftId={}", proposalId, proposal.getDraftId());
        return toVo(proposalMapper.selectById(proposalId));
    }

    /* ------------------------------------------------------------------ *
     * 内部
     * ------------------------------------------------------------------ */

    /**
     * 读一条待处理提案。
     *
     * @param proposalId 提案ID
     * @return 提案
     * @throws ServiceException 不存在或已被处理
     */
    private AigStudioChangeProposal requirePending(Long proposalId) {
        if (proposalId == null) {
            throw new ServiceException("提案ID不能为空");
        }
        AigStudioChangeProposal proposal = proposalMapper.selectById(proposalId);
        if (proposal == null) {
            throw new ServiceException("变更提案不存在：" + proposalId);
        }
        if (!AigStudioProposalStatusEnum.PENDING.getCode().equals(proposal.getStatus())) {
            throw new ServiceException("变更提案已被处理过：" + proposal.getStatus()
                + "（proposalId=" + proposalId + "）");
        }
        return proposal;
    }

    /**
     * 状态 CAS：把提案置为已处理；并发下只有一方成功。
     *
     * @param proposalId 提案ID
     * @param status     目标状态
     * @param actorId    处理人
     * @param reason     处理说明（可空）
     */
    private void markDecided(Long proposalId, AigStudioProposalStatusEnum status,
                             Long actorId, String reason) {
        AigStudioChangeProposal update = new AigStudioChangeProposal();
        update.setStatus(status.getCode());
        update.setDecidedBy(actorId);
        update.setDecidedAt(LocalDateTime.now());
        update.setDecidedReason(reason);
        int rows = proposalMapper.update(update, Wrappers.<AigStudioChangeProposal>lambdaUpdate()
            .eq(AigStudioChangeProposal::getProposalId, proposalId)
            .eq(AigStudioChangeProposal::getStatus, AigStudioProposalStatusEnum.PENDING.getCode()));
        if (rows == 0) {
            throw new ServiceException("提案已被他人处理（并发）：proposalId=" + proposalId
                + "。请刷新后查看它的最新状态。");
        }
    }

    /**
     * 责任人校验（与草稿写操作同一口径）。
     *
     * @param draft   草稿
     * @param actorId 操作用户
     */
    private void requireOwner(AigStudioDraftDetailVo draft, Long actorId) {
        if (actorId == null) {
            throw new ServiceException("训练台需要一个明确的操作用户");
        }
        if (!actorId.equals(draft.getOwnerId())) {
            throw new ServiceException("只有草稿责任人可以处理它的变更提案：draftId=" + draft.getDraftId()
                + "，责任人=" + draft.getOwnerId());
        }
    }

    /**
     * 归档的草稿不再接受提案（与草稿编辑同一口径）。
     *
     * @param draft 草稿
     */
    private void requireEditable(AigStudioDraftDetailVo draft) {
        if (AigStudioDraftStatusEnum.ARCHIVED.getCode().equals(draft.getStatus())) {
            throw new ServiceException("草稿已归档，不能再对它提提案：draftId=" + draft.getDraftId());
        }
    }

    /**
     * 解析草稿内容。
     *
     * @param contentJson 内容 JSON
     * @return 内容模型
     */
    private AigStudioDraftContent parseContent(String contentJson) {
        try {
            AigStudioDraftContent content = jsonMapper.readValue(contentJson, AigStudioDraftContent.class);
            if (content == null) {
                throw new ServiceException("草稿内容为空，无法提案");
            }
            return content;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("草稿内容无法解析：" + e.getMessage());
        }
    }

    /**
     * 应用操作（白名单拒绝在这里统一转成业务异常）。
     *
     * @param content    内容（就地修改）
     * @param operations 操作清单
     * @return 改动预览
     */
    private List<AigStudioProposalPlanner.DiffItem> plan(AigStudioDraftContent content,
                                                         List<AigStudioProposalPlanner.Operation> operations) {
        try {
            return AigStudioProposalPlanner.apply(content, operations);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("提案被拒绝：" + e.getMessage());
        }
    }

    /**
     * 入参操作 → 规划器操作。
     *
     * @param bos 入参操作
     * @return 操作清单
     */
    private static List<AigStudioProposalPlanner.Operation> toOperations(List<AigStudioProposalOperationBo> bos) {
        if (bos == null || bos.isEmpty()) {
            throw new ServiceException("提案至少要有一处改动");
        }
        List<AigStudioProposalPlanner.Operation> operations = new ArrayList<>(bos.size());
        for (AigStudioProposalOperationBo bo : bos) {
            if (bo == null) {
                throw new ServiceException("提案里有一处改动为空");
            }
            operations.add(new AigStudioProposalPlanner.Operation(bo.getPath(), bo.getValue()));
        }
        return operations;
    }

    /**
     * 入库用的规范化操作清单（只保留 path/value，路径去空白）。
     *
     * @param operations 操作
     * @return 规范化清单
     */
    private static List<Map<String, Object>> normalizedOperations(
        List<AigStudioProposalPlanner.Operation> operations) {
        List<Map<String, Object>> list = new ArrayList<>(operations.size());
        for (AigStudioProposalPlanner.Operation operation : operations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", operation.path() == null ? null : operation.path().trim());
            item.put("value", operation.value());
            list.add(item);
        }
        return list;
    }

    /**
     * 从入库的操作清单还原操作。
     *
     * @param operationsJson 操作清单 JSON
     * @return 操作
     */
    private List<AigStudioProposalPlanner.Operation> readOperations(String operationsJson) {
        try {
            List<AigStudioProposalOperationBo> bos =
                jsonMapper.readValue(operationsJson, jsonMapper.getTypeFactory()
                    .constructCollectionType(List.class, AigStudioProposalOperationBo.class));
            return toOperations(bos);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 库里的提案自己解析不了：这是数据损坏，不能当成"没有改动"放过去
            throw new ServiceException("提案的操作清单无法解析（数据损坏）：" + e.getMessage());
        }
    }

    /**
     * 内容 → 规范化 JSON → 哈希。
     *
     * @param content 内容
     * @return 哈希
     */
    private String hashOf(AigStudioDraftContent content) {
        try {
            String json = jsonMapper.writeValueAsString(content);
            return AigStudioContentHasher.hash(AigStudioContentHasher.canonicalize(json));
        } catch (Exception e) {
            throw new ServiceException("内容序列化失败：" + e.getMessage());
        }
    }

    /**
     * 序列化（失败即数据问题，不静默）。
     *
     * @param value 值
     * @return JSON
     */
    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("提案序列化失败：" + e.getMessage());
        }
    }

    /**
     * 实体 → 视图（previewJson 解析成结构化预览）。
     *
     * @param entity 实体
     * @return 视图
     */
    private AigStudioChangeProposalVo toVo(AigStudioChangeProposal entity) {
        AigStudioChangeProposalVo vo = new AigStudioChangeProposalVo();
        vo.setProposalId(entity.getProposalId());
        vo.setDraftId(entity.getDraftId());
        vo.setBaseRevision(entity.getBaseRevision());
        vo.setSource(entity.getSource());
        vo.setContentHashBefore(entity.getContentHashBefore());
        vo.setContentHashAfter(entity.getContentHashAfter());
        vo.setStatus(entity.getStatus());
        vo.setDecidedBy(entity.getDecidedBy());
        vo.setDecidedAt(entity.getDecidedAt());
        vo.setDecidedReason(entity.getDecidedReason());
        vo.setSummary(entity.getSummary());
        vo.setCreateTime(entity.getCreateTime());
        vo.setPreview(readPreview(entity.getPreviewJson()));
        return vo;
    }

    /**
     * 解析改动预览（解析失败不静默：宁可让页面看到"预览不可读"，也不假装没有改动）。
     *
     * @param previewJson 预览 JSON
     * @return 预览项
     */
    private List<AigStudioChangeProposalVo.DiffItem> readPreview(String previewJson) {
        if (StringUtils.isBlank(previewJson)) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(previewJson, jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, AigStudioChangeProposalVo.DiffItem.class));
        } catch (Exception e) {
            log.warn("提案预览无法解析：{}", e.getMessage());
            return List.of();
        }
    }

}
