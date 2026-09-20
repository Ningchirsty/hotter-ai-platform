package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.bo.ContentCardQueryBo;
import org.dromara.content.domain.bo.ContentCardResolveBo;
import org.dromara.content.domain.vo.CpInteractionCardVo;
import org.dromara.content.enums.ContentCardStatusEnum;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentFieldAlias;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpInteractionCardMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentCardService;
import org.dromara.content.service.IContentTaskGateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 互动确认卡服务实现。
 *
 * <p><b>红线</b>：本类是「解析结果 → 产品事实」的唯一通道，且必须由人主动确认。
 * 不存在任何自动确认分支。</p>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCardServiceImpl implements IContentCardService {

    /**
     * 处理选项：确认某个已有候选值
     */
    private static final String OPT_CONFIRM = "CONFIRM";

    /**
     * 处理选项：填写其他值（手工录入）
     */
    private static final String OPT_OTHER = "OTHER";

    /**
     * 处理选项：补充资料后重新解析
     */
    private static final String OPT_SUPPLEMENT = "SUPPLEMENT";

    /**
     * 处理选项：暂不确认并阻断
     */
    private static final String OPT_BLOCK = "BLOCK";

    /**
     * 卡片 Mapper
     */
    private final CpInteractionCardMapper cardMapper;

    /**
     * 事实快照 Mapper
     */
    private final CpFactSnapshotMapper factSnapshotMapper;

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 闸门重算服务
     */
    private final IContentTaskGateService taskGateService;

    @Override
    public PageResult<CpInteractionCardVo> queryPage(ContentCardQueryBo bo, PageQuery pageQuery) {
        ContentCardQueryBo q = bo == null ? new ContentCardQueryBo() : bo;
        Long mineId = Boolean.TRUE.equals(q.getMineOnly()) ? LoginHelper.getUserId() : q.getAssigneeId();

        // 任务号过滤：先用参数化查询取出任务ID，再用于 in 条件。
        // 刻意不用 inSql 拼接——拼字符串过滤是 SQL 注入面，即使做字符清理也不该写。
        List<Long> taskIds = null;
        if (StringUtils.isNotBlank(q.getTaskNo())) {
            taskIds = taskMapper.selectList(new LambdaQueryWrapper<CpTask>()
                    .like(CpTask::getTaskNo, q.getTaskNo()))
                .stream().map(CpTask::getTaskId).toList();
            if (taskIds.isEmpty()) {
                return PageResult.build(List.of(), 0L);
            }
        }

        LambdaQueryWrapper<CpInteractionCard> wrapper = new LambdaQueryWrapper<CpInteractionCard>()
            .eq(q.getTaskId() != null, CpInteractionCard::getTaskId, q.getTaskId())
            .in(taskIds != null, CpInteractionCard::getTaskId, taskIds)
            .eq(StringUtils.isNotBlank(q.getCardType()), CpInteractionCard::getCardType, q.getCardType())
            .eq(StringUtils.isNotBlank(q.getStatus()), CpInteractionCard::getStatus, q.getStatus())
            .eq(StringUtils.isNotBlank(q.getGateLevel()), CpInteractionCard::getGateLevel, q.getGateLevel())
            .eq(mineId != null, CpInteractionCard::getAssigneeId, mineId)
            .eq(Boolean.TRUE.equals(q.getBlockingOnly()), CpInteractionCard::getBlocking, "Y")
            // 阻断项排前面：用户最该先看到的是「卡住流程的那几张」
            .orderByDesc(CpInteractionCard::getBlocking)
            .orderByAsc(CpInteractionCard::getStatus)
            .orderByDesc(CpInteractionCard::getCreateTime);
        var voPage = cardMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CpInteractionCardVo resolve(ContentCardResolveBo bo) {
        CpInteractionCard card = load(bo.getCardId());
        if (!ContentCardStatusEnum.PENDING.getCode().equals(card.getStatus())) {
            throw new ServiceException("该卡已处理，不能重复处理");
        }
        String option = bo.getOption() == null ? "" : bo.getOption().trim().toUpperCase(java.util.Locale.ROOT);
        switch (option) {
            case OPT_CONFIRM -> confirmExisting(card, bo);
            case OPT_OTHER -> confirmManual(card, bo);
            case OPT_SUPPLEMENT -> {
                // 用户表示会补资料：先关掉这张卡，重新解析后若仍缺失会再生成一张新卡
                updateStatus(card, ContentCardStatusEnum.CLOSED, null, null, bo.getComment());
            }
            case OPT_BLOCK -> {
                // 显式阻断：任务不得流转，闸门会据此判为待确认
                updateStatus(card, ContentCardStatusEnum.BLOCKED, null, null, bo.getComment());
            }
            default -> throw new ServiceException("不支持的处理选项：" + bo.getOption());
        }

        taskGateService.recheckAndApply(card.getTaskId());
        log.info("互动卡处理完成, cardId={}, taskId={}, option={}",
            card.getCardId(), card.getTaskId(), option);
        return cardMapper.selectVoById(card.getCardId());
    }

    /**
     * 采用某个已有候选值：该行置为已确认，同字段的其它候选置为已否决。
     *
     * @param card 卡片
     * @param bo   入参
     */
    private void confirmExisting(CpInteractionCard card, ContentCardResolveBo bo) {
        List<CpFactSnapshot> rows = snapshotsOfField(card);
        if (rows.isEmpty()) {
            throw new ServiceException("该字段已无候选值，请改用「填写其他值」");
        }
        CpFactSnapshot target = pickTarget(rows, bo);
        if (target == null) {
            throw new ServiceException("未找到对应的候选值，请重新选择或改用「填写其他值」");
        }
        Long userId = LoginHelper.getUserId();
        LocalDateTime now = LocalDateTime.now();

        CpFactSnapshot confirm = new CpFactSnapshot();
        confirm.setSnapshotId(target.getSnapshotId());
        confirm.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
        confirm.setConfirmedBy(userId);
        confirm.setConfirmedAt(now);
        factSnapshotMapper.updateById(confirm);

        // 同字段的其它候选一律否决：它们是同一问题的不同答案，留着会一直被预检当成冲突
        int rejected = 0;
        for (CpFactSnapshot row : rows) {
            if (row.getSnapshotId().equals(target.getSnapshotId())) {
                continue;
            }
            if (ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(row.getConfirmStatus())) {
                continue;
            }
            CpFactSnapshot rej = new CpFactSnapshot();
            rej.setSnapshotId(row.getSnapshotId());
            rej.setConfirmStatus(ContentFactConfirmStatusEnum.REJECTED.getCode());
            rej.setConfirmedBy(userId);
            rej.setConfirmedAt(now);
            factSnapshotMapper.updateById(rej);
            rejected++;
        }
        updateStatus(card, ContentCardStatusEnum.RESOLVED, target.getFieldValue(), OPT_CONFIRM, bo.getComment());
        log.info("确认候选值, cardId={}, snapshotId={}, rejectedOthers={}",
            card.getCardId(), target.getSnapshotId(), rejected);
    }

    /**
     * 手工录入其他值：新增一行「已确认」事实。
     *
     * @param card 卡片
     * @param bo   入参
     */
    private void confirmManual(CpInteractionCard card, ContentCardResolveBo bo) {
        String value = bo.getValue() == null ? null : bo.getValue().trim();
        if (StringUtils.isBlank(value)) {
            throw new ServiceException("请填写确认值");
        }
        List<CpFactSnapshot> rows = snapshotsOfField(card);
        String fieldName = null;
        int maxVersion = 1;
        for (CpFactSnapshot r : rows) {
            if (StringUtils.isBlank(fieldName)) {
                fieldName = r.getFieldName();
            }
            if (r.getSnapshotVersion() != null && r.getSnapshotVersion() > maxVersion) {
                maxVersion = r.getSnapshotVersion();
            }
        }
        if (StringUtils.isBlank(fieldName)) {
            fieldName = ContentFieldAlias.fieldName(card.getFieldCode());
        }
        Long userId = LoginHelper.getUserId();
        LocalDateTime now = LocalDateTime.now();

        CpFactSnapshot manual = new CpFactSnapshot();
        manual.setTaskId(card.getTaskId());
        // 手工确认产生新一轮快照版本，便于开工包记录「冻结在哪一轮」
        manual.setSnapshotVersion(maxVersion + 1);
        manual.setFieldCode(card.getFieldCode());
        manual.setFieldName(fieldName);
        manual.setFieldValue(value);
        manual.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
        manual.setConfirmedBy(userId);
        manual.setConfirmedAt(now);
        manual.setRemark("由互动卡手工确认录入");
        factSnapshotMapper.insert(manual);

        // 原有候选全部否决，避免与手工值并存又被预检判成冲突
        for (CpFactSnapshot row : rows) {
            if (ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(row.getConfirmStatus())) {
                continue;
            }
            CpFactSnapshot rej = new CpFactSnapshot();
            rej.setSnapshotId(row.getSnapshotId());
            rej.setConfirmStatus(ContentFactConfirmStatusEnum.REJECTED.getCode());
            rej.setConfirmedBy(userId);
            rej.setConfirmedAt(now);
            factSnapshotMapper.updateById(rej);
        }
        updateStatus(card, ContentCardStatusEnum.RESOLVED, value, OPT_OTHER, bo.getComment());
        log.info("手工确认事实, cardId={}, fieldCode={}, version={}",
            card.getCardId(), card.getFieldCode(), manual.getSnapshotVersion());
    }

    /**
     * 取该卡片字段下的全部候选行。
     *
     * @param card 卡片
     * @return 候选行
     */
    private List<CpFactSnapshot> snapshotsOfField(CpInteractionCard card) {
        return factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, card.getTaskId())
            .eq(CpFactSnapshot::getFieldCode, card.getFieldCode()));
    }

    /**
     * 选出要确认的候选行：优先按 snapshotId 精确锚定，其次按值匹配。
     *
     * @param rows 候选行
     * @param bo   入参
     * @return 目标行，未命中返回 null
     */
    private CpFactSnapshot pickTarget(List<CpFactSnapshot> rows, ContentCardResolveBo bo) {
        if (bo.getSnapshotId() != null) {
            for (CpFactSnapshot r : rows) {
                if (bo.getSnapshotId().equals(r.getSnapshotId())) {
                    return r;
                }
            }
        }
        String value = bo.getValue() == null ? null : bo.getValue().trim();
        if (StringUtils.isNotBlank(value)) {
            for (CpFactSnapshot r : rows) {
                if (value.equals(r.getFieldValue())) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * 更新卡片状态与处理信息。
     *
     * @param card    卡片
     * @param status  新状态
     * @param value   确认值（可空）
     * @param option  所选选项（可空）
     * @param comment 处理说明（可空）
     */
    private void updateStatus(CpInteractionCard card, ContentCardStatusEnum status,
                              String value, String option, String comment) {
        CpInteractionCard update = new CpInteractionCard();
        update.setCardId(card.getCardId());
        update.setStatus(status.getCode());
        update.setResolvedValue(value);
        update.setResolvedOption(option);
        update.setResolvedBy(LoginHelper.getUserId());
        update.setResolvedAt(LocalDateTime.now());
        if (StringUtils.isNotBlank(comment)) {
            update.setRemark(comment);
        }
        cardMapper.updateById(update);
    }

    /**
     * 加载卡片。
     *
     * @param cardId 卡片ID
     * @return 卡片实体
     */
    private CpInteractionCard load(Long cardId) {
        if (cardId == null) {
            throw new ServiceException("互动卡ID不能为空");
        }
        CpInteractionCard card = cardMapper.selectById(cardId);
        if (card == null) {
            throw new ServiceException("互动卡不存在");
        }
        return card;
    }

}
