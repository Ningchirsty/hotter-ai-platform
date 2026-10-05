package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpBrandBrief;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.bo.BrandBriefBo;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.content.enums.ContentBriefStatusEnum;
import org.dromara.content.enums.ContentFileKindEnum;
import org.dromara.content.mapper.CpBrandBriefMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.system.api.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌 Brief 服务实现（内容协同侧）。
 *
 * <p><b>不做的事</b>：</p>
 * <ol>
 *   <li>不与 {@code brand_tone} 事实自动合并——两者作者不同（资料解析 vs 品牌方填写），
 *       冲突要人裁定，自动合并等于替人做决定；</li>
 *   <li>保存时<b>只在「已确认 + 内容有实质变更」这一种情况下</b>把状态打回草稿，其余情况状态只由确认接口推进。
 *       视觉门的 {@code BRAND_BRIEF_CONFIRMED} 判的就是这个状态：改完内容不重置，
 *       等于拿着「旧内容的确认」一路绿过去（内测 S9 实测成立），所以这一种情况必须重置；</li>
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

    /**
     * 参考风格图片最多几张：再多就说明该用一份图片资料而不是"风格参考"了
     */
    private static final int MAX_STYLE_IMAGES = 6;

    private final CpBrandBriefMapper briefMapper;
    private final IContentTaskService taskService;
    /**
     * 附件 Mapper：参考风格图片要逐个校验"存在、属于本任务、是图片"，读时还要解析文件名
     */
    private final CpTaskFileMapper taskFileMapper;
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
        // 保存前留一份旧值：下面要判断「这次保存到底改没改内容」（见类注释第 2 条）
        String[] before = created ? null : valuesOf(entity);
        boolean wasConfirmed = !created
            && ContentBriefStatusEnum.CONFIRMED.getCode().equals(entity.getStatus());
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
        entity.setStyleRefFiles(normalizeStyleRefFiles(taskId, form.getStyleRefFiles()));
        entity.setRemark(norm(form.getRemark()));
        // 状态口径（R54 修正，内测 S9）：
        //   · 新建                        → 草稿（确认权在品牌方，不在保存表单）；
        //   · 已确认 + 内容有实质变更      → 打回草稿，并清掉确认人与确认时间；
        //   · 已确认 + 内容没变（重复保存）→ 保持已确认（不该因为再点一次保存就把确认作废）；
        //   · 未确认                      → 保持原状，等品牌方点确认。
        // 为什么"改了就作废"：视觉门的 BRAND_BRIEF_CONFIRMED 判的是 status=CONFIRMED，
        // 不重置就会出现"内容改了、门还绿着"，等于用一个过期确认放行出图。
        boolean contentChanged = !created && !java.util.Arrays.equals(before, valuesOf(entity));
        boolean resetToDraft = wasConfirmed && contentChanged;
        if (resetToDraft) {
            entity.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
            entity.setConfirmedBy(null);
            entity.setConfirmedAt(null);
        }
        if (created) {
            briefMapper.insert(entity);
        } else {
            // 【为什么不用 updateById】MyBatis-Plus 默认 NOT_NULL 策略会把实体里的 null 字段从 UPDATE 里去掉，
            // 于是「把某个要求清空」永远存不进去——实测：删掉最后一张参考风格图片、清空禁用词，页面报成功、
            // 刷新又回来了（前端实施时发现并上报）。这张表是整行编辑（表单一次提交 8 个文本字段 + 图片引用），
            // "所见即所存"才是正确语义，所以这里把这 9 个业务列**逐个显式 set**（set 会写 NULL）。
            // 不用 @TableField(updateStrategy = IGNORED)：本项目 MP 3.5.17 的 FieldStrategy 不能作注解常量（编译报错）。
            LambdaUpdateWrapper<CpBrandBrief> wrapper = new LambdaUpdateWrapper<CpBrandBrief>()
                .eq(CpBrandBrief::getId, entity.getId())
                .set(CpBrandBrief::getBrandTone, entity.getBrandTone())
                .set(CpBrandBrief::getMustShow, entity.getMustShow())
                .set(CpBrandBrief::getForbiddenWords, entity.getForbiddenWords())
                .set(CpBrandBrief::getTargetAudience, entity.getTargetAudience())
                .set(CpBrandBrief::getMainPush, entity.getMainPush())
                .set(CpBrandBrief::getSizeSpecReq, entity.getSizeSpecReq())
                .set(CpBrandBrief::getStyleRef, entity.getStyleRef())
                .set(CpBrandBrief::getStyleRefFiles, entity.getStyleRefFiles())
                .set(CpBrandBrief::getRemark, entity.getRemark());
            if (resetToDraft) {
                // 同理必须显式 set：靠实体传 null 在这套策略下写不进去
                wrapper.set(CpBrandBrief::getStatus, ContentBriefStatusEnum.DRAFT.getCode())
                    .set(CpBrandBrief::getConfirmedBy, null)
                    .set(CpBrandBrief::getConfirmedAt, null);
            }
            briefMapper.update(entity, wrapper);
        }
        if (resetToDraft) {
            log.info("品牌 Brief 内容有变更，已从「已确认」打回「草稿」 taskId={}", taskId);
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
        // 「有记录」不等于「填过内容」：8 项全空且没传参考风格图片时，确认没有意义，
        // 还会在视觉门里造出一个「已确认但什么都没说」的假信号（内测 S16）。
        // 前端本来就有这条校验，这里补齐服务端——否则绕过页面直接调接口就能造出空确认。
        boolean anyContent = false;
        for (String value : valuesOf(entity)) {
            if (StringUtils.isNotBlank(value)) {
                anyContent = true;
                break;
            }
        }
        if (!anyContent) {
            throw new ServiceException("品牌要求 8 项全空：至少填一项再确认（确认后视觉工厂会按它创作）");
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
        vo.setStyleRefFiles(entity.getStyleRefFiles());
        vo.setStyleRefImages(imagesOf(entity.getTaskId(), entity.getStyleRefFiles()));
        vo.setStatus(StringUtils.blankToDefault(entity.getStatus(), ContentBriefStatusEnum.DRAFT.getCode()));
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setConfirmedByName(nicknameOf(entity.getConfirmedBy()));
        vo.setConfirmedAt(entity.getConfirmedAt());
        vo.setRemark(entity.getRemark());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 归一 + 校验参考风格图片 id（逗号分隔）。
     *
     * <p>规则：去空、去重、最多 {@link #MAX_STYLE_IMAGES} 张；每个 id 必须
     * **存在、属于本任务、且是图片附件**，否则直接拒绝并指出是哪一个——
     * 不静默丢弃（页面显示 3 张、库里存 2 张是最难查的一类问题）。</p>
     *
     * @param taskId 任务ID
     * @param raw    file_id 串（逗号/顿号/空格分隔都接受）
     * @return 归一后的串；没有返回 null
     */
    private String normalizeStyleRefFiles(Long taskId, String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String part : raw.split("[,，、\\s]+")) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() > MAX_STYLE_IMAGES) {
            throw new ServiceException("参考风格图片最多 " + MAX_STYLE_IMAGES + " 张，当前 " + ids.size() + " 张");
        }
        for (String id : ids) {
            long fileId;
            try {
                fileId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                throw new ServiceException("参考风格图片的附件ID不是数字：" + id);
            }
            CpTaskFile file = taskFileMapper.selectById(fileId);
            if (file == null) {
                throw new ServiceException("参考风格图片的附件不存在：" + id);
            }
            if (!taskId.equals(file.getTaskId())) {
                throw new ServiceException("附件 " + id + " 不属于本任务，不能作为参考风格图片");
            }
            if (!ContentFileKindEnum.IMAGE.getCode().equalsIgnoreCase(file.getFileKind())) {
                throw new ServiceException("「" + StringUtils.blankToDefault(file.getFileName(), id)
                    + "」不是图片，不能作为参考风格图片");
            }
        }
        return String.join(",", ids);
    }

    /**
     * 把 file_id 串解析成明细（解析不到的跳过：附件被删是可能的，页面按实际能显示的渲染）。
     *
     * @param taskId 任务ID
     * @param raw    file_id 串
     * @return 明细列表（可能为空）
     */
    private List<CpBrandBriefVo.BriefImage> imagesOf(Long taskId, String raw) {
        List<CpBrandBriefVo.BriefImage> list = new ArrayList<>();
        if (StringUtils.isBlank(raw) || taskId == null) {
            return list;
        }
        for (String part : raw.split("[,，、\\s]+")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                CpTaskFile file = taskFileMapper.selectById(Long.parseLong(id));
                if (file != null && taskId.equals(file.getTaskId())) {
                    list.add(new CpBrandBriefVo.BriefImage(file.getFileId(), file.getFileName()));
                }
            } catch (NumberFormatException ignored) {
                // 脏数据里的非数字片段直接跳过：读接口不该因为一条脏记录整页失败
            }
        }
        return list;
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
     * 把 9 个业务列取成数组，用于回答两个问题：「这次保存改没改内容」与「到底填过东西没有」。
     *
     * <p>两侧都过一遍 {@link #norm}：库里历史行可能存在 {@code ""} 与 {@code null} 混用，
     * 不归一化会把「没改」误判成「改了」，从而把已确认的 Brief 无谓地打回草稿。</p>
     *
     * @param entity Brief 实体（不可为空）
     * @return 长度固定为 9 的数组（8 个文本字段 + 参考风格图片引用串）
     */
    private static String[] valuesOf(CpBrandBrief entity) {
        return new String[] {
            norm(entity.getBrandTone()),
            norm(entity.getMustShow()),
            norm(entity.getForbiddenWords()),
            norm(entity.getTargetAudience()),
            norm(entity.getMainPush()),
            norm(entity.getSizeSpecReq()),
            norm(entity.getStyleRef()),
            norm(entity.getStyleRefFiles()),
            norm(entity.getRemark())
        };
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
