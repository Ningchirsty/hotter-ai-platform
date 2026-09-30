package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.helper.CreativeTemplatePin;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpDetailPage;
import org.dromara.creative.domain.DpDetailPageVersion;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.domain.vo.DpDetailPageVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpCopyBlockTypeEnum;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.RendererClient;
import org.dromara.creative.helper.SimpleMultipartFile;
import org.dromara.creative.mapper.DpDetailPageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeLayoutService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.dromara.creative.service.ICreativeTemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详情页排版服务实现。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeLayoutServiceImpl implements ICreativeLayoutService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * POC 阶段统一用长图模板；R3.2 铺开后按屏类型选模板
     */
    private static final String PAGE_TEMPLATE_CODE = "longpage";
    /**
     * 排版模板版本：R10（V0.2 F1）起用 1.0.2（页宽改为请求参数 {@code --page-width}）。
     *
     * <p><b>为什么不就地改旧版本</b>：模板走「登记 + 发布 + 校验和比对」，
     * 就地改文件会让 {@code CreativeTemplateServiceImpl#requirePublished} 因校验和不一致
     * <b>直接拒绝排版</b>，而且对账时还会把已发布的旧版本退回草稿
     * （见 {@code CreativeTemplateServiceImpl:82-92} 与 {@code :149-168}）。
     * 所以 1.0.0 / 1.0.1 保持不动、留给历史项目与其已渲染版本；1.0.2 只把
     * {@code width: 750px} 换成 {@code width: var(--page-width, 750px)}，行为与 1.0.1 完全一致。</p>
     *
     * <p><b>上线顺序依赖</b>：渲染服务必须先提供 longpage/1.0.2，再由人在「视觉模板库」对账
     * （{@code POST /creative/templates/sync}）并发布；否则排版会直接报「模板未登记」。</p>
     */
    private static final String PAGE_TEMPLATE_VERSION = "1.0.2";

    /**
     * 排版页宽（px，请求级传给渲染服务）。默认 750 = 电商详情页口径；改成别的值即出别的宽度，
     * 且每一版排版都会把实际页宽记进 {@code dp_detail_page_version.page_width}。
     */
    @Value("${creative.page-width:750}")
    private int pageWidth;

    private static final String KIND_V08 = "V08";
    private static final String KIND_FINAL = "V10_FINAL";

    private static final String VERSION_RENDERED = "RENDERED";
    private static final String VERSION_APPROVED = "APPROVED";
    private static final String VERSION_REJECTED = "REJECTED";

    private static final String PAGE_V08_READY = "V08_READY";
    private static final String PAGE_REFINING = "REFINING";
    private static final String PAGE_FINAL = "FINAL";

    private final DpDetailPageMapper pageMapper;
    private final DpDetailPageVersionMapper versionMapper;
    private final DpGenerationMapper generationMapper;
    private final ICreativeProjectService projectService;
    private final ICreativeGateService gateService;
    private final ICreativeDnaService dnaService;
    private final ICreativeStoryboardService storyboardService;
    private final ICreativeGenerationService generationService;
    private final ICreativeTemplateService templateService;

    /**
     * 模块引擎（R24）：排版按**当前模块计划**取模板码。
     * <p>为什么模板取当前计划、而参考图/视觉表达取屏上冻的那份：模板决定「现在怎么渲染已有这些屏」，改模板不该被要求重拆分镜；参考图/视觉表达是「这一屏当时怎么出的图」，那是历史事实，必须冻在屏上。</p>
     */
    private final ICreativeModuleService moduleService;
    private final RendererClient rendererClient;
    private final IContentTaskService contentTaskService;
    private final ContentOssHelper contentOssHelper;
    /**
     * 文案与要点块（R7）：详情页正文/卖点/参数行的数据来源
     */
    private final ICreativeCopyService copyService;
    /**
     * 场景配置层（V0.2 B2）：页宽优先取该交付类型的默认输出规格（dp_output_spec）
     */
    private final ICreativeScenarioConfigService scenarioConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpDetailPageVo render(Long taskId) {
        gateService.requireCanProduce(taskId);
        CreativeProjectVo project = projectService.getProject(taskId);
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null || storyboard.getScreens() == null || storyboard.getScreens().isEmpty()) {
            throw new ServiceException("还没有分镜，无法排版");
        }

        // 0) 交付类型**有没有排版环节**（R26）：MAIN_IMAGE（商品主图）的流程里没有 LAYOUT，
        //    它是"按张交付的多张图"，不该被渲染成一张长图——那样产出的东西没人要，
        //    更糟的是看起来"成功"了。所以这里直接拒绝并说明，而不是照渲染。
        requireLayoutStep(project.getDeliverableType());

        // 0.1) 先把"用哪个模板"定下来（R23）：模块规划钉了模板就用它，钉了但不可用就直接报错。
        //    放在逐屏取图之前，是因为**配置错要先于做工作被发现**：否则用户要先等一遍
        //    "还没有已选定产出图"的提示，才能看到真正的模板问题。
        CreativeTemplatePin.Pinned pinned = pinnedTemplate(taskId, storyboard);
        var template = templateService.requirePublished(pinned.code(), pinned.version());

        // 1) 逐屏取「已选定」产出并内联为 data URI；没有选定的屏不编造，交给模板写明缺什么
        ObjectNode structure = MAPPER.createObjectNode();
        ArrayNode screensNode = structure.putArray("screens");
        ArrayNode renderScreens = MAPPER.createArrayNode();
        List<String> missing = new ArrayList<>();
        int selectedCount = 0;
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            DpGeneration selected = approvedGeneration(taskId, screen.getId());
            String imageDataUri = null;
            ObjectNode row = MAPPER.createObjectNode();
            row.put("screenNo", screen.getScreenNo());
            row.put("screenType", screen.getScreenType());
            row.put("screenTypeDesc", screen.getScreenTypeDesc());
            row.put("title", StringUtils.blankToDefault(screen.getTitle(), ""));
            row.put("subtitle", StringUtils.blankToDefault(screen.getSubtitle(), ""));
            row.put("body", StringUtils.blankToDefault(screen.getBodyText(), ""));
            row.put("soloStatement", StringUtils.blankToDefault(screen.getPictureSoloStatement(), ""));
            if (selected == null) {
                missing.add(screen.getScreenNo());
            } else {
                selectedCount++;
                row.put("imageGenerationId", selected.getId());
                row.put("imageFileId", selected.getOutputFileId() == null ? 0L : selected.getOutputFileId());
                byte[] bytes = generationService.preview(selected.getId());
                imageDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
            screensNode.add(row);

            ObjectNode renderRow = row.deepCopy();
            if (imageDataUri != null) {
                renderRow.put("image", imageDataUri);
            }
            renderScreens.add(renderRow);
        }
        if (selectedCount == 0) {
            throw new ServiceException("还没有任何「已选定」的产出图，无法排版：请先在分镜页逐屏出图并选定候选");
        }

        // 2) 模板已在第 0 步定下来并过了发布门（这里不再重复查）

        // 3) 渲染（素材已内联，渲染服务会拒绝任何外链）
        ObjectNode renderLayout = MAPPER.createObjectNode();
        JsonNode dna = dnaService.activeDna(taskId);
        ObjectNode dnaNode = renderLayout.putObject("dna");
        if (dna != null) {
            String background = dna.path("colors").path("background").asText("#ffffff");
            dnaNode.put("background", background);
            ArrayNode swatches = dnaNode.putArray("swatches");
            for (String key : List.of("primary", "secondary", "accent", "background")) {
                String hex = dna.path("colors").path(key).asText(null);
                if (StringUtils.isNotBlank(hex)) {
                    swatches.add(hex);
                }
            }
        }
        renderLayout.set("screens", renderScreens);
        // R7：把「文案与要点块」一并下发给渲染服务（卖点/正文分段/参数行）。
        // 只做加法——老模板会忽略不认识的字段（1.0.0 不受影响），1.0.1 才渲染它们。
        // MUST_SHOW 刻意不下发：它是「必须出现」的约束，由闸门与提示词负责，不是要印在页面上的字。
        renderLayout.set("copyBlocks", copyBlocksNode(taskId));
        renderLayout.put("footerNote", "本页由 AI 视觉工厂生成 · 基因 "
            + StringUtils.blankToDefault(dna == null ? null : dna.path("schema").asText(), "-")
            + " · 分镜 " + storyboard.getStoryboardNo()
            + " · 渲染于 " + LocalDateTime.now().withNano(0));

        long started = System.currentTimeMillis();
        // 页宽：优先取该交付类型"默认输出规格"（dp_output_spec.is_default），没有配置才回落到 creative.page-width。
        // 渲染服务把宽度作为 CSS 变量 --page-width 交给模板，并会核对"实际渲染宽度 == 请求宽度"。
        int renderWidth = resolvePageWidth(project);
        RendererClient.RenderResult result = rendererClient.render(
            pinned.code(), pinned.version(), "page", null, toMap(renderLayout), renderWidth);
        long cost = System.currentTimeMillis() - started;

        // 4) 长图登记为任务附件（复用内容模块的上传通道）
        DpDetailPage page = requireOrCreatePage(taskId);
        int nextVersion = (page.getCurrentVersion() == null ? 0 : page.getCurrentVersion()) + 1;
        String fileName = "detail-" + storyboard.getStoryboardNo() + "-v" + nextVersion + ".png";
        Long fileId = contentTaskService.uploadFile(taskId, null,
            new SimpleMultipartFile("file", fileName, "image/png", result.png()));

        // 5) 版本落库
        DpDetailPageVersion version = new DpDetailPageVersion();
        version.setDetailPageId(page.getId());
        version.setTaskId(taskId);
        version.setVersion(nextVersion);
        version.setKind(KIND_V08);
        version.setLayoutJson(JsonUtils.toJsonString(MAPPER.convertValue(structure, Map.class)));
        version.setRenderedFileId(fileId);
        version.setPageWidth(result.width());
        version.setPageHeight(result.height());
        version.setScreenCount(storyboard.getScreens().size());
        version.setStatus(VERSION_RENDERED);
        version.setRemark("渲染 " + result.width() + "×" + result.height() + "，服务端耗时 "
            + result.renderMs() + "ms（本机往返 " + cost + "ms），sha256=" + shortOf(result.sha256())
            + "，模板 " + pinned.code() + "@" + pinned.version()
            + (pinned.fromPlan() ? "（模块规划指定）" : "（默认）")
            + "（校验和 " + shortOf(result.templateChecksum()) + "）");
        versionMapper.insert(version);

        page.setCurrentVersion(nextVersion);
        page.setStatus(PAGE_V08_READY);
        pageMapper.updateById(page);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("version", nextVersion);
        detail.put("fileId", fileId);
        detail.put("pageWidth", result.width());
        detail.put("pageHeight", result.height());
        detail.put("renderMs", result.renderMs());
        detail.put("sha256", result.sha256());
        detail.put("templateChecksum", result.templateChecksum());
        detail.put("screensWithoutSelection", missing);
        projectService.appendEvent(taskId, "LAYOUT", "LAYOUT_RENDER",
            JsonUtils.toJsonString(detail));
        projectService.moveStage(taskId, DpVisualStageEnum.V08_READY, "LAYOUT_RENDER",
            JsonUtils.toJsonString(Map.of("version", nextVersion, "pageHeight", result.height())));
        return detail(taskId);
    }

    /**
     * 交付类型的流程里有排版环节吗（R26）。
     *
     * <p>判据取**场景配置的步骤表**（`dp_scenario_step`），而不是写死"ECOM_DETAIL 才有"——
     * 以后新增别的长图类交付类型时，只要配置里配了 LAYOUT 就能排版，不用改代码。</p>
     *
     * @param deliveryType 交付类型
     * @throws ServiceException 没有排版环节时抛出（消息说明为什么不做）
     */
    private void requireLayoutStep(String deliveryType) {
        List<org.dromara.creative.domain.DpScenarioStep> steps =
            scenarioConfigService.listSteps(deliveryType);
        boolean hasLayout = steps.stream()
            .anyMatch(step -> "LAYOUT".equalsIgnoreCase(step.getStepCode()));
        if (!hasLayout) {
            throw new ServiceException("交付类型「" + deliveryType
                + "」的流程里没有排版环节：它的产出是逐张图片（例如商品主图 800×800），"
                + "不是一张长图。请到出图页逐屏出图与选定；长图排版只适用于配了 LAYOUT 步骤的交付类型。");
        }
    }

    /**
     * 从分镜各屏的 spec_json 汇总"这次排版用哪个模板"（R23）。
     *
     * <p>规则本体在 {@link CreativeTemplatePin}（可单测）；这里只负责把屏的 spec 收集起来。
     * 为什么规则不写在这里：排版接口第一道是视觉门，新项目走不到模板那步，
     * 分支只有抽出去才能被单测钉死。</p>
     *
     * @param storyboard 本次排版的分镜
     * @return 模板选择
     */
    private CreativeTemplatePin.Pinned pinnedTemplate(Long taskId, DpStoryboardVo storyboard) {
        // 正式路径：当前模块计划里启用模块的模板码（改模板立刻生效，不必重拆分镜）
        List<String> codes = new ArrayList<>();
        for (DpProjectModule module : moduleService.listProjectModules(taskId)) {
            if (!"1".equals(module.getEnabled())) {
                codes.addAll(CreativeModuleServiceImpl.splitCodes(module.getTemplateCodes()));
            }
        }
        if (!codes.isEmpty()) {
            return CreativeTemplatePin.resolveCodes(codes, PAGE_TEMPLATE_CODE, PAGE_TEMPLATE_VERSION);
        }
        // 兜底：老项目没有模块计划时，仍认屏上冻着的模板码（历史分镜的兼容路径）
        List<String> specs = new ArrayList<>();
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            specs.add(screen.getSpecJson());
        }
        return CreativeTemplatePin.resolve(specs, PAGE_TEMPLATE_CODE, PAGE_TEMPLATE_VERSION);
    }

    /**
     * 详情页当前用的模板标识（页面展示用；与排版时同一套规则，但**不抛错**——
     * 展示接口不该因为配置冲突就 500，冲突会在真正排版时明确报出来）。
     *
     * @param storyboard 分镜（可空）
     * @return 形如 longpage@1.0.2
     */
    private String templateKeyOf(Long taskId, DpStoryboardVo storyboard) {
        if (storyboard == null || storyboard.getScreens() == null) {
            return PAGE_TEMPLATE_CODE + "@" + PAGE_TEMPLATE_VERSION;
        }
        try {
            CreativeTemplatePin.Pinned pinned = pinnedTemplate(taskId, storyboard);
            return pinned.code() + "@" + pinned.version();
        } catch (Exception e) {
            return PAGE_TEMPLATE_CODE + "@" + PAGE_TEMPLATE_VERSION;
        }
    }

    /**
     * 文案与要点块的渲染载荷（R7）。
     *
     * <p>下发的三类是「会出现在详情页上的文字」：卖点、正文分段、参数行。
     * 顺序按 renderer 的分组需要固定为 SELLING_POINT → BODY_SECTION → SPEC_ROW，
     * 组内按 sortNo 升序（sortNo 就是页面从上到下的顺序）。</p>
     *
     * <p>无数据时下发空数组而不是 null：模板侧只需判断「有没有内容」，
     * 不必再区分 null 与 []（少一个分支就少一类线上差异）。</p>
     *
     * @param taskId 项目ID
     * @return 数组节点
     */
    /**
     * 解析本次排版用的页宽（V0.2 B2）。
     *
     * <p>顺序：该交付类型在 {@code dp_output_spec} 里的**默认规格**（{@code is_default='1'}，
     * 由 {@code listOutputSpecs} 排到最前）→ 回落配置 {@code creative.page-width}。
     * 这样"750"这个数字的权威从配置项变成了场景配置；配置层读不到时也不阻断排版（降级 + 告警）。</p>
     *
     * @param project 项目（取交付类型）
     * @return 页宽（px）
     */
    private int resolvePageWidth(CreativeProjectVo project) {
        try {
            if (project != null && StringUtils.isNotBlank(project.getDeliverableType())) {
                List<DpOutputSpec> specs = scenarioConfigService.listOutputSpecs(project.getDeliverableType());
                if (!specs.isEmpty() && specs.get(0).getWidth() != null && specs.get(0).getWidth() > 0) {
                    DpOutputSpec spec = specs.get(0);
                    log.info("排版页宽取默认输出规格 {} = {}px（交付类型 {}）",
                        spec.getSpecCode(), spec.getWidth(), project.getDeliverableType());
                    return spec.getWidth();
                }
                log.info("交付类型 {} 没有可用的默认输出规格，回落 creative.page-width={}",
                    project.getDeliverableType(), pageWidth);
            }
        } catch (Exception e) {
            log.warn("读取默认输出规格失败，回落 creative.page-width={}：{}", pageWidth, e.getMessage());
        }
        return pageWidth;
    }

    private ArrayNode copyBlocksNode(Long taskId) {
        ArrayNode array = MAPPER.createArrayNode();
        List<DpCopyBlockVo> blocks = copyService.list(taskId, null);
        for (String type : List.of(DpCopyBlockTypeEnum.SELLING_POINT.getCode(),
            DpCopyBlockTypeEnum.BODY_SECTION.getCode(), DpCopyBlockTypeEnum.SPEC_ROW.getCode())) {
            for (DpCopyBlockVo block : blocks) {
                if (!type.equals(block.getBlockType())) {
                    continue;
                }
                if (StringUtils.isBlank(block.getTitle()) && StringUtils.isBlank(block.getContent())) {
                    // 空块不下发（它渲染出来就是一块空白），但也不删——由人在页面上处理
                    continue;
                }
                ObjectNode row = MAPPER.createObjectNode();
                row.put("blockType", block.getBlockType());
                row.put("title", StringUtils.blankToDefault(block.getTitle(), ""));
                row.put("content", StringUtils.blankToDefault(block.getContent(), ""));
                row.put("sortNo", block.getSortNo() == null ? 0 : block.getSortNo());
                array.add(row);
            }
        }
        return array;
    }

    @Override
    public DpDetailPageVo detail(Long taskId) {
        DpDetailPage page = findPage(taskId);
        DpDetailPageVo vo = new DpDetailPageVo();
        vo.setRendererAvailable(rendererClient.version() != null);
        vo.setTemplateKey(templateKeyOf(taskId, storyboardService.latest(taskId)));
        vo.setScreensWithoutSelection(missingScreens(taskId));
        if (page == null) {
            vo.setTaskId(taskId);
            vo.setCurrentVersion(0);
            vo.setStatus("DRAFT");
            vo.setStatusDesc("未排版");
            return vo;
        }
        vo.setId(page.getId());
        vo.setTaskId(page.getTaskId());
        vo.setCurrentVersion(page.getCurrentVersion());
        vo.setStatus(page.getStatus());
        vo.setStatusDesc(pageStatusDesc(page.getStatus()));
        vo.setRemark(page.getRemark());
        List<DpDetailPageVersion> rows = versionMapper.selectList(
            new LambdaQueryWrapper<DpDetailPageVersion>()
                .eq(DpDetailPageVersion::getTaskId, taskId)
                .orderByDesc(DpDetailPageVersion::getVersion));
        List<DpDetailPageVo.DpDetailPageVersionVo> versions = new ArrayList<>();
        for (DpDetailPageVersion row : rows) {
            versions.add(toVersionVo(row));
        }
        vo.setVersions(versions);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpDetailPageVo.DpDetailPageVersionVo review(Long taskId, Long versionId, boolean approve,
                                                       String comment) {
        DpDetailPageVersion version = requireVersion(taskId, versionId);
        if (!VERSION_RENDERED.equals(version.getStatus())) {
            throw new ServiceException("该版本已处理过（当前状态：" + version.getStatus() + "），不能重复终审");
        }
        version.setStatus(approve ? VERSION_APPROVED : VERSION_REJECTED);
        version.setReviewBy(LoginHelper.getUserId());
        version.setReviewAt(LocalDateTime.now());
        version.setReviewComment(comment);
        versionMapper.updateById(version);

        DpDetailPage page = findPage(taskId);
        if (page != null && version.getVersion().equals(page.getCurrentVersion())) {
            page.setStatus(approve ? PAGE_REFINING : PAGE_V08_READY);
            pageMapper.updateById(page);
        }
        projectService.appendEvent(taskId, "FINAL",
            approve ? "LAYOUT_APPROVED" : "LAYOUT_REJECTED",
            JsonUtils.toJsonString(Map.of("versionId", versionId, "version", version.getVersion(),
                "comment", StringUtils.blankToDefault(comment, ""))));
        if (approve) {
            projectService.moveStage(taskId, DpVisualStageEnum.DESIGN_REFINING, "LAYOUT_APPROVED",
                JsonUtils.toJsonString(Map.of("version", version.getVersion())));
        }
        return toVersionVo(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpDetailPageVo uploadFinal(Long taskId, MultipartFile file, String comment) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择精修后的长图");
        }
        DpDetailPage page = requireOrCreatePage(taskId);
        int nextVersion = (page.getCurrentVersion() == null ? 0 : page.getCurrentVersion()) + 1;
        Long fileId = contentTaskService.uploadFile(taskId, null, file);

        DpDetailPageVersion version = new DpDetailPageVersion();
        version.setDetailPageId(page.getId());
        version.setTaskId(taskId);
        version.setVersion(nextVersion);
        version.setKind(KIND_FINAL);
        version.setLayoutJson(null);
        version.setRenderedFileId(fileId);
        version.setStatus(VERSION_APPROVED);
        version.setReviewBy(LoginHelper.getUserId());
        version.setReviewAt(LocalDateTime.now());
        version.setReviewComment(StringUtils.blankToDefault(comment, "人工精修后上传"));
        int[] size = readImageSize(file);
        version.setPageWidth(size[0]);
        version.setPageHeight(size[1]);
        version.setScreenCount(null);
        version.setRemark("人工精修最终版（V1.0）");
        versionMapper.insert(version);

        page.setCurrentVersion(nextVersion);
        page.setStatus(PAGE_FINAL);
        pageMapper.updateById(page);

        projectService.appendEvent(taskId, "FINAL", "FINAL_UPLOADED",
            JsonUtils.toJsonString(Map.of("versionId", version.getId(), "version", nextVersion,
                "fileId", fileId)));
        projectService.moveStage(taskId, DpVisualStageEnum.COMPLETED, "FINAL_UPLOADED",
            JsonUtils.toJsonString(Map.of("version", nextVersion)));
        return detail(taskId);
    }

    @Override
    public byte[] preview(Long taskId, Long versionId) {
        DpDetailPageVersion version = requireVersion(taskId, versionId);
        if (version.getRenderedFileId() == null) {
            throw new ServiceException("该版本没有可预览的长图");
        }
        List<CpTaskFileVo> files = contentTaskService.listFiles(taskId);
        for (CpTaskFileVo f : files) {
            if (version.getRenderedFileId().equals(f.getFileId())) {
                return contentOssHelper.getBytes(f.getFileRef());
            }
        }
        throw new ServiceException("长图附件不存在或已被删除：" + version.getRenderedFileId());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private DpGeneration approvedGeneration(Long taskId, Long screenId) {
        if (screenId == null) {
            return null;
        }
        List<DpGeneration> rows = generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .eq(DpGeneration::getScreenId, screenId)
            .eq(DpGeneration::getStatus, DpGenerationStatusEnum.APPROVED.getCode())
            .orderByDesc(DpGeneration::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> missingScreens(Long taskId) {
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        List<String> missing = new ArrayList<>();
        if (storyboard == null || storyboard.getScreens() == null) {
            return missing;
        }
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            if (approvedGeneration(taskId, screen.getId()) == null) {
                missing.add(screen.getScreenNo());
            }
        }
        return missing;
    }

    private DpDetailPage findPage(Long taskId) {
        List<DpDetailPage> rows = pageMapper.selectList(new LambdaQueryWrapper<DpDetailPage>()
            .eq(DpDetailPage::getTaskId, taskId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DpDetailPage requireOrCreatePage(Long taskId) {
        DpDetailPage page = findPage(taskId);
        if (page != null) {
            return page;
        }
        DpDetailPage created = new DpDetailPage();
        created.setTaskId(taskId);
        created.setCurrentVersion(0);
        created.setStatus(PAGE_V08_READY);
        pageMapper.insert(created);
        return created;
    }

    private DpDetailPageVersion requireVersion(Long taskId, Long versionId) {
        DpDetailPageVersion version = versionMapper.selectById(versionId);
        if (version == null || !taskId.equals(version.getTaskId())) {
            throw new ServiceException("版本不属于该项目：" + versionId);
        }
        return version;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(ObjectNode node) {
        return MAPPER.convertValue(node, Map.class);
    }

    private static int[] readImageSize(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image != null) {
                return new int[] {image.getWidth(), image.getHeight()};
            }
        } catch (Exception e) {
            // 读不出尺寸不影响上传：尺寸只是展示信息，不能因此把用户上传的精修图拒掉
            log.warn("读取精修图尺寸失败：{}", e.getMessage());
        }
        return new int[] {0, 0};
    }

    private DpDetailPageVo.DpDetailPageVersionVo toVersionVo(DpDetailPageVersion row) {
        DpDetailPageVo.DpDetailPageVersionVo vo = new DpDetailPageVo.DpDetailPageVersionVo();
        vo.setId(row.getId());
        vo.setVersion(row.getVersion());
        vo.setKind(row.getKind());
        vo.setKindDesc(KIND_FINAL.equals(row.getKind()) ? "最终版（人工精修）" : "机排版 V0.8");
        vo.setRenderedFileId(row.getRenderedFileId());
        vo.setPageWidth(row.getPageWidth());
        vo.setPageHeight(row.getPageHeight());
        vo.setScreenCount(row.getScreenCount());
        vo.setStatus(row.getStatus());
        vo.setStatusDesc(versionStatusDesc(row.getStatus()));
        vo.setReviewBy(row.getReviewBy());
        vo.setReviewAt(row.getReviewAt());
        vo.setReviewComment(row.getReviewComment());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        vo.setPreviewable(row.getRenderedFileId() != null);
        return vo;
    }

    private static String versionStatusDesc(String status) {
        return switch (StringUtils.blankToDefault(status, "")) {
            case VERSION_RENDERED -> "已渲染待终审";
            case VERSION_APPROVED -> "已通过";
            case VERSION_REJECTED -> "已打回";
            default -> status;
        };
    }

    private static String pageStatusDesc(String status) {
        return switch (StringUtils.blankToDefault(status, "")) {
            case PAGE_V08_READY -> "机排版完成";
            case PAGE_REFINING -> "人工精修中";
            case PAGE_FINAL -> "最终版已交付";
            default -> status;
        };
    }

    private static String shortOf(String value) {
        return value == null ? "(无)" : value.substring(0, Math.min(8, value.length()));
    }

}
