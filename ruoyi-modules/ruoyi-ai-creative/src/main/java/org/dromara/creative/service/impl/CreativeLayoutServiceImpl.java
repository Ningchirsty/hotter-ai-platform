package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
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
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpDetailPageVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.RendererClient;
import org.dromara.creative.helper.SimpleMultipartFile;
import org.dromara.creative.mapper.DpDetailPageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeLayoutService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.dromara.creative.service.ICreativeTemplateService;
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
    private static final String PAGE_TEMPLATE_VERSION = "1.0.0";

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
    private final RendererClient rendererClient;
    private final IContentTaskService contentTaskService;
    private final ContentOssHelper contentOssHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpDetailPageVo render(Long taskId) {
        gateService.requireCanProduce(taskId);
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null || storyboard.getScreens() == null || storyboard.getScreens().isEmpty()) {
            throw new ServiceException("还没有分镜，无法排版");
        }

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

        // 2) 模板必须已发布且校验和与渲染服务一致（发布门）
        var template = templateService.requirePublished(PAGE_TEMPLATE_CODE, PAGE_TEMPLATE_VERSION);

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
        renderLayout.put("footerNote", "本页由 AI 视觉工厂生成 · 基因 "
            + StringUtils.blankToDefault(dna == null ? null : dna.path("schema").asText(), "-")
            + " · 分镜 " + storyboard.getStoryboardNo()
            + " · 渲染于 " + LocalDateTime.now().withNano(0));

        long started = System.currentTimeMillis();
        RendererClient.RenderResult result = rendererClient.render(
            PAGE_TEMPLATE_CODE, PAGE_TEMPLATE_VERSION, "page", null, toMap(renderLayout));
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
            + "，模板 " + PAGE_TEMPLATE_CODE + "@" + PAGE_TEMPLATE_VERSION
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

    @Override
    public DpDetailPageVo detail(Long taskId) {
        DpDetailPage page = findPage(taskId);
        DpDetailPageVo vo = new DpDetailPageVo();
        vo.setRendererAvailable(rendererClient.version() != null);
        vo.setTemplateKey(PAGE_TEMPLATE_CODE + "@" + PAGE_TEMPLATE_VERSION);
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
