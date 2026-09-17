package org.dromara.talent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlExportTask;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentDuplicate;
import org.dromara.talent.domain.bo.TlExportCreateBo;
import org.dromara.talent.domain.bo.TlTalentQueryBo;
import org.dromara.talent.domain.vo.TlExportTaskVo;
import org.dromara.talent.domain.vo.TlTalentExportVo;
import org.dromara.talent.domain.vo.TlTalentVo;
import org.dromara.talent.enums.AuditActionEnum;
import org.dromara.talent.enums.AuditTargetTypeEnum;
import org.dromara.talent.enums.DuplicateConclusionEnum;
import org.dromara.talent.enums.ExportStatusEnum;
import org.dromara.talent.enums.TalentRegionEnum;
import org.dromara.talent.enums.TalentRoleEnum;
import org.dromara.talent.helper.TalentAuditRecorder;
import org.dromara.talent.helper.TalentOssHelper;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlExportTaskMapper;
import org.dromara.talent.mapper.TlTalentDuplicateMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人才台账异步导出服务实现。
 * <p>
 * {@code @Async} 生效方式：{@link #runExportAsync(Long)} 带 {@code @Async}，
 * 但实现类内部<b>禁止自调用</b>——{@link #createExport} 通过
 * {@link SpringUtils#getAopProxy(Object)} 拿到 Spring 代理后再调用，
 * 并注册 {@link TransactionSynchronization#afterCommit()}，确保导出任务行对异步线程可见。
 *
 * @author talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentExportServiceImpl implements ITalentExportService {

    /**
     * 导出文件的前端路由入口（链接列只允许这种应用内入口，不得出现 OSS URL / 预签名 URL / object key）。
     */
    private static final String PROFILE_URL = "https://pm.hottter.cn/talent/profile";

    /**
     * 查询条件快照字段上限（tl_export_task.query_snapshot varchar(2000)）。
     */
    private static final int SNAPSHOT_MAX_LENGTH = 1990;

    /**
     * 导出任务 Mapper。
     */
    private final TlExportTaskMapper exportTaskMapper;

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

    /**
     * 重复预警 Mapper（导出"重复状态"列）。
     */
    private final TlTalentDuplicateMapper duplicateMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 对象存储助手。
     */
    private final TalentOssHelper ossHelper;

    /**
     * 审计记录器。
     */
    private final TalentAuditRecorder auditRecorder;

    /**
     * 人才库配置。
     */
    private final TalentProperties talentProperties;

    /**
     * 创建导出任务。
     *
     * @param bo 导出参数
     * @return 导出任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExport(TlExportCreateBo bo) {
        Long operatorId = LoginHelper.getUserId();
        if (operatorId == null) {
            throw new ServiceException("未登录");
        }
        List<String> visibleRegions = scopeHelper.currentVisibleRegions();
        if (CollUtil.isEmpty(visibleRegions)) {
            throw new ServiceException("当前账号没有人才台账可见范围，无法导出");
        }
        TlTalentQueryBo query = resolveQuery(bo);
        if (query != null && StringUtils.isNotBlank(query.getRegionCode())
            && TalentRegionEnum.find(query.getRegionCode().trim().toUpperCase()) == null) {
            throw new ServiceException("非法的区域编码");
        }
        String snapshot = buildSnapshot(query, visibleRegions, operatorId);
        TlExportTask task = new TlExportTask();
        task.setTaskName(StringUtils.substring(StringUtils.defaultIfBlank(bo.getTaskName(), "人才台账导出"), 0, 128));
        task.setStatus(ExportStatusEnum.PENDING.getCode());
        task.setFileName("talent-ledger-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx");
        task.setRowCount(0);
        task.setExpireTime(LocalDateTime.now().plusHours(TalentConstants.EXPORT_EXPIRE_HOURS));
        task.setQuerySnapshot(snapshot);
        exportTaskMapper.insert(task);
        Long exportId = task.getExportId();
        dispatchAsync(exportId);
        return exportId;
    }

    /**
     * 我的导出任务分页。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public PageResult<TlExportTaskVo> queryMyPage(TlExportTaskVo query, PageQuery pageQuery) {
        LambdaQueryWrapper<TlExportTask> wrapper = new LambdaQueryWrapper<>();
        if (!isExportAdmin()) {
            Long userId = LoginHelper.getUserId();
            if (userId == null) {
                return PageResult.build(List.of(), 0L);
            }
            wrapper.eq(TlExportTask::getCreateBy, userId);
        }
        if (query != null) {
            wrapper.like(StringUtils.isNotBlank(query.getTaskName()), TlExportTask::getTaskName, query.getTaskName())
                .eq(StringUtils.isNotBlank(query.getStatus()), TlExportTask::getStatus, query.getStatus())
                .eq(query.getCreateBy() != null && isExportAdmin(), TlExportTask::getCreateBy, query.getCreateBy());
        }
        wrapper.orderByDesc(TlExportTask::getCreateTime);
        Page<TlExportTask> page = pageQuery.build();
        Page<TlExportTaskVo> voPage = exportTaskMapper.selectVoPage(page, wrapper);
        List<TlExportTaskVo> rows = voPage.getRecords();
        if (CollUtil.isNotEmpty(rows)) {
            LocalDateTime now = LocalDateTime.now();
            for (TlExportTaskVo vo : rows) {
                vo.setExpired(vo.getExpireTime() != null && vo.getExpireTime().isBefore(now));
            }
        }
        return PageResult.build(rows, voPage.getTotal());
    }

    /**
     * 受控下载导出文件。
     *
     * @param exportId 导出任务ID
     * @param response HTTP 响应
     */
    @Override
    public void download(Long exportId, HttpServletResponse response) {
        TlExportTask task = exportId == null ? null : exportTaskMapper.selectById(exportId);
        if (task == null || "1".equals(task.getDelFlag())) {
            throw new ServiceException("导出任务不存在");
        }
        if (!isExportAdmin() && !Objects.equals(task.getCreateBy(), LoginHelper.getUserId())) {
            throw new ServiceException("无权下载该导出文件");
        }
        if (!ExportStatusEnum.SUCCESS.getCode().equals(task.getStatus())) {
            throw new ServiceException("导出文件尚未生成或已失效");
        }
        if (task.getExpireTime() != null && task.getExpireTime().isBefore(LocalDateTime.now())) {
            TlExportTask expired = new TlExportTask();
            expired.setExportId(exportId);
            expired.setStatus(ExportStatusEnum.EXPIRED.getCode());
            exportTaskMapper.updateById(expired);
            throw new ServiceException("导出文件已过期");
        }
        if (StringUtils.isBlank(task.getObjectKey())) {
            throw new ServiceException("导出文件不存在");
        }
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + FileUtils.percentEncode(StringUtils.defaultIfBlank(task.getFileName(), "talent-ledger.xlsx")));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            ossHelper.get(task.getObjectKey(), response.getOutputStream());
            response.flushBuffer();
        } catch (ServiceException e) {
            auditRecorder.recordExport(exportId, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("导出文件下载失败, exportId={}, exception={}", exportId, e.getClass().getSimpleName());
            auditRecorder.recordExport(exportId, false, "导出文件下载失败");
            throw new ServiceException("导出文件下载失败");
        }
        auditRecorder.recordExport(exportId, true, "下载人才台账导出文件");
    }

    /**
     * 异步执行导出。
     * <p>
     * 注意：本方法运行在无请求上下文的异步线程中，因此数据范围取自创建任务时冻结的
     * {@code query_snapshot.regions} 快照，而不是 {@code TalentScopeHelper}（异步线程无登录态）。
     *
     * @param exportId 导出任务ID
     */
    @Override
    @Async
    public void runExportAsync(Long exportId) {
        TlExportTask task = exportTaskMapper.selectById(exportId);
        if (task == null) {
            log.warn("人才台账导出任务不存在, exportId={}", exportId);
            return;
        }
        try {
            markStatus(exportId, ExportStatusEnum.RUNNING.getCode(), null);
            Snapshot snapshot = readSnapshot(task.getQuerySnapshot());
            int maxRows = Math.max(1, talentProperties.getExportMaxRows());
            LambdaQueryWrapper<TlTalent> wrapper = buildExportWrapper(snapshot.query, snapshot.regions);
            Page<TlTalent> page = new Page<>(1, maxRows + 1L);
            Page<TlTalentVo> voPage = talentMapper.selectVoPage(page, wrapper);
            List<TlTalentVo> rows = voPage.getRecords() == null ? List.of() : voPage.getRecords();
            if (rows.size() > maxRows) {
                throw new ServiceException("导出行数超过上限 " + maxRows + " 行，请收窄查询条件");
            }
            Map<Long, String> duplicateStatus = loadDuplicateStatus(rows);
            List<TlTalentExportVo> exportRows = new ArrayList<>(rows.size());
            for (TlTalentVo vo : rows) {
                exportRows.add(toExportRow(vo, duplicateStatus));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 基线 ExcelBuilder 没有 sheet(String)，工作表名用 sheetName(String)
            ExcelBuilder.of(exportRows, TlTalentExportVo.class).sheetName("人才台账").toStream(out);
            String objectKey = ossHelper.buildExportKey(exportId);
            ossHelper.put(objectKey, out.toByteArray());

            TlExportTask update = new TlExportTask();
            update.setExportId(exportId);
            update.setObjectKey(objectKey);
            update.setRowCount(exportRows.size());
            update.setStatus(ExportStatusEnum.SUCCESS.getCode());
            update.setFinishTime(LocalDateTime.now());
            exportTaskMapper.updateById(update);
            auditRecorder.recordExport(exportId, true, "导出人才台账 " + exportRows.size() + " 行");
        } catch (Exception e) {
            log.error("人才台账导出任务执行失败, exportId={}, exception={}", exportId, e.getClass().getSimpleName());
            try {
                markStatus(exportId, ExportStatusEnum.FAILED.getCode(),
                    StringUtils.substring(StringUtils.defaultIfBlank(e.getMessage(), "导出失败"), 0, 500));
            } catch (Exception ignore) {
                log.error("导出任务失败状态回写异常, exportId={}", exportId, ignore);
            }
            auditRecorder.recordExport(exportId, false, "导出失败");
        }
    }

    /**
     * 提交异步任务：事务提交后再触发，避免异步线程读不到任务行。
     *
     * @param exportId 导出任务ID
     */
    private void dispatchAsync(Long exportId) {
        Runnable dispatch = () -> SpringUtils.getAopProxy(this).runExportAsync(exportId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    /**
     * 更新任务状态。
     *
     * @param exportId     导出任务ID
     * @param status       状态
     * @param errorSummary 错误摘要
     */
    private void markStatus(Long exportId, String status, String errorSummary) {
        TlExportTask update = new TlExportTask();
        update.setExportId(exportId);
        update.setStatus(status);
        update.setErrorSummary(errorSummary);
        if (ExportStatusEnum.SUCCESS.getCode().equals(status) || ExportStatusEnum.FAILED.getCode().equals(status)) {
            update.setFinishTime(LocalDateTime.now());
        }
        exportTaskMapper.updateById(update);
    }

    /**
     * 组装导出行；性别 / 学历 / 区域 / 状态保留字典原始码，由导出 VO 上的
     * {@code @ExcelDictFormat(dictType = ...)} 在写出时转换为中文。
     *
     * @param vo              人才 VO
     * @param duplicateStatus 重复状态映射
     * @return 导出行
     */
    private TlTalentExportVo toExportRow(TlTalentVo vo, Map<Long, String> duplicateStatus) {
        TlTalentExportVo row = BeanUtil.copyProperties(vo, TlTalentExportVo.class);
        row.setSalaryText(vo.getExpectSalaryText());
        row.setDuplicateStatus(duplicateStatus.getOrDefault(vo.getTalentId(), ""));
        String entry = PROFILE_URL + "?talentId=" + vo.getTalentId();
        row.setProfileLink(entry);
        row.setResumeLink(entry + "&tab=resume");
        row.setAttachmentLink(entry + "&tab=attachment");
        return row;
    }

    /**
     * 加载重复状态（只预警不合并）：PENDING → 疑似重复，SAME → 已确认重复。
     *
     * @param rows 人才 VO 列表
     * @return 人才ID → 重复状态文案
     */
    private Map<Long, String> loadDuplicateStatus(List<TlTalentVo> rows) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(rows)) {
            return result;
        }
        List<Long> ids = rows.stream().map(TlTalentVo::getTalentId).filter(Objects::nonNull).collect(Collectors.toList());
        if (CollUtil.isEmpty(ids)) {
            return result;
        }
        List<TlTalentDuplicate> duplicates = duplicateMapper.selectList(new LambdaQueryWrapper<TlTalentDuplicate>()
            .in(TlTalentDuplicate::getSourceTalentId, ids)
            .in(TlTalentDuplicate::getConclusion,
                DuplicateConclusionEnum.PENDING.getCode(), DuplicateConclusionEnum.SAME.getCode()));
        for (TlTalentDuplicate duplicate : duplicates) {
            if (duplicate.getSourceTalentId() == null) {
                continue;
            }
            boolean same = DuplicateConclusionEnum.SAME.getCode().equals(duplicate.getConclusion());
            if (same || !result.containsKey(duplicate.getSourceTalentId())) {
                // 列上带 @ExcelDictFormat("tl_duplicate_conclusion")，这里必须给字典原始码
                result.put(duplicate.getSourceTalentId(),
                    same ? DuplicateConclusionEnum.SAME.getCode() : DuplicateConclusionEnum.PENDING.getCode());
            }
        }
        return result;
    }

    /**
     * 组装导出查询条件：区域范围来自创建任务时的快照，bo.regionCode 只能额外收窄。
     *
     * @param bo      查询条件
     * @param regions 快照区域
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TlTalent> buildExportWrapper(TlTalentQueryBo bo, List<String> regions) {
        LambdaQueryWrapper<TlTalent> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TlTalent::getRegionCode, CollUtil.isEmpty(regions) ? List.of("__NONE__") : regions);
        if (bo != null) {
            wrapper.like(StringUtils.isNotBlank(bo.getName()), TlTalent::getName, bo.getName())
                .eq(StringUtils.isNotBlank(bo.getTalentNo()), TlTalent::getTalentNo, bo.getTalentNo())
                .eq(StringUtils.isNotBlank(bo.getPhoneTail4()), TlTalent::getPhoneTail4, bo.getPhoneTail4())
                .eq(StringUtils.isNotBlank(bo.getRegionCode()), TlTalent::getRegionCode, bo.getRegionCode())
                .eq(StringUtils.isNotBlank(bo.getStatus()), TlTalent::getStatus, bo.getStatus())
                .eq(StringUtils.isNotBlank(bo.getEducation()), TlTalent::getEducation, bo.getEducation())
                .like(StringUtils.isNotBlank(bo.getPosition()), TlTalent::getPosition, bo.getPosition())
                .eq(StringUtils.isNotBlank(bo.getSource()), TlTalent::getSource, bo.getSource())
                .ge(bo.getContactDateStart() != null, TlTalent::getContactDate, bo.getContactDateStart())
                .le(bo.getContactDateEnd() != null, TlTalent::getContactDate, bo.getContactDateEnd());
            if (Boolean.TRUE.equals(bo.getDuplicateOnly())) {
                wrapper.exists("select 1 from tl_talent_duplicate d where d.source_talent_id = tl_talent.talent_id"
                    + " and d.conclusion = '" + DuplicateConclusionEnum.PENDING.getCode() + "' and d.del_flag = '0'");
            }
        }
        wrapper.orderByDesc(TlTalent::getCreateTime);
        return wrapper;
    }

    /**
     * 解析导出参数中的查询条件。
     * <p>
     * 兼容两种 BO 形态：查询条件平铺在 {@code TlExportCreateBo} 上，或组合为 {@code query} 字段。
     *
     * @param bo 导出参数
     * @return 查询条件
     */
    private TlTalentQueryBo resolveQuery(TlExportCreateBo bo) {
        if (bo == null) {
            return null;
        }
        Object nested = ReflectUtil.getFieldValue(bo, "query");
        if (nested instanceof TlTalentQueryBo query) {
            return query;
        }
        return BeanUtil.copyProperties(bo, TlTalentQueryBo.class);
    }

    /**
     * 构造查询条件快照（只含字符串 / 布尔 / 区域列表，不含明文手机号）。
     *
     * @param query      查询条件
     * @param regions    创建时的可见区域
     * @param operatorId 操作人
     * @return JSON 快照
     */
    private String buildSnapshot(TlTalentQueryBo query, List<String> regions, Long operatorId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("operatorId", operatorId);
        snapshot.put("regions", regions);
        Map<String, Object> q = new LinkedHashMap<>();
        if (query != null) {
            q.put("name", query.getName());
            q.put("talentNo", query.getTalentNo());
            q.put("phoneTail4", query.getPhoneTail4());
            q.put("regionCode", query.getRegionCode());
            q.put("status", query.getStatus());
            q.put("education", query.getEducation());
            q.put("position", query.getPosition());
            q.put("source", query.getSource());
            q.put("duplicateOnly", query.getDuplicateOnly());
            q.put("contactDateStart", query.getContactDateStart() == null ? null : query.getContactDateStart().toString());
            q.put("contactDateEnd", query.getContactDateEnd() == null ? null : query.getContactDateEnd().toString());
        }
        snapshot.put("query", q);
        String json = JsonUtils.toJsonString(snapshot);
        if (json != null && json.length() > SNAPSHOT_MAX_LENGTH) {
            throw new ServiceException("导出条件过于复杂，请收窄查询条件后重试");
        }
        return json;
    }

    /**
     * 解析查询条件快照。
     *
     * @param json JSON 快照
     * @return 快照对象
     */
    @SuppressWarnings("unchecked")
    private Snapshot readSnapshot(String json) {
        Snapshot snapshot = new Snapshot();
        if (StringUtils.isBlank(json)) {
            snapshot.regions = List.of();
            return snapshot;
        }
        Map<String, Object> map = JsonUtils.parseObject(json, Map.class);
        if (map == null) {
            snapshot.regions = List.of();
            return snapshot;
        }
        Object regions = map.get("regions");
        snapshot.regions = regions instanceof List<?> list
            ? list.stream().map(String::valueOf).collect(Collectors.toList()) : List.of();
        Object query = map.get("query");
        if (query instanceof Map<?, ?> queryMap) {
            TlTalentQueryBo bo = new TlTalentQueryBo();
            bo.setName(text(queryMap.get("name")));
            bo.setTalentNo(text(queryMap.get("talentNo")));
            bo.setPhoneTail4(text(queryMap.get("phoneTail4")));
            bo.setRegionCode(text(queryMap.get("regionCode")));
            bo.setStatus(text(queryMap.get("status")));
            bo.setEducation(text(queryMap.get("education")));
            bo.setPosition(text(queryMap.get("position")));
            bo.setSource(text(queryMap.get("source")));
            bo.setDuplicateOnly(Boolean.TRUE.equals(queryMap.get("duplicateOnly")));
            bo.setContactDateStart(date(queryMap.get("contactDateStart")));
            bo.setContactDateEnd(date(queryMap.get("contactDateEnd")));
            snapshot.query = bo;
        }
        return snapshot;
    }

    /**
     * 快照字段转字符串。
     *
     * @param value 值
     * @return 字符串
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 快照字段转日期。
     *
     * @param value 值
     * @return 日期
     */
    private LocalDate date(Object value) {
        String text = text(value);
        return StringUtils.isBlank(text) ? null : LocalDate.parse(text);
    }

    /**
     * 是否导出管理员（超管 / 集团管理员）。
     *
     * @return 是否管理员
     */
    private boolean isExportAdmin() {
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        Set<String> roleKeys = scopeHelper.currentRoleKeys();
        return roleKeys.contains(TalentRoleEnum.ADMIN.getCode());
    }

    /**
     * 导出查询条件快照。
     */
    private static final class Snapshot {

        /**
         * 创建任务时冻结的可见区域。
         */
        private List<String> regions = List.of();

        /**
         * 查询条件。
         */
        private TlTalentQueryBo query;
    }

}
