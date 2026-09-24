package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.dromara.creative.helper.CreativeDraftBrain;
import org.dromara.creative.helper.CreativeDraftFactory;
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
     * 屏结构骨架：屏类型、展示名与产品保真等级是稳定业务骨架（顺序 = 讲故事的节奏）。
     *
     * <p>R4 起，每屏的标题/副标题/正文/画面独白<b>不再来自这里</b>：
     * 文案由 {@link CreativeDraftFactory#screens} 按「事实 + 基因 + 产品名」推导。
     * 之前把固定句子写在这个 record 里，正是「70 行分镜只有 7 句不同独白」的来源。</p>
     */
    private static final List<ScreenTemplate> TEMPLATES = List.of(
        new ScreenTemplate("HERO", "主图", "STRICT"),
        new ScreenTemplate("SELLING_POINT", "卖点一", "LOOSE"),
        new ScreenTemplate("SELLING_POINT", "卖点二", "LOOSE"),
        new ScreenTemplate("SCENE", "使用场景", "LOOSE"),
        new ScreenTemplate("DETAIL", "细节工艺", "STRICT"),
        new ScreenTemplate("SIZE", "尺寸参数", "STRICT"),
        new ScreenTemplate("BRAND", "品牌收尾", "LOOSE")
    );

    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;
    private final CreativeDraftBrain brain;

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

        // 参数化草稿：每屏标题/副标题/正文/画面独白由「事实 + 基因 + 产品名 + 屏类型」推导。
        List<CreativeDraftFactory.ScreenDraft> drafts =
            CreativeDraftFactory.screens(dna, project.getProductName(), facts);

        // 有可用模型就用模型润色（LOCAL 优先，不出公司）；没有就如实回落参数化草稿。
        // 逐屏逐字段采纳：模型没给的屏保留草稿文案，绝不因为「模型返回了」就整段照抄。
        String source = CreativeConstants.SOURCE_TEMPLATE;
        String modelKey = null;
        String traceId = null;
        String modelReason = null;
        int adopted = 0;
        CreativeDraftBrain.Suggestion suggestion = brain.suggest(CreativeConstants.CAP_STORYBOARD_DRAFT,
            project.getDataLevel(), "Y".equalsIgnoreCase(project.getAllowExternal()),
            storyboardPrompt(project, facts, drafts), storyboardPayload(dna, facts, drafts));
        if (suggestion.applied()) {
            modelKey = suggestion.modelKey();
            traceId = suggestion.traceId();
            List<CreativeDraftFactory.ScreenDraft> merged = new ArrayList<>();
            java.util.Set<String> usedTitles = new java.util.HashSet<>();
            for (CreativeDraftFactory.ScreenDraft draft : drafts) {
                JsonNode item = findScreen(suggestion.json(), draft.type(), merged.size());
                if (item == null) {
                    merged.add(draft);
                    continue;
                }
                String title = CreativeDraftBrain.text(item, "title", 60);
                // 标题守卫：模型偶尔把 7 屏标题都写成同一个产品名（比参数化草稿还差）。
                // 标题为空或与前面某屏重复时，保留草稿标题——不是编造，是不接受「更差但合法」的产出。
                if (title != null && !usedTitles.add(title)) {
                    modelReason = appendReason(modelReason,
                        "第 " + (merged.size() + 1) + " 屏标题与前面重复，已保留草稿标题「" + draft.title() + "」");
                    title = null;
                }
                String subtitle = CreativeDraftBrain.text(item, "subtitle", 120);
                String body = CreativeDraftBrain.text(item, "bodyText", 400);
                String solo = CreativeDraftBrain.text(item, "soloStatement", 400);
                if (title == null && subtitle == null && body == null && solo == null) {
                    merged.add(draft);
                    continue;
                }
                adopted++;
                merged.add(new CreativeDraftFactory.ScreenDraft(draft.type(), draft.label(),
                    draft.productLockLevel(),
                    title == null ? draft.title() : title,
                    subtitle == null ? draft.subtitle() : subtitle,
                    body == null ? draft.bodyText() : body,
                    solo == null ? draft.soloStatement() : solo));
            }
            drafts = merged;
            if (adopted > 0) {
                source = CreativeConstants.SOURCE_MODEL;
            } else {
                modelReason = "模型返回里没有可采纳的分镜文案（字段缺失或超长），已全部保留参数化草稿；"
                    + "实际顶层字段=" + CreativeDraftBrain.fieldNames(suggestion.json());
            }
        } else {
            modelReason = suggestion.reason();
        }
        storyboard.setSource(source);
        storyboard.setStatus(STATUS_DRAFT);
        storyboardMapper.insert(storyboard);

        int sortNo = 0;
        for (int i = 0; i < TEMPLATES.size(); i++) {
            ScreenTemplate template = TEMPLATES.get(i);
            // 骨架来自 TEMPLATES，文案来自草稿；两者数量必须一致，
            // 不一致属于编码错误，宁可当场炸掉也不要静默少一屏
            CreativeDraftFactory.ScreenDraft draft = drafts.get(i);
            sortNo++;
            DpStoryboardScreen screen = new DpStoryboardScreen();
            screen.setStoryboardId(storyboard.getId());
            screen.setTaskId(taskId);
            screen.setScreenNo(String.format("S%02d", sortNo));
            screen.setSortNo(sortNo);
            screen.setScreenType(template.type());
            screen.setTitle(draft.title());
            screen.setSubtitle(draft.subtitle());
            screen.setBodyText(draft.bodyText());
            screen.setPictureSoloStatement(draft.soloStatement());
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
        event.put("source", source);
        event.put("modelKey", modelKey);
        event.put("modelTraceId", traceId);
        event.put("modelAdoptedScreens", adopted);
        event.put("modelReason", modelReason);
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
     * 组提示词：把「已确认事实 + 参数化草稿」交给模型润色，并写死 JSON 契约。
     *
     * @param project 项目
     * @param facts   已确认事实
     * @param drafts  参数化草稿
     * @return 提示词
     */
    private static String storyboardPrompt(CreativeProjectVo project, Map<String, String> facts,
                                           List<CreativeDraftFactory.ScreenDraft> drafts) {
        StringBuilder sb = new StringBuilder();
        sb.append("为电商详情页项目「")
            .append(StringUtils.blankToDefault(project.getProductName(),
                StringUtils.blankToDefault(project.getTaskName(), "当前产品")))
            .append("」改写 7 屏分镜文案。\n");
        sb.append("已确认产品事实（只能用这些，不得编造）：")
            .append(facts.isEmpty() ? "暂无" : JsonUtils.toJsonString(facts)).append("\n");
        sb.append("屏的顺序与类型固定，不得增删改顺序：\n");
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            sb.append("  ").append(draft.type()).append("：").append(draft.title())
                .append(" —— ").append(draft.soloStatement()).append("\n");
        }
        sb.append("输出 JSON：{\"screens\":[{\"type\":\"HERO\",\"title\":\"形如「产品名 · 这一屏要讲什么」，"
            + "不超过 30 字，7 屏标题必须两两不同\",\"subtitle\":\"可省略\",\"bodyText\":\"可省略\","
            + "\"soloStatement\":\"20~200 字：这一屏的画面自己要讲清什么\"}, ...共 7 项]}。"
            + "type 必须与上面给出的完全一致，只输出这个 JSON。");
        return sb.toString();
    }

    /**
     * 追加一条未采纳说明（可多条累积，页面如实展示）。
     *
     * @param current 现有说明
     * @param extra   新增说明
     * @return 合并后的说明
     */
    private static String appendReason(String current, String extra) {
        return StringUtils.isBlank(current) ? extra : current + "；" + extra;
    }

    /**
     * 结构化载荷。
     *
     * @param dna    基因
     * @param facts  事实
     * @param drafts 草稿
     * @return 载荷
     */
    private static Map<String, Object> storyboardPayload(ObjectNode dna, Map<String, String> facts,
                                                        List<CreativeDraftFactory.ScreenDraft> drafts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "storyboard-draft");
        payload.put("facts", facts);
        payload.put("dna", dna.toString());
        payload.put("screenTypes", drafts.stream().map(CreativeDraftFactory.ScreenDraft::type).toList());
        return payload;
    }

    /**
     * 在模型输出里按屏类型找对应项；同类型有多屏（两个卖点屏）时按出现顺序取。
     *
     * @param json      模型输出
     * @param type      屏类型
     * @param usedCount 该类型此前已被取走的次数
     * @return 对应节点；找不到返回 null
     */
    private static JsonNode findScreen(ObjectNode json, String type, int usedCount) {
        if (json == null) {
            return null;
        }
        JsonNode array = json.path("screens");
        if (!array.isArray()) {
            array = json.path("items");
        }
        if (array.isArray()) {
            int seen = 0;
            int total = Math.max(1, countOfType(array, type));
            for (JsonNode item : array) {
                if (!type.equalsIgnoreCase(CreativeDraftBrain.text(item, "type", 24))) {
                    continue;
                }
                if (seen == usedCount % total) {
                    return item;
                }
                seen++;
            }
            return null;
        }
        // 按屏类型做键的形式：{"HERO": {...}}（两个同类型屏只能取到第一个，第二个回落到草稿）
        JsonNode keyed = json.path(type);
        if (keyed.isObject() && usedCount == 0) {
            return keyed;
        }
        return null;
    }

    /**
     * 统计模型输出里某屏类型出现的次数（两个卖点屏要分别对上）。
     *
     * @param array 模型输出的 screens
     * @param type  屏类型
     * @return 次数
     */
    private static int countOfType(JsonNode array, String type) {
        int count = 0;
        for (JsonNode item : array) {
            if (type.equalsIgnoreCase(CreativeDraftBrain.text(item, "type", 24))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 屏骨架模板（R4 起只保留结构性字段，文案交给参数化草稿工厂）。
     *
     * @param type             屏类型
     * @param label            展示名
     * @param productLockLevel 产品保真等级
     */
    private record ScreenTemplate(String type, String label, String productLockLevel) {
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
        if (SOURCE_TEMPLATE.equals(source)) {
            return "由屏骨架 + 参数化文案（已确认事实 + 锁定基因 + 参考图实测 + 选定方向派生，未使用模型）";
        }
        if (CreativeConstants.SOURCE_MODEL.equals(source)) {
            return "由受管模型产出文案（经逐字段验收后才采纳；屏骨架固定 7 屏）";
        }
        return source;
    }

}
