package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.helper.ReferenceImageFitter;
import org.dromara.content.enums.ContentFileSourceEnum;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.helper.CreativeOutputSpecResolver;
import org.dromara.creative.helper.CreativeImageRuleChecker;
import org.dromara.creative.helper.CreativeQaRules;
import org.dromara.creative.helper.CreativeScreenModuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.vo.CpOutputCheckVo;
import org.dromara.content.service.IContentOutputCheckService;
import org.dromara.content.service.IContentProductService;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeScreenSkeletonRegistry;
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

    /** 场景配置（R27）：选定后按交付类型的输出规格规格化交付图 */
    private final ICreativeScenarioConfigService scenarioConfigService;

    /** 内容任务服务（R27：规格化后的交付图要登记成任务附件） */
    private final IContentTaskService contentTaskService;
    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final IContentOutputCheckService outputCheckService;
    private final IContentProductService productService;

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

        // 选定即按输出规格规格化（R27）：主图的规格是 800×800（1:1），但现有工作流的契约是
        // "画布由输入图与 resolution 决定"（wf-whitebg 固定 1536 档），**送模型时改输入尺寸并不能
        // 决定产出尺寸**——真机实测送 800×800 进去，出来仍是 1536×1536。
        // 所以规格化必须发生在**产出侧**：把选定的这张图等比适配到规格尺寸后再作为交付图。
        // 做成"选定即规格化"而不是"生成时"，是因为只有选定那一下才确定"这张就是要交付的图"。
        normalizeDeliveryIfNeeded(taskId, row);

        // 选定即做「屏级规则体检」（R29，文档 §20 qaRules）：对**交付图**（规格化之后那张）
        // 做确定性像素度量——是不是 1:1、最短边够不够、有没有透明通道、边缘白度、主体占比、有没有贴边。
        // 只报告不判决：HARD 项不自动筛除候选（是否让平台硬性项自动筛除属于产品决策，不由代码替人拍板）。
        inspectScreenRules(taskId, row);

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
        // R7：把这一屏的文案作为「画面描述」传进去（画面独白 → 正文 → 标题，取第一个非空的）。
        // 屏文案说的就是「这一屏画面要讲什么」，而此前它完全不进提示词（prompt/negative 都传 null），
        // 批量出图只能按屏类型猜——两个卖点屏是同一屏类型，猜出来的画面必然雷同。
        // negative 仍传 null：负向词由视觉基因与品牌 Brief 的禁用词提供，不在这里重复喂。
        String screenText = firstNonBlank(screen.getPictureSoloStatement(), screen.getBodyText(),
            screen.getTitle());
        // V0.2 R23：模块规划里给这一屏配的「参考图」与「视觉表达」就在屏的 spec_json 里
        // （分镜生成时烙进去的）。这里把它们取出来喂给出图：
        //   * 参考图 → 用模块指定的那张，而不是"最近上传的那张"（同一分镜不同模块可以各用各的参考图）；
        //   * 视觉表达 → 作为该屏的额外视觉约束进提示词。
        // 取不到就照旧（回落最近一张附件 / 不加额外约束），不会因为解析失败就让出图跑不起来。
        CreativeScreenModuleConfig moduleConfig = CreativeScreenModuleConfig.parse(screen.getSpecJson());
        DpGenerationVo created = generationService.submitForScreen(
            taskId, screen.getId(), hint, screenText, null, null, screen.getWorkflowCode(), null, null,
            moduleConfig.referenceFileId(), moduleConfig.visualRules());
        markScreen(taskId, screen.getId(), "GENERATING");
        return created;
    }

    /**
     * 按输出规格规格化交付图（V0.2 R27）。
     *
     * <p><b>为什么在产出侧做</b>：工作流契约是"画布由输入图与 resolution 决定"
     * （`wf-whitebg-qwen21` 固定 1536 档、`wf-i2i-qwen21` 用 1536 总像素预算），
     * 所以把参考图裁成 800×800 送进去，产出仍是 1536×1536。要得到规格尺寸，只能在产出图上做。</p>
     *
     * <p>做法：等比缩放到盖住目标尺寸 → 中心裁切 → 登记为**新的任务附件**，并把该候选的
     * `output_file_id` 指向它（交付/下载看到的就是规格尺寸那张）。原始产出仍在图像内核的素材里，
     * 质检记录也仍指向原产出（审计不断链）。适配结果写进 `input_json.outputNormalized` 与事件。</p>
     *
     * @param taskId 项目ID
     * @param row    已选定的候选
     */
    private void normalizeDeliveryIfNeeded(Long taskId, DpGeneration row) {
        try {
            if (row.getOutputAssetId() == null) {
                return;
            }
            CreativeProjectVo project = projectService.getProject(taskId);
            List<DpOutputSpec> specs = scenarioConfigService.listOutputSpecs(project.getDeliverableType());
            if (specs.isEmpty()) {
                return;
            }
            CreativeOutputSpecResolver.TargetSize target = CreativeOutputSpecResolver.fixedSizeOf(specs.get(0));
            if (target == null || !target.usable()) {
                return;
            }
            byte[] raw = generationService.preview(row.getId());
            ReferenceImageFitter.Fitted fitted =
                ReferenceImageFitter.fitTo(raw, "candidate-" + row.getCandidateNo() + ".png",
                    target.width(), target.height());
            if (!fitted.scaled()) {
                log.info("候选 {} 已经是规格尺寸 {}×{}，无需规格化", row.getId(), target.width(), target.height());
                return;
            }
            Long fileId = contentTaskService.uploadFile(taskId, null,
                new SimpleMultipartFile("file", fitted.fileName(), fitted.contentType(), fitted.bytes()),
                ContentFileSourceEnum.GENERATED.getCode());
            row.setOutputFileId(fileId);
            generationMapper.updateById(row);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("outputNormalized", true);
            snapshot.put("outputSpec", target.code());
            snapshot.put("outputSpecSize", target.width() + "x" + target.height());
            snapshot.put("outputNormalizedNote", fitted.note());
            snapshot.put("outputNormalizedFileId", fileId);
            projectService.appendEvent(taskId, "GENERATION", "OUTPUT_NORMALIZED",
                toJson(snapshot));
            log.info("候选 {} 已按输出规格 {} 规格化为 {}×{}（附件 {}）",
                row.getId(), target.code(), target.width(), target.height(), fileId);
        } catch (Exception e) {
            // 规格化失败不该让"选定"失败：如实记一条事件，交付图就还是原始尺寸
            log.warn("候选 {} 按输出规格规格化失败：{}", row.getId(), e.getMessage());
            try {
                projectService.appendEvent(taskId, "GENERATION", "OUTPUT_NORMALIZE_FAILED",
                    toJson(Map.of("generationId", row.getId(), "reason", String.valueOf(e.getMessage()))));
            } catch (Exception ignored) {
                // 事件也写不进去就算了，日志里有
            }
        }
    }

    /**
     * 屏级规则体检（V0.2 R29，文档 §20 {@code qaRules} 的落地）。
     *
     * <p><b>查什么</b>：这一屏的模块在模块库里配的 qaRules（分镜生成时已冻进屏的 {@code spec_json}）
     * 对**交付图**逐项做确定性像素度量。交付图优先取规格化后的附件（{@code output_file_id}）——
     * 那才是要交付出去的那张；没有规格化过就取原始产出。</p>
     *
     * <p><b>没配规则就不检查</b>：结论里写 {@code NOT_CONFIGURED}，页面照实显示"未配置"，
     * 绝不用一套隐式默认值假装检查过（那会制造"通过"的假象）。</p>
     *
     * <p><b>只报告不判决</b>：不因为 HARD 项未通过就改动候选状态——是否让平台硬性项自动筛除
     * 属于产品决策（与文档 §25 第 3 步同类），代码不替人拍板。</p>
     *
     * @param taskId 项目ID
     * @param row    已选定的候选
     */
    private void inspectScreenRules(Long taskId, DpGeneration row) {
        try {
            DpStoryboardScreen screen = row.getScreenId() == null ? null : screenMapper.selectById(row.getScreenId());
            CreativeScreenModuleConfig config = CreativeScreenModuleConfig.parse(
                screen == null ? null : screen.getSpecJson());
            CreativeQaRules rules = CreativeQaRules.parse(config.qaRules());
            byte[] bytes;
            if (row.getOutputFileId() != null) {
                bytes = projectService.readFileContent(taskId, row.getOutputFileId()).bytes();
            } else {
                bytes = generationService.preview(row.getId());
            }
            CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(bytes, rules);
            row.setQaFindingsJson(report.toJson());
            generationMapper.updateById(row);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generationId", row.getId());
            payload.put("screenId", row.getScreenId() == null ? 0L : row.getScreenId());
            payload.put("moduleCode", screen == null ? "" : moduleCodeOf(screen));
            payload.put("verdict", report.verdict());
            payload.put("hardFailed", report.hardFailed());
            payload.put("softFailed", report.softFailed());
            payload.put("inspectedFileId", row.getOutputFileId() == null ? "" : row.getOutputFileId());
            projectService.appendEvent(taskId, "QA", "SCREEN_RULES_CHECKED", toJson(payload));
            log.info("候选 {} 屏级规则体检：verdict={} HARD未过={} SOFT未过={}",
                row.getId(), report.verdict(), report.hardFailed(), report.softFailed());
        } catch (Exception e) {
            // 体检失败不该让"选定"失败：留一条事件说明原因，页面显示"未做体检"而不是"通过"
            log.warn("候选 {} 屏级规则体检失败：{}", row.getId(), e.getMessage());
            try {
                projectService.appendEvent(taskId, "QA", "SCREEN_RULES_CHECK_FAILED",
                    toJson(Map.of("generationId", row.getId(), "reason", String.valueOf(e.getMessage()))));
            } catch (Exception ignored) {
                // 事件也写不进去就算了，日志里有
            }
        }
    }

    /**
     * 从屏规格里取模块编码（事件载荷用；取不到给空串，不让它成为失败原因）。
     *
     * @param screen 屏
     * @return 模块编码
     */
    private static String moduleCodeOf(DpStoryboardScreen screen) {
        String spec = screen.getSpecJson();
        if (StringUtils.isBlank(spec)) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(spec);
            return StringUtils.blankToDefault(node.path("moduleCode").asText(null), "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 取第一个非空值（屏文案的三个候选字段有优先级：画面独白最具体，标题最笼统）。
     *
     * @param values 候选值
     * @return 第一个非空值；都没有返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
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

        // 基准一：本次出图实际喂进模型的参考图（回答「风格改动了多少」）。
        // 结论不一致 → 不参与选定（既有「只筛除不放行」口径）。
        Long checkId = outputCheckService.run(taskId, row.getInputFileId(), null, resultFile, remark);
        row.setQaCheckId(checkId);
        row.setQaVerdict(null);

        // 基准二：产品图（回答「产品还是不是那个产品」）。用刚才已登记的成品图附件再跑一次，
        // 不重复上传，避免同一张生成图在附件表里出现两条。
        Long productFileId = productService.ensureProductAttachment(taskId);
        row.setProductFileId(productFileId);
        row.setProductCheckId(null);
        row.setProductVerdict(null);
        if (productFileId != null) {
            try {
                CpOutputCheckVo first = outputCheckService.getDetail(checkId);
                Long resultFileId = first == null ? null : first.getResultFileId();
                if (resultFileId != null) {
                    row.setProductCheckId(outputCheckService.runWithFiles(taskId, productFileId, resultFileId,
                        (remark == null ? "" : remark + "；") + "基准=产品图"));
                }
            } catch (Exception e) {
                // 产品基准跑不起来不该让「参考图基准」这条主线失败：如实记原因，页面照实显示
                log.warn("产品图基准质检提交失败 generationId={} reason={}", row.getId(), e.getMessage());
                row.setErrorMessage("产品图基准质检未提交：" + e.getMessage());
            }
        }
        generationMapper.updateById(row);
        projectService.appendEvent(taskId, "QA", "QA_SUBMIT",
            toJson(Map.of("generationId", row.getId(), "checkId", checkId,
                "referenceFileId", row.getInputFileId(),
                "productFileId", productFileId == null ? "" : productFileId,
                "productCheckId", row.getProductCheckId() == null ? "" : row.getProductCheckId())));
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
        refreshProductQa(taskId);
    }

    /**
     * 回填「以产品图为基准」的质检结论。
     *
     * <p><b>只提示，不自动筛除</b>：产品图与生成图的差异很可能正是设计意图（换背景、换角度、
     * 换景别），自动按它筛除等于替人做艺术判断——那是这套系统一直拒绝做的事。
     * 这里只把结论写进记录并留一条事件，让页面把它响亮地摆出来，决定权仍在人手上。</p>
     *
     * @param taskId 项目ID
     */
    private void refreshProductQa(Long taskId) {
        List<DpGeneration> pending = generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .isNotNull(DpGeneration::getProductCheckId)
            .isNull(DpGeneration::getProductVerdict));
        for (DpGeneration row : pending) {
            try {
                CpOutputCheckVo check = outputCheckService.getDetail(row.getProductCheckId());
                if (check == null || !"DONE".equalsIgnoreCase(check.getStatus())) {
                    if (check != null && "FAILED".equalsIgnoreCase(check.getStatus())) {
                        row.setProductVerdict(VERDICT_UNCERTAIN);
                        generationMapper.updateById(row);
                        projectService.appendEvent(taskId, "QA", "QA_PRODUCT_UNCERTAIN",
                            toJson(Map.of("generationId", row.getId(),
                                "checkId", row.getProductCheckId(),
                                "reason", StringUtils.blankToDefault(check.getFailureReason(), "未知原因"))));
                    }
                    continue;
                }
                row.setProductVerdict(check.getVerdict());
                generationMapper.updateById(row);
                projectService.appendEvent(taskId, "QA", "QA_PRODUCT_" + StringUtils.blankToDefault(
                    check.getVerdict(), "UNKNOWN"),
                    toJson(Map.of("generationId", row.getId(),
                        "checkId", check.getCheckId(),
                        "verdict", StringUtils.blankToDefault(check.getVerdict(), ""),
                        "baseline", "product",
                        "note", "产品图基准：只提示，不自动筛除")));
            } catch (Exception e) {
                log.warn("回填候选 {} 的产品图基准结论失败：{}", row.getId(), e.getMessage());
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

    /**
     * 把屏实体转成 VO（手写映射，刻意不整表拷贝）。
     *
     * <p><b>R7 踩过的坑</b>：这里原来只映射了 id/屏号/类型/状态等字段，**漏掉了四个文案字段**，
     * 而 R7 的「屏文案进提示词」正是从 {@code pictureSoloStatement/bodyText/title} 取值——
     * 于是 {@code submitScreen} 拿到的 VO 里文案全是 null，屏文案进提示词变成静默空转
     * （实测：单屏重出图的提示词里没有「本屏画面要讲什么」这一句）。漏字段不会报错，
     * 只会让功能悄悄不生效，所以这里把文案字段补齐，并在下面留注释说明它们为什么必须在这里。</p>
     *
     * @param screen 屏实体
     * @return 屏 VO
     */
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
        // 文案字段：出图提示词的「本屏画面要讲什么」就从这三列按优先级取（画面独白 → 正文 → 标题）
        vo.setTitle(screen.getTitle());
        vo.setSubtitle(screen.getSubtitle());
        vo.setBodyText(screen.getBodyText());
        vo.setPictureSoloStatement(screen.getPictureSoloStatement());
        vo.setRemark(screen.getRemark());
        // 画面用途：类型描述优先（与分镜页面展示一致）
        vo.setScreenTypeDesc(screenTypeDesc(screen.getScreenType()));
        return vo;
    }

    /**
     * 屏类型的展示短名（C′：与分镜服务共用屏骨架契约的同一份映射，不再各写一份 switch）。
     *
     * @param type 屏类型
     * @return 展示短名；契约里没有的类型原样返回（历史分镜可能有已下线的屏类型）
     */
    private static String screenTypeDesc(String type) {
        return CreativeScreenSkeletonRegistry.skeleton().descOf(type);
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
        vo.setProductVerdict(row.getProductVerdict());
        // R29：屏级规则体检结论（选定那一刻算出来的，页面直接显示）
        vo.setQaFindingsJson(row.getQaFindingsJson());
        vo.setErrorCode(row.getErrorCode());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setPreviewable(row.getOutputAssetId() != null);
        vo.setRetryable(status != null && status.isRetryable());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

}
