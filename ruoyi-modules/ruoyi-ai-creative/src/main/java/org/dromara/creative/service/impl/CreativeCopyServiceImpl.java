package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpCopyBlock;
import org.dromara.creative.domain.bo.CopyBlockBo;
import org.dromara.creative.domain.bo.CopyBlockReorderBo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.enums.DpCopyBlockSourceEnum;
import org.dromara.creative.enums.DpCopyBlockTypeEnum;
import org.dromara.creative.mapper.DpCopyBlockMapper;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文案与要点块服务实现。
 *
 * <p><b>为什么新增只追加到末尾</b>：块顺序就是详情页的阅读顺序与卖点优先级。
 * 若新增时默认插到中间，用户每加一条都要重新排序一遍；追加到末尾 + 显式拖拽重排，
 * 是「人只在需要时表达顺序」的做法。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeCopyServiceImpl implements ICreativeCopyService {

    /**
     * 排序步长：留出空隙，便于以后在两条之间插入而不必整体重排
     */
    private static final int SORT_STEP = 10;

    /**
     * 可派生为参数行的事实编码（参数/版本/颜色/数量/包装）。
     *
     * <p>刻意不含 {@code brand_tone}：品牌调性是一句风格要求，不是参数行；
     * 也不含 {@code reference_image}（那是图，不是文字）。要作为「必显信息」的话，
     * 由人在 MUST_SHOW 块里写——派生器不替人做语义判断。</p>
     */
    private static final List<String> SPEC_FACT_CODES =
        List.of("spec_params", "main_version", "color", "quantity", "package_version");

    private static final Set<String> BLOCK_STATUSES = Set.of("DRAFT", "CONFIRMED");

    private final DpCopyBlockMapper blockMapper;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;

    @Override
    public List<DpCopyBlockVo> list(Long taskId, String blockType) {
        projectService.getProject(taskId);
        LambdaQueryWrapper<DpCopyBlock> wrapper = new LambdaQueryWrapper<DpCopyBlock>()
            .eq(DpCopyBlock::getTaskId, taskId);
        if (StringUtils.isNotBlank(blockType)) {
            wrapper.eq(DpCopyBlock::getBlockType, requireType(blockType).getCode());
        }
        wrapper.orderByAsc(DpCopyBlock::getBlockType).orderByAsc(DpCopyBlock::getSortNo)
            .orderByAsc(DpCopyBlock::getId);
        List<DpCopyBlock> rows = blockMapper.selectList(wrapper);
        List<DpCopyBlockVo> list = new ArrayList<>();
        for (DpCopyBlock row : rows) {
            list.add(toVo(row));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(Long taskId, CopyBlockBo bo) {
        projectService.getProject(taskId);
        if (bo == null) {
            throw new ServiceException("文案块内容不能为空");
        }
        DpCopyBlockTypeEnum type = requireType(bo.getBlockType());
        if (StringUtils.isBlank(bo.getTitle()) && StringUtils.isBlank(bo.getContent())) {
            throw new ServiceException("标题与内容不能同时为空："
                + DpCopyBlockTypeEnum.descOf(type.getCode()) + "块至少要写清一件事");
        }
        DpCopyBlock entity = new DpCopyBlock();
        entity.setTaskId(taskId);
        entity.setBlockType(type.getCode());
        entity.setSortNo(bo.getSortNo() == null ? nextSortNo(taskId, type.getCode()) : bo.getSortNo());
        entity.setTitle(norm(bo.getTitle()));
        entity.setContent(norm(bo.getContent()));
        entity.setSource(requireSource(bo.getSource()).getCode());
        entity.setSourceRef(norm(bo.getSourceRef()));
        entity.setStatus(requireStatus(bo.getStatus()));
        entity.setRemark(norm(bo.getRemark()));
        blockMapper.insert(entity);
        log.info("文案块已新增 taskId={} blockId={} type={} sortNo={}",
            taskId, entity.getId(), entity.getBlockType(), entity.getSortNo());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long taskId, Long blockId, CopyBlockBo bo) {
        DpCopyBlock entity = requireOwned(taskId, blockId);
        if (bo == null) {
            throw new ServiceException("文案块内容不能为空");
        }
        if (StringUtils.isNotBlank(bo.getBlockType())) {
            entity.setBlockType(requireType(bo.getBlockType()).getCode());
        }
        if (bo.getSortNo() != null) {
            entity.setSortNo(bo.getSortNo());
        }
        if (bo.getTitle() != null) {
            entity.setTitle(norm(bo.getTitle()));
        }
        if (bo.getContent() != null) {
            entity.setContent(norm(bo.getContent()));
        }
        if (StringUtils.isNotBlank(bo.getSource())) {
            entity.setSource(requireSource(bo.getSource()).getCode());
        }
        if (bo.getSourceRef() != null) {
            entity.setSourceRef(norm(bo.getSourceRef()));
        }
        if (StringUtils.isNotBlank(bo.getStatus())) {
            entity.setStatus(requireStatus(bo.getStatus()));
        }
        if (bo.getRemark() != null) {
            entity.setRemark(norm(bo.getRemark()));
        }
        if (StringUtils.isBlank(entity.getTitle()) && StringUtils.isBlank(entity.getContent())) {
            throw new ServiceException("标题与内容不能同时为空："
                + DpCopyBlockTypeEnum.descOf(entity.getBlockType()) + "块至少要写清一件事");
        }
        blockMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(Long taskId, CopyBlockReorderBo bo) {
        if (bo == null) {
            throw new ServiceException("重排参数不能为空");
        }
        DpCopyBlockTypeEnum type = requireType(bo.getBlockType());
        List<Long> ids = bo.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("重排的块ID列表不能为空");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ServiceException("重排列表里有重复的块ID，顺序无法确定：" + ids);
        }
        for (Long id : ids) {
            if (id == null) {
                throw new ServiceException("重排列表里存在空的块ID");
            }
        }
        // 一次取回该类型下的全部块：既能校验归属，也能在报错时给出「哪些属于本组」
        List<DpCopyBlock> owned = blockMapper.selectList(new LambdaQueryWrapper<DpCopyBlock>()
            .eq(DpCopyBlock::getTaskId, taskId)
            .eq(DpCopyBlock::getBlockType, type.getCode()));
        Set<Long> ownedIds = new LinkedHashSet<>();
        for (DpCopyBlock row : owned) {
            ownedIds.add(row.getId());
        }
        List<Long> foreign = new ArrayList<>();
        for (Long id : ids) {
            if (!ownedIds.contains(id)) {
                foreign.add(id);
            }
        }
        if (!foreign.isEmpty()) {
            throw new ServiceException("以下块ID不属于本项目（" + taskId + "）的「"
                + DpCopyBlockTypeEnum.descOf(type.getCode()) + "」分组，不能一起排序：" + foreign
                + "；该分组现有块ID=" + ownedIds);
        }
        int index = 0;
        for (Long id : ids) {
            index++;
            DpCopyBlock update = new DpCopyBlock();
            update.setId(id);
            update.setSortNo(index * SORT_STEP);
            blockMapper.updateById(update);
        }
        log.info("文案块已重排 taskId={} type={} count={}", taskId, type.getCode(), ids.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long taskId, Long blockId) {
        requireOwned(taskId, blockId);
        // 逻辑删除（@TableLogic）：详情页历史版本引用过的块要能回看，不做物理删除
        blockMapper.deleteById(blockId);
        log.info("文案块已删除 taskId={} blockId={}", taskId, blockId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int seedFromFacts(Long taskId) {
        projectService.getProject(taskId);
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);
        if (detail == null || detail.getFacts() == null || detail.getFacts().isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        List<DpCopyBlock> current = blockMapper.selectList(new LambdaQueryWrapper<DpCopyBlock>()
            .eq(DpCopyBlock::getTaskId, taskId)
            .eq(DpCopyBlock::getBlockType, DpCopyBlockTypeEnum.SPEC_ROW.getCode())
            .eq(DpCopyBlock::getSource, DpCopyBlockSourceEnum.FACT.getCode()));
        for (DpCopyBlock row : current) {
            if (StringUtils.isNotBlank(row.getSourceRef())) {
                existing.add(row.getSourceRef());
            }
        }

        int sortNo = nextSortNo(taskId, DpCopyBlockTypeEnum.SPEC_ROW.getCode()) - SORT_STEP;
        int created = 0;
        for (CpFactSnapshotVo fact : detail.getFacts()) {
            if (!ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(fact.getConfirmStatus())) {
                continue;
            }
            if (StringUtils.isBlank(fact.getFieldCode())
                || !SPEC_FACT_CODES.contains(fact.getFieldCode())) {
                continue;
            }
            if (StringUtils.isBlank(fact.getFieldValue())) {
                continue;
            }
            // 幂等：同项目同类型同来源引用已存在则跳过（重复点击不会堆块）
            if (!existing.add(fact.getFieldCode())) {
                continue;
            }
            sortNo += SORT_STEP;
            DpCopyBlock entity = new DpCopyBlock();
            entity.setTaskId(taskId);
            entity.setBlockType(DpCopyBlockTypeEnum.SPEC_ROW.getCode());
            entity.setSortNo(sortNo);
            entity.setTitle(StringUtils.blankToDefault(fact.getFieldName(), fact.getFieldCode()));
            entity.setContent(fact.getFieldValue());
            entity.setSource(DpCopyBlockSourceEnum.FACT.getCode());
            entity.setSourceRef(fact.getFieldCode());
            entity.setStatus("CONFIRMED");
            entity.setRemark("由已确认事实派生（" + fact.getFieldCode() + "），改这里等于改事实口径");
            blockMapper.insert(entity);
            created++;
        }
        log.info("从已确认事实派生参数行 taskId={} created={}", taskId, created);
        return created;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private DpCopyBlock requireOwned(Long taskId, Long blockId) {
        if (blockId == null) {
            throw new ServiceException("块ID不能为空");
        }
        DpCopyBlock entity = blockMapper.selectById(blockId);
        if (entity == null || !taskId.equals(entity.getTaskId())) {
            throw new ServiceException("该文案块不属于该项目：" + blockId);
        }
        return entity;
    }

    private static DpCopyBlockTypeEnum requireType(String blockType) {
        DpCopyBlockTypeEnum type = DpCopyBlockTypeEnum.find(blockType);
        if (type == null) {
            throw new ServiceException("块类型非法：" + blockType
                + "；合法取值 " + DpCopyBlockTypeEnum.codes());
        }
        return type;
    }

    private static DpCopyBlockSourceEnum requireSource(String source) {
        if (StringUtils.isBlank(source)) {
            return DpCopyBlockSourceEnum.MANUAL;
        }
        DpCopyBlockSourceEnum parsed = DpCopyBlockSourceEnum.find(source);
        if (parsed == null) {
            throw new ServiceException("来源非法：" + source
                + "；合法取值 " + DpCopyBlockSourceEnum.codes());
        }
        return parsed;
    }

    private static String requireStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return "DRAFT";
        }
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!BLOCK_STATUSES.contains(normalized)) {
            throw new ServiceException("状态非法：" + status + "；合法取值 DRAFT/CONFIRMED");
        }
        return normalized;
    }

    /**
     * 同项目同类型的下一个排序值（最大 sortNo + 10；没有块时从 10 开始）。
     *
     * @param taskId    项目ID
     * @param blockType 块类型
     * @return 排序值
     */
    private int nextSortNo(Long taskId, String blockType) {
        List<DpCopyBlock> rows = blockMapper.selectList(new LambdaQueryWrapper<DpCopyBlock>()
            .eq(DpCopyBlock::getTaskId, taskId)
            .eq(DpCopyBlock::getBlockType, blockType)
            .orderByDesc(DpCopyBlock::getSortNo)
            .last("limit 1"));
        Integer max = rows.isEmpty() ? null : rows.get(0).getSortNo();
        return (max == null ? 0 : max) + SORT_STEP;
    }

    private static DpCopyBlockVo toVo(DpCopyBlock entity) {
        DpCopyBlockVo vo = new DpCopyBlockVo();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setBlockType(entity.getBlockType());
        vo.setSortNo(entity.getSortNo());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setSource(entity.getSource());
        vo.setSourceRef(entity.getSourceRef());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static String norm(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
