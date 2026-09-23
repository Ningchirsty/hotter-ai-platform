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
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.domain.vo.DpVisualDnaVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.mapper.CreativeCardMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
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
    private final ICreativeDnaService dnaService;
    private final ICreativeDirectionService directionService;
    private final ICreativeStoryboardService storyboardService;
    private final IContentTaskService contentTaskService;
    private final IContentTaskGateService contentTaskGateService;
    private final CreativeCardMapper cardMapper;
    private final DpStageEventMapper eventMapper;

    @Override
    public GateEvaluation evaluate(Long taskId) {
        CreativeProjectVo project = projectService.getProject(taskId);
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);

        List<GateItem> items = new ArrayList<>();

        // 1) 视觉基因已锁定（硬性）：出图的提示词与规范都从它派生，没有它就没有「统一视觉」。
        //    判据必须是「已锁定版本」而不是「最新版本」——最新版可能是锁定后又改出来的待确认稿，
        //    而实际出图依据仍是那一版锁定的基因。
        DpVisualDnaVo lockedDna = dnaService.locked(taskId);
        DpVisualDnaVo latestDna = dnaService.latest(taskId);
        boolean dnaLocked = lockedDna != null;
        items.add(new GateItem("DNA_LOCKED", "视觉基因已锁定", LEVEL_BLOCK, dnaLocked,
            dnaLocked
                ? "已锁定 " + lockedDna.getDnaNo() + "（来源：" + lockedDna.getSourceDesc() + "）"
                : (latestDna == null ? "尚未生成视觉基因"
                    : "尚无已锁定版本；最新版 " + latestDna.getDnaNo()
                        + " 状态为「" + latestDna.getStatusDesc() + "」，请先锁定")));

        // 2) 参考图齐备（硬性）：出图要拿它当输入
        long images = imageCount(detail);
        items.add(new GateItem("REFERENCE_IMAGE", "产品参考图已上传", LEVEL_BLOCK, images > 0,
            images > 0 ? "已上传 " + images + " 张图片附件" : "项目附件里还没有图片"));

        // 3) 视觉方向已选定（建议）：没有方向也能出图，但同一屏的取舍会不一致
        DpVisualDirectionVo direction = directionService.selected(taskId);
        items.add(new GateItem("DIRECTION_SELECTED", "视觉方向已选定", LEVEL_CONDITION, direction != null,
            direction == null ? "尚未在 A/B/C 中选定方向"
                : "已选定 " + direction.getDirectionCode() + " · " + direction.getDirectionName()));

        // 4) 分镜已锁定（建议）
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        boolean storyboardLocked = storyboard != null && "LOCKED".equals(storyboard.getStatus());
        items.add(new GateItem("STORYBOARD_LOCKED", "分镜已锁定", LEVEL_CONDITION, storyboardLocked,
            storyboard == null ? "尚未生成分镜"
                : (storyboardLocked ? "已锁定 " + storyboard.getStoryboardNo()
                    + "（" + storyboard.getScreenCount() + " 屏）"
                    : "当前 " + storyboard.getStoryboardNo() + " 还是草稿")));

        // 5) 品牌调性事实（建议，且仅当该交付类型确实声明了这条事实时才检查）
        items.add(brandToneItem(detail));

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
