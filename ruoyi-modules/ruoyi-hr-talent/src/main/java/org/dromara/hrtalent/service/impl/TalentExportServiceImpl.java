package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.TalentExportCreateBo;
import org.dromara.hrtalent.domain.bo.talent.TalentExportQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.entity.TalentExportTask;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentProfileTag;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.domain.vo.talent.TalentExportRowVo;
import org.dromara.hrtalent.domain.vo.talent.TalentExportTaskVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.mapper.TalentExportTaskMapper;
import org.dromara.hrtalent.mapper.TalentProfileTagMapper;
import org.dromara.hrtalent.mapper.TalentTagMapper;
import org.dromara.hrtalent.service.talent.ITalentExportService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.dromara.system.api.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人才导出任务服务实现（SPEC-P4 §2.6 F 线、设计文档 §8.20、§11.1、§15.2）。
 *
 * <p><b>导出模式（本阶段取舍）</b>：采用「<b>同步生成结果文件 + 单次最大条数上限</b>」，
 * 上限取配置 {@code hrtalent.export-max-rows}（缺省 <b>10000</b> 条）。
 * 理由：解析/导出等耗时动作原则上应异步（§11.1），但异步任务需要调度、重试与进度回传，
 * 不在本线范围；在上限内同步生成可保证「任务记录、结果文件、审计」三者一致，
 * 代价是超大范围的导出会被拒绝（提示缩小筛选范围），而不是静默截断。
 * 超过上限时<b>直接拒绝</b>并写 {@code denied} 审计。</p>
 *
 * <p><b>结果文件</b>：写入私有对象存储（{@code hr-talent-private/exports/{taskId}/...}），
 * 数据库只保存对象标识 {@code oss_id}；对外只提供系统内受控下载地址
 * {@code /talent/exports/{taskId}/download}，<b>绝不返回长期公网地址或预签名地址</b>（§8.13、§11.1）。</p>
 *
 * <p><b>敏感台账</b>：在按钮权限 {@code talent:profile:export} 之外，
 * <b>额外</b>要求独立权限 {@code talent:profile:phone-view} 与<b>非空用途</b> {@code purpose}；
 * 用途为空或权限不足一律写 {@code denied} 审计后拒绝（§8.20）。</p>
 *
 * <p><b>到期处理</b>：{@code expire_time} = 生成时间 + {@code hrtalent.export-valid-duration}（缺省 7 天）。
 * 下载时校验过期并拒绝（同时把任务状态置 {@code expired}）；<b>定时清理对象存储文件的任务不在本线范围</b>，
 * 由后续阶段的清理任务按 {@code (status, expire_time)} 索引扫描删除。</p>
 *
 * <p><b>审计</b>：导出与下载动作均通过 {@link SensitiveAuditRecorder} 记录
 * {@code EVENT_EXPORT + BIZ_TALENT}；审计明细只含类型、行数、字段数等非敏感信息，
 * <b>不写</b>电话明文、对象键与文件名。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentExportServiceImpl implements ITalentExportService {

    /**
     * 导出类型：普通台账。
     */
    public static final String EXPORT_TYPE_NORMAL = "normal";

    /**
     * 导出类型：敏感台账。
     */
    public static final String EXPORT_TYPE_SENSITIVE = "sensitive";

    /**
     * 任务状态：成功。
     */
    public static final String STATUS_SUCCESS = "success";

    /**
     * 任务状态：失败。
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * 任务状态：已过期。
     */
    public static final String STATUS_EXPIRED = "expired";

    /**
     * 工作表名称。
     */
    public static final String SHEET_NAME = "人才台账";

    /**
     * 系统内受控下载地址模板（导出文件）。
     */
    public static final String DOWNLOAD_API_TEMPLATE = "/talent/exports/%d/download";

    /**
     * 系统内受控访问地址模板（人才档案详情）。
     */
    public static final String PROFILE_API_TEMPLATE = "/talent/profiles/%d";

    /**
     * 系统内受控下载地址模板（简历）。
     */
    public static final String RESUME_DOWNLOAD_API_TEMPLATE = "/talent/resumes/%d/download";

    /**
     * 普通台账默认字段（顺序即列顺序）。
     */
    private static final List<String> NORMAL_FIELDS = List.of(
        "talentNo", "name", "positionDirection", "education", "region", "talentStatus", "tags", "owner");

    /**
     * 敏感台账允许追加的字段（§8.20：联系方式、薪资、附件访问地址）。
     */
    private static final List<String> SENSITIVE_EXTRA_FIELDS = List.of(
        "phone", "email", "salary", "profileAccess", "resumeAccess");

    /**
     * 用途最大长度，与 DDL {@code purpose varchar(255)} 一致。
     */
    private static final int PURPOSE_MAX_LENGTH = 255;

    /**
     * 失败原因最大长度，与 DDL {@code failure_reason varchar(500)} 一致。
     */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    /**
     * 备注最大长度，与 DDL {@code remark varchar(500)} 一致。
     */
    private static final int REMARK_MAX_LENGTH = 500;

    /**
     * 配置缺失（非正数）时兜底的单次导出行数上限：任何情况下都必须有上限（§11.1）。
     */
    private static final int DEFAULT_EXPORT_MAX_ROWS = 10000;

    /**
     * 敏感标签标志（{@code sensitive_flag = '1'}），不进入检索与导出。
     */
    private static final String SENSITIVE_TAG_FLAG = "1";

    /**
     * 导出任务 Mapper。
     */
    private final TalentExportTaskMapper talentExportTaskMapper;

    /**
     * 人才主档服务（导出条件与可见范围统一委托，避免两套检索逻辑分叉）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才标签关系 Mapper（只读：拼装导出行的标签列）。
     */
    private final TalentProfileTagMapper talentProfileTagMapper;

    /**
     * 人才标签字典 Mapper（只读）。
     */
    private final TalentTagMapper talentTagMapper;

    /**
     * 人才可见范围领域服务（唯一授权入口，用于集团级管理员判定）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 私有对象存储访问封装（只保存对象标识，不保存公网地址）。
     */
    private final HrTalentOssHelper hrTalentOssHelper;

    /**
     * 招聘与人才管理业务配置（导出行数上限与文件有效期）。
     */
    private final HrTalentProperties hrTalentProperties;

    /**
     * 敏感操作审计统一入口。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /**
     * 业务编号生成器（导出任务编号）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    /**
     * 用户服务扩展点（负责人昵称）；取不到实现时降级为「负责人ID」，不影响导出主流程。
     */
    private final ObjectProvider<UserService> userServiceProvider;

    /* ------------------------------------------------------------------ 创建导出 ------------------------------------------------------------------ */

    @Override
    public Long createExport(TalentExportCreateBo bo) {
        if (bo == null) {
            throw new ServiceException("导出入参不能为空");
        }
        String exportType = resolveExportType(bo.getExportType());
        boolean sensitive = EXPORT_TYPE_SENSITIVE.equals(exportType);
        String purpose = normalizePurpose(bo.getPurpose());
        if (sensitive) {
            // 敏感台账：用途必填 → 独立权限 → 才允许继续（两条都在拒绝时写 denied 审计）
            if (purpose == null) {
                recordExport(null, exportType, null, SensitiveAuditRecorder.RESULT_DENIED,
                    detailOf(null, 0, 0, "purpose 为空"));
                throw new ServiceException("敏感台账导出必须填写用途（purpose）");
            }
            if (!hasSensitiveExportPermission()) {
                recordExport(null, exportType, purpose, SensitiveAuditRecorder.RESULT_DENIED,
                    detailOf(null, 0, 0, "缺少敏感台账独立权限"));
                throw new ServiceException("无权导出敏感台账：需要独立权限 " + HrTalentConstants.PERM_PROFILE_PHONE_VIEW);
            }
        }
        List<String> fields = resolveFields(exportType, bo.getFields());
        int maxRows = resolveMaxRows();
        // 多取一条用于判定是否超过上限：超过即拒绝，绝不静默截断
        List<TalentProfile> profiles = talentProfileService.searchForExport(bo.getFilters(), maxRows + 1);
        if (profiles.size() > maxRows) {
            recordExport(null, exportType, purpose, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(null, profiles.size(), fields.size(), "超过单次导出上限 " + maxRows));
            throw new ServiceException("本次筛选命中记录超过单次导出上限 " + maxRows + " 条，请缩小筛选范围后重试");
        }
        if (profiles.isEmpty()) {
            recordExport(null, exportType, purpose, SensitiveAuditRecorder.RESULT_FAILED,
                detailOf(null, 0, fields.size(), "无匹配记录"));
            throw new ServiceException("当前筛选条件下没有可导出的人才记录");
        }

        Long taskId = IdUtil.getSnowflakeNextId();
        TalentExportTask task = buildTask(bo, taskId, exportType, fields, profiles.size(), purpose);
        List<TalentExportRowVo> rows = buildRows(profiles, sensitive);

        byte[] data;
        try {
            data = buildWorkbook(rows, fields);
        } catch (RuntimeException e) {
            log.error("人才台账导出文件生成失败, exportType={}, rows={}, exception={}",
                exportType, rows.size(), e.getClass().getSimpleName());
            saveFailedTask(task, "导出文件生成失败");
            recordExport(taskId, exportType, purpose, SensitiveAuditRecorder.RESULT_FAILED,
                detailOf(taskId, rows.size(), fields.size(), "文件生成失败"));
            throw new ServiceException("导出失败，请稍后重试");
        }

        String ossKey = hrTalentOssHelper.buildExportKey(taskId);
        try {
            hrTalentOssHelper.put(ossKey, data);
            task.setOssId(ossKey);
            talentExportTaskMapper.insert(task);
        } catch (RuntimeException e) {
            // 数据库登记失败时清理孤立对象，避免产生无归属的导出文件
            hrTalentOssHelper.delete(ossKey);
            log.error("人才导出任务登记失败, taskId={}, exception={}", taskId, e.getClass().getSimpleName());
            saveFailedTask(task, "导出文件登记失败");
            recordExport(taskId, exportType, purpose, SensitiveAuditRecorder.RESULT_FAILED,
                detailOf(taskId, rows.size(), fields.size(), "文件登记失败"));
            throw new ServiceException("导出失败，请稍后重试");
        }
        recordExport(taskId, exportType, purpose, SensitiveAuditRecorder.RESULT_SUCCESS,
            detailOf(taskId, rows.size(), fields.size(), null));
        log.info("人才台账导出完成, taskId={}, exportType={}, rows={}, fieldCount={}",
            taskId, exportType, rows.size(), fields.size());
        return taskId;
    }

    /* ------------------------------------------------------------------ 列表与下载 ------------------------------------------------------------------ */

    @Override
    public PageResult<TalentExportTaskVo> queryPage(TalentExportQueryBo bo, PageQuery pageQuery) {
        TalentExportQueryBo query = bo == null ? new TalentExportQueryBo() : bo;
        LambdaQueryWrapper<TalentExportTask> wrapper = new LambdaQueryWrapper<TalentExportTask>()
            .eq(StringUtils.isNotBlank(query.getExportType()), TalentExportTask::getExportType, query.getExportType())
            .eq(StringUtils.isNotBlank(query.getStatus()), TalentExportTask::getStatus, query.getStatus())
            .eq(query.getExportedBy() != null, TalentExportTask::getExportedBy, query.getExportedBy())
            .ge(query.getCreateDateBegin() != null, TalentExportTask::getCreateTime,
                query.getCreateDateBegin() == null ? null : query.getCreateDateBegin().atStartOfDay())
            .le(query.getCreateDateEnd() != null, TalentExportTask::getCreateTime,
                query.getCreateDateEnd() == null ? null : query.getCreateDateEnd().atTime(23, 59, 59))
            .orderByDesc(TalentExportTask::getCreateTime);
        Page<TalentExportTaskVo> page = talentExportTaskMapper.selectVoPage(pageQuery.build(), wrapper);
        List<TalentExportTaskVo> records = page.getRecords() == null ? List.of() : page.getRecords();
        for (TalentExportTaskVo vo : records) {
            fillDownloadApi(vo);
        }
        return PageResult.build(records, page.getTotal());
    }

    @Override
    public void download(Long taskId, String purpose, HttpServletResponse response) {
        String normalizedPurpose = normalizePurpose(purpose);
        if (taskId == null) {
            throw new ServiceException("导出任务ID不能为空");
        }
        if (normalizedPurpose == null) {
            // 用途为空：先写 denied 审计再拒绝，保证「被拒绝的受控下载」同样留痕
            recordExport(taskId, null, null, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(taskId, 0, 0, "purpose 为空"));
            throw new ServiceException("下载导出文件必须填写用途（purpose）");
        }
        if (normalizedPurpose.length() > PURPOSE_MAX_LENGTH) {
            recordExport(taskId, null, normalizedPurpose.substring(0, PURPOSE_MAX_LENGTH),
                SensitiveAuditRecorder.RESULT_DENIED, detailOf(taskId, 0, 0, "purpose 超长"));
            throw new ServiceException("用途长度不能超过 " + PURPOSE_MAX_LENGTH + " 个字符");
        }
        TalentExportTask task = talentExportTaskMapper.selectById(taskId);
        if (task == null) {
            recordExport(taskId, null, normalizedPurpose, SensitiveAuditRecorder.RESULT_FAILED,
                detailOf(taskId, 0, 0, "任务不存在"));
            throw new ServiceException("导出任务不存在或已删除");
        }
        if (!STATUS_SUCCESS.equals(task.getStatus()) || StringUtils.isBlank(task.getOssId())) {
            recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, "任务状态不可下载"));
            throw new ServiceException("导出文件不可下载：任务状态为 " + task.getStatus());
        }
        if (isExpired(task)) {
            // 到期即不可下载：同时把状态推进为 expired，便于后续清理任务收敛
            markExpired(taskId);
            recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, "文件已过期"));
            throw new ServiceException("导出文件已过期，请重新导出");
        }
        if (EXPORT_TYPE_SENSITIVE.equals(task.getExportType()) && !hasSensitiveExportPermission()) {
            // 防止绕过「创建时」的敏感权限校验，从任务列表直接取敏感文件
            recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, "缺少敏感台账独立权限"));
            throw new ServiceException("无权下载敏感台账：需要独立权限 " + HrTalentConstants.PERM_PROFILE_PHONE_VIEW);
        }
        if (!canAccessTask(task)) {
            recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_DENIED,
                detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, "无权下载他人台账"));
            throw new ServiceException("无权下载他人创建的人才台账");
        }
        try {
            writeStream(task, response);
        } catch (ServiceException e) {
            recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_FAILED,
                detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, "文件读取失败"));
            throw e;
        }
        recordExport(taskId, task.getExportType(), normalizedPurpose, SensitiveAuditRecorder.RESULT_SUCCESS,
            detailOf(taskId, task.getRecordCount() == null ? 0 : task.getRecordCount(), 0, null));
        log.info("人才台账下载完成, taskId={}, exportType={}, rows={}",
            taskId, task.getExportType(), task.getRecordCount());
    }

    /* ------------------------------------------------------------------ 内部方法：任务记录 ------------------------------------------------------------------ */

    /**
     * 组装导出任务实体（成功态字段在此一次性填好，{@code oss_id} 由调用方在对象写入成功后补上）。
     *
     * @param bo         导出入参
     * @param taskId     任务ID
     * @param exportType 导出类型
     * @param fields     字段清单
     * @param rowCount   记录数
     * @param purpose    用途
     * @return 导出任务实体
     */
    private TalentExportTask buildTask(TalentExportCreateBo bo, Long taskId, String exportType,
                                       List<String> fields, int rowCount, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        TalentExportTask task = new TalentExportTask();
        task.setTaskId(taskId);
        task.setTaskNo(businessNoGenerator.nextExportNo());
        task.setExportType(exportType);
        // scope_json 只写结构化条件，且剥离电话/邮箱等完整联系方式，禁止敏感明文落快照
        task.setScopeJson(toJson(snapshotFilters(bo.getFilters())));
        task.setFieldsJson(toJson(fields));
        task.setExportedBy(currentUserId());
        task.setPurpose(purpose);
        task.setRecordCount(rowCount);
        task.setFileName(HrTalentConstants.EXPORT_FILE_NAME);
        task.setStatus(STATUS_SUCCESS);
        task.setExpireTime(now.plus(resolveValidDuration()));
        task.setFinishedTime(now);
        task.setRemark(truncate(bo.getRemark(), REMARK_MAX_LENGTH));
        return task;
    }

    /**
     * 落一条失败任务记录；任何异常只记日志，不影响原始异常向上抛出。
     *
     * @param task   任务实体（至少已填 taskId/taskNo/类型）
     * @param reason 失败原因（技术性中文说明）
     */
    private void saveFailedTask(TalentExportTask task, String reason) {
        try {
            task.setStatus(STATUS_FAILED);
            task.setOssId(null);
            task.setFailureReason(truncate(reason, FAILURE_REASON_MAX_LENGTH));
            task.setFinishedTime(LocalDateTime.now());
            talentExportTaskMapper.insert(task);
        } catch (RuntimeException e) {
            log.error("导出失败任务记录写入失败, taskId={}, exception={}",
                task == null ? null : task.getTaskId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 把过期任务状态推进为 {@code expired}；失败只记日志（下载仍会被拒绝）。
     *
     * @param taskId 任务ID
     */
    private void markExpired(Long taskId) {
        try {
            talentExportTaskMapper.update(null, new LambdaUpdateWrapper<TalentExportTask>()
                .eq(TalentExportTask::getTaskId, taskId)
                .set(TalentExportTask::getStatus, STATUS_EXPIRED));
        } catch (RuntimeException e) {
            log.warn("导出任务过期状态更新失败, taskId={}, exception={}", taskId, e.getClass().getSimpleName());
        }
    }

    /**
     * 判定任务结果文件是否已过期。
     *
     * @param task 导出任务
     * @return 是否已过期
     */
    private boolean isExpired(TalentExportTask task) {
        return task.getExpireTime() != null && !task.getExpireTime().isAfter(LocalDateTime.now());
    }

    /**
     * 填充系统内受控下载地址；仅成功且未过期的任务提供下载入口。
     *
     * @param vo 导出任务视图对象
     */
    private void fillDownloadApi(TalentExportTaskVo vo) {
        if (vo == null || !STATUS_SUCCESS.equals(vo.getStatus())) {
            return;
        }
        if (vo.getExpireTime() != null && !vo.getExpireTime().isAfter(LocalDateTime.now())) {
            return;
        }
        vo.setDownloadApi(DOWNLOAD_API_TEMPLATE.formatted(vo.getTaskId()));
    }

    /**
     * 判定当前用户是否可以下载该导出文件。
     *
     * <p>规则：导出人本人、超级管理员、集团级管理员（集团招聘/人才管理员）可下载；
     * 其余一律拒绝。角色判定统一走 {@code TalentScopeDomainService#isGroupLevelAdmin()}，
     * 不在本类自写角色判断。</p>
     *
     * @param task 导出任务
     * @return 是否可下载
     */
    private boolean canAccessTask(TalentExportTask task) {
        Long currentUserId = currentUserId();
        if (currentUserId != null && currentUserId.equals(task.getExportedBy())) {
            return true;
        }
        // 导出人本人之外：仅超级管理员与集团级管理员（集团招聘/人才管理员）可下载
        try {
            if (LoginHelper.isSuperAdmin()) {
                return true;
            }
        } catch (Exception e) {
            // 无登录态或容器外调用：继续走集团级角色判定
            log.debug("下载导出文件时无法判定超级管理员, taskId={}", task.getTaskId());
        }
        try {
            return talentScopeDomainService.isGroupLevelAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 流式输出结果文件；<b>不</b>返回任何对象存储地址（§11.1）。
     *
     * @param task     导出任务
     * @param response HTTP 响应
     */
    private void writeStream(TalentExportTask task, HttpServletResponse response) {
        FileUtils.setAttachmentResponseHeader(response, task.getFileName());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        try {
            hrTalentOssHelper.get(task.getOssId(), response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            // 不记录对象键与文件名
            log.error("导出文件流式输出失败, taskId={}, exception={}", task.getTaskId(), e.getClass().getSimpleName());
            throw new ServiceException("导出文件读取失败");
        }
    }

    /* ------------------------------------------------------------------ 内部方法：结果文件 ------------------------------------------------------------------ */

    /**
     * 生成结果文件字节（xlsx）。
     *
     * @param rows   导出行
     * @param fields 字段清单（列裁剪与顺序）
     * @return xlsx 字节
     */
    private byte[] buildWorkbook(List<TalentExportRowVo> rows, List<String> fields) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelBuilder.of(rows, TalentExportRowVo.class)
                .sheetName(SHEET_NAME)
                .includeFields(fields)
                .toStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("导出文件生成失败");
        }
    }

    /**
     * 组装导出行。
     *
     * <p><b>脱敏口径</b>：普通台账的姓名列写脱敏值、<b>不含</b>任何联系方式；
     * 敏感台账写姓名明文与联系方式，调用方已在创建前完成「独立权限 + 用途 + 审计」三道闸门（§8.20）。</p>
     *
     * <p><b>访问地址口径</b>：附件/简历地址一律为系统内受控地址
     * （{@code /talent/profiles/{id}}、{@code /talent/resumes/{id}/download}），
     * <b>不是</b>对象存储永久地址（§8.13、§11.1）。</p>
     *
     * @param profiles  主档实体列表
     * @param sensitive 是否敏感台账
     * @return 导出行
     */
    private List<TalentExportRowVo> buildRows(List<TalentProfile> profiles, boolean sensitive) {
        Map<Long, String> tagNames = loadTagNames(profiles);
        Map<Long, String> ownerNames = loadOwnerNames(profiles);
        List<TalentExportRowVo> rows = new ArrayList<>(profiles.size());
        for (TalentProfile profile : profiles) {
            TalentExportRowVo row = new TalentExportRowVo();
            row.setTalentNo(profile.getTalentNo());
            row.setName(sensitive ? profile.getName() : DesensitizedUtil.chineseName(profile.getName()));
            row.setPositionDirection(firstNonBlank(profile.getExpectedPosition(), profile.getCurrentPosition()));
            row.setEducation(educationText(profile.getHighestEducation()));
            row.setRegion(profile.getCurrentCity());
            row.setTalentStatus(statusText(profile.getTalentStatus()));
            row.setTags(tagNames.get(profile.getTalentId()));
            row.setOwner(ownerText(profile.getOwnerId(), ownerNames));
            if (sensitive) {
                row.setPhone(profile.getPhoneCipher());
                row.setEmail(profile.getEmailCipher());
                row.setSalary(salaryText(profile.getExpectedSalaryMin(), profile.getExpectedSalaryMax()));
                row.setProfileAccess(PROFILE_API_TEMPLATE.formatted(profile.getTalentId()));
                row.setResumeAccess(profile.getCurrentResumeId() == null
                    ? null : RESUME_DOWNLOAD_API_TEMPLATE.formatted(profile.getCurrentResumeId()));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量装载「人才ID → 标签名（英文逗号分隔）」。
     *
     * <p><b>敏感标签不入表</b>：{@code sensitive_flag = '1'} 的标签不进入导出结果
     * （设计文档 §7.6.4：背调失败/健康/家庭/年龄等敏感内容不得被普通用户检索与批量带出）。</p>
     *
     * @param profiles 主档实体列表
     * @return 标签名映射（无标签时为空 Map）
     */
    private Map<Long, String> loadTagNames(List<TalentProfile> profiles) {
        List<Long> talentIds = profiles.stream().map(TalentProfile::getTalentId).filter(Objects::nonNull).distinct().toList();
        if (talentIds.isEmpty()) {
            return Map.of();
        }
        List<TalentProfileTag> relations = talentProfileTagMapper.selectList(
            new LambdaQueryWrapper<TalentProfileTag>().in(TalentProfileTag::getTalentId, talentIds));
        if (CollUtil.isEmpty(relations)) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(TalentProfileTag::getTagId).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        List<TalentTag> tags = talentTagMapper.selectByIds(tagIds);
        Map<Long, String> tagNameById = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(tags)) {
            for (TalentTag tag : tags) {
                if (tag == null || SENSITIVE_TAG_FLAG.equals(tag.getSensitiveFlag())) {
                    continue;
                }
                tagNameById.put(tag.getTagId(), tag.getTagName());
            }
        }
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (TalentProfileTag relation : relations) {
            String name = tagNameById.get(relation.getTagId());
            if (name == null) {
                continue;
            }
            List<String> names = grouped.computeIfAbsent(relation.getTalentId(), key -> new ArrayList<>());
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        Map<Long, String> result = new LinkedHashMap<>();
        grouped.forEach((talentId, names) -> result.put(talentId, String.join(",", names)));
        return result;
    }

    /**
     * 批量装载「用户ID → 昵称」；用户服务不可用时返回空 Map（导出降级为负责人ID）。
     *
     * @param profiles 主档实体列表
     * @return 昵称映射
     */
    private Map<Long, String> loadOwnerNames(List<TalentProfile> profiles) {
        Set<Long> ownerIds = profiles.stream().map(TalentProfile::getOwnerId).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        UserService userService = userServiceProvider.getIfAvailable();
        if (userService == null) {
            return Map.of();
        }
        try {
            Map<Long, String> names = userService.selectUserNicksByIds(ownerIds);
            return names == null ? Map.of() : names;
        } catch (Exception e) {
            log.warn("导出负责人昵称解析失败, exception={}", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    /* ------------------------------------------------------------------ 内部方法：校验与快照 ------------------------------------------------------------------ */

    /**
     * 解析导出类型：空值按普通台账处理，未知编码拒绝（fail-safe）。
     *
     * @param exportType 导出类型
     * @return 规范化后的导出类型
     */
    private String resolveExportType(String exportType) {
        if (StringUtils.isBlank(exportType)) {
            return EXPORT_TYPE_NORMAL;
        }
        String value = exportType.trim().toLowerCase();
        if (!EXPORT_TYPE_NORMAL.equals(value) && !EXPORT_TYPE_SENSITIVE.equals(value)) {
            throw new ServiceException("导出类型不合法，只能为 normal（普通台账）或 sensitive（敏感台账）");
        }
        return value;
    }

    /**
     * 解析字段清单：为空取该类型默认字段集；非白名单字段一律拒绝。
     *
     * @param exportType 导出类型
     * @param requested  请求字段清单，可为空
     * @return 字段清单（非空、去重、保持声明顺序）
     */
    private List<String> resolveFields(String exportType, List<String> requested) {
        List<String> allowed = new ArrayList<>(NORMAL_FIELDS);
        if (EXPORT_TYPE_SENSITIVE.equals(exportType)) {
            allowed.addAll(SENSITIVE_EXTRA_FIELDS);
        }
        if (CollUtil.isEmpty(requested)) {
            return allowed;
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (String field : requested) {
            if (StringUtils.isBlank(field)) {
                continue;
            }
            String key = field.trim();
            if (!allowed.contains(key)) {
                throw new ServiceException("不支持的导出字段：" + key);
            }
            fields.add(key);
        }
        if (fields.isEmpty()) {
            throw new ServiceException("导出字段清单不能为空");
        }
        return new ArrayList<>(fields);
    }

    /**
     * 取单次导出行数上限；配置非正数时兜底为 {@link #DEFAULT_EXPORT_MAX_ROWS}。
     *
     * @return 行数上限（恒为正数）
     */
    private int resolveMaxRows() {
        int configured = hrTalentProperties.getExportMaxRows();
        return configured > 0 ? configured : DEFAULT_EXPORT_MAX_ROWS;
    }

    /**
     * 取导出文件有效期；配置为空或非正数时兜底为 7 天。
     *
     * @return 有效期
     */
    private java.time.Duration resolveValidDuration() {
        java.time.Duration duration = hrTalentProperties.getExportValidDuration();
        return duration == null || duration.isZero() || duration.isNegative()
            ? java.time.Duration.ofDays(7) : duration;
    }

    /**
     * 规范化用途：去空白；空串返回 null（由调用方决定是否拒绝）。
     *
     * @param purpose 原始用途
     * @return 规范化用途或 null
     */
    private String normalizePurpose(String purpose) {
        if (purpose == null) {
            return null;
        }
        String trimmed = purpose.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判定当前用户是否具备「敏感台账独立权限」。
     *
     * <p>超管直接通过；其余用户必须具备 {@code talent:profile:phone-view} 菜单权限。
     * 本期不新增权限串，因此无需改动菜单 SQL（复用既有独立按钮，符合 §8.20「独立权限」要求）。</p>
     *
     * @return 是否具备
     */
    private boolean hasSensitiveExportPermission() {
        try {
            if (LoginHelper.isSuperAdmin()) {
                return true;
            }
        } catch (Exception e) {
            log.debug("判定超级管理员失败，继续走菜单权限判定");
        }
        try {
            return talentScopeDomainService.hasMenuPermission(HrTalentConstants.PERM_PROFILE_PHONE_VIEW);
        } catch (Exception e) {
            // 无登录态（如容器外调用）一律按无权限处理
            return false;
        }
    }

    /**
     * 构造筛选条件快照：只保留非空条件，并<b>剥离</b>电话/邮箱完整值（§8.20 禁止敏感明文落快照）。
     *
     * @param filters 检索条件，可为空
     * @return 结构化快照
     */
    private Map<String, Object> snapshotFilters(TalentProfileQueryBo filters) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (filters == null) {
            return snapshot;
        }
        putIfPresent(snapshot, "name", filters.getName());
        putIfPresent(snapshot, "talentNo", filters.getTalentNo());
        // 完整手机号/邮箱不落快照，仅记录「使用过精确匹配」这一事实
        putIfPresent(snapshot, "phoneExactMatch", StringUtils.isNotBlank(filters.getPhone()) ? Boolean.TRUE : null);
        putIfPresent(snapshot, "phoneTail4", filters.getPhoneTail4());
        putIfPresent(snapshot, "emailExactMatch", StringUtils.isNotBlank(filters.getEmail()) ? Boolean.TRUE : null);
        putIfPresent(snapshot, "currentPosition", filters.getCurrentPosition());
        putIfPresent(snapshot, "historyPosition", filters.getHistoryPosition());
        putIfPresent(snapshot, "expectedPosition", filters.getExpectedPosition());
        putIfPresent(snapshot, "tagIds", CollUtil.isEmpty(filters.getTagIds()) ? null : filters.getTagIds());
        putIfPresent(snapshot, "highestEducation", filters.getHighestEducation());
        putIfPresent(snapshot, "major", filters.getMajor());
        putIfPresent(snapshot, "schoolName", filters.getSchoolName());
        putIfPresent(snapshot, "currentCity", filters.getCurrentCity());
        putIfPresent(snapshot, "expectedCity", filters.getExpectedCity());
        putIfPresent(snapshot, "workYearsBegin", filters.getWorkYearsBegin());
        putIfPresent(snapshot, "workYearsEnd", filters.getWorkYearsEnd());
        putIfPresent(snapshot, "industry", filters.getIndustry());
        putIfPresent(snapshot, "currentCompany", filters.getCurrentCompany());
        putIfPresent(snapshot, "sourceChannelId", filters.getSourceChannelId());
        putIfPresent(snapshot, "ownerDeptId", filters.getOwnerDeptId());
        putIfPresent(snapshot, "ownerId", filters.getOwnerId());
        putIfPresent(snapshot, "poolId", filters.getPoolId());
        putIfPresent(snapshot, "talentStatus", filters.getTalentStatus());
        putIfPresent(snapshot, "lastFollowTimeBegin", filters.getLastFollowTimeBegin());
        putIfPresent(snapshot, "lastFollowTimeEnd", filters.getLastFollowTimeEnd());
        putIfPresent(snapshot, "hasCurrentResume", filters.getHasCurrentResume());
        putIfPresent(snapshot, "resumeParseStatus", filters.getResumeParseStatus());
        putIfPresent(snapshot, "includeArchived", filters.getIncludeArchived());
        return snapshot;
    }

    /**
     * 非空时写入键值。
     *
     * @param target 快照
     * @param key    键
     * @param value  值
     */
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /* ------------------------------------------------------------------ 内部方法：审计 ------------------------------------------------------------------ */

    /**
     * 记录一次导出相关审计（统一 {@code EVENT_EXPORT + BIZ_TALENT}）。
     *
     * @param taskId     导出任务ID，可为空
     * @param exportType 导出类型，可为空
     * @param purpose    用途，可为空
     * @param result     结果（success/denied/failed）
     * @param detailJson 脱敏明细 JSON，可为空
     */
    private void recordExport(Long taskId, String exportType, String purpose, String result, String detailJson) {
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_EXPORT, SensitiveAuditRecorder.BIZ_TALENT,
            taskId, purpose, result, detailJson);
    }

    /**
     * 构造审计明细：只含任务ID、类型、行数、字段数与拒绝原因，<b>不含</b>任何联系方式和对象键。
     *
     * @param taskId   任务ID，可为空
     * @param rows     行数
     * @param fields   字段数
     * @param note     说明（拒绝/失败原因），可为空
     * @return 脱敏 JSON 明细
     */
    private String detailOf(Long taskId, int rows, int fields, String note) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId);
        detail.put("rows", rows);
        detail.put("fieldCount", fields);
        detail.put("note", note);
        return toJson(detail);
    }

    /**
     * 极简 JSON 序列化（服务端自持，不依赖 Spring 容器）。
     *
     * <p><b>为什么不用 {@code JsonUtils}</b>：{@code JsonUtils} 的静态初始化依赖 Spring 容器，
     * 而导出任务快照与审计明细必须能在无容器上下文中生成（可单测）。这里只支持
     * Map / Iterable / String / Number / Boolean / 时间类型，足够表达快照与明细结构，
     * 且不存在反射或对象图遍历带来的敏感字段泄漏风险。</p>
     *
     * @param value 任意受支持的值
     * @return JSON 文本
     */
    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return jsonString(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof LocalDateTime dateTime) {
            return jsonString(dateTime.toString());
        }
        if (value instanceof java.time.LocalDate date) {
            return jsonString(date.toString());
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append(jsonString(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            return json.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append(toJson(item));
            }
            return json.append(']').toString();
        }
        return jsonString(String.valueOf(value));
    }

    /**
     * JSON 字符串转义（含控制字符）。
     *
     * @param value 原值
     * @return 带引号的转义字符串
     */
    private String jsonString(String value) {
        StringBuilder json = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    /* ------------------------------------------------------------------ 内部方法：通用 ------------------------------------------------------------------ */

    /**
     * 取当前登录用户ID；无登录态返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按列长度截断。
     *
     * @param value  原值
     * @param maxLen 最大长度
     * @return 截断后的值
     */
    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    /**
     * 取第一个非空白值。
     *
     * @param first  首选值
     * @param second 备选值
     * @return 非空白值；都没有时返回 null
     */
    private String firstNonBlank(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first;
        }
        return StringUtils.isNotBlank(second) ? second : null;
    }

    /**
     * 负责人列：优先昵称，取不到昵称时回落为用户ID 文本。
     *
     * @param ownerId   负责人用户ID
     * @param ownerNames 昵称映射
     * @return 展示值
     */
    private String ownerText(Long ownerId, Map<Long, String> ownerNames) {
        if (ownerId == null) {
            return null;
        }
        String name = ownerNames.get(ownerId);
        return StringUtils.isNotBlank(name) ? name : String.valueOf(ownerId);
    }

    /**
     * 期望薪资区间文本（敏感台账列）。
     *
     * @param min 下限
     * @param max 上限
     * @return 文本；两端都为空时返回 null
     */
    private String salaryText(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return min.stripTrailingZeros().toPlainString() + "-" + max.stripTrailingZeros().toPlainString();
        }
        return (min == null ? max : min).stripTrailingZeros().toPlainString();
    }

    /**
     * 学历中文兜底（§10 未定义学历字典，编码口径与 {@code TalentProfileVo} 一致）。
     *
     * @param code 学历编码
     * @return 中文；未知编码回落为原编码
     */
    private String educationText(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return switch (code) {
            case "high_school" -> "高中及以下";
            case "college" -> "大专";
            case "bachelor" -> "本科";
            case "master" -> "硕士";
            case "doctor" -> "博士";
            case "other" -> "其他";
            default -> code;
        };
    }

    /**
     * 人才状态中文兜底。
     *
     * @param code 状态编码
     * @return 中文；未知编码回落为原编码
     */
    private String statusText(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        TalentStatusEnum status = TalentStatusEnum.find(code);
        return status == null ? code : status.getDesc();
    }

}
