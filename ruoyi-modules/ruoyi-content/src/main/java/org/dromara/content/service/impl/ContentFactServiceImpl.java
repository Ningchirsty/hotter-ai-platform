package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.ContentFactFieldOptionVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentFieldAlias;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentFactService;
import org.dromara.content.service.IContentGateRuleService;
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

    /**
     * 闸门规则服务（用于「可录入字段」选项与录入编码校验）
     */
    private final IContentGateRuleService gateRuleService;

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
        CpTask task = loadTask(taskId);
        if (StringUtils.isBlank(fieldCode)) {
            throw new ServiceException("事实字段编码不能为空");
        }
        if (StringUtils.isBlank(value)) {
            throw new ServiceException("事实值不能为空");
        }
        String code = fieldCode.trim();
        requireRecordableCode(task, code);
        List<CpFactSnapshot> rows = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getFieldCode, code));
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
            fieldName = ContentFieldAlias.fieldName(code);
        }

        CpFactSnapshot entity = new CpFactSnapshot();
        entity.setTaskId(taskId);
        entity.setSnapshotVersion(maxVersion + 1);
        entity.setFieldCode(code);
        entity.setFieldName(fieldName);
        entity.setFieldValue(value.trim());
        entity.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
        entity.setConfirmedBy(LoginHelper.getUserId());
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setRemark(StringUtils.blankToDefault(remark, "人工录入"));
        factSnapshotMapper.insert(entity);

        // 「录了但闸门不动」是对用户最不友好的一种结果：编码不在该交付类型的闸门规则里时，
        // 本次录入对流转毫无影响。这里显式留一条日志，便于排障时对上。
        Set<String> requiredCodes = requiredFieldCodes(task.getDeliverableType());
        if (!requiredCodes.contains(code)) {
            log.warn("人工录入的字段不属于该交付类型的闸门要求, taskId={}, deliverableType={}, fieldCode={}",
                taskId, task.getDeliverableType(), code);
        }
        taskGateService.recheckAndApply(taskId);
        log.info("人工录入事实, taskId={}, fieldCode={}, version={}", taskId, code, entity.getSnapshotVersion());
        return entity.getSnapshotId();
    }

    @Override
    public List<ContentFactFieldOptionVo> fieldOptions(Long taskId) {
        CpTask task = loadTask(taskId);
        Set<String> confirmed = confirmedFieldCodes(taskId);
        List<ContentFactFieldOptionVo> options = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        // 1) 本交付类型的闸门要求项排在前：用户最该先看到的就是「还差哪几项」
        for (CpGateRule rule : gateRuleService.listEnabledRules(task.getDeliverableType())) {
            if (StringUtils.isBlank(rule.getFieldCode()) || !added.add(rule.getFieldCode())) {
                continue;
            }
            ContentFactFieldOptionVo vo = new ContentFactFieldOptionVo();
            vo.setFieldCode(rule.getFieldCode());
            vo.setFieldName(StringUtils.blankToDefault(rule.getFieldName(),
                ContentFieldAlias.fieldName(rule.getFieldCode())));
            vo.setGateLevel(rule.getGateLevel());
            vo.setRequirePresent(rule.getRequirePresent());
            vo.setRequiredByGate(true);
            vo.setSatisfied(confirmed.contains(rule.getFieldCode()));
            vo.setKnown(ContentFieldAlias.known(rule.getFieldCode()));
            vo.setDescription(confirmed.contains(rule.getFieldCode())
                ? "本交付类型的闸门要求项，已确认"
                : "本交付类型的闸门要求项，尚未确认");
            options.add(vo);
        }

        // 2) 别名表里其他已登记字段：不满足本交付类型的闸门，但对记录事实仍有意义
        for (String code : ContentFieldAlias.knownFields()) {
            if (!added.add(code)) {
                continue;
            }
            ContentFactFieldOptionVo vo = new ContentFactFieldOptionVo();
            vo.setFieldCode(code);
            vo.setFieldName(ContentFieldAlias.fieldName(code));
            vo.setRequiredByGate(false);
            vo.setSatisfied(confirmed.contains(code));
            vo.setKnown(true);
            vo.setDescription("非本交付类型的闸门要求项，录入后不会改变闸门判定");
            options.add(vo);
        }
        return options;
    }

    /**
     * 校验字段编码是否「可录入」。
     *
     * <p><b>为什么要拦</b>：手工录入原先接受任意字符串。用户打错一个字符（甚至照抄界面
     * 提示里的示例编码 {@code product_height}）会得到一条 {@code confirm_status=CONFIRMED}
     * 的事实——看起来填好了，闸门却纹丝不动，因为闸门只认与
     * {@code cp_gate_rule.field_code} 完全一致的编码。<b>静默无效比报错难查得多</b>。</p>
     *
     * <p>可录入 = 别名表已登记 ∪ 存在启用中的闸门规则。取并集而不是只认别名表：
     * 闸门规则是表驱动、运营可改的，若运营新加一条规则，手工录入不该反而被拒。</p>
     *
     * @param task 任务
     * @param code 字段编码
     */
    private void requireRecordableCode(CpTask task, String code) {
        if (ContentFieldAlias.known(code) || gateRuleService.allEnabledFieldCodes().contains(code)) {
            return;
        }
        Set<String> required = requiredFieldCodes(task.getDeliverableType());
        String hint = required.isEmpty()
            ? "该交付类型尚未配置闸门规则"
            : "本交付类型要求的事实字段为：" + String.join("、", required);
        throw new ServiceException("字段编码「" + code + "」不是平台已登记的字段，录入后闸门不会认账。"
            + hint + "。请从下拉中选择，或在「闸门规则」中先登记该字段。");
    }

    /**
     * 取某交付类型下闸门要求的字段编码集合。
     *
     * @param deliverableType 交付类型
     * @return 字段编码集合
     */
    private Set<String> requiredFieldCodes(String deliverableType) {
        Set<String> codes = new LinkedHashSet<>();
        for (CpGateRule rule : gateRuleService.listEnabledRules(deliverableType)) {
            if (StringUtils.isNotBlank(rule.getFieldCode())) {
                codes.add(rule.getFieldCode());
            }
        }
        return codes;
    }

    /**
     * 取任务下已有「已确认」事实的字段编码。
     *
     * @param taskId 任务ID
     * @return 字段编码集合
     */
    private Set<String> confirmedFieldCodes(Long taskId) {
        Set<String> codes = new LinkedHashSet<>();
        for (CpFactSnapshot s : factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getConfirmStatus, ContentFactConfirmStatusEnum.CONFIRMED.getCode()))) {
            codes.add(s.getFieldCode());
        }
        return codes;
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
        loadTask(taskId);
    }

    /**
     * 加载任务，不存在抛业务异常。
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    private CpTask loadTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        return task;
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
