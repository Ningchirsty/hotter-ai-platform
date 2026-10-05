package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.service.IContentTaskGateService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpGateItem;
import org.dromara.creative.domain.DpGateProfile;
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.domain.vo.DpVisualDnaVo;
import org.dromara.content.enums.ContentBriefStatusEnum;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.mapper.CreativeCardMapper;
import org.dromara.creative.mapper.DpGateItemMapper;
import org.dromara.creative.mapper.DpGateProfileMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉门服务实现。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeGateServiceImpl implements ICreativeGateService {

    /**
     * 视觉门在互动卡里的字段编码（同一任务同一编码只保留一张待处理卡）
     */
    private static final String CARD_FIELD_CODE = "visual_gate";

    /**
     * 通过凭据（dp_stage_event.action）
     */
    private static final String ACTION_PASS = "VISUAL_GATE_PASS";

    private static final String LEVEL_BLOCK = "BLOCK";
    private static final String LEVEL_CONDITION = "CONDITION";

    private static final String OPTION_CONFIRM = "CONFIRM";
    private static final String OPTION_BLOCK = "BLOCK";

    private final ICreativeProjectService projectService;
    private final IContentBrandBriefService briefService;
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeStoryboardService storyboardService;
    private final IContentTaskService contentTaskService;
    private final IContentTaskGateService contentTaskGateService;
    private final CreativeCardMapper cardMapper;
    private final DpStageEventMapper eventMapper;
    private final DpGateProfileMapper gateProfileMapper;
    private final DpGateItemMapper gateItemMapper;

    /**
     * 默认闸门项清单（**没有配置时的回落**，也是"配置写错了"的参照物）。
     *
     * <p><b>等级变更（内测冲突 A 的落地，2026-10）</b>：{@code BRAND_BRIEF_CONFIRMED} 从
     * {@code CONDITION} 升为 {@code BLOCK}。这是一次**有意的行为变更**，
     * {@code CreativeGateItemConfigTest} 的 golden list 就是为此设的闸门，改它必须是有意识的决定。</p>
     *
     * <p><b>为什么升级</b>：内测实测"不填品牌要求也能一路出图到交付 V1.0"（S1）。
     * 牌子是同一件事：品牌部没做完功课。按"同一产线的同一根因只留一道硬拦"的定案，
     * 这道硬拦就是<b>品牌 Brief 是否已确认</b>——它是品牌部功课的总闸，
     * 而 {@code FORBIDDEN_WORDS_DECLARED} 只是它的一项细项，再设成 BLOCK 就是对同一根因拦第二次。</p>
     *
     * <p><b>⚠️ 与数据库配置必须一致</b>：有闸门档案（{@code dp_gate_profile}）的交付类型，
     * 等级以**档案里的 {@code dp_gate_item.level} 为准**（见 {@link #mergeItems}），
     * 本清单只在"该交付类型没有已发布档案"时生效。两处不一致 = 同一件要求在不同交付类型上
     * 时紧时松，那正是内测冲突 F 的形状。配套 SQL：
     * {@code script/sql/dp_gate_brand_requirement_uniform.sql}。</p>
     */
    static final List<ItemSpec> DEFAULT_ITEMS = List.of(
        new ItemSpec("DNA_LOCKED", "视觉基因已锁定", LEVEL_BLOCK),
        new ItemSpec("REFERENCE_IMAGE", "产品参考图已上传", LEVEL_BLOCK),
        new ItemSpec("DIRECTION_SELECTED", "视觉方向已选定", LEVEL_CONDITION),
        new ItemSpec("STORYBOARD_LOCKED", "分镜已锁定", LEVEL_CONDITION),
        new ItemSpec("BRAND_TONE_CONFIRMED", "品牌调性已确认", LEVEL_CONDITION),
        new ItemSpec("BRAND_BRIEF_CONFIRMED", "品牌 Brief 已填写并确认", LEVEL_BLOCK),
        new ItemSpec("FORBIDDEN_WORDS_DECLARED", "已声明禁用词与合规红线", LEVEL_CONDITION)
    );

    /**
     * 一条闸门项的"配置侧"定义：**有哪些项、什么等级、什么顺序、叫什么名字**。
     *
     * <p>注意：这里没有"怎么判"——判定逻辑不可能配置化（见类注释与对照文档 D3）。</p>
     *
     * @param code  项编码（代码里按它找检查器）
     * @param label 展示名
     * @param level 等级（BLOCK/CONDITION）
     */
    record ItemSpec(String code, String label, String level) {
    }

    @Override
    public GateEvaluation evaluate(Long taskId) {
        CreativeProjectVo project = projectService.getProject(taskId);
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);

        // 先把每一项"检查结果"算出来（key = 项编码）：判定逻辑仍在代码里，配置只决定取舍与顺序
        Map<String, GateItem> checked = new LinkedHashMap<>();
        checked.put("DNA_LOCKED", dnaLockedItem(taskId));
        checked.put("REFERENCE_IMAGE", referenceImageItem(detail));
        checked.put("DIRECTION_SELECTED", directionSelectedItem(taskId));
        checked.put("STORYBOARD_LOCKED", storyboardLockedItem(taskId));
        checked.put("BRAND_TONE_CONFIRMED", brandToneItem(detail));
        CpBrandBriefVo brief = briefService.get(taskId);
        checked.put("BRAND_BRIEF_CONFIRMED", brandBriefItem(brief));
        checked.put("FORBIDDEN_WORDS_DECLARED", forbiddenWordsItem(brief));

        // 再按"该交付类型的闸门配置"组装（没配就回落 DEFAULT_ITEMS）
        List<GateItem> items = mergeItems(configuredItems(project.getDeliverableType()), checked);

        List<String> blocked = items.stream()
            .filter(item -> LEVEL_BLOCK.equals(item.level()) && !item.passed())
            .map(item -> item.label() + "（" + item.detail() + "）")
            .toList();
        boolean submittable = blocked.isEmpty();
        boolean passed = hasPassed(taskId);
        Long cardId = cardMapper.selectPendingCardId(taskId, CARD_FIELD_CODE);
        String cardStatus = cardMapper.selectLatestCardStatus(taskId, CARD_FIELD_CODE);
        String stage = projectService.stageOf(taskId);
        return new GateEvaluation(items, blocked, submittable, passed, cardStatus, cardId,
            stage, DpVisualStageEnum.descOf(stage));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GateEvaluation submit(Long taskId) {
        GateEvaluation evaluation = evaluate(taskId);
        if (!evaluation.submittable()) {
            throw new ServiceException("视觉门还不满足提交条件：" + String.join("；", evaluation.blocked()));
        }
        Long existing = cardMapper.selectPendingCardId(taskId, CARD_FIELD_CODE);
        if (existing == null) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("cardId", IdGeneratorUtil.nextLongId());
            card.put("taskId", taskId);
            card.put("fieldCode", CARD_FIELD_CODE);
            card.put("title", "视觉方案待确认（" + projectName(evaluation) + "）");
            card.put("question", buildQuestion(evaluation));
            card.put("evidenceJson", JsonUtils.toJsonString(checklistJson(evaluation)));
            card.put("impactJson", JsonUtils.toJsonString(List.of(Map.of(
                "note", "确认后即可按该方案批量出图；打回会阻断内容任务流转并回到视觉门"))));
            card.put("optionsJson", JsonUtils.toJsonString(List.of(
                Map.of("option", OPTION_CONFIRM, "label", "确认方案，允许开始出图"),
                Map.of("option", OPTION_BLOCK, "label", "打回：方案需修改"))));
            card.put("assigneeId", LoginHelper.getUserId());
            card.put("assigneeName", displayName());
            card.put("dueAt", null);
            card.put("userId", LoginHelper.getUserId());
            card.put("deptId", LoginHelper.getDeptId());
            cardMapper.insertApprovalCard(card);
            log.info("视觉门已建审批卡 taskId={}", taskId);
        }
        projectService.moveStage(taskId, DpVisualStageEnum.VISUAL_GATE, "VISUAL_GATE_SUBMIT",
            JsonUtils.toJsonString(Map.of("items", evaluation.items().size(),
                "conditions", evaluation.items().stream()
                    .filter(i -> LEVEL_CONDITION.equals(i.level()) && !i.passed())
                    .map(GateItem::label).toList())));
        return evaluate(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GateEvaluation review(Long taskId, String option, String comment) {
        String normalized = StringUtils.blankToDefault(option, "").toUpperCase();
        if (!OPTION_CONFIRM.equals(normalized) && !OPTION_BLOCK.equals(normalized)) {
            throw new ServiceException("处理选项只能是 CONFIRM 或 BLOCK");
        }
        Long cardId = cardMapper.selectPendingCardId(taskId, CARD_FIELD_CODE);
        if (cardId == null) {
            throw new ServiceException("没有待处理的视觉门确认项，请先「提交视觉门」");
        }
        // 审批卡的状态流转由视觉工厂自己完成（内容模块的 resolve 语义是「采用候选事实值」，
        // 审批卡没有候选值，借道会报错或把审批记成「补充资料」）。
        // 但闸门重算是复用的：打回后必须让内容侧状态同步为待确认。
        String status = OPTION_CONFIRM.equals(normalized) ? "RESOLVED" : "BLOCKED";
        int updated = cardMapper.resolveApprovalCard(cardId, status, normalized, comment, LoginHelper.getUserId());
        if (updated == 0) {
            throw new ServiceException("该确认项已被处理过，请刷新后重试");
        }
        contentTaskGateService.recheckAndApply(taskId);

        if (OPTION_CONFIRM.equals(normalized)) {
            projectService.moveStage(taskId, DpVisualStageEnum.VISUAL_LOCKED, ACTION_PASS,
                JsonUtils.toJsonString(Map.of("cardId", cardId, "comment",
                    StringUtils.blankToDefault(comment, "视觉方案确认通过"))));
        } else {
            projectService.moveStage(taskId, DpVisualStageEnum.VISUAL_GATE, "VISUAL_GATE_REJECT",
                JsonUtils.toJsonString(Map.of("cardId", cardId, "comment",
                    StringUtils.blankToDefault(comment, "视觉方案被打回"))));
        }
        return evaluate(taskId);
    }

    @Override
    public boolean hasPassed(Long taskId) {
        Long count = eventMapper.selectCount(new LambdaQueryWrapper<DpStageEvent>()
            .eq(DpStageEvent::getTaskId, taskId)
            .eq(DpStageEvent::getAction, ACTION_PASS));
        return count != null && count > 0;
    }

    @Override
    public void requireCanProduce(Long taskId) {
        if (hasPassed(taskId)) {
            return;
        }
        GateEvaluation evaluation = evaluate(taskId);
        StringBuilder reason = new StringBuilder("尚未通过视觉门，不能出图。");
        if (!evaluation.submittable()) {
            reason.append("当前硬性项未满足：").append(String.join("；", evaluation.blocked())).append("。");
        }
        reason.append("请到「详情页与审核」提交视觉门并由人确认后再出图");
        if (!evaluation.items().stream()
            .filter(i -> LEVEL_CONDITION.equals(i.level()) && !i.passed())
            .toList().isEmpty()) {
            reason.append("（建议项：").append(evaluation.items().stream()
                .filter(i -> LEVEL_CONDITION.equals(i.level()) && !i.passed())
                .map(GateItem::label).reduce((a, b) -> a + "、" + b).orElse("")).append("）");
        }
        throw new ServiceException(reason.toString());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 闸门项配置（V0.2 B2）：配置决定"有哪些项/顺序/等级/展示名"，代码决定"怎么判"
    // ------------------------------------------------------------------

    /**
     * 取该交付类型的闸门项配置；没有已发布配置就回落 {@link #DEFAULT_ITEMS}。
     *
     * @param deliveryType 交付类型（cp_task.deliverable_type）
     * @return 项定义列表（已按 sort_no 排序）
     */
    List<ItemSpec> configuredItems(String deliveryType) {
        if (StringUtils.isBlank(deliveryType)) {
            return DEFAULT_ITEMS;
        }
        List<DpGateProfile> profiles = gateProfileMapper.selectList(new LambdaQueryWrapper<DpGateProfile>()
            .eq(DpGateProfile::getDeliveryType, deliveryType)
            .eq(DpGateProfile::getStatus, "PUBLISHED")
            .orderByDesc(DpGateProfile::getVersion)
            .orderByDesc(DpGateProfile::getId));
        if (profiles.isEmpty()) {
            // 没配置不是错误：回落默认清单（与改造前逐字一致），并留痕便于发现"某场景还没配闸门"
            log.info("交付类型 {} 没有已发布的闸门配置，使用默认 {} 项清单", deliveryType, DEFAULT_ITEMS.size());
            return DEFAULT_ITEMS;
        }
        List<DpGateItem> rows = gateItemMapper.selectList(new LambdaQueryWrapper<DpGateItem>()
            .eq(DpGateItem::getProfileId, profiles.get(0).getId())
            .orderByAsc(DpGateItem::getSortNo)
            .orderByAsc(DpGateItem::getId));
        if (rows.isEmpty()) {
            log.warn("交付类型 {} 的闸门档案 {} 没有任何闸门项，使用默认清单",
                deliveryType, profiles.get(0).getProfileCode());
            return DEFAULT_ITEMS;
        }
        return rows.stream()
            .map(row -> new ItemSpec(row.getItemCode(), row.getItemLabel(), row.getLevel()))
            .toList();
    }

    /**
     * 按配置项定义组装最终清单：**判定结果取自代码检查器，展示名与等级取自配置**。
     *
     * <p>配置里出现了代码没有检查器的项时按**未通过**处理（fail-closed）：
     * 配置说"这一项要检查"，而我们没有对应逻辑，就绝不能显示成通过——
     * 那等于用一个看不见的假绿把门放开。</p>
     *
     * @param specs   配置项定义
     * @param checked 代码给出的检查结果（key = 项编码）
     * @return 最终清单（顺序 = 配置顺序）
     */
    static List<GateItem> mergeItems(List<ItemSpec> specs, Map<String, GateItem> checked) {
        List<GateItem> items = new ArrayList<>();
        for (ItemSpec spec : specs) {
            GateItem result = checked.get(spec.code());
            if (result == null) {
                items.add(new GateItem(spec.code(), spec.label(), spec.level(), false,
                    "该配置项在当前版本没有对应的检查逻辑，已按未通过处理（请补齐检查器或从配置里移除）"));
                continue;
            }
            items.add(new GateItem(spec.code(), spec.label(), spec.level(), result.passed(), result.detail()));
        }
        return items;
    }

    /**
     * 视觉基因已锁定（硬性）：出图的提示词与规范都从它派生，没有它就没有「统一视觉」。
     *
     * <p>判据必须是「已锁定版本」而不是「最新版本」——最新版可能是锁定后又改出来的待确认稿，
     * 而实际出图依据仍是那一版锁定的基因。</p>
     *
     * @param taskId 项目ID
     * @return 闸门项
     */
    private GateItem dnaLockedItem(Long taskId) {
        DpVisualDnaVo lockedDna = dnaService.locked(taskId);
        DpVisualDnaVo latestDna = dnaService.latest(taskId);
        boolean dnaLocked = lockedDna != null;
        return new GateItem("DNA_LOCKED", "视觉基因已锁定", LEVEL_BLOCK, dnaLocked,
            dnaLocked
                ? "已锁定 " + lockedDna.getDnaNo() + "（来源：" + lockedDna.getSourceDesc() + "）"
                : (latestDna == null ? "尚未生成视觉基因"
                    : "尚无已锁定版本；最新版 " + latestDna.getDnaNo()
                        + " 状态为「" + latestDna.getStatusDesc() + "」，请先锁定"));
    }

    /**
     * 参考图齐备（硬性）：出图要拿它当输入。
     *
     * @param detail 任务详情
     * @return 闸门项
     */
    private GateItem referenceImageItem(ContentTaskDetailVo detail) {
        long images = imageCount(detail);
        return new GateItem("REFERENCE_IMAGE", "产品参考图已上传", LEVEL_BLOCK, images > 0,
            images > 0 ? "已上传 " + images + " 张图片附件" : "项目附件里还没有图片");
    }

    /**
     * 视觉方向已选定（建议）：没有方向也能出图，但同一屏的取舍会不一致。
     *
     * @param taskId 项目ID
     * @return 闸门项
     */
    private GateItem directionSelectedItem(Long taskId) {
        DpVisualDirectionVo direction = directionService.selected(taskId);
        return new GateItem("DIRECTION_SELECTED", "视觉方向已选定", LEVEL_CONDITION, direction != null,
            direction == null ? "尚未在 A/B/C 中选定方向"
                : "已选定 " + direction.getDirectionCode() + " · " + direction.getDirectionName());
    }

    /**
     * 分镜已锁定（建议）。
     *
     * @param taskId 项目ID
     * @return 闸门项
     */
    private GateItem storyboardLockedItem(Long taskId) {
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        boolean storyboardLocked = storyboard != null && "LOCKED".equals(storyboard.getStatus());
        return new GateItem("STORYBOARD_LOCKED", "分镜已锁定", LEVEL_CONDITION, storyboardLocked,
            storyboard == null ? "尚未生成分镜"
                : (storyboardLocked ? "已锁定 " + storyboard.getStoryboardNo()
                    + "（" + storyboard.getScreenCount() + " 屏）"
                    : "当前 " + storyboard.getStoryboardNo() + " 还是草稿"));
    }

    /**
     * 品牌 Brief 是否已由品牌方确认（R7）。
     *
     * <p>判据是 {@code status=CONFIRMED}：光「填过」不算——若只判「有没有记录」，
     * 保存一次草稿就能让这一项变绿，闸门就退化成了「有没有点过保存」。</p>
     *
     * @param brief 品牌 Brief 视图
     * @return 闸门项
     */
    /**
     * 品牌 Brief 是否已由品牌方确认（R7）。
     *
     * <p><b>这里产出的 level 会被 {@link #mergeItems} 覆盖</b>（等级以场景配置为准），
     * 所以 <b>detail 文案里绝不能声明等级</b>：文案写"当前是建议级、以后会升级"，
     * 而等级改到配置里之后，文案就变成谎话——内测真机上就出现过这一幕
     * （冲突 A 把本项升为 BLOCK 后，提示仍在说"只提示、不阻断"）。
     * 等级由界面上的「等级」列展示，只有一处来源。</p>
     *
     * <p>包可见是为了让单测能直接钉住"三态分开说"这件事——这段文案出错的后果是
     * 品牌部照它去点一个必然被拒绝的按钮（v1 反馈的"确认没反应"）。</p>
     *
     * @param brief 品牌 Brief 视图
     * @return 闸门项
     */
    static GateItem brandBriefItem(CpBrandBriefVo brief) {
        boolean configured = brief != null && Boolean.TRUE.equals(brief.getConfigured());
        boolean confirmed = configured
            && ContentBriefStatusEnum.CONFIRMED.getCode().equals(brief.getStatus());
        if (confirmed) {
            return new GateItem("BRAND_BRIEF_CONFIRMED", "品牌 Brief 已填写并确认", LEVEL_CONDITION, true,
                "品牌方已确认（确认时间 " + brief.getConfirmedAt() + "）");
        }
        // v1 反馈「品牌 brief 确认点了没反应」的根因之一：**有一条全空的 Brief 记录**
        // 被这里说成「Brief 已填写但状态是『草稿』」——品牌部照这话去点「品牌方确认」，
        // 后端会以「8 项全空：至少填一项再确认」拒绝（本地实测：确认接口返回 500，
        // 状态仍是 DRAFT），于是看起来就是"点了没反应"。三态必须分开说：
        // 没有记录 / 有记录但一个字没填 / 填了但没确认。
        boolean hasContent = configured && briefHasContent(brief);
        return new GateItem("BRAND_BRIEF_CONFIRMED", "品牌 Brief 已填写并确认", LEVEL_CONDITION, false,
            (!configured
                ? "还没有填品牌 Brief：请品牌部到「内容生产协同 → 内容任务 → 任务详情」填写品牌调性/"
                    + "必显信息/禁用词/主推卖点，然后点「品牌方确认」"
                : !hasContent
                    ? "Brief 还是一份空草稿（8 项都没填、也没有参考风格图片）：请品牌部先填内容再点"
                        + "「品牌方确认」——全空时确认会被直接拒绝，所以现在点它不会有任何变化"
                    : "Brief 已填写但还没确认（当前状态「"
                        + ContentBriefStatusEnum.descOf(brief.getStatus())
                        + "」）：确认权在品牌部——请品牌部到「内容生产协同 → 内容任务 → 任务详情」"
                        + "点「品牌方确认」")
                + "。设计侧对本项只读（C1 起已收回设计侧的确认入口）");
    }

    /**
     * 这份 Brief 里到底有没有内容（8 个文字字段 + 参考风格图片）。
     *
     * <p>口径与内容域的确认校验一致（那边是"8 项全空且没传参考风格图片就不许确认"）：
     * 只传了参考风格图片也算填过——否则会出现"两边对同一份 Brief 说法不同"。</p>
     *
     * @param brief 品牌 Brief 视图
     * @return 有内容返回 true
     */
    private static boolean briefHasContent(CpBrandBriefVo brief) {
        if (brief == null) {
            return false;
        }
        for (String value : List.of(
            // `List.of` 不收 null，所以先空串化——这里只是"有没有字"，不改变取值口径
            StringUtils.blankToDefault(brief.getBrandTone(), ""),
            StringUtils.blankToDefault(brief.getMustShow(), ""),
            StringUtils.blankToDefault(brief.getForbiddenWords(), ""),
            StringUtils.blankToDefault(brief.getTargetAudience(), ""),
            StringUtils.blankToDefault(brief.getMainPush(), ""),
            StringUtils.blankToDefault(brief.getSizeSpecReq(), ""),
            StringUtils.blankToDefault(brief.getStyleRef(), ""),
            StringUtils.blankToDefault(brief.getStyleRefFiles(), ""))) {
            if (StringUtils.isNotBlank(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否已声明禁用词与合规红线（R7）。
     *
     * <p>同样地，detail 里不声明等级（等级来自场景配置）。它是「品牌 Brief 已确认」的细项，
     * 按冲突 A 的定案不与总闸各设一道硬拦——但**这件事也不写在文案里**，
     * 否则配置一改文案就失真。</p>
     *
     * @param brief 品牌 Brief 视图
     * @return 闸门项
     */
    private static GateItem forbiddenWordsItem(CpBrandBriefVo brief) {
        boolean declared = brief != null && StringUtils.isNotBlank(brief.getForbiddenWords());
        return new GateItem("FORBIDDEN_WORDS_DECLARED", "已声明禁用词与合规红线", LEVEL_CONDITION, declared,
            declared ? "已声明 " + lineCount(brief.getForbiddenWords()) + " 条；出图负向提示词会逐条追加"
                : "还没有声明禁用词：请品牌部到「内容生产协同 → 内容任务 → 任务详情」的"
                    + "「禁用词与合规红线」里一行一条填上（未声明时出图只能用默认禁忌词表）");
    }

    /**
     * 多行文本的有效行数（仅用于可读说明）。
     *
     * @param value 多行文本
     * @return 行数
     */
    private static int lineCount(String value) {
        int count = 0;
        for (String line : value.split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                count++;
            }
        }
        return count;
    }

    private GateItem brandToneItem(ContentTaskDetailVo detail) {
        boolean present = false;
        String value = null;
        String status = null;
        if (detail != null && detail.getFacts() != null) {
            for (CpFactSnapshotVo fact : detail.getFacts()) {
                if ("brand_tone".equals(fact.getFieldCode())) {
                    present = true;
                    value = fact.getFieldValue();
                    status = fact.getConfirmStatus();
                    break;
                }
            }
        }
        if (!present) {
            return new GateItem("BRAND_TONE_CONFIRMED", "品牌调性已确认", LEVEL_CONDITION, true,
                "该交付类型未声明品牌调性事实，本项不适用");
        }
        boolean confirmed = ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(status);
        return new GateItem("BRAND_TONE_CONFIRMED", "品牌调性已确认", LEVEL_CONDITION, confirmed,
            confirmed ? "已确认：" + value : "存在品牌调性事实但状态为「" + status + "」");
    }

    private long imageCount(ContentTaskDetailVo detail) {
        if (detail == null || detail.getFiles() == null) {
            return 0;
        }
        return detail.getFiles().stream()
            .filter(file -> "IMAGE".equalsIgnoreCase(file.getFileKind()))
            .count();
    }

    private List<Map<String, Object>> checklistJson(GateEvaluation evaluation) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GateItem item : evaluation.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", item.code());
            row.put("label", item.label());
            row.put("level", item.level());
            row.put("passed", item.passed());
            row.put("detail", item.detail());
            list.add(row);
        }
        return list;
    }

    private String buildQuestion(GateEvaluation evaluation) {
        List<String> unmet = evaluation.items().stream()
            .filter(item -> !item.passed())
            .map(item -> item.label() + "：" + item.detail())
            .toList();
        String base = "请确认这一版视觉方案（视觉基因 + 方向 + 分镜）可以进入批量出图。"
            + "确认后出图将严格按已锁定的视觉基因派生提示词；打回会回到视觉门并阻断内容任务流转。";
        return unmet.isEmpty() ? base : base + "\n未满足的建议项：" + String.join("；", unmet);
    }

    private String projectName(GateEvaluation evaluation) {
        return evaluation.stageDesc() == null ? "视觉方案" : evaluation.stageDesc();
    }

    private static String displayName() {
        try {
            return LoginHelper.getLoginUser() == null ? "系统"
                : StringUtils.blankToDefault(LoginHelper.getLoginUser().getNickname(), "用户");
        } catch (Exception e) {
            return "系统";
        }
    }

}
