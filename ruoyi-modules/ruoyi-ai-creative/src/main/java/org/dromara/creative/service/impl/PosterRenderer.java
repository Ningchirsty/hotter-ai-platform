package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.content.enums.ContentFileSourceEnum;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpCopyBlockTypeEnum;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeDeliveryManifest;
import org.dromara.creative.helper.CreativePosterLayout;
import org.dromara.creative.helper.CreativeRenderer;
import org.dromara.creative.helper.CreativeScreenModuleConfig;
import org.dromara.creative.helper.CreativeStepTypes;
import org.dromara.creative.helper.RendererClient;
import org.dromara.creative.helper.SimpleMultipartFile;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.dromara.creative.service.ICreativeTemplateService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海报渲染器（V0.2 R52，文档 §9.4 / §26 的 PosterRenderer）。
 *
 * <p><b>它解决什么</b>：品牌海报的交付物不是长图、也不是"各屏原图打包"，而是
 * <b>同一套版面按多档尺寸各出一张</b>（3:4 / 9:16 / 16:9 —— 见 {@code dp_output_spec}）。
 * R50 真机干跑时海报卡在「版式」这一步：那时它既没有渲染器，也没有海报模板。</p>
 *
 * <p><b>它复用长图那套基础设施</b>：模板走"登记 + 发布 + 校验和比对"
 * （{@link ICreativeTemplateService#requirePublished}），渲染走独立渲染服务的
 * {@code POST /render}（素材内联、可复现），产出登记为任务附件。
 * 差异只有两处：<b>一个版面渲 N 档</b>、<b>按模块取主视觉而不是按屏拼长图</b>。</p>
 *
 * <p><b>门禁与守卫</b>：视觉门未过不放行（与出图/排版同一条约束）；交付类型必须有
 * {@code step_type=LAYOUT} 的步骤（没排版环节的类型不该有海报渲染器）；
 * 一张已选定的产出图都没有时**在渲染之前**就拒绝（不浪费一次渲染、也不产出"空海报"）。</p>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PosterRenderer implements CreativeRenderer {

    /** 渲染器编码（文档 §26 的 POSTER） */
    public static final String CODE = "POSTER";

    /** 海报模板（渲染服务里的 templates/poster/<version>；由「视觉模板库」对账后发布） */
    private static final String TEMPLATE_CODE = "poster";
    private static final String TEMPLATE_VERSION = "1.0.0";

    /** 渲染模式：排版类，页面按「整页」截图（模板的 #page-root 就是整张海报） */
    private static final String RENDER_MODE = "page";

    /** 事件类型/动作（阶段变更与操作日志里都用它，便于检索） */
    private static final String EVENT_TYPE = "POSTER";
    private static final String ACTION_RENDERED = "POSTER_RENDERED";

    private final ICreativeProjectService projectService;
    private final ICreativeScenarioConfigService scenarioConfigService;
    private final ICreativeStoryboardService storyboardService;
    private final ICreativeGenerationService generationService;
    private final ICreativeGateService gateService;
    private final ICreativeTemplateService templateService;
    private final ICreativeCopyService copyService;
    private final ICreativeDnaService dnaService;
    private final IContentTaskService contentTaskService;
    private final DpGenerationMapper generationMapper;
    private final RendererClient rendererClient;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "海报渲染器";
    }

    @Override
    public String targetStep() {
        return "POSTER_LAYOUT";
    }

    @Override
    public boolean implemented() {
        return true;
    }

    @Override
    public String note() {
        return "把已选定的主视觉按输出规格逐档排版成海报（模板 poster，独立渲染服务），"
            + "3:4 / 9:16 / 16:9 各出一张";
    }

    @Override
    public Outcome render(Context context) {
        Long taskId = context.taskId();
        if (taskId == null) {
            throw new ServiceException("taskId 不能为空。");
        }
        // 门禁与出图/排版一致：没过视觉门不放行（避免拿没定稿的视觉方案去印海报）
        gateService.requireCanProduce(taskId);

        CreativeProjectVo project = projectService.getProject(taskId);
        String deliveryType = project.getDeliverableType();
        // 有排版类步骤才该走海报渲染器：这是"配置决定形态"的那条判据（R52 起按 step_type 判）
        if (!CreativeStepTypes.hasLayout(scenarioConfigService.listSteps(deliveryType))) {
            throw new ServiceException("交付类型「" + deliveryType + "」的流程里没有排版类步骤："
                + "它的产出不是一张排版好的海报，请用「生成交付产物」按渲染模式选渲染器。");
        }
        var template = templateService.requirePublished(TEMPLATE_CODE, TEMPLATE_VERSION);
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null || storyboard.getScreens() == null || storyboard.getScreens().isEmpty()) {
            throw new ServiceException("还没有概念/主视觉，无法生成海报");
        }

        // 1) 逐屏取「已选定」产出并内联为 data URI：没有选定的屏不编造，交给模板画提示块
        List<CreativePosterLayout.PosterModule> modules = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int withImage = 0;
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            DpGeneration selected = approvedGeneration(taskId, screen.getId());
            String dataUri = null;
            if (selected == null) {
                missing.add(StringUtils.blankToDefault(screen.getScreenNo(), String.valueOf(screen.getId())));
            } else {
                withImage++;
                byte[] bytes = generationService.preview(selected.getId());
                dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
            modules.add(new CreativePosterLayout.PosterModule(
                CreativeScreenModuleConfig.moduleCodeOf(screen.getSpecJson()),
                screen.getScreenNo(), screen.getScreenType(), screen.getTitle(), dataUri, selected == null ? null : selected.getId()));
        }
        if (withImage == 0) {
            throw new ServiceException("还没有任何「已选定」的主视觉产出图，无法生成海报："
                + "请先在出图页逐屏出图并选定候选");
        }

        // 2) 文案：主张优先取「主张」那一屏的标题，其次必显信息块，最后才用项目名兜底
        //    候选里可能有 null（"没有这一项"是正常情况），一律走 CreativePosterLayout.candidates
        //    —— List.of 遇 null 直接抛 NPE（R52 真机第一跑就栽在这里）
        CreativePosterLayout.PosterModule campaign = firstOfModule(modules, "CAMPAIGN_LINE");
        List<DpCopyBlockVo> blocks = copyService.list(taskId, null);
        String headline = CreativePosterLayout.pickFirst(CreativePosterLayout.candidates(
            campaign == null ? null : campaign.title(),
            contentOf(blocks, DpCopyBlockTypeEnum.MUST_SHOW),
            contentOf(blocks, DpCopyBlockTypeEnum.SELLING_POINT),
            project.getTaskName()));
        // 副题取产品与 SKU（海报上"这是哪一款"必须有据可查，不编品牌口号）
        String sku = StringUtils.trimToNull(project.getSkuCode());
        String subline = CreativePosterLayout.pickFirst(CreativePosterLayout.candidates(
            project.getProductName(), sku == null ? null : "SKU " + sku));

        // 3) 逐档渲染：一档 = 一张海报（数据 URI 已内联，渲染服务会拒绝任何外链）
        List<DpOutputSpec> specs = usableSpecs(deliveryType);
        Map<String, Object> dna = dnaNode(taskId);
        String footerNote = "本海报由 AI 视觉工厂生成 · 项目 " + StringUtils.blankToDefault(project.getTaskNo(), "-")
            + " · 模板 " + TEMPLATE_CODE + "@" + TEMPLATE_VERSION
            + " · 渲染于 " + LocalDateTime.now().withNano(0);

        List<Product> products = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        int index = 0;
        for (DpOutputSpec spec : specs) {
            index++;
            CreativePosterLayout.Canvas canvas = new CreativePosterLayout.Canvas(
                spec.getSpecCode(), spec.getRatio(), spec.getWidth(), heightOf(spec));
            Map<String, Object> payload = CreativePosterLayout.build(canvas, modules, headline, subline, dna,
                brandNameOf(project), project.getProductName(), footerNote);
            long started = System.currentTimeMillis();
            RendererClient.RenderResult result = rendererClient.render(
                TEMPLATE_CODE, TEMPLATE_VERSION, RENDER_MODE, null, payload, spec.getWidth());
            long cost = System.currentTimeMillis() - started;

            // 产出登记为任务附件（交付包下载与交付清单都按 fileId 取字节，不重复存两份）
            String fileName = "poster-" + StringUtils.blankToDefault(spec.getSpecCode(), "spec") + ".png";
            Long fileId = contentTaskService.uploadFile(taskId, null,
                new SimpleMultipartFile("file", fileName, "image/png", result.png()),
                ContentFileSourceEnum.GENERATED.getCode());
            products.add(new Product(
                CreativeRenderer.fileNameOf(index, spec.getSpecCode(), CODE, "png"),
                CreativeDeliveryManifest.ROLE_POSTER,
                StringUtils.blankToDefault(spec.getSpecCode(), ""),
                null, null, fileId, null,
                result.width(), result.height(), (long) result.png().length, result.sha256()));
            sizes.add(result.width() + "×" + result.height());
            log.info("海报渲染 taskId={} 规格={} 尺寸={}×{} 字节={} 服务端={}ms 本机={}ms 模板校验和={}",
                taskId, spec.getSpecCode(), result.width(), result.height(), result.png().length,
                result.renderMs(), cost, shortOf(result.templateChecksum()));
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specs", specs.size());
        event.put("sizes", sizes);
        event.put("template", TEMPLATE_CODE + "@" + TEMPLATE_VERSION);
        event.put("missingScreens", missing);
        projectService.appendEvent(taskId, EVENT_TYPE, ACTION_RENDERED, JsonUtils.toJsonString(event));
        // 版式完成 → V08_READY（机排版完成），「终审」那一步随之变成进行中
        projectService.moveStage(taskId, DpVisualStageEnum.V08_READY, ACTION_RENDERED,
            JsonUtils.toJsonString(Map.of("specs", specs.size(), "sizes", sizes)));

        String remark = "海报 " + products.size() + " 档：" + String.join(" / ", sizes)
            + "（模板 " + TEMPLATE_CODE + "@" + TEMPLATE_VERSION + "）"
            + (missing.isEmpty() ? "" : "；未选定的屏 " + missing.size() + " 个（" + String.join("、", missing) + "，已按缺图提示处理）");
        log.info("海报渲染器完成 taskId={} 档数={} 缺图屏={}", taskId, products.size(), missing);
        return new Outcome(products, remark);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 可用的输出规格（宽高都要是正数；一档都没有时直接拒绝，不猜"用默认尺寸"）。
     *
     * @param deliveryType 交付类型
     * @return 规格列表（按配置顺序）
     */
    private List<DpOutputSpec> usableSpecs(String deliveryType) {
        List<DpOutputSpec> specs = new ArrayList<>();
        for (DpOutputSpec spec : scenarioConfigService.listOutputSpecs(deliveryType)) {
            if (spec == null || spec.getWidth() == null || spec.getWidth() <= 0) {
                continue;
            }
            if (heightOf(spec) <= 0) {
                continue;
            }
            specs.add(spec);
        }
        if (specs.isEmpty()) {
            throw new ServiceException("交付类型「" + deliveryType + "」没有可用的输出规格（dp_output_spec）："
                + "海报要按规格逐档出图，没有规格就不知道该出多大，因此不做。");
        }
        return specs;
    }

    /**
     * 规格高度：优先 {@code height}，为 0/空时按 {@code ratio} 与宽度算（参考图口径 AUTO 的兜底）。
     *
     * @param spec 输出规格
     * @return 高度（px；算不出来返回 0）
     */
    private static int heightOf(DpOutputSpec spec) {
        if (spec.getHeight() != null && spec.getHeight() > 0) {
            return spec.getHeight();
        }
        String ratio = StringUtils.trimToNull(spec.getRatio());
        if (ratio == null || spec.getWidth() == null) {
            return 0;
        }
        String[] parts = ratio.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            double w = Double.parseDouble(parts[0].trim());
            double h = Double.parseDouble(parts[1].trim());
            if (w <= 0 || h <= 0) {
                return 0;
            }
            return (int) Math.round(spec.getWidth() * h / w);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 取某个模块的那一条素材（按 moduleCode 精确匹配，大小写不敏感）。
     *
     * @param modules    素材清单
     * @param moduleCode 模块编码
     * @return 素材；没有返回 null
     */
    private static CreativePosterLayout.PosterModule firstOfModule(
        List<CreativePosterLayout.PosterModule> modules, String moduleCode) {
        for (CreativePosterLayout.PosterModule module : modules) {
            if (moduleCode.equalsIgnoreCase(StringUtils.trimToEmpty(module.moduleCode()))) {
                return module;
            }
        }
        return null;
    }

    /**
     * 取某类文案块的第一条可用文本（title 优先，其次 content）。
     *
     * @param blocks 文案块
     * @param type   类型
     * @return 文本；没有返回 null
     */
    private static String contentOf(List<DpCopyBlockVo> blocks, DpCopyBlockTypeEnum type) {
        for (DpCopyBlockVo block : blocks == null ? List.<DpCopyBlockVo>of() : blocks) {
            if (block == null || !type.getCode().equalsIgnoreCase(StringUtils.trimToEmpty(block.getBlockType()))) {
                continue;
            }
            String value = StringUtils.trimToNull(block.getTitle());
            if (value == null) {
                value = StringUtils.trimToNull(block.getContent());
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 视觉基因里与海报有关的两个颜色（背景与强调色）。
     *
     * @param taskId 项目ID
     * @return 节点（取不到返回空 Map——模板会用自己的兜底色）
     */
    private Map<String, Object> dnaNode(Long taskId) {
        Map<String, Object> node = new LinkedHashMap<>();
        try {
            var dna = dnaService.activeDna(taskId);
            if (dna == null) {
                return node;
            }
            String background = dna.path("colors").path("background").asText(null);
            String accent = dna.path("colors").path("accent").asText(null);
            if (StringUtils.isNotBlank(background)) {
                node.put("background", background);
            }
            if (StringUtils.isNotBlank(accent)) {
                node.put("accent", accent);
            }
            List<String> swatches = new ArrayList<>();
            for (String key : List.of("primary", "secondary", "accent", "background")) {
                String hex = dna.path("colors").path(key).asText(null);
                if (StringUtils.isNotBlank(hex)) {
                    swatches.add(hex);
                }
            }
            node.put("swatches", swatches);
        } catch (Exception e) {
            log.warn("读取视觉基因失败（海报按无基因处理）taskId={}：{}", taskId, e.getMessage());
        }
        return node;
    }

    /**
     * 品牌名：项目没关联产品时用项目名兜底（页脚与标识文字兜底都用它）。
     *
     * @param project 项目
     * @return 品牌名（可空）
     */
    private static String brandNameOf(CreativeProjectVo project) {
        return CreativePosterLayout.pickFirst(
            CreativePosterLayout.candidates(project.getProductName(), project.getTaskName()));
    }

    /**
     * 某个已选定候选（与排版/交付取的是同一条：该屏最新的一条 APPROVED）。
     *
     * @param taskId   项目ID
     * @param screenId 屏ID
     * @return 候选；没有返回 null
     */
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

    private static String shortOf(String sha) {
        return StringUtils.isBlank(sha) || sha.length() < 12 ? StringUtils.blankToDefault(sha, "-") : sha.substring(0, 12);
    }
}
