package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.helper.CreativeOutputSpecResolver;
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
import org.dromara.creative.helper.ReferenceImageFitter;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.content.service.IContentBrandBriefService;
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
    /**
     * 品牌 Brief（R7）：必显信息/主推卖点进正向提示词，禁用词进负向提示词
     */
    private final IContentBrandBriefService briefService;
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

    /**
     * 场景配置（R27）：出图尺寸按交付类型的默认输出规格（主图 800×800 这类固定规格）。
     */
    private final ICreativeScenarioConfigService scenarioConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo submitHero(Long taskId, CreativeHeroBo bo) {
        return submitInternal(taskId, null, "HERO 主图", null, bo.getFileId(), bo.getPrompt(),
            bo.getNegativePrompt(), bo.getWorkflowCode(), bo.getSizeLabel(), bo.getStrengthLabel(),
            "HERO_SUBMIT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpGenerationVo submitForScreen(Long taskId, Long screenId, String screenHint, String screenText,
                                          String prompt, String negativePrompt, String workflowCode,
                                          String sizeLabel, String strengthLabel,
                                          Long referenceFileId, String moduleVisualRules) {
        return submitInternal(taskId, screenId, screenHint, screenText, referenceFileId, prompt, negativePrompt,
            workflowCode, sizeLabel, strengthLabel, "SCREEN_SUBMIT", moduleVisualRules);
    }

    /**
     * 出图提交的内部实现：HERO 单张与逐屏批量都走这里。
     *
     * <p>刻意不复制两份装配逻辑——R0 就在内核侧留过一次「两处重复」的债，
     * 这里再复制一次以后必然出现「单张能出、批量少记一个字段」这类偏差。</p>
     *
     * @param screenId      分镜单屏ID（HERO 单张为 null）
     * @param screenHint    画面用途（用于派生提示词，如「HERO 主图」「卖点一」）
     * @param screenText    屏文案（R7：作为画面描述参与派生；HERO 单张为 null）
     * @param fileId        指定参考图附件ID（可空＝取最近一张图片附件）
     * @param promptInput   用户提示词（空则按基因+屏文案+品牌 Brief 派生）
     * @param eventAction   事件动作编码
     * @return 生成记录
     */
    private DpGenerationVo submitInternal(Long taskId, Long screenId, String screenHint, String screenText,
                                          Long fileId, String promptInput, String negativeInput,
                                          String workflowInput, String sizeLabel, String strengthLabel,
                                          String eventAction) {
        return submitInternal(taskId, screenId, screenHint, screenText, fileId, promptInput, negativeInput,
            workflowInput, sizeLabel, strengthLabel, eventAction, null);
    }

    /**
     * 出图提交的内部实现（带模块视觉表达；R23）。
     *
     * @param moduleVisualRules 模块规划里的「视觉表达」（可空）
     */
    private DpGenerationVo submitInternal(Long taskId, Long screenId, String screenHint, String screenText,
                                          Long fileId, String promptInput, String negativeInput,
                                          String workflowInput, String sizeLabel, String strengthLabel,
                                          String eventAction, String moduleVisualRules) {
        // 视觉门前置：未过门不放行。放在最前面，避免白白生成素材、占一次 GPU。
        // 门禁在后端强制，前端按钮状态只是提示——绕过页面直接调接口同样会被拒。
        gateService.requireCanProduce(taskId);

        CreativeProjectVo project = projectService.getProject(taskId);
        ImageTaskSubmissionService submission = requireSubmission();

        // 0) 工作流契约要先拿到：它的 maxPixels 决定「参考图能有多大」。
        //    这个顺序是必须的——wf-i2i-qwen21 按输入图尺寸出图，参考图超上限时产出必然被判 OUTPUT_INVALID，
        //    所以必须先按契约预算把要送模型的那份图适配好，再上传素材。
        String workflowCode = StringUtils.isBlank(workflowInput)
            ? CreativeConstants.DEFAULT_HERO_WORKFLOW : workflowInput;
        ImageWorkflowVersion version = requireWorkflow(submission, workflowCode);

        // 1) 挑参考图：指定优先，否则取最近一张图片附件
        List<CpTaskFileVo> files = contentTaskService.listFiles(taskId);
        CpTaskFileVo reference = resolveReference(taskId, fileId, files);

        // 2) 参考图字节 → 适配工作流像素预算 → 内核素材（内核按 tenant+user 校验归属，必须由执行者本人上传）
        byte[] bytes = contentOssHelper.getBytes(reference.getFileRef());
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("参考图内容为空，无法出图：" + reference.getFileName());
        }
        // 2.1) 输出规格（R27）：交付类型在 dp_output_spec 里的**默认规格**如果是固定尺寸
        //      （主图 800×800、1200×1200），就按它出图。
        //      为什么必须在这里做：wf-i2i-qwen21 / wf-whitebg-qwen21 这类契约没有固定尺寸档位
        //      （输出跟随输入图），所以"要出多大"只能由我们送进去的图决定——因此把参考图
        //      等比缩放到盖住目标尺寸后从中心裁切，而不是拉伸（拉伸会把产品拍扁）。
        CreativeOutputSpecResolver.TargetSize target = resolveTargetSize(project);
        ReferenceImageFitter.Fitted fitted = target != null && target.usable()
            && ImageTaskSubmissionService.outputFollowsInput(version, sizeLabel)
            ? ReferenceImageFitter.fitTo(bytes, reference.getFileName(), target.width(), target.height())
            : ReferenceImageFitter.fit(bytes, reference.getFileName(), version.maxPixels());
        long assetId = submission.storeAsset(fitted.fileName(), fitted.bytes(), fitted.contentType());

        // 3) 提示词：由「已锁定的视觉基因 + 屏文案 + 品牌 Brief」派生（人可在页面上改，改了以人写的为准）
        Long dnaId = dnaService.activeDnaId(taskId);
        List<String> promptApplied = new ArrayList<>();
        List<String> promptOmitted = new ArrayList<>();
        String prompt = promptInput;
        String negativePrompt = negativeInput;
        if (StringUtils.isBlank(prompt) || StringUtils.isBlank(negativePrompt)) {
            DnaPromptBuilder.Prompt derived = dnaPromptBuilder.build(
                dnaService.activeDna(taskId), project.getProductName(), screenHint,
                briefService.get(taskId), screenText, moduleVisualRules,
                // ⑤：措辞种子跟生效版本走——页面上预览的那套说法与实际下发的必须是同一套
                dnaService.activePromptSeed(taskId));
            if (StringUtils.isBlank(prompt)) {
                prompt = derived.prompt();
                promptApplied = derived.applied();
            }
            if (StringUtils.isBlank(negativePrompt)) {
                negativePrompt = derived.negativePrompt();
            }
            // 只要正/负**任意一侧**是派生出来的，未放入的条目就都要留痕。
            // 以前这行写在"正向也是派生"的分支里：人写了正向提示词、只派生负向时，
            // 「禁用词因长度上限没放进去」会被静默丢掉（内测 S13 复查时发现）。
            promptOmitted.addAll(derived.omitted());
        }
        // 人工写了哪一侧，那一侧就没有带上品牌要求——**两侧要分开说**，因为进的词不一样：
        // 必显信息/主推卖点在正向，禁用词在负向。不区分就会给出"品牌要求没生效"这种
        // 说不清哪一半的提示（第一版就是这么写的，实测漏掉了"只人工写正向"这种最常见的情况）。
        promptOmitted.addAll(promptOmissionNotes(promptInput, negativeInput));

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
        // S13：把"填了却没进提示词"的条目落成可查字段（可读文案，分号连接）。
        // 事件 detail 里那份留着不动——它是给排障看的完整快照，这一列是给用户看的一句话。
        row.setPromptOmitted(promptOmitted.isEmpty() ? null : String.join("；", promptOmitted));
        row.setInputFileId(reference.getFileId());
        row.setInputAssetId(assetId);
        Map<String, Object> snapshot = inputSnapshot(reference, version, assetId, fitted);
        // R23：把"这次为什么用这张参考图""用了哪个模块的视觉表达"一起留痕。
        // 出图是异步的，事后要能回答"这屏当时按什么出的"，不能只靠猜。
        // 标签要和实际取图逻辑一致：兜底现在会优先「被登记为参考图」的附件，
        // 所以只有真的没有那种附件时才算"取最新附件"——否则这条留痕本身就在说谎。
        snapshot.put("referenceFrom", referenceFromLabel(fileId, reference));
        if (target != null && target.usable()) {
            snapshot.put("outputSpec", target.code());
            snapshot.put("outputSpecSize", target.width() + "x" + target.height());
            snapshot.put("outputSpecReason", target.reason());
        }
        if (StringUtils.isNotBlank(moduleVisualRules)) {
            snapshot.put("moduleVisualRules", singleLine(moduleVisualRules));
        }
        row.setInputJson(JsonUtils.toJsonString(snapshot));
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

        if (fitted.scaled()) {
            // 参考图被适配过就留一条事件：页面上要能回答「这张图是按多大的参考图出的」
            projectService.appendEvent(taskId, "GENERATION", "REFERENCE_SCALED",
                JsonUtils.toJsonString(Map.of(
                    "generationId", row.getId() == null ? 0L : row.getId(),
                    "fromWidth", fitted.fromWidth() == null ? 0 : fitted.fromWidth(),
                    "fromHeight", fitted.fromHeight() == null ? 0 : fitted.fromHeight(),
                    "toWidth", fitted.width() == null ? 0 : fitted.width(),
                    "toHeight", fitted.height() == null ? 0 : fitted.height(),
                    "maxPixels", fitted.maxPixels(),
                    "note", StringUtils.blankToDefault(fitted.note(), ""))));
        }

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
        // R7：因提示词长度上限未能放入的条目（必显信息/主推卖点/禁用词/屏文案截断）如实留痕，
        // 否则「我明明填了必显信息，出图却没体现」只能靠翻代码解释
        detail.put("promptOmitted", promptOmitted);
        detail.put("screenTextUsed", StringUtils.isNotBlank(screenText));
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

    /**
     * 压成一行（提示词与快照里都不该出现换行：换行在提示词里没有语义，只会让长度统计失真）。
     *
     * @param text 原文本（可空）
     * @return 单行文本；空返回 null
     */
    private static String singleLine(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 取本项目交付类型的默认输出规格（固定尺寸才有意义）。
     *
     * @param project 项目（取交付类型）
     * @return 目标尺寸；没有固定规格返回 null（照旧按工作流默认出图）
     */
    private CreativeOutputSpecResolver.TargetSize resolveTargetSize(CreativeProjectVo project) {
        try {
            if (project == null || StringUtils.isBlank(project.getDeliverableType())) {
                return null;
            }
            List<DpOutputSpec> specs = scenarioConfigService.listOutputSpecs(project.getDeliverableType());
            if (specs.isEmpty()) {
                return null;
            }
            CreativeOutputSpecResolver.TargetSize size = CreativeOutputSpecResolver.fixedSizeOf(specs.get(0));
            if (size != null) {
                log.info("出图尺寸按输出规格 {}：{}×{}（交付类型 {}）", size.code(), size.width(),
                    size.height(), project.getDeliverableType());
            }
            return size;
        } catch (Exception e) {
            log.warn("读取输出规格失败，出图尺寸回落工作流默认：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 人工指定提示词时，"品牌要求没进提示词"的留痕（内测 S13）。
     *
     * <p><b>为什么正负分开说</b>：品牌要求分两路进词——必显信息 / 主推卖点进**正向**，
     * 禁用词进**负向**。所以"人工写了正向"与"人工写了负向"漏掉的是不同的东西，
     * 合成一句"品牌要求没生效"用户不知道去补哪一半。第一版就是这么写的，
     * 实测漏掉了"只人工写正向"（最常见的那种）——因为那时整段逻辑挂在
     * "正负都人工指定"的分支上。</p>
     *
     * <p>纯函数、无依赖，便于单测逐条钉住。</p>
     *
     * @param promptInput        调用方传入的正向提示词（可空）
     * @param negativePromptInput 调用方传入的负向提示词（可空）
     * @return 说明列表（都不为空时返回空列表）
     */
    static List<String> promptOmissionNotes(String promptInput, String negativePromptInput) {
        List<String> notes = new ArrayList<>();
        if (StringUtils.isNotBlank(promptInput)) {
            notes.add("正向提示词由人工指定：必显信息 / 主推卖点没有自动追加，请自行确认画面与文案已包含");
        }
        if (StringUtils.isNotBlank(negativePromptInput)) {
            notes.add("负向提示词由人工指定：禁用词没有自动追加，请自行确认品牌红线已覆盖");
        }
        return notes;
    }

    /**
     * 参考图来源的标签（写进 {@code dp_generation.input_json} 的 {@code referenceFrom}）。
     *
     * <p>三档与 {@link #resolveReference} 一一对应：显式指定 / 被登记为参考图的附件 / 最新图片附件。
     * 以前只有两档（{@code MODULE_PLAN} 与 {@code LATEST_ATTACHMENT}），而兜底已经开始优先
     * REFERENCE——标签不改就会把"取了被登记为参考图的那张"说成"取最新一张"，
     * 排障时按这条留痕核对会得出相反结论。</p>
     *
     * @param fileId    显式指定的附件ID（可空）
     * @param reference 实际选中的附件
     * @return 标签
     */
    private static String referenceFromLabel(Long fileId, CpTaskFileVo reference) {
        if (fileId != null) {
            return "MODULE_PLAN";
        }
        return reference != null && "REFERENCE".equalsIgnoreCase(reference.getSourceType())
            ? "MARKED_REFERENCE"
            : "LATEST_ATTACHMENT";
    }

    /**
     * 挑本次出图的参考图（**参考图优先级只有这一处**，内测 S4 的收口）。
     *
     * <p>顺序：</p>
     * <ol>
     *   <li><b>显式指定</b>（{@code fileId}）：项目页「当前参考图」选中项、或模块规划里
     *       {@code referenceFileIds} 的第一项。指定了就必须存在，否则报错而不是悄悄换一张——
     *       "我选的是 A，出图用的是 B"是最难查的一类问题；</li>
     *   <li><b>被登记为参考图的附件</b>（{@code source_type=REFERENCE}，取最新一张）：
     *       内测 S4/S15 之前，设计侧上传的参考图与普通图片附件混在一起，兜底只能取"最新一张图片"，
     *       于是后传的产品图会把参考图顶掉；</li>
     *   <li><b>最新一张图片附件</b>：最后的兜底，保持与改造前一致（没有标注过角色时不能因此拒绝出图）。</li>
     * </ol>
     *
     * <p><b>为什么兜底要优先 REFERENCE</b>：{@code source_type} 是"这张图是干什么用的"的唯一声明，
     * 而 {@code createTime} 只说明"谁后传的"。拿后者当判据，等于让上传顺序决定出图输入。</p>
     *
     * <p>包可见且无实例依赖，便于单测逐条钉住（见 {@code CreativeReferencePrecedenceTest}）。</p>
     *
     * @param taskId 项目ID（仅用于报错文案）
     * @param fileId 显式指定的参考图附件ID（可空）
     * @param files  项目附件
     * @return 本次要喂给模型的参考图（一定非空）
     */
    static CpTaskFileVo resolveReference(Long taskId, Long fileId, List<CpTaskFileVo> files) {
        if (fileId != null) {
            return files.stream()
                .filter(f -> fileId.equals(f.getFileId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("参考图不在该项目附件中：" + fileId));
        }
        List<CpTaskFileVo> images = files.stream()
            .filter(f -> "IMAGE".equalsIgnoreCase(f.getFileKind()))
            .toList();
        List<CpTaskFileVo> marked = images.stream()
            .filter(f -> "REFERENCE".equalsIgnoreCase(f.getSourceType()))
            .toList();
        List<CpTaskFileVo> candidates = marked.isEmpty() ? images : marked;
        return candidates.stream()
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

    private Map<String, Object> inputSnapshot(CpTaskFileVo reference, ImageWorkflowVersion version,
                                              long assetId, ReferenceImageFitter.Fitted fitted) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("referenceFileId", reference.getFileId());
        snapshot.put("referenceFileName", reference.getFileName());
        snapshot.put("inputAssetId", assetId);
        snapshot.put("workflowCode", version.workflowCode());
        snapshot.put("workflowVersion", version.version());
        snapshot.put("modelCode", version.modelCode());
        snapshot.put("capabilityCode", version.capabilityCode());
        snapshot.put("workflowMaxPixels", version.maxPixels());
        // 送模型的图与附件原图可能不是同一份（该工作流按输入图尺寸出图，超上限必须适配）——如实记录
        snapshot.put("inputScaled", fitted.scaled());
        if (fitted.scaled()) {
            snapshot.put("inputFrom", fitted.fromWidth() + "x" + fitted.fromHeight());
            snapshot.put("inputTo", fitted.width() + "x" + fitted.height());
            snapshot.put("inputScaleNote", fitted.note());
            snapshot.put("inputAssetName", fitted.fileName());
        }
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
        // R29：屏级规则体检结论（确定性像素度量；空 = 这一屏没配规则，页面显示「未配置规则」）
        vo.setQaFindingsJson(row.getQaFindingsJson());
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
