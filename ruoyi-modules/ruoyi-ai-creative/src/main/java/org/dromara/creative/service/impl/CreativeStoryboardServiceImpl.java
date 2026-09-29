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
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.enums.DpCopyBlockTypeEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeDraftBrain;
import org.dromara.creative.helper.CreativeDraftFactory;
import org.dromara.creative.helper.CreativeScreenSkeleton;
import org.dromara.creative.helper.CreativeScreenSkeletonRegistry;
import org.dromara.creative.helper.VisualDnaSchema;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeModuleService;
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
import java.util.Set;

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
     * 屏骨架加载器（构造依赖）。
     *
     * <p>为什么注入而不是直接调静态方法：如果把骨架读进字段初始化器，这个字段会在
     * <b>本 Bean 构造时</b>求值，而 {@link CreativeScreenSkeletonRegistry} 的 {@code @PostConstruct}
     * 可能还没跑，于是走静态懒加载兜底——启动日志会打印两次"契约加载完成"，
     * 还多出一个骨架实例。把它做成构造依赖，Spring 必须先把它初始化完再注入，
     * 加载点就只剩一处。</p>
     */
    private final CreativeScreenSkeletonRegistry skeletonRegistry;

    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;
    private final CreativeDraftBrain brain;
    /**
     * 品牌 Brief（R7）：必显信息进品牌收尾屏，并要求模型改写时不得违背品牌方要求
     */
    private final IContentBrandBriefService briefService;
    /**
     * 文案与要点块（R7）：卖点块进两个卖点屏
     */
    private final ICreativeCopyService copyService;

    /**
     * 模块引擎（R21）：优先用**项目模块计划**（dp_project_module）出屏。
     *
     * <p>分镜的"屏集合"从"一份进程级契约文件"变成"按交付类型的一套模块定义 + 每个项目一份模块计划"；
     * 没有模块计划时回落到契约文件（见 {@link #resolveSkeleton}）。</p>
     */
    private final ICreativeModuleService moduleService;

    /**
     * 当前屏骨架（类型/顺序/展示名/保真等级/取景都来自 {@code creative/screen-skeleton.json}）。
     *
     * <p>为什么做成可配置：屏数属于场景配置（电商详情页是 7 屏，别的场景不是），
     * 写死在 Java 里会让"改屏数"变成一次改代码 + 重新发版；改契约（或配
     * {@code creative.screen-skeleton.path} 指到外部文件）即可，且启动时会校验并打日志。</p>
     *
     * <p>R4 起，每屏的标题/副标题/正文/画面独白<b>不再来自骨架</b>：
     * 文案由 {@link CreativeDraftFactory#screens} 按「事实 + 基因 + 产品名」推导。</p>
     *
     * @return 骨架
     */
    /**
     * 解析"本次生成用哪份骨架"（R21）。
     *
     * <p><b>权威顺序</b>：项目模块计划（`dp_project_module`，由交付类型的默认骨架初始化）优先；
     * 项目还没有模块计划时**回落到契约文件**（`creative/screen-skeleton.json`）并记一条 INFO 日志——
     * 回落是"老项目照旧可用"的保证，但必须可见，不能悄悄换掉屏集合。</p>
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型（来自项目）
     * @return 骨架（永远非空：契约文件内置且加载时已校验）
     */
    private CreativeScreenSkeleton resolveSkeleton(Long taskId, String deliveryType) {
        return resolveActive(taskId, deliveryType).skeleton();
    }

    /**
     * 解析"本次生成用哪份屏 + 每屏来自哪个模块"（R21 起按模块计划，R22 起带上逐屏归属）。
     *
     * <p><b>权威顺序</b>：项目模块计划（`dp_project_module`，由交付类型的默认骨架初始化）优先；
     * 项目还没有模块计划时**回落到契约文件**（`creative/screen-skeleton.json`）并记一条 INFO 日志——
     * 回落是"老项目照旧可用"的保证，但必须可见，不能悄悄换掉屏集合。</p>
     *
     * <p><b>业务错误不吞</b>：模块计划"存在但不可用"（一个模块都没启用、展示名撞车等）抛的是
     * {@link ServiceException}，这里**原样抛出**——用户刚在模块规划页保存了计划，却看到契约文件的 7 屏，
     * 那是最坏的一种"看起来正常"。只有取计划时的基础设施异常才回落到契约文件并告警。</p>
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型（来自项目）
     * @return 生效的屏（骨架永远非空）+ 逐屏模块行（回落时为空）
     */
    private ICreativeModuleService.ActiveScreens resolveActive(Long taskId, String deliveryType) {
        try {
            ICreativeModuleService.ActiveScreens active = moduleService.activeScreens(taskId, deliveryType);
            if (active != null && active.skeleton() != null && active.skeleton().size() > 0) {
                log.info("项目 {} 分镜按模块计划出屏：交付类型={} 屏数={}（含人工文案/Workflow 覆盖 {} 行）",
                    taskId, deliveryType, active.skeleton().size(), active.owners().size());
                return active;
            }
        } catch (ServiceException e) {
            // 计划本身不可用：让用户看到原因（而不是悄悄换回契约文件的 7 屏）
            throw e;
        } catch (Exception e) {
            log.warn("按项目模块计划取屏骨架失败，回落到契约文件 taskId={}：{}", taskId, e.getMessage());
        }
        log.info("项目 {} 没有模块计划（交付类型 {}），分镜按契约文件 {} 生成",
            taskId, deliveryType, CreativeScreenSkeleton.RESOURCE);
        return new ICreativeModuleService.ActiveScreens(skeleton(), List.of());
    }

    private CreativeScreenSkeleton skeleton() {
        return skeletonRegistry.skeleton();
    }

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

        // R21/R22：本次生效的屏 = 项目模块计划（按交付类型）→ 没有就回落契约文件。
        // 一起带回来的还有"逐屏来自哪个模块行"：人工文案、Workflow、对应卖点都挂在模块行上。
        // 一次生成只解析一次并沿调用链传下去——不能存成实例字段：本 Bean 是单例，会串请求。
        ICreativeModuleService.ActiveScreens activePlan = resolveActive(taskId, project.getDeliverableType());
        CreativeScreenSkeleton active = activePlan.skeleton();
        List<DpProjectModule> owners = activePlan.owners();

        int version = nextVersion(taskId);
        DpStoryboard storyboard = new DpStoryboard();
        storyboard.setTaskId(taskId);
        storyboard.setStoryboardNo(storyboardNo(version));
        storyboard.setVersion(version);
        storyboard.setVisualDnaId(dnaId);
        storyboard.setVisualDirectionId(direction == null ? null : direction.getId());
        storyboard.setScreenCount(active.size());
        storyboard.setRhythmJson(rhythm());

        // 参数化草稿：每屏标题/副标题/正文/画面独白由「事实 + 基因 + 产品名 + 屏类型」推导。
        // R7 起，「品牌 Brief 的必显信息」与「卖点块」也参与推导；两者都为空时与 R7 之前完全一致。
        // R22 起，模块行上配了「对应卖点」的屏按配置取块（没配的仍按顺序兜底）。
        CpBrandBriefVo brief = briefService.get(taskId);
        String mustShow = mustShowFirstLine(brief);
        List<CreativeDraftFactory.CopyHint> sellingPoints = sellingPointHints(taskId, active, owners);
        List<CreativeDraftFactory.ScreenDraft> drafts =
            CreativeDraftFactory.screensOf(active, dna, project.getProductName(), facts, mustShow, sellingPoints);

        // 有可用模型就用模型润色（LOCAL 优先，不出公司）；没有就如实回落参数化草稿。
        // 逐屏逐字段采纳：模型没给的屏保留草稿文案，绝不因为「模型返回了」就整段照抄。
        String source = CreativeConstants.SOURCE_TEMPLATE;
        String modelKey = null;
        String traceId = null;
        String modelReason = null;
        int adopted = 0;
        CreativeDraftBrain.Suggestion suggestion = brain.suggest(CreativeConstants.CAP_STORYBOARD_DRAFT,
            project.getDataLevel(), "Y".equalsIgnoreCase(project.getAllowExternal()),
            storyboardPrompt(project, facts, drafts, brief), storyboardPayload(dna, facts, drafts, brief));
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
                // 标题守卫：模型偶尔把 N 屏标题都写成同一个产品名（比参数化草稿还差）。
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
        // R22：模块规划里写了「文案」的模块，它的屏用**人工文案**覆盖正文。
        // 放在模型润色之后是刻意的：人写的是最新意图，模型不该盖掉它；但标题/副标题保留推导结果——
        // 人只写了正文时，把标题也清空只会让页面更空。覆盖了几屏会写进说明，页面看得见。
        int copyOverrides = 0;
        if (!owners.isEmpty()) {
            for (int i = 0; i < drafts.size() && i < owners.size(); i++) {
                String copy = owners.get(i) == null ? null : StringUtils.trimToNull(owners.get(i).getCopyText());
                if (copy == null) {
                    continue;
                }
                CreativeDraftFactory.ScreenDraft draft = drafts.get(i);
                if (copy.equals(draft.bodyText())) {
                    continue;
                }
                drafts.set(i, new CreativeDraftFactory.ScreenDraft(draft.type(), draft.label(),
                    draft.productLockLevel(), draft.title(), draft.subtitle(), copy, draft.soloStatement()));
                copyOverrides++;
            }
            if (copyOverrides > 0) {
                modelReason = appendReason(modelReason,
                    "第 " + copyOverrides + " 屏用了模块规划里的人工文案（人工优先于模型与模板）");
            }
        }
        storyboard.setSource(source);
        storyboard.setStatus(STATUS_DRAFT);
        storyboardMapper.insert(storyboard);

        // 【C′】骨架与草稿的数量必须一致：两者现在都由同一份骨架契约驱动，理论上不会不一致，
        // 但这个校验必须留着——一旦将来有人只改了其中一处（或外部契约在运行中被换掉），
        // 下面按下标取草稿会变成难以定位的越界异常，而这里能给出"哪边少了几屏"的可读错误。
        if (drafts.size() != active.size()) {
            throw new ServiceException("屏骨架与文案草稿数量不一致：骨架 " + active.size()
                + " 屏（" + active.brief() + "），草稿 " + drafts.size()
                + " 屏。这属于代码/契约不一致，请检查屏骨架契约与文案策略是否配套。");
        }

        int sortNo = 0;
        for (CreativeScreenSkeleton.ScreenSpec template : active.screens()) {
            // 骨架来自契约，文案来自草稿（此刻两者数量已被上面的校验钉住）
            CreativeDraftFactory.ScreenDraft draft = drafts.get(sortNo);
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
            screen.setSpecJson(spec(template, dna, direction, active));
            screen.setWorkflowCode(workflowOf(owners, sortNo - 1));
            screen.setProductLockLevel(template.productLockLevel());
            screen.setStatus(STATUS_DRAFT);
            screenMapper.insert(screen);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("storyboardId", storyboard.getId());
        event.put("version", version);
        event.put("screenCount", active.size());
        event.put("dnaId", dnaId);
        event.put("directionId", storyboard.getVisualDirectionId());
        event.put("source", source);
        event.put("modelKey", modelKey);
        event.put("modelTraceId", traceId);
        event.put("modelAdoptedScreens", adopted);
        event.put("modelReason", modelReason);
        // R7：把「草稿用了哪些外部输入」记下来，便于回答「这版分镜为什么这么写」
        event.put("briefUsed", brief != null && Boolean.TRUE.equals(brief.getConfigured()));
        event.put("briefMustShowUsed", StringUtils.isNotBlank(mustShow));
        event.put("sellingPointBlocksUsed", sellingPoints.size());
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
     * <p>R7 起把品牌方要求一并写进去：模型改写时若不告诉它「必显什么、不能出现什么」，
     * 它会把品牌方明确要求的内容改掉——那正是逐字段采纳最容易踩的坑。</p>
     *
     * @param project 项目
     * @param facts   已确认事实
     * @param drafts  参数化草稿
     * @param brief   品牌 Brief（可空）
     * @return 提示词
     */
    private static String storyboardPrompt(CreativeProjectVo project, Map<String, String> facts,
                                           List<CreativeDraftFactory.ScreenDraft> drafts,
                                           CpBrandBriefVo brief) {
        StringBuilder sb = new StringBuilder();
        sb.append("为电商详情页项目「")
            .append(StringUtils.blankToDefault(project.getProductName(),
                StringUtils.blankToDefault(project.getTaskName(), "当前产品")))
            .append("」改写 ").append(drafts.size()).append(" 屏分镜文案。\n");
        sb.append("已确认产品事实（只能用这些，不得编造）：")
            .append(facts.isEmpty() ? "暂无" : JsonUtils.toJsonString(facts)).append("\n");
        if (brief != null && Boolean.TRUE.equals(brief.getConfigured())) {
            // 只带与文案相关的三项，且各自截断：这段是给模型看的约束，不是全量档案
            sb.append("品牌方要求（必须遵守，不得违背）：\n");
            sb.append("  必显信息：").append(clip(brief.getMustShow())).append("\n");
            sb.append("  禁用词与合规红线（不得出现）：").append(clip(brief.getForbiddenWords())).append("\n");
            sb.append("  主推卖点（按优先级）：").append(clip(brief.getMainPush())).append("\n");
            sb.append("  品牌调性：").append(clip(brief.getBrandTone())).append("\n");
        }
        sb.append("屏的顺序与类型固定，不得增删改顺序：\n");
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            sb.append("  ").append(draft.type()).append("：").append(draft.title())
                .append(" —— ").append(draft.soloStatement()).append("\n");
        }
        sb.append("输出 JSON：{\"screens\":[{\"type\":\"HERO\",\"title\":\"形如「产品名 · 这一屏要讲什么」，"
            + "不超过 30 字，" + drafts.size() + " 屏标题必须两两不同\",\"subtitle\":\"可省略\",\"bodyText\":\"可省略\","
            + "\"soloStatement\":\"20~200 字：这一屏的画面自己要讲清什么\"}, ...共 " + drafts.size() + " 项]}。"
            + "type 必须与上面给出的完全一致，只输出这个 JSON。");
        return sb.toString();
    }

    /**
     * 多行要求折成一行并截断（进模型提示词用，避免把整份 Brief 塞进去）。
     *
     * @param value 多行文本
     * @return 单行摘要；空返回「（未填）」
     */
    private static String clip(String value) {
        if (StringUtils.isBlank(value)) {
            return "（未填）";
        }
        String single = String.join("；", value.split("\\R")).trim();
        return single.length() > 200 ? single.substring(0, 200) + "…" : single;
    }

    /**
     * 品牌 Brief 必显信息的第一行（非空行）。
     *
     * @param brief 品牌 Brief（可空）
     * @return 第一行；没有返回 null
     */
    private static String mustShowFirstLine(CpBrandBriefVo brief) {
        if (brief == null || StringUtils.isBlank(brief.getMustShow())) {
            return null;
        }
        for (String line : brief.getMustShow().split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                return line.trim();
            }
        }
        return null;
    }

    /**
     * 卖点块 → 草稿提示（R22：模块行配了「对应卖点」就按配置取，没配的按顺序兜底）。
     *
     * <p>返回值与"第几个卖点屏"**按下标对齐**（草稿工厂就是按下标取的），因此取不到块时
     * 也要放一个占位（{@code CopyHint(null, null)}），否则后面的卖点屏会整体前移一位、
     * 拿到别人的文案。</p>
     *
     * @param taskId 项目ID
     * @param active 本次生效的骨架
     * @param owners 逐屏所属的模块行（可空：回落契约文件时没有模块）
     * @return 提示列表（长度 = 卖点屏数）
     */
    private List<CreativeDraftFactory.CopyHint> sellingPointHints(Long taskId, CreativeScreenSkeleton active,
                                                                  List<DpProjectModule> owners) {
        List<DpCopyBlockVo> blocks = new ArrayList<>();
        for (DpCopyBlockVo block : copyService.list(taskId, DpCopyBlockTypeEnum.SELLING_POINT.getCode())) {
            if (StringUtils.isBlank(block.getTitle()) && StringUtils.isBlank(block.getContent())) {
                continue;
            }
            blocks.add(block);
        }
        Map<String, DpCopyBlockVo> byId = new LinkedHashMap<>();
        for (DpCopyBlockVo block : blocks) {
            byId.put(String.valueOf(block.getId()), block);
        }
        List<CreativeDraftFactory.CopyHint> hints = new ArrayList<>();
        Set<Long> used = new java.util.HashSet<>();
        int fallback = 0;
        for (int i = 0; i < active.screens().size(); i++) {
            if (!"SELLING_POINT".equals(active.screens().get(i).type())) {
                continue;
            }
            DpProjectModule owner = owners != null && i < owners.size() ? owners.get(i) : null;
            DpCopyBlockVo chosen = null;
            if (owner != null) {
                for (String code : CreativeModuleServiceImpl.splitCodes(owner.getSellingPointCodes())) {
                    DpCopyBlockVo block = byId.get(code);
                    if (block != null && used.add(block.getId())) {
                        chosen = block;
                        break;
                    }
                }
            }
            while (chosen == null && fallback < blocks.size()) {
                DpCopyBlockVo block = blocks.get(fallback++);
                if (used.add(block.getId())) {
                    chosen = block;
                }
            }
            hints.add(chosen == null
                ? new CreativeDraftFactory.CopyHint(null, null)
                : new CreativeDraftFactory.CopyHint(chosen.getTitle(), chosen.getContent()));
        }
        return hints;
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
     * 这一屏用哪个工作流出图（V0.2 R22）。
     *
     * <p>模块规划里给模块配了 Workflow 就用**第一个**（右栏是逗号分隔的候选清单，第一个是选中项）；
     * 没配就沿用默认图生图工作流。这里**不校验编码是否已发布**：真正的把关在出图入口
     * （提交时会按已发布契约校验并明确报错），在这里拦会让"配置尚未部署"变成保存不了的假故障。</p>
     *
     * @param owners 逐屏所属模块行（可空）
     * @param index  屏下标
     * @return 工作流编码
     */
    private String workflowOf(List<DpProjectModule> owners, int index) {
        if (owners == null || index < 0 || index >= owners.size() || owners.get(index) == null) {
            return CreativeConstants.DEFAULT_HERO_WORKFLOW;
        }
        List<String> codes = CreativeModuleServiceImpl.splitCodes(owners.get(index).getWorkflowCodes());
        return codes.isEmpty() ? CreativeConstants.DEFAULT_HERO_WORKFLOW : codes.get(0);
    }

    /**
     * 结构化载荷。
     *
     * @param dna    基因
     * @param facts  事实
     * @param drafts 草稿
     * @param brief  品牌 Brief（可空；只带与文案相关的摘要）
     * @return 载荷
     */
    private static Map<String, Object> storyboardPayload(ObjectNode dna, Map<String, String> facts,
                                                        List<CreativeDraftFactory.ScreenDraft> drafts,
                                                        CpBrandBriefVo brief) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "storyboard-draft");
        payload.put("facts", facts);
        payload.put("dna", dna.toString());
        payload.put("screenTypes", drafts.stream().map(CreativeDraftFactory.ScreenDraft::type).toList());
        if (brief != null && Boolean.TRUE.equals(brief.getConfigured())) {
            Map<String, Object> briefSummary = new LinkedHashMap<>();
            briefSummary.put("mustShow", clip(brief.getMustShow()));
            briefSummary.put("forbiddenWords", clip(brief.getForbiddenWords()));
            briefSummary.put("mainPush", clip(brief.getMainPush()));
            briefSummary.put("brandTone", clip(brief.getBrandTone()));
            briefSummary.put("status", brief.getStatus());
            payload.put("brandBrief", briefSummary);
        }
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
     * 每屏的视觉规格（取景/构图/光线/背景/占比/留白）。
     *
     * <p>C′：取景（shot）不再由这里的 switch 决定，而是来自屏骨架契约里的 {@code shot} 字段
     * （可含 {@code {ratio}} 占位符，用锁定基因的占比代入）。这样"加一种屏"不需要改这段代码。</p>
     *
     * @param template  屏骨架定义
     * @param dna       锁定基因
     * @param direction 选定方向（可空）
     * @return 规格 JSON
     */
    private String spec(CreativeScreenSkeleton.ScreenSpec template, ObjectNode dna,
                            DpVisualDirectionVo direction, CreativeScreenSkeleton active) {
        Map<String, Object> spec = new LinkedHashMap<>();
        String scene = direction != null ? String.valueOf(direction.getStrategy().getOrDefault("scene", "")) : "";
        String lighting = direction != null
            ? String.valueOf(direction.getStrategy().getOrDefault("lighting", "")) : "";
        String composition = direction != null
            ? String.valueOf(direction.getStrategy().getOrDefault("composition", "")) : "";
        spec.put("shot", active.shotOf(template, ratio(dna)));
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

    /**
     * 屏类型的展示短名（C′：收敛到屏骨架契约的 typeDesc，不再在本类里写一份 switch）。
     *
     * @param type 屏类型
     * @return 展示短名；契约里没有的类型原样返回（历史分镜可能有已下线的屏类型）
     */
    private String screenTypeDesc(String type) {
        return skeleton().descOf(type);
    }

    private String sourceDesc(String source) {
        if (SOURCE_TEMPLATE.equals(source)) {
            return "由屏骨架 + 参数化文案（已确认事实 + 锁定基因 + 参考图实测 + 选定方向派生，未使用模型）";
        }
        if (CreativeConstants.SOURCE_MODEL.equals(source)) {
            return "由受管模型产出文案（经逐字段验收后才采纳；屏骨架共 " + skeleton().size() + " 屏）";
        }
        return source;
    }

}
