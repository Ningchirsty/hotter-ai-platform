package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.service.ImageTaskSubmissionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.bo.CreativeHeroBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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
public class CreativeGenerationServiceImpl implements ICreativeGenerationService {

    private final ICreativeProjectService projectService;
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeStoryboardService storyboardService;
    private final ICreativeGateService gateService;
    private final DnaPromptBuilder dnaPromptBuilder;
    private final IContentTaskService contentTaskService;
    private final ContentOssHelper contentOssHelper;
    private final DpGenerationMapper generationMapper;
    private final CreativeTaskStageMapper stageMapper;
    /**
     * 图像内核未启用（image.enabled=false）时该 Bean 不存在，故用 ObjectProvider 延迟解析，
     * 调用时给出可读提示，而不是让整个应用起不来。
     */
    private final ObjectProvider<ImageTaskSubmissionService> submissionProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo submitHero(Long taskId, CreativeHeroBo bo) {
        return submitInternal(taskId, null, "HERO 主图", bo.getFileId(), bo.getPrompt(),
            bo.getNegativePrompt(), bo.getWorkflowCode(), bo.getSizeLabel(), bo.getStrengthLabel(),
            "HERO_SUBMIT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo submitForScreen(Long taskId, Long screenId, String screenHint, String prompt,
                                          String negativePrompt, String workflowCode,
                                          String sizeLabel, String strengthLabel) {
        return submitInternal(taskId, screenId, screenHint, null, prompt, negativePrompt,
            workflowCode, sizeLabel, strengthLabel, "SCREEN_SUBMIT");
    }

    /**
     * 出图提交的内部实现：HERO 单张与逐屏批量都走这里。
     *
     * <p>刻意不复制两份装配逻辑——R0 就在内核侧留过一次「两处重复」的债，
     * 这里再复制一次以后必然出现「单张能出、批量少记一个字段」这类偏差。</p>
     *
     * @param screenId      分镜单屏ID（HERO 单张为 null）
     * @param screenHint    画面用途（用于派生提示词，如「HERO 主图」「卖点一」）
     * @param fileId        指定参考图附件ID（可空＝取最近一张图片附件）
     * @param promptInput   用户提示词（空则按基因派生）
     * @param eventAction   事件动作编码
     * @return 生成记录
     */
    private DpGenerationVo submitInternal(Long taskId, Long screenId, String screenHint, Long fileId,
                                          String promptInput, String negativeInput, String workflowInput,
                                          String sizeLabel, String strengthLabel, String eventAction) {
        // 视觉门前置：未过门不放行。放在最前面，避免白白生成素材、占一次 GPU。
        // 门禁在后端强制，前端按钮状态只是提示——绕过页面直接调接口同样会被拒。
        gateService.requireCanProduce(taskId);

        CreativeProjectVo project = projectService.getProject(taskId);
        ImageTaskSubmissionService submission = requireSubmission();

        // 1) 挑参考图：指定优先，否则取最近一张图片附件
        List<CpTaskFileVo> files = contentTaskService.listFiles(taskId);
        CpTaskFileVo reference = resolveReference(taskId, fileId, files);

        // 2) 参考图字节 → 内核素材（内核按 tenant+user 校验归属，必须由执行者本人上传）
        byte[] bytes = contentOssHelper.getBytes(reference.getFileRef());
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("参考图内容为空，无法出图：" + reference.getFileName());
        }
        String contentType = guessContentType(reference.getFileExt());
        long assetId = submission.storeAsset(reference.getFileName(), bytes, contentType);

        // 3) 工作流与提示词：提示词由「已锁定的视觉基因」派生（人可在页面上改，改了以人写的为准）
        String workflowCode = StringUtils.isBlank(workflowInput)
            ? CreativeConstants.DEFAULT_HERO_WORKFLOW : workflowInput;
        ImageWorkflowVersion version = requireWorkflow(submission, workflowCode);
        Long dnaId = dnaService.activeDnaId(taskId);
        List<String> promptApplied = new ArrayList<>();
        String prompt = promptInput;
        String negativePrompt = negativeInput;
        if (StringUtils.isBlank(prompt) || StringUtils.isBlank(negativePrompt)) {
            DnaPromptBuilder.Prompt derived = dnaPromptBuilder.build(
                dnaService.activeDna(taskId), project.getProductName(), screenHint);
            if (StringUtils.isBlank(prompt)) {
                prompt = derived.prompt();
                promptApplied = derived.applied();
            }
            if (StringUtils.isBlank(negativePrompt)) {
                negativePrompt = derived.negativePrompt();
            }
        }

        // 4) 候选序号（同项目累加；重试也会递增，因此「第几次尝试」可数）
        long existing = countGenerations(taskId);
        int candidateNo = (int) existing + 1;

        // 5) 提交内核（入库 + 派发）。幂等键包含候选序号，因此连点两次只会产生一个候选。
        String idempotencyKey = "creative:" + taskId + ":" + reference.getFileId()
            + ":" + candidateNo + ":" + sha256Hex(prompt);
        ImageTaskSubmissionService.Submission result = submission.submitAndDispatch(
            new ImageTaskSubmissionService.Command(
                version.capabilityCode(), workflowCode,
                "视觉工厂 " + screenHint + " · " + project.getTaskName(),
                prompt, negativePrompt, sizeLabel, strengthLabel,
                List.of(assetId), idempotencyKey, true));

        // 6) 幂等命中：内核已有同一任务，直接返回已有记录，不重复插入
        DpGeneration sameTask = generationMapper.selectOne(
            new LambdaQueryWrapper<DpGeneration>().eq(DpGeneration::getImageTaskId, result.imageTaskId()));
        if (sameTask != null) {
            return toVo(sameTask);
        }

        DpGeneration row = new DpGeneration();
        row.setTaskId(taskId);
        row.setScreenId(screenId);
        row.setCandidateNo(candidateNo);
        row.setDnaId(dnaId);
        DpVisualDirectionVo direction = directionService.selected(taskId);
        row.setDirectionId(direction == null ? null : direction.getId());
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        row.setStoryboardId(storyboard == null ? null : storyboard.getId());
        row.setWorkflowCode(workflowCode);
        row.setWorkflowVersion(version.version());
        row.setPrompt(prompt);
        row.setNegativePrompt(negativePrompt);
        row.setInputFileId(reference.getFileId());
        row.setInputAssetId(assetId);
        row.setInputJson(JsonUtils.toJsonString(inputSnapshot(reference, version, assetId)));
        row.setImageTaskId(result.imageTaskId());
        row.setExecTenantId(result.tenantId());
        row.setExecUserId(result.userId());
        row.setStatus(result.status());
        if (!result.accepted() && !"IDEMPOTENT".equals(result.outcome())) {
            // 入库成功但没派发成功（多为线程池队列满）：状态仍是 QUEUED，
            // 刷新时会尝试补派发；这里如实记下原因，不假装在跑。
            row.setErrorMessage("暂未派发（" + result.outcome() + "），系统会自动重试派发");
        }
        generationMapper.insert(row);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("generationId", row.getId());
        detail.put("imageTaskId", result.imageTaskId());
        detail.put("taskNo", result.taskNo());
        detail.put("workflowCode", workflowCode);
        detail.put("candidateNo", candidateNo);
        detail.put("referenceFileId", reference.getFileId());
        detail.put("dispatched", result.accepted());
        detail.put("dnaId", dnaId);
        detail.put("directionId", row.getDirectionId());
        detail.put("storyboardId", row.getStoryboardId());
        detail.put("promptFromDna", !promptApplied.isEmpty());
        detail.put("promptApplied", promptApplied);
        detail.put("screenId", screenId);
        detail.put("screenHint", screenHint);
        projectService.moveStage(taskId, DpVisualStageEnum.PRODUCING, eventAction,
            JsonUtils.toJsonString(detail));
        return toVo(row);
    }

    @Override
    public List<DpGenerationVo> listByProject(Long taskId) {
        refresh(taskId);
        List<DpGeneration> rows = generationMapper.selectList(
            new LambdaQueryWrapper<DpGeneration>()
                .eq(DpGeneration::getTaskId, taskId)
                .orderByDesc(DpGeneration::getId));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo retry(Long generationId) {
        DpGeneration row = generationMapper.selectById(generationId);
        if (row == null) {
            throw new ServiceException("候选不存在：" + generationId);
        }
        DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
        if (status == null || !status.isRetryable()) {
            throw new ServiceException("当前状态不可重试：" + row.getStatus());
        }
        CreativeHeroBo bo = new CreativeHeroBo();
        bo.setFileId(row.getInputFileId());
        bo.setPrompt(row.getPrompt());
        bo.setNegativePrompt(row.getNegativePrompt());
        bo.setWorkflowCode(row.getWorkflowCode());
        DpGenerationVo created = submitHero(row.getTaskId(), bo);
        projectService.appendEvent(row.getTaskId(), "GENERATION", "HERO_RETRY",
            JsonUtils.toJsonString(Map.of("fromGenerationId", generationId, "newGenerationId", created.getId())));
        return created;
    }

    @Override
    public byte[] thumbnail(Long generationId) {
        DpGeneration row = requireGeneration(generationId);
        ImageTaskSubmissionService submission = requireSubmission();
        Long assetId = row.getOutputAssetId();
        if (assetId == null) {
            return null;
        }
        byte[] thumb = submission.readAssetThumbnail(assetId, row.getExecTenantId(), row.getExecUserId());
        return thumb != null ? thumb : preview(generationId);
    }

    @Override
    public byte[] preview(Long generationId) {
        DpGeneration row = requireGeneration(generationId);
        if (row.getOutputAssetId() == null) {
            throw new ServiceException("该候选还没有产出图");
        }
        return requireSubmission().readAssetBytes(
            row.getOutputAssetId(), row.getExecTenantId(), row.getExecUserId());
    }

    @Override
    public int refresh(Long taskId) {
        List<DpGeneration> pending = generationMapper.selectList(
            new LambdaQueryWrapper<DpGeneration>()
                .eq(DpGeneration::getTaskId, taskId)
                .in(DpGeneration::getStatus,
                    DpGenerationStatusEnum.QUEUED.getCode(), DpGenerationStatusEnum.RUNNING.getCode()));
        return refreshRows(pending);
    }

    @Override
    public PageResult<DpGenerationVo> queryPage(String status, PageQuery pageQuery) {
        Page<DpGeneration> page = generationMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<DpGeneration>()
                .eq(StringUtils.isNotBlank(status), DpGeneration::getStatus, status)
                .orderByDesc(DpGeneration::getId));
        List<DpGeneration> records = page.getRecords();
        // 未结束的候选先向内核要一次真实状态（record 是同一批对象，刷新后直接反映到 VO）
        refreshRows(records.stream()
            .filter(row -> {
                DpGenerationStatusEnum s = DpGenerationStatusEnum.find(row.getStatus());
                return s != null && !s.isTerminal();
            })
            .toList());
        List<DpGenerationVo> rows = new ArrayList<>();
        for (DpGeneration row : records) {
            DpGenerationVo vo = toVo(row);
            vo.setTaskName(stageMapper.selectTaskName(row.getTaskId()));
            rows.add(vo);
        }
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 批量刷新候选状态。
     *
     * @param pending 待刷新候选（会被就地修改）
     * @return 真正发生状态变化的条数
     */
    private int refreshRows(List<DpGeneration> pending) {
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        ImageTaskSubmissionService submission = requireSubmission();
        int changed = 0;
        for (DpGeneration row : pending) {
            if (row.getImageTaskId() == null || row.getExecTenantId() == null || row.getExecUserId() == null) {
                continue;
            }
            try {
                if (DpGenerationStatusEnum.QUEUED.getCode().equals(row.getStatus())) {
                    String outcome = submission.dispatchQueued(
                        row.getImageTaskId(), row.getExecTenantId(), row.getExecUserId());
                    if (!"INVALID_STATUS".equals(outcome)) {
                        log.debug("补派发候选 {} 结果 {}", row.getId(), outcome);
                    }
                }
                Map<String, Object> task = submission.statusOf(
                    row.getImageTaskId(), row.getExecTenantId(), row.getExecUserId());
                if (applyKernelState(row, task)) {
                    generationMapper.updateById(row);
                    changed++;
                    DpGenerationStatusEnum now = DpGenerationStatusEnum.find(row.getStatus());
                    if (now != null && now.isTerminal()) {
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("generationId", row.getId());
                        detail.put("imageTaskId", row.getImageTaskId());
                        detail.put("status", row.getStatus());
                        if (row.getErrorMessage() != null) {
                            detail.put("error", row.getErrorMessage());
                        }
                        projectService.appendEvent(row.getTaskId(), "GENERATION", "HERO_" + row.getStatus(),
                            JsonUtils.toJsonString(detail));
                    }
                }
            } catch (Exception e) {
                // 单个候选读状态失败不能拖垮整个列表
                log.warn("刷新候选 {} 状态失败：{}", row.getId(), e.getMessage());
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> availableWorkflows() {
        ImageTaskSubmissionService submission = requireSubmission();
        return submission.availableWorkflows().stream().map(v -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflowCode", v.workflowCode());
            item.put("capabilityCode", v.capabilityCode());
            item.put("modelCode", v.modelCode());
            item.put("version", v.version());
            item.put("status", v.status());
            item.put("defaultSize", v.defaultSize());
            item.put("defaultStrength", v.defaultStrength());
            item.put("supportedStrengths", v.supportedStrengths());
            item.put("published", v.isPublished());
            return item;
        }).toList();
    }

    /**
     * 把内核任务行合并进候选记录。
     *
     * @param row  候选
     * @param task 内核任务行
     * @return 是否有字段变化
     */
    private boolean applyKernelState(DpGeneration row, Map<String, Object> task) {
        boolean changed = false;
        String kernelStatus = task.get("status") == null ? null : String.valueOf(task.get("status"));
        if (kernelStatus != null && !kernelStatus.equals(row.getStatus())
            && DpGenerationStatusEnum.find(kernelStatus) != null) {
            row.setStatus(kernelStatus);
            changed = true;
        }
        Object outputAssetId = task.get("output_asset_id");
        if (outputAssetId != null && !String.valueOf(outputAssetId).equals(String.valueOf(row.getOutputAssetId()))) {
            row.setOutputAssetId(((Number) outputAssetId).longValue());
            changed = true;
        }
        Integer width = intOf(task.get("output_width"));
        if (width != null && !width.equals(row.getOutputWidth())) {
            row.setOutputWidth(width);
            changed = true;
        }
        Integer height = intOf(task.get("output_height"));
        if (height != null && !height.equals(row.getOutputHeight())) {
            row.setOutputHeight(height);
            changed = true;
        }
        String worker = task.get("comfy_worker") == null ? null : String.valueOf(task.get("comfy_worker"));
        if (StringUtils.isNotBlank(worker) && !worker.equals(row.getGpuNode())) {
            row.setGpuNode(worker);
            changed = true;
        }
        String errorCode = task.get("error_code") == null ? null : String.valueOf(task.get("error_code"));
        if (errorCode != null && !errorCode.equals(row.getErrorCode())) {
            row.setErrorCode(errorCode);
            changed = true;
        }
        String errorMessage = task.get("error_message") == null ? null : String.valueOf(task.get("error_message"));
        if (StringUtils.isNotBlank(errorMessage) && !errorMessage.equals(row.getErrorMessage())) {
            row.setErrorMessage(errorMessage);
            changed = true;
        }
        Long duration = durationOf(task.get("started_time"), task.get("finished_time"));
        if (duration != null && !duration.equals(row.getDurationMs())) {
            row.setDurationMs(duration);
            changed = true;
        }
        return changed;
    }

    private DpGeneration requireGeneration(Long generationId) {
        if (generationId == null) {
            throw new ServiceException("候选ID不能为空");
        }
        DpGeneration row = generationMapper.selectById(generationId);
        if (row == null) {
            throw new ServiceException("候选不存在：" + generationId);
        }
        return row;
    }

    private CpTaskFileVo resolveReference(Long taskId, Long fileId, List<CpTaskFileVo> files) {
        if (fileId != null) {
            return files.stream()
                .filter(f -> fileId.equals(f.getFileId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("参考图不在该项目附件中：" + fileId));
        }
        return files.stream()
            .filter(f -> "IMAGE".equalsIgnoreCase(f.getFileKind()))
            .max(Comparator.comparing(CpTaskFileVo::getCreateTime,
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElseThrow(() -> new ServiceException(
                "项目 " + taskId + " 还没有参考图，请先上传产品图再出图"));
    }

    private ImageWorkflowVersion requireWorkflow(ImageTaskSubmissionService submission, String workflowCode) {
        return submission.availableWorkflows().stream()
            .filter(v -> v.workflowCode().equals(workflowCode))
            .findFirst()
            .orElseThrow(() -> new ServiceException(
                "出图工作流不可用（未注册或未通过验收）：" + workflowCode));
    }

    private long countGenerations(Long taskId) {
        Long count = generationMapper.selectCount(
            new LambdaQueryWrapper<DpGeneration>().eq(DpGeneration::getTaskId, taskId));
        return count == null ? 0 : count;
    }

    private ImageTaskSubmissionService requireSubmission() {
        ImageTaskSubmissionService submission = submissionProvider.getIfAvailable();
        if (submission == null) {
            throw new ServiceException("图像出图能力未启用（image.enabled=false），无法出图");
        }
        return submission;
    }

    private Map<String, Object> inputSnapshot(CpTaskFileVo reference,
                                              ImageWorkflowVersion version, long assetId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("referenceFileId", reference.getFileId());
        snapshot.put("referenceFileName", reference.getFileName());
        snapshot.put("inputAssetId", assetId);
        snapshot.put("workflowCode", version.workflowCode());
        snapshot.put("workflowVersion", version.version());
        snapshot.put("modelCode", version.modelCode());
        snapshot.put("capabilityCode", version.capabilityCode());
        return snapshot;
    }

    private String buildDefaultPrompt(String productName) {
        // 提示词统一由 DnaPromptBuilder 派生（它内部已处理「没有基因」的默认值），
        // 这里不再保留第二套默认模板——两处默认值迟早会不一致。
        return dnaPromptBuilder.build(null, productName, "HERO 主图").prompt();
    }

    private DpGenerationVo toVo(DpGeneration row) {
        DpGenerationVo vo = new DpGenerationVo();
        vo.setId(row.getId());
        vo.setTaskId(row.getTaskId());
        vo.setScreenId(row.getScreenId());
        vo.setDnaId(row.getDnaId());
        vo.setDirectionId(row.getDirectionId());
        vo.setStoryboardId(row.getStoryboardId());        vo.setCandidateNo(row.getCandidateNo());
        vo.setWorkflowCode(row.getWorkflowCode());
        vo.setWorkflowVersion(row.getWorkflowVersion());
        vo.setPrompt(row.getPrompt());
        vo.setStatus(row.getStatus());
        DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
        vo.setStatusDesc(status == null ? row.getStatus() : status.getDesc());
        vo.setOutputAssetId(row.getOutputAssetId());
        vo.setOutputWidth(row.getOutputWidth());
        vo.setOutputHeight(row.getOutputHeight());
        vo.setQaVerdict(row.getQaVerdict());
        vo.setProductFileId(row.getProductFileId());
        vo.setProductVerdict(row.getProductVerdict());
        vo.setErrorCode(row.getErrorCode());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setDurationMs(row.getDurationMs());
        vo.setGpuNode(row.getGpuNode());
        vo.setPreviewable(row.getOutputAssetId() != null);
        vo.setRetryable(status != null && status.isRetryable());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private static String guessContentType(String ext) {
        String e = ext == null ? "" : ext.trim().toLowerCase();
        return switch (e) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    private static Integer intOf(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long durationOf(Object started, Object finished) {
        if (started == null || finished == null) {
            return null;
        }
        try {
            LocalDateTime from = toLocalDateTime(started);
            LocalDateTime to = toLocalDateTime(finished);
            if (from == null || to == null) {
                return null;
            }
            return Duration.between(from, to).toMillis();
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        return null;
    }

    private static String sha256Hex(String value) {
        if (value == null) {
            return "0";
        }
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

}
