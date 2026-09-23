package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.VisualDnaSchema;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分镜服务实现。
 *
 * <p><b>为什么是模板派生而不是「AI 写文案」</b>：本部署未注册可用的文本生成能力
 * （治理台无对应能力/路由/绑定），硬编一段看似 AI 的文案是造假。因此这里用
 * 「行业屏结构模板 + 已确认事实 + 锁定基因 + 选定方向」派生，来源标 TEMPLATE，
 * 页面如实展示；文案由人改，改完即是最新意图。模型就绪后同一处替换为模型产出。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeStoryboardServiceImpl implements ICreativeStoryboardService {

    /**
     * 来源：模板派生
     */
    private static final String SOURCE_TEMPLATE = "TEMPLATE";

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_LOCKED = "LOCKED";

    /**
     * 屏类型定义：编号不参与展示，只表达顺序。
     */
    private static final List<ScreenTemplate> TEMPLATES = List.of(
        new ScreenTemplate("HERO", "主图", "STRICT",
            "一眼看清这是什么产品：形态、配色、材质，无需文案也能认出来"),
        new ScreenTemplate("SELLING_POINT", "卖点一", "LOOSE",
            "把第一个卖点用画面讲清楚，而不是靠一行字解释"),
        new ScreenTemplate("SELLING_POINT", "卖点二", "LOOSE",
            "把第二个卖点用画面讲清楚，与前一个卖点在画面上有区分"),
        new ScreenTemplate("SCENE", "使用场景", "LOOSE",
            "展示它在真实生活里的样子：放在哪、和什么在一起、什么氛围"),
        new ScreenTemplate("DETAIL", "细节工艺", "STRICT",
            "让人相信做工：材质纹理、结构接缝、表面处理经得起看"),
        new ScreenTemplate("SIZE", "尺寸参数", "STRICT",
            "不靠文案也能感知大小与构成，比例必须真实"),
        new ScreenTemplate("BRAND", "品牌收尾", "LOOSE",
            "留下品牌印象并收尾，画面克制、不抢产品")
    );

    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpStoryboardVo generate(Long taskId) {
        CreativeProjectVo project = projectService.getProject(taskId);
        Long dnaId = dnaService.activeDnaId(taskId);
        ObjectNode dna = dnaService.activeDna(taskId);
        if (dnaId == null) {
            throw new ServiceException("还没有视觉基因，请先生成并锁定基因再拆分镜");
        }
        DpVisualDirectionVo direction = directionService.selected(taskId);
        Map<String, String> facts = confirmedFacts(contentTaskService.getDetail(taskId));

        int version = nextVersion(taskId);
        DpStoryboard storyboard = new DpStoryboard();
        storyboard.setTaskId(taskId);
        storyboard.setStoryboardNo(storyboardNo(version));
        storyboard.setVersion(version);
        storyboard.setVisualDnaId(dnaId);
        storyboard.setVisualDirectionId(direction == null ? null : direction.getId());
        storyboard.setScreenCount(TEMPLATES.size());
        storyboard.setRhythmJson(rhythm());
        storyboard.setStatus(STATUS_DRAFT);
        storyboard.setSource(SOURCE_TEMPLATE);
        storyboardMapper.insert(storyboard);

        int sortNo = 0;
        for (ScreenTemplate template : TEMPLATES) {
            sortNo++;
            DpStoryboardScreen screen = new DpStoryboardScreen();
            screen.setStoryboardId(storyboard.getId());
            screen.setTaskId(taskId);
            screen.setScreenNo(String.format("S%02d", sortNo));
            screen.setSortNo(sortNo);
            screen.setScreenType(template.type());
            screen.setTitle(title(template, project, facts));
            screen.setSubtitle(subtitle(template, facts));
            screen.setBodyText(body(template, project, facts));
            screen.setPictureSoloStatement(template.soloStatement());
            screen.setSpecJson(spec(template, dna, direction));
            screen.setWorkflowCode(CreativeConstants.DEFAULT_HERO_WORKFLOW);
            screen.setProductLockLevel(template.productLockLevel());
            screen.setStatus(STATUS_DRAFT);
            screenMapper.insert(screen);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("storyboardId", storyboard.getId());
        event.put("version", version);
        event.put("screenCount", TEMPLATES.size());
        event.put("dnaId", dnaId);
        event.put("directionId", storyboard.getVisualDirectionId());
        event.put("source", SOURCE_TEMPLATE);
        projectService.moveStage(taskId, DpVisualStageEnum.STORYBOARD_REVIEW, "STORYBOARD_GENERATE",
            JsonUtils.toJsonString(event));
        return loadVo(storyboard);
    }

    @Override
    public DpStoryboardVo latest(Long taskId) {
        DpStoryboard entity = latestEntity(taskId);
        return entity == null ? null : loadVo(entity);
    }

    @Override
    public List<DpStoryboardVo> versions(Long taskId) {
        List<DpStoryboard> rows = storyboardMapper.selectList(new LambdaQueryWrapper<DpStoryboard>()
            .eq(DpStoryboard::getTaskId, taskId)
            .orderByDesc(DpStoryboard::getVersion));
        List<DpStoryboardVo> list = new ArrayList<>();
        for (DpStoryboard row : rows) {
            list.add(toVo(row, List.of()));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpStoryboardScreenVo updateScreen(Long taskId, CreativeScreenBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("屏ID不能为空");
        }
        DpStoryboardScreen screen = screenMapper.selectById(bo.getId());
        if (screen == null || !taskId.equals(screen.getTaskId())) {
            throw new ServiceException("屏不属于该项目：" + bo.getId());
        }
        DpStoryboard storyboard = storyboardMapper.selectById(screen.getStoryboardId());
        if (storyboard != null && STATUS_LOCKED.equals(storyboard.getStatus())) {
            throw new ServiceException("该分镜已锁定（" + storyboard.getStoryboardNo()
                + "），锁定版不可修改；请先「重新生成分镜」得到新版本再改");
        }
        if (bo.getTitle() != null) {
            screen.setTitle(bo.getTitle());
        }
        if (bo.getSubtitle() != null) {
            screen.setSubtitle(bo.getSubtitle());
        }
        if (bo.getBodyText() != null) {
            screen.setBodyText(bo.getBodyText());
        }
        if (bo.getPictureSoloStatement() != null) {
            screen.setPictureSoloStatement(bo.getPictureSoloStatement());
        }
        if (bo.getWorkflowCode() != null) {
            screen.setWorkflowCode(bo.getWorkflowCode());
        }
        if (bo.getProductLockLevel() != null) {
            screen.setProductLockLevel(bo.getProductLockLevel());
        }
        if (bo.getRemark() != null) {
            screen.setRemark(bo.getRemark());
        }
        Map<String, Object> spec = parseSpec(screen.getSpecJson());
        putIfNotNull(spec, "shot", bo.getShot());
        putIfNotNull(spec, "composition", bo.getComposition());
        putIfNotNull(spec, "lighting", bo.getLighting());
        putIfNotNull(spec, "background", bo.getBackground());
        screen.setSpecJson(JsonUtils.toJsonString(spec));
        screenMapper.updateById(screen);

        projectService.moveStage(taskId, DpVisualStageEnum.STORYBOARD_REVIEW, "STORYBOARD_EDIT",
            JsonUtils.toJsonString(Map.of("screenId", screen.getId(), "screenNo", screen.getScreenNo())));
        return toScreenVo(screen);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpStoryboardVo lock(Long taskId, Long storyboardId) {
        DpStoryboard storyboard = storyboardId != null ? requireOwned(taskId, storyboardId)
            : latestEntity(taskId);
        if (storyboard == null) {
            throw new ServiceException("还没有分镜可锁定，请先生成分镜");
        }
        if (STATUS_LOCKED.equals(storyboard.getStatus())) {
            return loadVo(storyboard);
        }
        List<DpStoryboardScreen> screens = screensOf(storyboard.getId());
        List<String> missing = new ArrayList<>();
        for (DpStoryboardScreen screen : screens) {
            if (StringUtils.isBlank(screen.getPictureSoloStatement())) {
                missing.add(screen.getScreenNo() + "（缺画面独白）");
            }
        }
        if (!missing.isEmpty()) {
            throw new ServiceException("以下屏还没想清楚「画面自己要说清什么」，不能锁定："
                + String.join("、", missing));
        }
        storyboard.setStatus(STATUS_LOCKED);
        storyboard.setApprovedBy(LoginHelper.getUserId());
        storyboard.setApprovedAt(LocalDateTime.now());
        storyboardMapper.updateById(storyboard);
        projectService.moveStage(taskId, DpVisualStageEnum.STORYBOARD_LOCKED, "STORYBOARD_LOCK",
            JsonUtils.toJsonString(Map.of("storyboardId", storyboard.getId(),
                "version", storyboard.getVersion(), "screenCount", screens.size())));
        return loadVo(storyboard);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 屏结构模板。
     *
     * @param type             屏类型
     * @param label            展示名
     * @param productLockLevel 产品保真等级
     * @param soloStatement    画面独白（该屏画面自己要讲清的事）
     */
    private record ScreenTemplate(String type, String label, String productLockLevel, String soloStatement) {
    }

    private String title(ScreenTemplate template, CreativeProjectVo project, Map<String, String> facts) {
        String product = StringUtils.blankToDefault(project.getProductName(),
            StringUtils.blankToDefault(project.getTaskName(), "产品"));
        return switch (template.type()) {
            case "HERO" -> product + " · 主图";
            case "SELLING_POINT" -> product + " · " + template.label();
            case "SCENE" -> product + " · " + template.label();
            case "DETAIL" -> product + " · " + template.label();
            case "SIZE" -> product + " · " + template.label();
            case "BRAND" -> "品牌收尾";
            default -> product + " · " + template.label();
        };
    }

    private String subtitle(ScreenTemplate template, Map<String, String> facts) {
        return switch (template.type()) {
            case "SIZE" -> firstNonBlank(facts.get("spec_params"), facts.get("quantity"), "尺寸与构成");
            case "DETAIL" -> firstNonBlank(facts.get("main_version"), "工艺与结构细节");
            case "BRAND" -> firstNonBlank(facts.get("package_version"), "品牌与包装");
            default -> null;
        };
    }

    private String body(ScreenTemplate template, CreativeProjectVo project, Map<String, String> facts) {
        return switch (template.type()) {
            case "HERO" -> joinNonBlank("，", facts.get("product_name"), facts.get("color"),
                facts.get("main_version"));
            case "SELLING_POINT" -> null;
            case "SCENE" -> null;
            case "DETAIL" -> joinNonBlank("；", facts.get("craft") == null ? facts.get("spec_params") : facts.get("craft"));
            case "SIZE" -> joinNonBlank("；", facts.get("spec_params"), facts.get("quantity"));
            case "BRAND" -> joinNonBlank("；", facts.get("package_version"), facts.get("brand_tone"));
            default -> null;
        };
    }

    private String spec(ScreenTemplate template, ObjectNode dna, DpVisualDirectionVo direction) {
        Map<String, Object> spec = new LinkedHashMap<>();
        String scene = direction != null ? String.valueOf(direction.getStrategy().getOrDefault("scene", "")) : "";
        String lighting = direction != null
            ? String.valueOf(direction.getStrategy().getOrDefault("lighting", "")) : "";
        String composition = direction != null
            ? String.valueOf(direction.getStrategy().getOrDefault("composition", "")) : "";
        spec.put("shot", switch (template.type()) {
            case "HERO" -> "产品全貌，正视角（或 15° 微侧）";
            case "SELLING_POINT" -> "功能/卖点相关的中近景";
            case "SCENE" -> "环境全景，产品占画面 " + ratio(dna);
            case "DETAIL" -> "局部大特写（材质/结构/接口）";
            case "SIZE" -> "含参照物的平视构图";
            case "BRAND" -> "产品与品牌元素的合影";
            default -> "中景";
        });
        spec.put("composition", StringUtils.blankToDefault(composition, "产品居中，四周留白均等"));
        spec.put("lighting", StringUtils.blankToDefault(lighting,
            "光线：" + dna.path("lighting").path("type").asText("SOFT")));
        spec.put("background", StringUtils.blankToDefault(scene,
            dna.path("colors").path("background").asText("纯色底")));
        spec.put("productRatio", ratio(dna));
        spec.put("whitespace", dna.path("whitespaceLevel").asText("HIGH"));
        return JsonUtils.toJsonString(spec);
    }

    private static String ratio(ObjectNode dna) {
        int min = dna.path("productRatio").path("min").asInt(45);
        int max = dna.path("productRatio").path("max").asInt(65);
        return min + "%~" + max + "%";
    }

    private static String rhythm() {
        return JsonUtils.toJsonString(Map.of(
            "schema", "storyboard-rhythm/1",
            "note", "首屏给结论，中段逐层加深（卖点→场景→细节），尾屏收束",
            "density", List.of("高", "中", "中", "中", "高", "中", "低")));
    }

    private Map<String, String> confirmedFacts(ContentTaskDetailVo detail) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (detail == null || detail.getFacts() == null) {
            return facts;
        }
        for (CpFactSnapshotVo fact : detail.getFacts()) {
            if (ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(fact.getConfirmStatus())) {
                facts.putIfAbsent(fact.getFieldCode(), fact.getFieldValue());
            }
        }
        return facts;
    }

    private int nextVersion(Long taskId) {
        DpStoryboard latest = latestEntity(taskId);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    private static String storyboardNo(int version) {
        return "SB-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + String.format("%04d", version);
    }

    private DpStoryboard latestEntity(Long taskId) {
        List<DpStoryboard> rows = storyboardMapper.selectList(new LambdaQueryWrapper<DpStoryboard>()
            .eq(DpStoryboard::getTaskId, taskId)
            .orderByDesc(DpStoryboard::getVersion)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DpStoryboard requireOwned(Long taskId, Long storyboardId) {
        DpStoryboard entity = storyboardMapper.selectById(storyboardId);
        if (entity == null || !taskId.equals(entity.getTaskId())) {
            throw new ServiceException("分镜不属于该项目：" + storyboardId);
        }
        return entity;
    }

    private List<DpStoryboardScreen> screensOf(Long storyboardId) {
        return screenMapper.selectList(new LambdaQueryWrapper<DpStoryboardScreen>()
            .eq(DpStoryboardScreen::getStoryboardId, storyboardId)
            .orderByAsc(DpStoryboardScreen::getSortNo));
    }

    private DpStoryboardVo loadVo(DpStoryboard entity) {
        return toVo(entity, screensOf(entity.getId()));
    }

    private DpStoryboardVo toVo(DpStoryboard entity, List<DpStoryboardScreen> screens) {
        DpStoryboardVo vo = new DpStoryboardVo();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setStoryboardNo(entity.getStoryboardNo());
        vo.setVersion(entity.getVersion());
        vo.setVisualDirectionId(entity.getVisualDirectionId());
        vo.setVisualDnaId(entity.getVisualDnaId());
        vo.setScreenCount(entity.getScreenCount());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(STATUS_LOCKED.equals(entity.getStatus()) ? "已锁定" : "草稿");
        vo.setSource(entity.getSource());
        vo.setSourceDesc(sourceDesc(entity.getSource()));
        vo.setApprovedBy(entity.getApprovedBy());
        vo.setApprovedAt(entity.getApprovedAt());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        List<DpStoryboardScreenVo> screenVos = new ArrayList<>();
        for (DpStoryboardScreen screen : screens) {
            screenVos.add(toScreenVo(screen));
        }
        vo.setScreens(screenVos);
        return vo;
    }

    private DpStoryboardScreenVo toScreenVo(DpStoryboardScreen screen) {
        DpStoryboardScreenVo vo = new DpStoryboardScreenVo();
        vo.setId(screen.getId());
        vo.setStoryboardId(screen.getStoryboardId());
        vo.setTaskId(screen.getTaskId());
        vo.setScreenNo(screen.getScreenNo());
        vo.setSortNo(screen.getSortNo());
        vo.setScreenType(screen.getScreenType());
        vo.setScreenTypeDesc(screenTypeDesc(screen.getScreenType()));
        vo.setTitle(screen.getTitle());
        vo.setSubtitle(screen.getSubtitle());
        vo.setBodyText(screen.getBodyText());
        vo.setPictureSoloStatement(screen.getPictureSoloStatement());
        vo.setSpec(parseSpec(screen.getSpecJson()));
        vo.setSpecJson(screen.getSpecJson());
        vo.setWorkflowCode(screen.getWorkflowCode());
        vo.setProductLockLevel(screen.getProductLockLevel());
        vo.setStatus(screen.getStatus());
        vo.setRemark(screen.getRemark());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSpec(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(json);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
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

    private static String sourceDesc(String source) {
        return SOURCE_TEMPLATE.equals(source)
            ? "由屏结构模板 + 已确认事实 + 锁定基因 + 选定方向派生（未使用模型）" : source;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

}
