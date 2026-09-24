package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.vo.CpOutputCheckVo;
import org.dromara.content.service.IContentOutputCheckService;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.SimpleMultipartFile;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeProductionService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出图生产服务实现。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeProductionServiceImpl implements ICreativeProductionService {

    /**
     * 事件载荷用本地 Jackson 序列化。
     *
     * <p>刻意不用 {@code JsonUtils}：它经 Hutool 的 SpringUtil 取 Bean，脱离 Spring 容器
     * （例如策略单测里）会在静态初始化阶段就失败。事件只是写一段 JSON 文本，本地序列化足够，
     * 也让「自动重试策略」这种会真烧 GPU 的逻辑可以脱离容器被确定性测试。</p>
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private static String toJson(java.util.Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 每屏最大尝试次数（首次 + 自动重试），到顶后停止自动重试、转人工。
     */
    private static final int MAX_ATTEMPTS_PER_SCREEN = 3;

    /**
     * QA 结论：不一致（筛除）
     */
    private static final String VERDICT_INCONSISTENT = "INCONSISTENT";
    /**
     * QA 结论：无法判定（转人工）
     */
    private static final String VERDICT_UNCERTAIN = "UNCERTAIN";

    private final ICreativeProjectService projectService;
    private final ICreativeGateService gateService;
    private final ICreativeGenerationService generationService;
    private final ICreativeStoryboardService storyboardService;
    private final DpGenerationMapper generationMapper;
    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final IContentOutputCheckService outputCheckService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionRun start(Long taskId, boolean force) {
        gateService.requireCanProduce(taskId);
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null) {
            throw new ServiceException("还没有分镜，请先生成并锁定分镜再批量出图");
        }
        List<DpStoryboardScreenVo> screens = storyboard.getScreens() == null
            ? List.of() : storyboard.getScreens();
        if (screens.isEmpty()) {
            throw new ServiceException("分镜里没有屏，无法批量出图");
        }

        int submitted = 0;
        int skipped = 0;
        for (DpStoryboardScreenVo screen : screens) {
            List<DpGeneration> existing = generationsOfScreen(taskId, screen.getId());
            boolean hasUsable = existing.stream().anyMatch(row -> {
                DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
                return status != null && (status == DpGenerationStatusEnum.APPROVED
                    || status == DpGenerationStatusEnum.SUCCEEDED
                    || !status.isTerminal() || status == DpGenerationStatusEnum.REJECTED);
            });
            if (hasUsable && !force) {
                // 已有在跑/已出图/已筛除的候选都算「这一屏已有记录」：不重复烧卡；
                // 想再出一张就显式点「重生成这一屏」（或 force=true）。
                skipped++;
                continue;
            }
            submitScreen(taskId, screen);
            submitted++;
        }
        if (submitted > 0) {
            projectService.appendEvent(taskId, "GENERATION", "PRODUCTION_START",
                toJson(Map.of("submitted", submitted, "skipped", skipped,
                    "force", force, "storyboardVersion", storyboard.getVersion())));
        }
        return new ProductionRun(submitted, skipped, screenStatuses(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo regenerateScreen(Long taskId, Long screenId) {
        gateService.requireCanProduce(taskId);
        DpStoryboardScreen screen = screenMapper.selectById(screenId);
        if (screen == null || !taskId.equals(screen.getTaskId())) {
            throw new ServiceException("屏不属于该项目：" + screenId);
        }
        return submitScreen(taskId, toScreenVo(screen));
    }

    @Override
    public ProductionRun refresh(Long taskId) {
        // 1) 候选状态（含补派发）
        generationService.refresh(taskId);
        // 2) QA 结论回填 + 只筛除
        refreshQa(taskId);
        // 3) 失败自动重试（≤ MAX_ATTEMPTS_PER_SCREEN）
        autoRetryFailed(taskId);
        return new ProductionRun(0, 0, screenStatuses(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo select(Long taskId, Long generationId) {
        DpGeneration row = requireGeneration(taskId, generationId);
        if (DpGenerationStatusEnum.REJECTED.getCode().equals(row.getStatus())) {
            throw new ServiceException("该候选已被质检筛除，不能选定");
        }
        if (!DpGenerationStatusEnum.SUCCEEDED.getCode().equals(row.getStatus())
            && !DpGenerationStatusEnum.APPROVED.getCode().equals(row.getStatus())) {
            throw new ServiceException("只有已出图的候选才能选定，当前状态：" + row.getStatus());
        }
        // 同一屏只允许一个已选定：其余已选定的降回已出图（保留历史，不删除）
        if (row.getScreenId() != null) {
            List<DpGeneration> sameScreen = generationsOfScreen(taskId, row.getScreenId());
            for (DpGeneration item : sameScreen) {
                if (!item.getId().equals(row.getId())
                    && DpGenerationStatusEnum.APPROVED.getCode().equals(item.getStatus())) {
                    item.setStatus(DpGenerationStatusEnum.SUCCEEDED.getCode());
                    generationMapper.updateById(item);
                }
            }
        }
        row.setStatus(DpGenerationStatusEnum.APPROVED.getCode());
        generationMapper.updateById(row);

        // 选定即触发「登记 + 质检」：产出登记成任务附件，并对原图做一致性检查。
        // 这一步不改变选定结果——QA 只做减法（不一致时由 refreshQa 把候选筛掉）。
        try {
            runQaInternal(taskId, row, "候选选定自动质检");
        } catch (Exception e) {
            log.warn("候选 {} 选定后自动质检失败：{}", generationId, e.getMessage());
        }

        projectService.appendEvent(taskId, "GENERATION", "CANDIDATE_SELECTED",
            toJson(Map.of("generationId", row.getId(),
                "screenId", row.getScreenId() == null ? 0L : row.getScreenId(),
                "candidateNo", row.getCandidateNo() == null ? 0 : row.getCandidateNo())));
        syncScreenStatus(taskId, row.getScreenId());
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo runQa(Long taskId, Long generationId) {
        DpGeneration row = requireGeneration(taskId, generationId);
        runQaInternal(taskId, row, "人工发起质检");
        return toVo(row);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 提交一屏的出图。
     *
     * @param taskId 项目ID
     * @param screen 屏
     * @return 新候选
     */
    private DpGenerationVo submitScreen(Long taskId, DpStoryboardScreenVo screen) {
        // 屏类型转成「画面用途」，让提示词按屏派生（卖点屏与尺寸屏不该用同一句话）
        String hint = StringUtils.blankToDefault(screen.getScreenTypeDesc(),
            StringUtils.blankToDefault(screen.getScreenType(), "分镜屏"));
        DpGenerationVo created = generationService.submitForScreen(
            taskId, screen.getId(), hint, null, null, screen.getWorkflowCode(), null, null);
        markScreen(taskId, screen.getId(), "GENERATING");
        return created;
    }

    /**
     * 发起质检（同时把产出登记为任务附件）。
     *
     * @param taskId 项目ID
     * @param row    候选
     * @param remark 备注
     */
    private void runQaInternal(Long taskId, DpGeneration row, String remark) {
        if (row.getOutputAssetId() == null) {
            throw new ServiceException("该候选还没有产出图，无法质检");
        }
        if (row.getInputFileId() == null) {
            throw new ServiceException("该候选没有记录参考图附件，无法做「原图 vs 成品」比对");
        }
        byte[] bytes = generationService.preview(row.getId());
        String contentType = "image/png";
        SimpleMultipartFile resultFile = new SimpleMultipartFile(
            "file", "candidate-" + row.getCandidateNo() + ".png", contentType, bytes);
        Long checkId = outputCheckService.run(taskId, row.getInputFileId(), null, resultFile, remark);
        row.setQaCheckId(checkId);
        row.setQaVerdict(null);
        generationMapper.updateById(row);
        projectService.appendEvent(taskId, "QA", "QA_SUBMIT",
            toJson(Map.of("generationId", row.getId(), "checkId", checkId,
                "referenceFileId", row.getInputFileId())));
    }

    /**
     * 回填 QA 结论，并按「只筛除不放行」处置。
     *
     * @param taskId 项目ID
     */
    private void refreshQa(Long taskId) {
        List<DpGeneration> pending = generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .isNotNull(DpGeneration::getQaCheckId)
            .isNull(DpGeneration::getQaVerdict));
        for (DpGeneration row : pending) {
            try {
                CpOutputCheckVo check = outputCheckService.getDetail(row.getQaCheckId());
                if (check == null || !"DONE".equalsIgnoreCase(check.getStatus())) {
                    if (check != null && "FAILED".equalsIgnoreCase(check.getStatus())) {
                        row.setQaVerdict(VERDICT_UNCERTAIN);
                        row.setErrorMessage("质检未取得结论：" + StringUtils.blankToDefault(
                            check.getFailureReason(), "未知原因"));
                        generationMapper.updateById(row);
                    }
                    continue;
                }
                row.setQaVerdict(check.getVerdict());
                // 产出登记：检查服务已把成品图登记为任务附件，这里记下附件ID便于后续排版取用
                if (check.getResultFileId() != null) {
                    row.setOutputFileId(check.getResultFileId());
                }
                if (VERDICT_INCONSISTENT.equalsIgnoreCase(check.getVerdict())
                    && !DpGenerationStatusEnum.APPROVED.getCode().equals(row.getStatus())) {
                    // 只筛除：不一致的候选不再参与选定（已选定的会另外提示，不静默改人的决定）
                    row.setStatus(DpGenerationStatusEnum.REJECTED.getCode());
                }
                generationMapper.updateById(row);
                projectService.appendEvent(taskId, "QA", "QA_" + StringUtils.blankToDefault(
                    check.getVerdict(), "UNKNOWN"),
                    toJson(Map.of("generationId", row.getId(),
                        "checkId", check.getCheckId(),
                        "verdict", StringUtils.blankToDefault(check.getVerdict(), ""),
                        "score", check.getScore() == null ? "" : check.getScore().toPlainString(),
                        "traceId", StringUtils.blankToDefault(check.getTraceId(), ""))));
            } catch (Exception e) {
                log.warn("回填候选 {} 的质检结论失败：{}", row.getId(), e.getMessage());
            }
        }
    }

    /**
     * 失败候选自动重试（每屏最多 {@link #MAX_ATTEMPTS_PER_SCREEN} 次尝试），到顶转人工。
     *
     * <p>包级可见（不是 private）：重试策略是「自动环节只做减法也会做加法」的地方——
     * 它会真的再烧一次 GPU，必须能被单测直接钉住，而不是只能靠真实失败去碰。</p>
     *
     * @param taskId 项目ID
     */
    int autoRetryFailed(Long taskId) {
        int retried = 0;
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null || storyboard.getScreens() == null) {
            return 0;
        }
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            List<DpGeneration> rows = generationsOfScreen(taskId, screen.getId());
            if (rows.isEmpty()) {
                continue;
            }
            boolean hasActiveOrGood = rows.stream().anyMatch(row -> {
                DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
                return status != null && (status == DpGenerationStatusEnum.QUEUED
                    || status == DpGenerationStatusEnum.RUNNING
                    || status == DpGenerationStatusEnum.SUCCEEDED
                    || status == DpGenerationStatusEnum.APPROVED);
            });
            if (hasActiveOrGood) {
                continue;
            }
            long failed = rows.stream().filter(row ->
                DpGenerationStatusEnum.FAILED.getCode().equals(row.getStatus())
                    || DpGenerationStatusEnum.TIMEOUT.getCode().equals(row.getStatus())).count();
            if (failed == 0 || rows.size() >= MAX_ATTEMPTS_PER_SCREEN) {
                if (rows.size() >= MAX_ATTEMPTS_PER_SCREEN && failed > 0) {
                    markScreen(taskId, screen.getId(), "REJECTED");
                }
                continue;
            }
            log.info("屏 {} 失败 {} 次，自动重试（该屏已有 {} 个候选，上限 {}）",
                screen.getScreenNo(), failed, rows.size(), MAX_ATTEMPTS_PER_SCREEN);
            try {
                submitScreen(taskId, screen);
                retried++;
                projectService.appendEvent(taskId, "GENERATION", "AUTO_RETRY",
                    toJson(Map.of("screenId", screen.getId(),
                        "screenNo", StringUtils.blankToDefault(screen.getScreenNo(), ""),
                        "failedCount", failed, "attempts", rows.size() + 1,
                        "maxAttempts", MAX_ATTEMPTS_PER_SCREEN)));
            } catch (Exception e) {
                log.warn("屏 {} 自动重试提交失败：{}", screen.getScreenNo(), e.getMessage());
            }
        }
        return retried;
    }

    /**
     * 汇总逐屏生产状态。
     *
     * @param taskId 项目ID
     * @return 逐屏状态
     */
    private List<ScreenProduction> screenStatuses(Long taskId) {
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        List<ScreenProduction> list = new ArrayList<>();
        if (storyboard == null || storyboard.getScreens() == null) {
            return list;
        }
        Map<Long, List<DpGeneration>> cache = new HashMap<>();
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            List<DpGeneration> rows = cache.computeIfAbsent(screen.getId(),
                id -> generationsOfScreen(taskId, id));
            DpGeneration latest = rows.isEmpty() ? null : rows.get(0);
            DpGeneration selected = rows.stream()
                .filter(row -> DpGenerationStatusEnum.APPROVED.getCode().equals(row.getStatus()))
                .findFirst().orElse(null);
            long failed = rows.stream().filter(row ->
                DpGenerationStatusEnum.FAILED.getCode().equals(row.getStatus())
                    || DpGenerationStatusEnum.TIMEOUT.getCode().equals(row.getStatus())).count();
            String status = screen.getStatus();
            if (selected != null) {
                status = "APPROVED";
            } else if (latest != null) {
                DpGenerationStatusEnum latestStatus = DpGenerationStatusEnum.find(latest.getStatus());
                if (latestStatus == DpGenerationStatusEnum.QUEUED || latestStatus == DpGenerationStatusEnum.RUNNING) {
                    status = "GENERATING";
                } else if (latestStatus == DpGenerationStatusEnum.SUCCEEDED) {
                    status = "GENERATED";
                }
            }
            if (status == null) {
                status = "DRAFT";
            }
            String note = failed > 0
                ? "已失败 " + failed + " 次（上限 " + MAX_ATTEMPTS_PER_SCREEN + " 次尝试，到顶转人工）" : null;
            // 已选定的候选若质检不一致：不静默推翻人的决定，但必须在页面上大声提示——
            // 「状态已选定 + 质检不一致」是一个需要人来处理的冲突，不能悄悄放过去。
            if (selected != null && VERDICT_INCONSISTENT.equalsIgnoreCase(selected.getQaVerdict())) {
                note = "已选定候选的质检结论为「不一致」，需人工处理（可重出这一屏后再选定）";
            } else if (selected == null && latest != null
                && DpGenerationStatusEnum.REJECTED.getCode().equals(latest.getStatus())) {
                note = "候选因质检不一致被筛除，请重出这一屏";
            }
            list.add(new ScreenProduction(screen.getId(), screen.getScreenNo(), screen.getScreenTypeDesc(),
                status, rows.size(), selected == null ? null : selected.getId(),
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getQaVerdict(), note));
        }
        return list;
    }

    private List<DpGeneration> generationsOfScreen(Long taskId, Long screenId) {
        if (screenId == null) {
            return List.of();
        }
        return generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .eq(DpGeneration::getScreenId, screenId)
            .orderByDesc(DpGeneration::getId));
    }

    private DpGeneration requireGeneration(Long taskId, Long generationId) {
        DpGeneration row = generationMapper.selectById(generationId);
        if (row == null || !taskId.equals(row.getTaskId())) {
            throw new ServiceException("候选不属于该项目：" + generationId);
        }
        return row;
    }

    private void markScreen(Long taskId, Long screenId, String status) {
        if (screenId == null) {
            return;
        }
        DpStoryboardScreen screen = new DpStoryboardScreen();
        screen.setId(screenId);
        screen.setStatus(status);
        screenMapper.updateById(screen);
    }

    private void syncScreenStatus(Long taskId, Long screenId) {
        List<ScreenProduction> list = screenStatuses(taskId);
        list.stream().filter(item -> item.screenId().equals(screenId)).findFirst()
            .ifPresent(item -> markScreen(taskId, screenId, item.status()));
    }

    private DpStoryboardScreenVo toScreenVo(DpStoryboardScreen screen) {
        DpStoryboardScreenVo vo = new DpStoryboardScreenVo();
        vo.setId(screen.getId());
        vo.setStoryboardId(screen.getStoryboardId());
        vo.setTaskId(screen.getTaskId());
        vo.setScreenNo(screen.getScreenNo());
        vo.setSortNo(screen.getSortNo());
        vo.setScreenType(screen.getScreenType());
        vo.setWorkflowCode(screen.getWorkflowCode());
        vo.setProductLockLevel(screen.getProductLockLevel());
        vo.setStatus(screen.getStatus());
        // 画面用途：类型描述优先（与分镜页面展示一致）
        vo.setScreenTypeDesc(screenTypeDesc(screen.getScreenType()));
        return vo;
    }

    private static String screenTypeDesc(String type) {
        return switch (StringUtils.blankToDefault(type, "")) {
            case "HERO" -> "主图";
            case "SELLING_POINT" -> "卖点";
            case "SCENE" -> "场景";
            case "DETAIL" -> "细节";
            case "SIZE" -> "尺寸";
            case "PACKAGE" -> "包装";
            case "BRAND" -> "品牌";
            default -> type;
        };
    }

    private DpGenerationVo toVo(DpGeneration row) {
        // 复用生成服务的 VO 组装口径：这里只补充生产视角需要的字段
        DpGenerationVo vo = new DpGenerationVo();
        vo.setId(row.getId());
        vo.setTaskId(row.getTaskId());
        vo.setScreenId(row.getScreenId());
        vo.setDnaId(row.getDnaId());
        vo.setDirectionId(row.getDirectionId());
        vo.setStoryboardId(row.getStoryboardId());
        vo.setCandidateNo(row.getCandidateNo());
        vo.setWorkflowCode(row.getWorkflowCode());
        vo.setPrompt(row.getPrompt());
        vo.setStatus(row.getStatus());
        DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
        vo.setStatusDesc(status == null ? row.getStatus() : status.getDesc());
        vo.setOutputAssetId(row.getOutputAssetId());
        vo.setOutputWidth(row.getOutputWidth());
        vo.setOutputHeight(row.getOutputHeight());
        vo.setQaVerdict(row.getQaVerdict());
        vo.setErrorCode(row.getErrorCode());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setPreviewable(row.getOutputAssetId() != null);
        vo.setRetryable(status != null && status.isRetryable());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

}
