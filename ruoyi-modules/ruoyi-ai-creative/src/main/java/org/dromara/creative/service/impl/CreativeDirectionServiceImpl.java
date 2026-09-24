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
import org.dromara.creative.domain.DpVisualDirection;
import org.dromara.creative.domain.bo.CreativeDirectionBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeDraftFactory;
import org.dromara.creative.helper.VisualDnaSchema;
import org.dromara.creative.mapper.DpVisualDirectionMapper;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉方向服务实现。
 *
 * <p><b>来源如实标注</b>：当前部署没有注册可用的文本/视觉模型（治理台未注册视觉能力），
 * 因此方向由「锁定基因 + 明确的行业取舍模板」派生，来源标为 TEMPLATE 并在概念里说明。
 * 将来注册模型后，同一处改为模型产出即可，页面来源徽标会随之变化。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeDirectionServiceImpl implements ICreativeDirectionService {

    /**
     * 来源：模板派生（非模型）
     */
    private static final String SOURCE_TEMPLATE = "TEMPLATE";

    private static final String STATUS_GENERATED = "GENERATED";
    private static final String STATUS_SELECTED = "SELECTED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final DpVisualDirectionMapper directionMapper;
    private final ICreativeDnaService dnaService;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DpVisualDirectionVo> generate(Long taskId) {
        projectService.getProject(taskId);
        ObjectNode dna = dnaService.activeDna(taskId);
        if (dnaService.activeDnaId(taskId) == null) {
            throw new ServiceException("还没有视觉基因，请先生成并锁定基因再定方向");
        }
        Long dnaId = dnaService.activeDnaId(taskId);

        // 废弃旧的未选定方向（保留已选定与历史记录的可读性：只改状态不删行）
        List<DpVisualDirection> existing = directionMapper.selectList(
            new LambdaQueryWrapper<DpVisualDirection>()
                .eq(DpVisualDirection::getTaskId, taskId)
                .eq(DpVisualDirection::getStatus, STATUS_GENERATED));
        for (DpVisualDirection old : existing) {
            old.setStatus(STATUS_REJECTED);
            old.setRemark(appendRemark(old.getRemark(), "被重新生成的方向取代"));
            directionMapper.updateById(old);
        }

        // 参数化草稿：方向名/取舍说明/策略明细全部由「锁定基因（含参考图实测）+ 已确认事实 + 产品名」推导。
        // 同输入可复现（不引入随机数），不同输入必然不同——这正是「刷新内容永远一样」的修复点。
        // 来源仍如实标 TEMPLATE：参数化模板不是模型产物。
        CreativeProjectVo project = projectService.getProject(taskId);
        Map<String, String> facts = confirmedFacts(contentTaskService.getDetail(taskId));
        List<CreativeDraftFactory.DirectionDraft> drafts =
            CreativeDraftFactory.directions(dna, project.getProductName(), facts);

        List<DpVisualDirection> created = new ArrayList<>();
        for (CreativeDraftFactory.DirectionDraft draft : drafts) {
            created.add(build(taskId, draft.code(), draft.name(), draft.concept(),
                draft.strategy(), created.size() + 1));
        }

        List<DpVisualDirectionVo> result = new ArrayList<>();
        for (DpVisualDirection entity : created) {
            entity.setSource(SOURCE_TEMPLATE);
            directionMapper.insert(entity);
        }
        // 差异点必须在这里也算出来：generate 的响应与 list/select 的响应不能有两种形状，
        // 否则页面「刚生成完看不到差异、刷新后才看到」。
        List<String> diff = differences(created);
        for (DpVisualDirection entity : created) {
            result.add(toVo(entity, diff));
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("dnaId", dnaId);
        event.put("directions", result.stream().map(DpVisualDirectionVo::getDirectionCode).toList());
        event.put("source", SOURCE_TEMPLATE);
        projectService.moveStage(taskId, DpVisualStageEnum.DIRECTION_REVIEW, "DIRECTION_GENERATE",
            JsonUtils.toJsonString(event));
        return result;
    }

    @Override
    public List<DpVisualDirectionVo> list(Long taskId) {
        List<DpVisualDirection> rows = directionMapper.selectList(
            new LambdaQueryWrapper<DpVisualDirection>()
                .eq(DpVisualDirection::getTaskId, taskId)
                .orderByAsc(DpVisualDirection::getSortNo)
                .orderByDesc(DpVisualDirection::getId));
        return toVoList(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpVisualDirectionVo select(Long taskId, Long directionId) {
        DpVisualDirection target = directionMapper.selectById(directionId);
        if (target == null || !taskId.equals(target.getTaskId())) {
            throw new ServiceException("方向不属于该项目：" + directionId);
        }
        if (STATUS_REJECTED.equals(target.getStatus()) && StringUtils.isNotBlank(target.getRemark())) {
            // 允许重新选回被取代的方向：只提示，不阻断（人可能会改主意）
            log.info("选定了一个曾被取代的方向 taskId={} directionId={}", taskId, directionId);
        }
        List<DpVisualDirection> all = directionMapper.selectList(
            new LambdaQueryWrapper<DpVisualDirection>().eq(DpVisualDirection::getTaskId, taskId));
        for (DpVisualDirection item : all) {
            boolean isTarget = item.getId().equals(directionId);
            String targetStatus = isTarget ? STATUS_SELECTED : STATUS_REJECTED;
            if (!targetStatus.equals(item.getStatus())) {
                item.setStatus(targetStatus);
                directionMapper.updateById(item);
            }
        }
        target.setStatus(STATUS_SELECTED);
        target.setSelectedBy(LoginHelper.getUserId());
        target.setSelectedAt(LocalDateTime.now());
        directionMapper.updateById(target);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("directionId", target.getId());
        event.put("directionCode", target.getDirectionCode());
        event.put("directionName", target.getDirectionName());
        projectService.moveStage(taskId, DpVisualStageEnum.DIRECTION_LOCKED, "DIRECTION_SELECT",
            JsonUtils.toJsonString(event));
        return toVo(target, differences(all));
    }

    @Override
    public DpVisualDirectionVo selected(Long taskId) {
        List<DpVisualDirection> rows = directionMapper.selectList(
            new LambdaQueryWrapper<DpVisualDirection>()
                .eq(DpVisualDirection::getTaskId, taskId)
                .eq(DpVisualDirection::getStatus, STATUS_SELECTED)
                .orderByDesc(DpVisualDirection::getId)
                .last("limit 1"));
        if (rows.isEmpty()) {
            return null;
        }
        return toVo(rows.get(0), differences(directionMapper.selectList(
            new LambdaQueryWrapper<DpVisualDirection>().eq(DpVisualDirection::getTaskId, taskId))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpVisualDirectionVo update(Long taskId, CreativeDirectionBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("方向ID不能为空");
        }
        DpVisualDirection entity = directionMapper.selectById(bo.getId());
        if (entity == null || !taskId.equals(entity.getTaskId())) {
            throw new ServiceException("方向不属于该项目：" + bo.getId());
        }
        if (bo.getDirectionName() != null) {
            entity.setDirectionName(bo.getDirectionName());
        }
        if (bo.getConcept() != null) {
            entity.setConcept(bo.getConcept());
        }
        if (bo.getRemark() != null) {
            entity.setRemark(bo.getRemark());
        }
        directionMapper.updateById(entity);
        return toVo(entity, List.of());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private DpVisualDirection build(Long taskId, String code, String name, String concept,
                                   Map<String, Object> strategy, int sortNo) {
        DpVisualDirection entity = new DpVisualDirection();
        entity.setTaskId(taskId);
        entity.setDirectionCode(code);
        entity.setDirectionName(name);
        entity.setConcept(concept);
        entity.setStrategyJson(JsonUtils.toJsonString(strategy));
        entity.setStatus(STATUS_GENERATED);
        entity.setSortNo(sortNo);
        entity.setSource(SOURCE_TEMPLATE);
        return entity;
    }

    /**
     * 只取「已确认」的事实：未确认的一律不带入文案，避免把待核信息写进交付物。
     *
     * @param detail 任务详情
     * @return 字段编码 → 值
     */
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

    /**
     * 计算「哪几项在各方向之间真的不同」，页面据此高亮差异，而不是把所有字段都铺一遍。
     */
    private List<String> differences(List<DpVisualDirection> all) {
        Map<String, String> first = new LinkedHashMap<>();
        List<String> diff = new ArrayList<>();
        for (DpVisualDirection item : all) {
            Map<String, Object> strategy = parseStrategy(item);
            for (Map.Entry<String, Object> entry : strategy.entrySet()) {
                if ("schema".equals(entry.getKey()) || "differences".equals(entry.getKey())) {
                    continue;
                }
                String value = String.valueOf(entry.getValue());
                String previous = first.putIfAbsent(entry.getKey(), value);
                if (previous != null && !previous.equals(value) && !diff.contains(entry.getKey())) {
                    diff.add(entry.getKey());
                }
            }
        }
        return diff;
    }

    private DpVisualDirectionVo toVo(DpVisualDirection entity, List<String> differences) {
        DpVisualDirectionVo vo = new DpVisualDirectionVo();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setDirectionCode(entity.getDirectionCode());
        vo.setDirectionName(entity.getDirectionName());
        vo.setConcept(entity.getConcept());
        vo.setStrategy(parseStrategy(entity));
        vo.setStrategyJson(entity.getStrategyJson());
        vo.setDifferences(differences);
        vo.setPreviewFileIds(splitIds(entity.getPreviewFileIds()));
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(statusDesc(entity.getStatus()));
        vo.setSortNo(entity.getSortNo());
        vo.setSource(entity.getSource());
        vo.setSelectedBy(entity.getSelectedBy());
        vo.setSelectedAt(entity.getSelectedAt());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private List<DpVisualDirectionVo> toVoList(List<DpVisualDirection> rows) {
        List<String> diff = differences(rows);
        List<DpVisualDirectionVo> list = new ArrayList<>();
        for (DpVisualDirection row : rows) {
            list.add(toVo(row, diff));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStrategy(DpVisualDirection entity) {
        if (StringUtils.isBlank(entity.getStrategyJson())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(entity.getStrategyJson());
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static List<String> splitIds(String ids) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isNotBlank(ids)) {
            for (String part : ids.split(",")) {
                if (StringUtils.isNotBlank(part)) {
                    list.add(part.trim());
                }
            }
        }
        return list;
    }

    private static String statusDesc(String status) {
        return switch (StringUtils.blankToDefault(status, "")) {
            case STATUS_GENERATED -> "待选定";
            case STATUS_SELECTED -> "已选定";
            case STATUS_REJECTED -> "已弃用";
            default -> status;
        };
    }

    private static String appendRemark(String remark, String extra) {
        return StringUtils.isBlank(remark) ? extra : remark + "；" + extra;
    }

}
