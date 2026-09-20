package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentFieldAlias;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentFactService;
import org.dromara.content.service.IContentTaskGateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产品事实快照服务实现。
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentFactServiceImpl implements IContentFactService {

    /**
     * 事实 Mapper
     */
    private final CpFactSnapshotMapper factSnapshotMapper;

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 附件 Mapper（用于回填来源文件名）
     */
    private final CpTaskFileMapper taskFileMapper;

    /**
     * 闸门重算服务
     */
    private final IContentTaskGateService taskGateService;

    @Override
    public List<CpFactSnapshotVo> list(Long taskId) {
        requireTask(taskId);
        List<CpFactSnapshotVo> rows = factSnapshotMapper.selectVoList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .orderByAsc(CpFactSnapshot::getFieldCode)
            .orderByDesc(CpFactSnapshot::getSnapshotVersion));
        fillSourceFileName(rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long snapshotId) {
        CpFactSnapshot row = load(snapshotId);
        CpFactSnapshot update = new CpFactSnapshot();
        update.setSnapshotId(snapshotId);
        update.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
        update.setConfirmedBy(LoginHelper.getUserId());
        update.setConfirmedAt(LocalDateTime.now());
        factSnapshotMapper.updateById(update);
        taskGateService.recheckAndApply(row.getTaskId());
        log.info("确认事实, taskId={}, snapshotId={}, fieldCode={}", row.getTaskId(), snapshotId, row.getFieldCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long snapshotId) {
        CpFactSnapshot row = load(snapshotId);
        CpFactSnapshot update = new CpFactSnapshot();
        update.setSnapshotId(snapshotId);
        update.setConfirmStatus(ContentFactConfirmStatusEnum.REJECTED.getCode());
        update.setConfirmedBy(LoginHelper.getUserId());
        update.setConfirmedAt(LocalDateTime.now());
        factSnapshotMapper.updateById(update);
        taskGateService.recheckAndApply(row.getTaskId());
        log.info("否决事实, taskId={}, snapshotId={}, fieldCode={}", row.getTaskId(), snapshotId, row.getFieldCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmUnambiguous(Long taskId) {
        requireTask(taskId);
        List<CpFactSnapshot> pending = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getConfirmStatus, ContentFactConfirmStatusEnum.PENDING.getCode()));

        // 按字段分组：只有「同字段仅一个待确认候选」的才算无争议。
        // 取值是否相同也要看——两个候选值相同并不构成争议，但为稳妥起见仍按多候选处理，
        // 交由人去核对，避免把「看似相同、实则有别」的值批量确认掉。
        Map<String, List<CpFactSnapshot>> byField = new LinkedHashMap<>();
        for (CpFactSnapshot s : pending) {
            byField.computeIfAbsent(s.getFieldCode(), k -> new ArrayList<>()).add(s);
        }
        // 已被确认过的字段不再重复确认
        Set<String> confirmedFields = new LinkedHashSet<>();
        for (CpFactSnapshot s : factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getConfirmStatus, ContentFactConfirmStatusEnum.CONFIRMED.getCode()))) {
            confirmedFields.add(s.getFieldCode());
        }

        Long userId = LoginHelper.getUserId();
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Map.Entry<String, List<CpFactSnapshot>> e : byField.entrySet()) {
            if (confirmedFields.contains(e.getKey()) || e.getValue().size() != 1) {
                continue;
            }
            CpFactSnapshot update = new CpFactSnapshot();
            update.setSnapshotId(e.getValue().get(0).getSnapshotId());
            update.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
            update.setConfirmedBy(userId);
            update.setConfirmedAt(now);
            factSnapshotMapper.updateById(update);
            count++;
        }
        taskGateService.recheckAndApply(taskId);
        log.info("一键确认无争议项, taskId={}, confirmedCount={}, skippedConflictFields={}",
            taskId, count, byField.size() - count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addManual(Long taskId, String fieldCode, String value, String remark) {
        requireTask(taskId);
        if (StringUtils.isBlank(fieldCode)) {
            throw new ServiceException("事实字段编码不能为空");
        }
        if (StringUtils.isBlank(value)) {
            throw new ServiceException("事实值不能为空");
        }
        List<CpFactSnapshot> rows = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getFieldCode, fieldCode));
        int maxVersion = 1;
        String fieldName = null;
        for (CpFactSnapshot r : rows) {
            if (StringUtils.isBlank(fieldName)) {
                fieldName = r.getFieldName();
            }
            if (r.getSnapshotVersion() != null && r.getSnapshotVersion() > maxVersion) {
                maxVersion = r.getSnapshotVersion();
            }
        }
        if (StringUtils.isBlank(fieldName)) {
            fieldName = ContentFieldAlias.fieldName(fieldCode);
        }

        CpFactSnapshot entity = new CpFactSnapshot();
        entity.setTaskId(taskId);
        entity.setSnapshotVersion(maxVersion + 1);
        entity.setFieldCode(fieldCode);
        entity.setFieldName(fieldName);
        entity.setFieldValue(value.trim());
        entity.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
        entity.setConfirmedBy(LoginHelper.getUserId());
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setRemark(StringUtils.blankToDefault(remark, "人工录入"));
        factSnapshotMapper.insert(entity);
        taskGateService.recheckAndApply(taskId);
        log.info("人工录入事实, taskId={}, fieldCode={}, version={}", taskId, fieldCode, entity.getSnapshotVersion());
        return entity.getSnapshotId();
    }

    /**
     * 加载事实行。
     *
     * @param snapshotId 快照行ID
     * @return 事实行
     */
    private CpFactSnapshot load(Long snapshotId) {
        if (snapshotId == null) {
            throw new ServiceException("事实行ID不能为空");
        }
        CpFactSnapshot row = factSnapshotMapper.selectById(snapshotId);
        if (row == null) {
            throw new ServiceException("事实行不存在");
        }
        return row;
    }

    /**
     * 校验任务存在。
     *
     * @param taskId 任务ID
     */
    private void requireTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
    }

    /**
     * 回填来源文件名，便于前端在事实清单里直接显示证据出处。
     *
     * @param rows 事实行
     */
    private void fillSourceFileName(List<CpFactSnapshotVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> fileIds = new ArrayList<>();
        for (CpFactSnapshotVo r : rows) {
            if (r.getSourceFileId() != null && !fileIds.contains(r.getSourceFileId())) {
                fileIds.add(r.getSourceFileId());
            }
        }
        if (fileIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new LinkedHashMap<>();
        taskFileMapper.selectList(new LambdaQueryWrapper<org.dromara.content.domain.CpTaskFile>()
                .in(org.dromara.content.domain.CpTaskFile::getFileId, fileIds))
            .forEach(f -> nameById.put(f.getFileId(), f.getFileName()));
        for (CpFactSnapshotVo r : rows) {
            if (r.getSourceFileId() != null) {
                r.setSourceFileName(nameById.get(r.getSourceFileId()));
            }
        }
    }

}
