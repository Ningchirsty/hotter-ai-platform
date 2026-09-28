package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.creative.domain.DpBrandBrief;
import org.dromara.creative.domain.bo.BrandBriefBo;
import org.dromara.creative.domain.vo.DpBrandBriefVo;
import org.dromara.creative.enums.DpBrandBriefStatusEnum;
import org.dromara.creative.mapper.DpBrandBriefMapper;
import org.dromara.creative.service.ICreativeBriefService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.system.api.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 品牌 Brief 服务实现。
 *
 * <p><b>不做的事</b>：不与 {@code brand_tone} 事实自动合并（两者的作者不同，冲突要人裁定）；
 * 不在保存时动状态（状态只能由确认接口推进）。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeBriefServiceImpl implements ICreativeBriefService {

    /**
     * 阶段事件的类型编码（不改阶段，只留痕）
     */
    private static final String EVENT_TYPE = "BRAND_BRIEF";

    private final DpBrandBriefMapper briefMapper;
    private final ICreativeProjectService projectService;
    /**
     * 用户昵称解析（ruoyi-api 的 UserService，实现由 system 模块提供）。
     *
     * <p>用它而不是把昵称在确认时写进库：昵称会改，落库的昵称迟早是旧的；
     * 也不新造轮子——平台已有这个通用接口。</p>
     */
    private final UserService userService;

    @Override
    public DpBrandBriefVo get(Long taskId) {
        // 先确认项目存在（顺带保证是电商详情页项目），避免对不存在的项目返回一个「空 Brief」
        projectService.getProject(taskId);
        DpBrandBrief entity = find(taskId);
        return entity == null ? emptyVo(taskId) : toVo(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpBrandBriefVo save(Long taskId, BrandBriefBo bo) {
        projectService.getProject(taskId);
        BrandBriefBo form = bo == null ? new BrandBriefBo() : bo;
        DpBrandBrief entity = find(taskId);
        boolean created = entity == null;
        if (created) {
            entity = new DpBrandBrief();
            entity.setTaskId(taskId);
            // 新记录一律从草稿开始：确认权在品牌方，不在保存表单
            entity.setStatus(DpBrandBriefStatusEnum.DRAFT.getCode());
        } else if (StringUtils.isBlank(entity.getStatus())) {
            // 历史脏数据：状态为空时按草稿处理，避免页面显示空白状态
            entity.setStatus(DpBrandBriefStatusEnum.DRAFT.getCode());
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

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("created", created);
        detail.put("status", entity.getStatus());
        detail.put("mustShowLines", lineCount(entity.getMustShow()));
        detail.put("forbiddenWordLines", lineCount(entity.getForbiddenWords()));
        detail.put("mainPushLines", lineCount(entity.getMainPush()));
        projectService.appendEvent(taskId, EVENT_TYPE, "BRAND_BRIEF_SAVED", JsonUtils.toJsonString(detail));
        log.info("品牌 Brief 已保存 taskId={} created={} status={}", taskId, created, entity.getStatus());
        return toVo(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpBrandBriefVo confirm(Long taskId) {
        projectService.getProject(taskId);
        DpBrandBrief entity = find(taskId);
        if (entity == null) {
            throw new ServiceException("请先填写品牌 Brief 再确认：品牌方要求（必显信息/禁用词/主推卖点）还没有录入");
        }
        boolean already = DpBrandBriefStatusEnum.CONFIRMED.getCode().equals(entity.getStatus());
        entity.setStatus(DpBrandBriefStatusEnum.CONFIRMED.getCode());
        entity.setConfirmedBy(LoginHelper.getUserId());
        entity.setConfirmedAt(LocalDateTime.now());
        briefMapper.updateById(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("alreadyConfirmed", already);
        detail.put("confirmedBy", entity.getConfirmedBy());
        detail.put("mustShowLines", lineCount(entity.getMustShow()));
        detail.put("forbiddenWordLines", lineCount(entity.getForbiddenWords()));
        projectService.appendEvent(taskId, EVENT_TYPE, "BRAND_BRIEF_CONFIRMED", JsonUtils.toJsonString(detail));
        log.info("品牌 Brief 已确认 taskId={} already={}", taskId, already);
        return toVo(entity, true);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 读实体（每项目一行，唯一键兜底）。
     *
     * @param taskId 项目ID
     * @return 实体；没有返回 null
     */
    private DpBrandBrief find(Long taskId) {
        List<DpBrandBrief> rows = briefMapper.selectList(new LambdaQueryWrapper<DpBrandBrief>()
            .eq(DpBrandBrief::getTaskId, taskId)
            .orderByDesc(DpBrandBrief::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 空视图：没有记录也要有 data，页面才能把「还没填」与「接口失败」分开显示。
     *
     * @param taskId 项目ID
     * @return 空视图
     */
    private static DpBrandBriefVo emptyVo(Long taskId) {
        DpBrandBriefVo vo = new DpBrandBriefVo();
        vo.setTaskId(taskId);
        vo.setConfigured(false);
        vo.setStatus(DpBrandBriefStatusEnum.DRAFT.getCode());
        return vo;
    }

    private DpBrandBriefVo toVo(DpBrandBrief entity, boolean configured) {
        DpBrandBriefVo vo = new DpBrandBriefVo();
        vo.setTaskId(entity.getTaskId());
        vo.setConfigured(configured);
        vo.setBrandTone(entity.getBrandTone());
        vo.setMustShow(entity.getMustShow());
        vo.setForbiddenWords(entity.getForbiddenWords());
        vo.setTargetAudience(entity.getTargetAudience());
        vo.setMainPush(entity.getMainPush());
        vo.setSizeSpecReq(entity.getSizeSpecReq());
        vo.setStyleRef(entity.getStyleRef());
        vo.setStatus(StringUtils.blankToDefault(entity.getStatus(), DpBrandBriefStatusEnum.DRAFT.getCode()));
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
     * 行数统计（按 \n 计，仅用于事件留痕，不做业务判定）。
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
