package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpBrandBrief;
import org.dromara.content.domain.bo.BrandBriefBo;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.content.enums.ContentBriefStatusEnum;
import org.dromara.content.mapper.CpBrandBriefMapper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.system.api.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 品牌 Brief 服务实现（内容协同侧）。
 *
 * <p><b>不做的事</b>：</p>
 * <ol>
 *   <li>不与 {@code brand_tone} 事实自动合并——两者作者不同（资料解析 vs 品牌方填写），
 *       冲突要人裁定，自动合并等于替人做决定；</li>
 *   <li>不在保存时动状态——状态只能由确认接口推进，因为视觉门的闸门项判的就是它；</li>
 *   <li>不往视觉阶段时间线（{@code dp_stage_event}）写事件——R7 把 Brief 归到内容侧之后，
 *       内容模块不该去写创作域的表（那是反向依赖）。品牌要求的审计信息落在本表自身：
 *       {@code update_time} / {@code confirmed_by} / {@code confirmed_at}；
 *       平面设计侧看得到"品牌要求是否已确认"，靠的是视觉门里的
 *       {@code BRAND_BRIEF_CONFIRMED} 闸门项（它带确认时间）。</li>
 * </ol>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentBrandBriefServiceImpl implements IContentBrandBriefService {

    private final CpBrandBriefMapper briefMapper;
    private final IContentTaskService taskService;
    /**
     * 用户昵称解析（{@code ruoyi-api} 的 UserService，实现由 system 模块提供）。
     *
     * <p>用它而不是把昵称在确认时写进库：昵称会改，落库的昵称迟早是旧的。</p>
     */
    private final UserService userService;

    @Override
    public CpBrandBriefVo get(Long taskId) {
        taskService.requireTask(taskId);
        CpBrandBrief entity = find(taskId);
        return entity == null ? emptyVo(taskId) : toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CpBrandBriefVo save(Long taskId, BrandBriefBo bo) {
        taskService.requireTask(taskId);
        BrandBriefBo form = bo == null ? new BrandBriefBo() : bo;
        CpBrandBrief entity = find(taskId);
        boolean created = entity == null;
        if (created) {
            entity = new CpBrandBrief();
            entity.setTaskId(taskId);
            // 新记录一律从草稿开始：确认权在品牌方，不在保存表单
            entity.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
        } else if (StringUtils.isBlank(entity.getStatus())) {
            // 历史脏数据：状态为空时按草稿处理，避免页面显示空白状态
            entity.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
        }
        entity.setBrandTone(norm(form.getBrandTone()));
        entity.setMustShow(norm(form.getMustShow()));
        entity.setForbiddenWords(norm(form.getForbiddenWords()));
        entity.setTargetAudience(norm(form.getTargetAudience()));
        entity.setMainPush(norm(form.getMainPush()));
        entity.setSizeSpecReq(norm(form.getSizeSpecReq()));
        entity.setStyleRef(norm(form.getStyleRef()));
        entity.setRemark(norm(form.getRemark()));
        // status/confirmedBy/confirmedAt 刻意不在这里赋值：保存草稿不该把已确认打回草稿，
        // 也不该替品牌方按下确认。原记录是 CONFIRMED 时保存后仍是 CONFIRMED（下面如实带回）。
        if (created) {
            briefMapper.insert(entity);
        } else {
            briefMapper.updateById(entity);
        }
        log.info("品牌 Brief 已保存 taskId={} created={} status={} 必显={}条 禁用词={}条 主推={}条",
            taskId, created, entity.getStatus(), lineCount(entity.getMustShow()),
            lineCount(entity.getForbiddenWords()), lineCount(entity.getMainPush()));
        return toVo(find(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CpBrandBriefVo confirm(Long taskId) {
        taskService.requireTask(taskId);
        CpBrandBrief entity = find(taskId);
        if (entity == null) {
            throw new ServiceException("请先填写品牌要求（必显信息/禁用词/主推卖点）再确认");
        }
        boolean already = ContentBriefStatusEnum.CONFIRMED.getCode().equals(entity.getStatus());
        entity.setStatus(ContentBriefStatusEnum.CONFIRMED.getCode());
        entity.setConfirmedBy(LoginHelper.getUserId());
        entity.setConfirmedAt(LocalDateTime.now());
        briefMapper.updateById(entity);
        log.info("品牌 Brief 已确认 taskId={} already={}", taskId, already);
        return toVo(entity);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 读实体（每任务一行，按 id 兜底取最新）。
     *
     * @param taskId 任务ID
     * @return 实体；没有返回 null
     */
    private CpBrandBrief find(Long taskId) {
        List<CpBrandBrief> rows = briefMapper.selectList(new LambdaQueryWrapper<CpBrandBrief>()
            .eq(CpBrandBrief::getTaskId, taskId)
            .orderByDesc(CpBrandBrief::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 空视图：没有记录也要有 data，页面才能把「还没填」与「接口失败」分开显示。
     *
     * @param taskId 任务ID
     * @return 空视图
     */
    private static CpBrandBriefVo emptyVo(Long taskId) {
        CpBrandBriefVo vo = new CpBrandBriefVo();
        vo.setTaskId(taskId);
        vo.setConfigured(false);
        vo.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
        return vo;
    }

    private CpBrandBriefVo toVo(CpBrandBrief entity) {
        if (entity == null) {
            return emptyVo(null);
        }
        CpBrandBriefVo vo = new CpBrandBriefVo();
        vo.setTaskId(entity.getTaskId());
        vo.setConfigured(true);
        vo.setBrandTone(entity.getBrandTone());
        vo.setMustShow(entity.getMustShow());
        vo.setForbiddenWords(entity.getForbiddenWords());
        vo.setTargetAudience(entity.getTargetAudience());
        vo.setMainPush(entity.getMainPush());
        vo.setSizeSpecReq(entity.getSizeSpecReq());
        vo.setStyleRef(entity.getStyleRef());
        vo.setStatus(StringUtils.blankToDefault(entity.getStatus(), ContentBriefStatusEnum.DRAFT.getCode()));
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setConfirmedByName(nicknameOf(entity.getConfirmedBy()));
        vo.setConfirmedAt(entity.getConfirmedAt());
        vo.setRemark(entity.getRemark());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 解析确认人昵称。
     *
     * <p>查不到（用户被删/被禁用）时返回 null，并保留 confirmedBy——页面据此显示「已确认（ID · 时间）」，
     * 不能因为一个昵称查不到就让整页 500。</p>
     *
     * @param userId 用户ID
     * @return 昵称；无法解析返回 null
     */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userService.selectNicknameById(userId);
        } catch (Exception e) {
            log.warn("解析品牌 Brief 确认人昵称失败 userId={} error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 空白归一为 null：空字符串与「没填」在页面上是同一件事，
     * 但存进库里会变成两种值（闸门「禁用词非空」判定也会因此出现假通过）。
     *
     * @param value 原值
     * @return 归一后的值
     */
    private static String norm(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 行数统计（按行计，仅用于日志留痕，不做业务判定）。
     *
     * @param value 多行文本
     * @return 行数
     */
    private static int lineCount(String value) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        int count = 0;
        for (String line : value.split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                count++;
            }
        }
        return count;
    }

}
