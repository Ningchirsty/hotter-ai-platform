package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardBo;
import org.dromara.hrtalent.domain.entity.RecruitImportBatch;
import org.dromara.hrtalent.domain.entity.RecruitImportError;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportBatchVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportErrorVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportPreviewVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportResultVo;
import org.dromara.hrtalent.enums.RecruitImportBatchStatusEnum;
import org.dromara.hrtalent.enums.RecruitImportSeverityEnum;
import org.dromara.hrtalent.enums.RecruitImportTypeEnum;
import org.dromara.hrtalent.helper.RecruitImportExcelReader;
import org.dromara.hrtalent.mapper.RecruitImportBatchMapper;
import org.dromara.hrtalent.mapper.RecruitImportErrorMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitImportService;
import org.dromara.hrtalent.service.recruitment.IRecruitPlanService;
import org.dromara.hrtalent.service.recruitment.IRecruitStandardService;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.system.api.DeptService;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.DeptDTO;
import org.dromara.system.api.domain.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 招聘数据导入服务实现（招聘期限标准 / 月度招聘计划）。
 *
 * <p><b>两段式的落点</b>：</p>
 * <ol>
 *     <li>{@link #preview}：建批次 → 源文件入对象存储 → 解析+逐行校验 → 逐行问题落
 *     {@code hr_recruit_import_error} → 批次置「待确认」。<b>一行业务数据都不写。</b></li>
 *     <li>{@link #confirm}：取回源文件重放（不依赖进程内存）→ 只导入无 error 的行 →
 *     回写成功/失败数并置终态。</li>
 * </ol>
 *
 * <p><b>两条容易做错的地方，这里刻意这么处理</b>：</p>
 * <ul>
 *     <li><b>公司/部门按名称解析</b>：用户表格里写的是「XX 子公司」，入库存的是 deptId。
 *     解析不到就明确报错（公司）或降级为提示（用工部门，因为该字段是名称快照，允许人工核对）。</li>
 *     <li><b>确认阶段重新校验</b>：预检到确认之间可能过了很久（用户去改文件、去问人），
 *     组织机构或计划状态都可能变了。重放时重新校验，发现问题该行不计入成功，
 *     而不是拿旧结论硬写。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitImportServiceImpl implements IRecruitImportService {

    /**
     * 单次返回给前端的问题条数上限（避免几万行错误把响应撑爆）
     */
    private static final int MAX_RETURNED_ISSUES = 100;

    /**
     * 预检结果里回显给用户看的解析样例行数
     */
    private static final int PREVIEW_LIMIT = 10;

    /**
     * 上传文件大小上限（5MB）。
     * <p>上限只对着「有多少行」这件事：{@value RecruitImportExcelReader#MAX_DATA_ROWS} 行
     * 的纯文本表远达不到 5MB，超过基本意味着传错了文件。</p>
     */
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /**
     * 允许的扩展名
     */
    private static final Set<String> ALLOWED_EXT = Set.of("xlsx", "xls");

    /* ------------------------------------------------------------------ 列定义 ------------------------------------------------------------------ */

    /**
     * 一列的声明。
     *
     * @param header   表头名（解析与模板共用，改这里就等于改契约）
     * @param required 是否必填
     * @param hint     填写说明（写进模板的「填写说明」表）
     */
    private record ColumnDef(String header, boolean required, String hint) {
    }

    /**
     * 招聘期限标准模板列。
     */
    private static final List<ColumnDef> STANDARD_COLUMNS = List.of(
        new ColumnDef("岗位名称", true, "必填。如「前端开发工程师」。与公司、生效日期共同构成标准的唯一标识"),
        new ColumnDef("公司名称", false, "选填。填组织架构里的公司名称；留空表示集团通用（所有公司适用）"),
        new ColumnDef("招聘期限标准天数", true, "必填。非负整数，如 30"),
        new ColumnDef("生效日期", false, "选填。格式 2026-08-01；留空表示不限定生效日"),
        new ColumnDef("失效日期", false, "选填。格式 2026-12-31；不得早于生效日期"),
        new ColumnDef("状态", false, "选填。active 生效（默认）/ inactive 停用"),
        new ColumnDef("备注", false, "选填。不超过 500 字"));

    /**
     * 月度招聘计划模板列。
     */
    private static final List<ColumnDef> PLAN_COLUMNS = List.of(
        new ColumnDef("公司名称", true, "必填。必须是组织架构里已存在的公司名称，例如「趣往」"),
        new ColumnDef("计划月份", true, "必填。格式 2026-08 或 2026-8"),
        new ColumnDef("岗位名称", true, "必填。如「前端开发工程师」"),
        new ColumnDef("计划人数", true, "必填。大于 0 的整数"),
        new ColumnDef("用工部门名称", false, "选填。组织架构里存在的部门名称；填了但不存在会有提示，仍会导入"),
        new ColumnDef("招聘期限标准天数", false, "选填。非负整数"),
        new ColumnDef("紧急程度", false, "选填。正常 / 紧急 / 非常紧急（也可填 normal / urgent / very_urgent），默认正常"),
        new ColumnDef("任务负责人", false, "选填。填用户账号或姓名；填了但匹配不到会有提示，仍会导入（负责人留空）"),
        new ColumnDef("是否允许结转", false, "选填。是 / 否，默认「是」"),
        new ColumnDef("备注", false, "选填。不超过 500 字"));

    /* ------------------------------------------------------------------ 行模型 ------------------------------------------------------------------ */

    /**
     * 一条校验问题。
     *
     * @param rowNo    Excel 1 起行号
     * @param field    表头名
     * @param rawValue 原始值（截断后）
     * @param code     稳定错误编码
     * @param severity 严重级别
     * @param message  可读说明
     */
    private record Issue(int rowNo, String field, String rawValue, String code,
                         RecruitImportSeverityEnum severity, String message) {
    }

    /**
     * 招聘期限标准的一行（已校验）。
     */
    private record StandardRow(int rowNo, String jobName, Long companyDeptId, String companyName,
                               Integer standardDays, LocalDate effectiveDate, LocalDate expiryDate,
                               String status, String remark) {
    }

    /**
     * 月度计划的一行（已校验）。
     */
    private record PlanRow(int rowNo, Long companyDeptId, String companyName, String planMonth,
                           String jobName, Integer planQty, Long useDeptId, String useDeptName,
                           Integer standardDays, String urgency, Long ownerId,
                           String carryoverEnabled, String remark) {
    }

    /**
     * 一次解析的结果。
     *
     * @param total     数据行总数
     * @param rows      可导入的行
     * @param issues    问题清单
     * @param errorRows 存在 error 的行号集合
     */
    private record Parsed<T>(int total, List<T> rows, List<Issue> issues, Set<Integer> errorRows) {
    }

    /* ------------------------------------------------------------------ 依赖 ------------------------------------------------------------------ */

    private final RecruitImportBatchMapper batchMapper;

    private final RecruitImportErrorMapper errorMapper;

    private final RecruitImportExcelReader excelReader;

    private final IRecruitStandardService standardService;

    private final IRecruitPlanService planService;

    private final HrTalentOssHelper ossHelper;

    private final RecruitBusinessNoGenerator businessNoGenerator;

    private final DeptService deptService;

    private final UserService userService;

    /* ------------------------------------------------------------------ 模板 ------------------------------------------------------------------ */

    @Override
    public void writeTemplate(RecruitImportTypeEnum type, HttpServletResponse response) {
        List<ColumnDef> columns = columnsOf(type);
        String fileName = type.getDesc() + "导入模板.xlsx";
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet data = workbook.createSheet("数据");
            Row header = data.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).header());
                // 列宽按表头长度粗估，保证在 Excel 里一眼能看清列名
                data.setColumnWidth(i, Math.min(60, Math.max(14, columns.get(i).header().length() * 4)) * 256);
            }
            // 说明单独放一张表：写进数据表会被当成数据行，或者逼解析端去跳过固定行数
            Sheet help = workbook.createSheet("填写说明");
            Row helpHeader = help.createRow(0);
            helpHeader.createCell(0).setCellValue("列名");
            helpHeader.createCell(1).setCellValue("是否必填");
            helpHeader.createCell(2).setCellValue("填写说明");
            help.setColumnWidth(0, 22 * 256);
            help.setColumnWidth(1, 10 * 256);
            help.setColumnWidth(2, 80 * 256);
            for (int i = 0; i < columns.size(); i++) {
                Row row = help.createRow(i + 1);
                row.createCell(0).setCellValue(columns.get(i).header());
                row.createCell(1).setCellValue(columns.get(i).required() ? "必填" : "选填");
                row.createCell(2).setCellValue(columns.get(i).hint());
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
            log.info("导出导入模板, type={}, columns={}", type.getCode(), columns.size());
        } catch (Exception e) {
            log.error("导出导入模板失败, type={}, exception={}", type.getCode(), e.getClass().getSimpleName());
            throw new ServiceException("模板生成失败，请稍后重试");
        }
    }

    /* ------------------------------------------------------------------ 预检 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecruitImportPreviewVo preview(RecruitImportTypeEnum type, MultipartFile file) {
        if (type == null) {
            throw new ServiceException("导入类型不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要导入的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ServiceException("文件超过 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB，请拆分后导入");
        }
        String originalName = StringUtils.blankToDefault(file.getOriginalFilename(), "unnamed.xlsx");
        String ext = extOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ServiceException("仅支持 .xlsx / .xls 文件，当前为 ." + ext);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ServiceException("文件读取失败，请重新上传");
        }
        RecruitImportExcelReader.SheetData sheet = excelReader.read(bytes);

        // 1. 先建批次拿到主键，源文件对象键以批次ID为分段
        RecruitImportBatch batch = new RecruitImportBatch();
        batch.setBatchNo(businessNoGenerator.nextImportBatchNo());
        batch.setImportType(type.getCode());
        batch.setSourceFileName(originalName);
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailureCount(0);
        batch.setStatus(RecruitImportBatchStatusEnum.PENDING.getCode());
        batch.setOperatorId(LoginHelper.getUserId());
        batch.setRemark("上传预检，尚未导入业务数据");
        batchMapper.insert(batch);

        String key = ossHelper.buildImportKey(batch.getBatchId());
        ossHelper.put(key, bytes);
        batchMapper.update(null, new LambdaUpdateWrapper<RecruitImportBatch>()
            .eq(RecruitImportBatch::getBatchId, batch.getBatchId())
            .set(RecruitImportBatch::getSourceFileOssId, key));

        // 2. 解析 + 校验，只落错误明细，不写业务数据
        Validation validation = validate(type, sheet);
        saveIssues(batch.getBatchId(), sheet.sheetName(), validation.issues());
        int errorRows = validation.errorRowCount();
        batchMapper.update(null, new LambdaUpdateWrapper<RecruitImportBatch>()
            .eq(RecruitImportBatch::getBatchId, batch.getBatchId())
            .set(RecruitImportBatch::getTotalCount, validation.total())
            .set(RecruitImportBatch::getFailureCount, errorRows)
            .set(RecruitImportBatch::getSuccessCount, 0));

        RecruitImportPreviewVo vo = new RecruitImportPreviewVo();
        vo.setBatchId(batch.getBatchId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setImportType(type.getCode());
        vo.setImportTypeName(type.getDesc());
        vo.setSourceFileName(originalName);
        vo.setTotalCount(validation.total());
        vo.setErrorCount(errorRows);
        vo.setValidCount(validation.total() - errorRows);
        vo.setWarningCount(validation.warningCount());
        vo.setColumns(columnMeta(type));
        vo.setIssues(toIssueVos(validation.issues()));
        vo.setPreview(previewRows(sheet));
        vo.setMessage(buildPreviewMessage(validation, errorRows));
        log.info("导入预检完成, batchId={}, type={}, total={}, valid={}, errors={}, warnings={}",
            batch.getBatchId(), type.getCode(), validation.total(), vo.getValidCount(),
            vo.getErrorCount(), vo.getWarningCount());
        return vo;
    }

    /* ------------------------------------------------------------------ 确认 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecruitImportResultVo confirm(Long batchId) {
        RecruitImportBatch batch = loadBatch(batchId);
        RecruitImportTypeEnum type = RecruitImportTypeEnum.find(batch.getImportType());
        if (type == null) {
            throw new ServiceException("批次导入类型非法：" + batch.getImportType());
        }
        String status = batch.getStatus();
        if (RecruitImportBatchStatusEnum.SUCCESS.getCode().equals(status)) {
            throw new ServiceException("该批次已导入成功，请勿重复导入");
        }
        if (!RecruitImportBatchStatusEnum.PENDING.getCode().equals(status)
            && !RecruitImportBatchStatusEnum.PARTIAL_FAILED.getCode().equals(status)) {
            throw new ServiceException("该批次当前状态为「"
                + statusLabel(status) + "」，不可导入");
        }
        if (StringUtils.isBlank(batch.getSourceFileOssId())) {
            throw new ServiceException("该批次的源文件已不可用，请重新上传预检");
        }
        batchMapper.update(null, new LambdaUpdateWrapper<RecruitImportBatch>()
            .eq(RecruitImportBatch::getBatchId, batchId)
            .set(RecruitImportBatch::getStatus, RecruitImportBatchStatusEnum.CONFIRMING.getCode())
            .set(RecruitImportBatch::getStartedTime, LocalDateTime.now()));

        // 取回源文件重放：不依赖进程内存，重启或多端操作都不会丢
        RecruitImportExcelReader.SheetData sheet = excelReader.read(ossHelper.getBytes(batch.getSourceFileOssId()));
        Validation validation = validate(type, sheet);
        // 重放阶段新出现的问题也要留痕（预检到确认之间组织机构可能变了）
        saveIssues(batchId, sheet.sheetName(), validation.issues());

        ApplyResult applied = apply(type, validation, batchId);
        int success = applied.success();
        int failure = applied.failure();
        RecruitImportBatchStatusEnum finalStatus;
        if (failure == 0) {
            finalStatus = RecruitImportBatchStatusEnum.SUCCESS;
        } else if (success == 0) {
            finalStatus = RecruitImportBatchStatusEnum.FAILED;
        } else {
            finalStatus = RecruitImportBatchStatusEnum.PARTIAL_FAILED;
        }
        batchMapper.update(null, new LambdaUpdateWrapper<RecruitImportBatch>()
            .eq(RecruitImportBatch::getBatchId, batchId)
            .set(RecruitImportBatch::getStatus, finalStatus.getCode())
            .set(RecruitImportBatch::getTotalCount, validation.total())
            .set(RecruitImportBatch::getSuccessCount, success)
            .set(RecruitImportBatch::getFailureCount, failure)
            .set(RecruitImportBatch::getFinishedTime, LocalDateTime.now())
            .set(RecruitImportBatch::getRemark, finalStatus.getDesc()));

        RecruitImportResultVo vo = new RecruitImportResultVo();
        vo.setBatchId(batchId);
        vo.setBatchNo(batch.getBatchNo());
        vo.setStatus(finalStatus.getCode());
        vo.setStatusLabel(finalStatus.getDesc());
        vo.setTotalCount(validation.total());
        vo.setSuccessCount(success);
        vo.setFailureCount(failure);
        vo.setIssues(toIssueVos(applied.issues()));
        vo.setMessages(applied.messages());
        log.info("导入确认完成, batchId={}, type={}, status={}, success={}, failure={}",
            batchId, type.getCode(), finalStatus.getCode(), success, failure);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long batchId) {
        RecruitImportBatch batch = loadBatch(batchId);
        if (!RecruitImportBatchStatusEnum.PENDING.getCode().equals(batch.getStatus())) {
            throw new ServiceException("只有「待确认」的批次可以取消，当前状态："
                + statusLabel(batch.getStatus()));
        }
        batchMapper.update(null, new LambdaUpdateWrapper<RecruitImportBatch>()
            .eq(RecruitImportBatch::getBatchId, batchId)
            .set(RecruitImportBatch::getStatus, RecruitImportBatchStatusEnum.CANCELLED.getCode())
            .set(RecruitImportBatch::getFinishedTime, LocalDateTime.now())
            .set(RecruitImportBatch::getRemark, "人工取消，未导入任何数据"));
        log.info("导入批次已取消, batchId={}", batchId);
    }

    @Override
    public RecruitImportBatchVo getBatch(Long batchId) {
        RecruitImportBatchVo vo = batchMapper.selectVoById(loadBatch(batchId).getBatchId());
        fillBatchDerived(vo);
        return vo;
    }

    @Override
    public PageResult<RecruitImportBatchVo> queryBatchPage(String importType, PageQuery pageQuery) {
        LambdaQueryWrapper<RecruitImportBatch> wrapper = new LambdaQueryWrapper<RecruitImportBatch>()
            .eq(StringUtils.isNotBlank(importType), RecruitImportBatch::getImportType, importType)
            .orderByDesc(RecruitImportBatch::getBatchId);
        var page = batchMapper.selectVoPage(pageQuery.build(), wrapper);
        for (RecruitImportBatchVo vo : page.getRecords()) {
            fillBatchDerived(vo);
        }
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /* ------------------------------------------------------------------ 校验 ------------------------------------------------------------------ */

    /**
     * 按类型解析并逐行校验。
     *
     * @param type  导入类型
     * @param sheet 工作表数据
     * @return 校验结果
     */
    private Validation validate(RecruitImportTypeEnum type, RecruitImportExcelReader.SheetData sheet) {
        requireColumns(type, sheet.headers());
        Directories dirs = Directories.of(deptService, userService);
        List<Issue> issues = new ArrayList<>();
        Set<Integer> errorRows = new LinkedHashSet<>();
        if (type == RecruitImportTypeEnum.STANDARD) {
            List<StandardRow> rows = new ArrayList<>();
            for (RecruitImportExcelReader.RowData row : sheet.rows()) {
                StandardRow parsed = parseStandardRow(row, dirs, issues, errorRows);
                if (parsed != null) {
                    rows.add(parsed);
                }
            }
            return new Validation(sheet.rows().size(), issues, errorRows, rows, null);
        }
        List<PlanRow> rows = new ArrayList<>();
        for (RecruitImportExcelReader.RowData row : sheet.rows()) {
            PlanRow parsed = parsePlanRow(row, dirs, issues, errorRows);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return new Validation(sheet.rows().size(), issues, errorRows, null, rows);
    }

    /**
     * 校验必需列是否齐全。
     *
     * @param type    导入类型
     * @param headers 实际表头
     */
    private void requireColumns(RecruitImportTypeEnum type, List<String> headers) {
        List<String> missing = new ArrayList<>();
        for (ColumnDef column : columnsOf(type)) {
            if (column.required() && !headers.contains(column.header())) {
                missing.add(column.header());
            }
        }
        if (!missing.isEmpty()) {
            throw new ServiceException("导入文件缺少必需列：" + String.join("、", missing)
                + "。请用页面上的「下载模板」重新填报");
        }
    }

    /**
     * 解析招聘期限标准的一行。
     *
     * @param row       原始行
     * @param dirs      目录（公司/用户）
     * @param issues    问题收集
     * @param errorRows 错误行号收集
     * @return 可导入行；有 error 时返回 null
     */
    private StandardRow parseStandardRow(RecruitImportExcelReader.RowData row, Directories dirs,
                                         List<Issue> issues, Set<Integer> errorRows) {
        int no = row.rowNo();
        Map<String, String> v = row.values();
        boolean bad = false;

        String jobName = text(v, "岗位名称");
        if (StringUtils.isBlank(jobName)) {
            issues.add(error(no, "岗位名称", jobName, "REQUIRED_MISSING", "岗位名称不能为空"));
            bad = true;
        } else if (jobName.length() > 200) {
            issues.add(error(no, "岗位名称", jobName, "TOO_LONG", "岗位名称不能超过 200 字"));
            bad = true;
        }

        Integer days = parseDays(no, v, "招聘期限标准天数", true, false, issues);
        if (days == null) {
            bad = true;
        }

        Long companyDeptId = null;
        String companyName = text(v, "公司名称");
        if (StringUtils.isNotBlank(companyName)) {
            companyDeptId = dirs.deptId(companyName);
            if (companyDeptId == null) {
                issues.add(error(no, "公司名称", companyName, "DEPT_NOT_FOUND",
                    "组织架构里找不到公司「" + companyName + "」；如需集团通用请留空该列"));
                bad = true;
            }
        }

        LocalDate effective = parseDate(no, v, "生效日期", issues);
        LocalDate expiry = parseDate(no, v, "失效日期", issues);
        if (effective != null && expiry != null && expiry.isBefore(effective)) {
            issues.add(error(no, "失效日期", text(v, "失效日期"), "DATE_ORDER", "失效日期不能早于生效日期"));
            bad = true;
        }

        String status = normalizeStatus(no, text(v, "状态"), issues);
        String remark = limit(no, v, "备注", 500, issues);

        // 覆盖是静默的数据变更：预检阶段就要说出来，否则用户会以为自己在「新增」，
        // 直到发现旧标准的天数变了才知道被覆盖了。放在 error 判定之前、并排除已出错的行走。
        if (!bad && !hasError(issues, no)
            && standardService.findByKey(jobName, companyDeptId, effective) != null) {
            issues.add(warning(no, "岗位名称", jobName, "OVERWRITE_EXIST",
                "已存在同「岗位 + 公司 + 生效日期」的标准，导入将覆盖其天数与状态"));
        }

        // 统一以「本行是否产生了 error 级问题」为准，而不是逐个分支手工置位：
        // 少置一处就会出现「错误已经记了、行却照样入库」这种自相矛盾的结果。
        if (bad || hasError(issues, no)) {
            errorRows.add(no);
            return null;
        }
        return new StandardRow(no, jobName, companyDeptId, StringUtils.blankToDefault(companyName, null),
            days, effective, expiry, status, remark);
    }

    /**
     * 解析月度计划的一行。
     *
     * @param row       原始行
     * @param dirs      目录（公司/用户）
     * @param issues    问题收集
     * @param errorRows 错误行号收集
     * @return 可导入行；有 error 时返回 null
     */
    private PlanRow parsePlanRow(RecruitImportExcelReader.RowData row, Directories dirs,
                                 List<Issue> issues, Set<Integer> errorRows) {
        int no = row.rowNo();
        Map<String, String> v = row.values();
        boolean bad = false;

        String companyName = text(v, "公司名称");
        Long companyDeptId = null;
        if (StringUtils.isBlank(companyName)) {
            issues.add(error(no, "公司名称", companyName, "REQUIRED_MISSING", "公司名称不能为空"));
            bad = true;
        } else {
            companyDeptId = dirs.deptId(companyName);
            if (companyDeptId == null) {
                issues.add(error(no, "公司名称", companyName, "DEPT_NOT_FOUND",
                    "组织架构里找不到公司「" + companyName + "」"));
                bad = true;
            }
        }

        String planMonth = normalizeMonth(text(v, "计划月份"));
        if (planMonth == null) {
            issues.add(error(no, "计划月份", text(v, "计划月份"), "MONTH_INVALID",
                "计划月份格式应为 2026-08 或 2026-8"));
            bad = true;
        }

        String jobName = text(v, "岗位名称");
        if (StringUtils.isBlank(jobName)) {
            issues.add(error(no, "岗位名称", jobName, "REQUIRED_MISSING", "岗位名称不能为空"));
            bad = true;
        } else if (jobName.length() > 200) {
            issues.add(error(no, "岗位名称", jobName, "TOO_LONG", "岗位名称不能超过 200 字"));
            bad = true;
        }

        Integer planQty = parseInt(no, v, "计划人数", true, issues);
        if (planQty == null || planQty <= 0) {
            if (planQty != null) {
                issues.add(error(no, "计划人数", text(v, "计划人数"), "QTY_INVALID", "计划人数必须大于 0"));
            }
            bad = true;
        }

        Long useDeptId = null;
        String useDeptName = text(v, "用工部门名称");
        if (StringUtils.isNotBlank(useDeptName)) {
            useDeptId = dirs.deptId(useDeptName);
            if (useDeptId == null) {
                // 该字段本身是「名称快照」，匹配不到不影响数据完整性，给提示让人核对即可
                issues.add(warning(no, "用工部门名称", useDeptName, "DEPT_NOT_FOUND_WARN",
                    "组织架构里找不到部门「" + useDeptName + "」，将按文本保留该名称"));
            }
        }

        Integer standardDays = parseDays(no, v, "招聘期限标准天数", false, false, issues);
        String urgency = normalizeUrgency(no, text(v, "紧急程度"), issues);
        String carryover = normalizeCarryover(no, text(v, "是否允许结转"), issues);
        Long ownerId = resolveOwner(no, text(v, "任务负责人"), dirs, issues);
        String remark = limit(no, v, "备注", 500, issues);

        // 同 parseStandardRow：以本行是否产生 error 为准，避免「记了错还入库」
        if (bad || hasError(issues, no)) {
            errorRows.add(no);
            return null;
        }
        return new PlanRow(no, companyDeptId, companyName, planMonth, jobName, planQty,
            useDeptId, StringUtils.blankToDefault(useDeptName, null), standardDays, urgency,
            ownerId, carryover, remark);
    }

    /* ------------------------------------------------------------------ 落库 ------------------------------------------------------------------ */

    /**
     * 按类型导入合法行。
     *
     * @param type        导入类型
     * @param validation  校验结果
     * @param batchId     批次ID
     * @return 导入结果
     */
    @SuppressWarnings("unchecked")
    private ApplyResult apply(RecruitImportTypeEnum type, Validation validation, Long batchId) {
        List<Issue> issues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int success = 0;
        int failure = validation.errorRowCount();
        if (type == RecruitImportTypeEnum.STANDARD) {
            List<StandardRow> rows = (List<StandardRow>) validation.standardRows();
            int updated = 0;
            for (StandardRow row : rows) {
                try {
                    var exist = standardService.findByKey(row.jobName(), row.companyDeptId(), row.effectiveDate());
                    standardService.upsertByImport(toStandardBo(row));
                    success++;
                    if (exist != null) {
                        updated++;
                    }
                } catch (Exception e) {
                    failure++;
                    issues.add(error(row.rowNo(), null, null, "IMPORT_FAILED",
                        StringUtils.blankToDefault(e.getMessage(), "导入失败")));
                }
            }
            if (updated > 0) {
                messages.add("其中 " + updated + " 条为同岗位/同公司/同生效日期的已有标准，已按本次文件覆盖其天数");
            }
        } else {
            List<PlanRow> rows = (List<PlanRow>) validation.planRows();
            Map<String, IRecruitPlanService.PlanHeader> planCache = new LinkedHashMap<>();
            for (PlanRow row : rows) {
                try {
                    String cacheKey = row.companyDeptId() + "#" + row.planMonth();
                    IRecruitPlanService.PlanHeader header = planCache.computeIfAbsent(cacheKey,
                        k -> planService.findOrCreatePlan(row.companyDeptId(), row.companyName(), row.planMonth()));
                    planService.addImportedItem(header.planId(), toPlanItemBo(row), batchId);
                    success++;
                } catch (Exception e) {
                    failure++;
                    issues.add(error(row.rowNo(), null, null, "IMPORT_FAILED",
                        StringUtils.blankToDefault(e.getMessage(), "导入失败")));
                }
            }
            long created = planCache.values().stream().filter(IRecruitPlanService.PlanHeader::created).count();
            long reused = planCache.size() - created;
            // 分开报「新建」与「复用」：合并成一句话时用户无法判断这次导入有没有动表头结构
            messages.add("涉及 " + planCache.size() + " 个「公司 × 月份」的计划表头："
                + "新建 " + created + " 张，复用已有 " + reused + " 张");
            messages.add("共处理 " + rows.size() + " 行合法数据；按现行规则「相似任务不合并」，每行都会独立新增");
        }
        // 重放阶段新出现的问题落库，保证错误清单与最终结果一致
        saveIssues(batchId, null, issues);
        return new ApplyResult(success, failure, issues, messages);
    }

    /**
     * 行 → 标准入参。
     *
     * @param row 行
     * @return 入参
     */
    private RecruitStandardBo toStandardBo(StandardRow row) {
        RecruitStandardBo bo = new RecruitStandardBo();
        bo.setJobName(row.jobName());
        bo.setCompanyDeptId(row.companyDeptId());
        bo.setCompanyName(row.companyName());
        bo.setStandardDays(row.standardDays());
        bo.setEffectiveDate(row.effectiveDate());
        bo.setExpiryDate(row.expiryDate());
        bo.setStatus(row.status());
        bo.setRemark(row.remark());
        return bo;
    }

    /**
     * 行 → 计划任务入参。
     *
     * @param row 行
     * @return 入参
     */
    private RecruitPlanItemBo toPlanItemBo(PlanRow row) {
        RecruitPlanItemBo bo = new RecruitPlanItemBo();
        bo.setJobName(row.jobName());
        bo.setPlanQty(row.planQty());
        bo.setUseDeptId(row.useDeptId());
        bo.setUseDeptName(row.useDeptName());
        bo.setStandardDays(row.standardDays());
        bo.setUrgency(row.urgency());
        bo.setOwnerId(row.ownerId());
        bo.setCarryoverEnabled(row.carryoverEnabled());
        bo.setRemark(row.remark());
        return bo;
    }

    /* ------------------------------------------------------------------ 解析工具 ------------------------------------------------------------------ */

    /**
     * 取单元格文本。
     *
     * @param values 行数据
     * @param header 表头名
     * @return 文本（非 null）
     */
    private String text(Map<String, String> values, String header) {
        String value = values.get(header);
        return value == null ? "" : value.trim();
    }

    /**
     * 解析整数天数。
     *
     * @param rowNo    行号
     * @param values   行数据
     * @param header   表头名
     * @param required 是否必填
     * @param positive 是否必须为正（否则只要求非负）
     * @param issues   问题收集
     * @return 天数；不合法返回 null
     */
    private Integer parseDays(int rowNo, Map<String, String> values, String header,
                              boolean required, boolean positive, List<Issue> issues) {
        Integer value = parseInt(rowNo, values, header, required, issues);
        if (value == null) {
            return null;
        }
        int min = positive ? 1 : 0;
        if (value < min) {
            issues.add(error(rowNo, header, text(values, header), "DAYS_INVALID",
                header + "必须是不小于 " + min + " 的整数"));
            return null;
        }
        return value;
    }

    /**
     * 解析整数。
     *
     * @param rowNo    行号
     * @param values   行数据
     * @param header   表头名
     * @param required 是否必填
     * @param issues   问题收集
     * @return 数值；缺失且非必填返回 null 且不加问题
     */
    private Integer parseInt(int rowNo, Map<String, String> values, String header,
                             boolean required, List<Issue> issues) {
        String raw = text(values, header);
        if (StringUtils.isBlank(raw)) {
            if (required) {
                issues.add(error(rowNo, header, raw, "REQUIRED_MISSING", header + "不能为空"));
            }
            return null;
        }
        try {
            // Excel 里 30 可能被读成 "30.0"，先按小数解析再取整
            double d = Double.parseDouble(raw.replace(",", "").trim());
            if (d != Math.floor(d)) {
                issues.add(error(rowNo, header, raw, "NOT_INTEGER", header + "必须是整数"));
                return null;
            }
            return (int) d;
        } catch (NumberFormatException e) {
            issues.add(error(rowNo, header, raw, "NOT_NUMBER", header + "必须是数字"));
            return null;
        }
    }

    /**
     * 解析日期（容忍 {@code yyyy/M/d}、{@code yyyy.M.d}、{@code yyyyMMdd} 等写法）。
     *
     * @param rowNo  行号
     * @param values 行数据
     * @param header 表头名
     * @param issues 问题收集
     * @return 日期；空值返回 null 且不加问题
     */
    private LocalDate parseDate(int rowNo, Map<String, String> values, String header, List<Issue> issues) {
        String raw = text(values, header);
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String normalized = raw.replace('/', '-').replace('.', '-').replace("年", "-")
            .replace("月", "-").replace("日", "").trim();
        if (normalized.matches("\\d{8}")) {
            normalized = normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6);
        }
        String[] parts = normalized.split("-");
        try {
            if (parts.length == 3) {
                return LocalDate.of(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            }
        } catch (RuntimeException ignored) {
            // 落到下面的统一报错
        }
        issues.add(error(rowNo, header, raw, "DATE_INVALID", header + "格式应为 2026-08-01"));
        return null;
    }

    /**
     * 归一计划月份为 {@code yyyy-MM}。
     *
     * @param raw 原始值
     * @return 归一后的月份；不合法返回 null
     */
    private String normalizeMonth(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String s = raw.replace('/', '-').replace('.', '-').replace("年", "-")
            .replace("月", "").trim();
        if (s.matches("\\d{6}")) {
            s = s.substring(0, 4) + "-" + s.substring(4);
        }
        if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            // 用户可能把计划月份填成了具体日期，取年月即可
            s = s.substring(0, s.lastIndexOf('-'));
        }
        if (!s.matches("\\d{4}-\\d{1,2}")) {
            return null;
        }
        String[] parts = s.split("-");
        try {
            return YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 归一状态。
     *
     * @param rowNo  行号
     * @param raw    原始值
     * @param issues 问题收集
     * @return active / inactive；非法时回退 active 并记错误由调用方决定
     */
    private String normalizeStatus(int rowNo, String raw, List<Issue> issues) {
        if (StringUtils.isBlank(raw)) {
            return "active";
        }
        String value = raw.trim().toLowerCase();
        if (!"active".equals(value) && !"inactive".equals(value)) {
            issues.add(error(rowNo, "状态", raw, "STATUS_INVALID", "状态只能为 active 或 inactive"));
            return null;
        }
        return value;
    }

    /**
     * 归一紧急程度（接受中文或稳定编码）。
     *
     * @param rowNo  行号
     * @param raw    原始值
     * @param issues 问题收集
     * @return 稳定编码
     */
    private String normalizeUrgency(int rowNo, String raw, List<Issue> issues) {
        if (StringUtils.isBlank(raw)) {
            return "normal";
        }
        String value = raw.trim();
        String lower = value.toLowerCase();
        if ("normal".equals(lower) || "正常".equals(value)) {
            return "normal";
        }
        if ("urgent".equals(lower) || "紧急".equals(value)) {
            return "urgent";
        }
        if ("very_urgent".equals(lower) || "非常紧急".equals(value) || "特急".equals(value)) {
            return "very_urgent";
        }
        issues.add(warning(rowNo, "紧急程度", raw, "URGENCY_UNKNOWN",
            "无法识别的紧急程度「" + raw + "」，已按「正常」处理；可用值：正常/紧急/非常紧急"));
        return "normal";
    }

    /**
     * 归一结转开关（接受是/否与 1/0）。
     *
     * @param rowNo  行号
     * @param raw    原始值
     * @param issues 问题收集
     * @return 1 / 0
     */
    private String normalizeCarryover(int rowNo, String raw, List<Issue> issues) {
        if (StringUtils.isBlank(raw)) {
            return "1";
        }
        String value = raw.trim().toLowerCase();
        if (value.startsWith("是") || "1".equals(value) || "y".equals(value) || "true".equals(value)) {
            return "1";
        }
        if (value.startsWith("否") || "0".equals(value) || "n".equals(value) || "false".equals(value)) {
            return "0";
        }
        issues.add(warning(rowNo, "是否允许结转", raw, "CARRYOVER_UNKNOWN",
            "无法识别的取值「" + raw + "」，已按「是」处理"));
        return "1";
    }

    /**
     * 解析任务负责人（用户ID 或 账号/姓名）。
     *
     * @param rowNo  行号
     * @param raw    原始值
     * @param dirs   目录
     * @param issues 问题收集
     * @return 用户ID；解析不到返回 null
     */
    private Long resolveOwner(int rowNo, String raw, Directories dirs, List<Issue> issues) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("\\d{1,19}")) {
            return Long.parseLong(value);
        }
        Long userId = dirs.userId(value);
        if (userId == null) {
            issues.add(warning(rowNo, "任务负责人", value, "USER_NOT_FOUND",
                "匹配不到负责人「" + value + "」，将留空；可填用户账号或姓名，导入后在页面指派"));
        }
        return userId;
    }

    /**
     * 校验可选文本长度。
     *
     * @param rowNo  行号
     * @param values 行数据
     * @param header 表头名
     * @param max    最大长度
     * @param issues 问题收集
     * @return 截断后的文本
     */
    private String limit(int rowNo, Map<String, String> values, String header, int max, List<Issue> issues) {
        String raw = text(values, header);
        if (raw.length() > max) {
            issues.add(error(rowNo, header, raw, "TOO_LONG", header + "不能超过 " + max + " 字"));
            return null;
        }
        return StringUtils.blankToDefault(raw, null);
    }

    /**
     * 判断某行是否已产生 error 级问题。
     *
     * @param issues 问题清单
     * @param rowNo  行号
     * @return 有 error 返回 true
     */
    private boolean hasError(List<Issue> issues, int rowNo) {
        for (Issue issue : issues) {
            if (issue.rowNo() == rowNo && issue.severity() == RecruitImportSeverityEnum.ERROR) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造 error 级问题。
     *
     * @param rowNo   行号
     * @param field   字段
     * @param raw     原始值
     * @param code    编码
     * @param message 说明
     * @return 问题
     */
    private Issue error(int rowNo, String field, String raw, String code, String message) {
        return new Issue(rowNo, field, truncate(raw), code, RecruitImportSeverityEnum.ERROR, message);
    }

    /**
     * 构造 warning 级问题。
     *
     * @param rowNo   行号
     * @param field   字段
     * @param raw     原始值
     * @param code    编码
     * @param message 说明
     * @return 问题
     */
    private Issue warning(int rowNo, String field, String raw, String code, String message) {
        return new Issue(rowNo, field, truncate(raw), code, RecruitImportSeverityEnum.WARNING, message);
    }

    /**
     * 截断原始值，避免把整行内容写进错误表。
     *
     * @param raw 原始值
     * @return 截断后的值
     */
    private String truncate(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= 200 ? raw : raw.substring(0, 200);
    }

    /* ------------------------------------------------------------------ 批次与错误落库 ------------------------------------------------------------------ */

    /**
     * 落错误明细。
     *
     * @param batchId   批次ID
     * @param sheetName 工作表名
     * @param issues    问题清单
     */
    private void saveIssues(Long batchId, String sheetName, List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        for (Issue issue : issues) {
            RecruitImportError entity = new RecruitImportError();
            entity.setBatchId(batchId);
            entity.setSheetName(sheetName);
            entity.setRowNo(issue.rowNo());
            entity.setFieldName(issue.field());
            entity.setRawValue(issue.rawValue());
            entity.setErrorCode(issue.code());
            entity.setSeverity(issue.severity().getCode());
            entity.setErrorMessage(issue.message());
            errorMapper.insert(entity);
        }
    }

    /**
     * 问题 → 视图对象（限量）。
     *
     * @param issues 问题清单
     * @return 视图对象列表
     */
    private List<RecruitImportErrorVo> toIssueVos(List<Issue> issues) {
        List<RecruitImportErrorVo> list = new ArrayList<>();
        if (issues == null) {
            return list;
        }
        for (Issue issue : issues) {
            if (list.size() >= MAX_RETURNED_ISSUES) {
                break;
            }
            RecruitImportErrorVo vo = new RecruitImportErrorVo();
            vo.setRowNo(issue.rowNo());
            vo.setFieldName(issue.field());
            vo.setRawValue(issue.rawValue());
            vo.setErrorCode(issue.code());
            vo.setSeverity(issue.severity().getCode());
            vo.setErrorMessage(issue.message());
            list.add(vo);
        }
        return list;
    }

    /**
     * 取解析后的前若干行作为预览。
     *
     * @param sheet 工作表数据
     * @return 预览行
     */
    private List<Map<String, Object>> previewRows(RecruitImportExcelReader.SheetData sheet) {
        List<Map<String, Object>> preview = new ArrayList<>();
        for (RecruitImportExcelReader.RowData row : sheet.rows()) {
            if (preview.size() >= PREVIEW_LIMIT) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rowNo", row.rowNo());
            item.putAll(row.values());
            preview.add(item);
        }
        return preview;
    }

    /**
     * 模板列元信息（供前端在预检结果里回显口径）。
     *
     * @param type 导入类型
     * @return 列元信息
     */
    private List<Map<String, Object>> columnMeta(RecruitImportTypeEnum type) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ColumnDef column : columnsOf(type)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("header", column.header());
            item.put("required", column.required());
            item.put("hint", column.hint());
            list.add(item);
        }
        return list;
    }

    /**
     * 取导入类型的模板列。
     *
     * @param type 导入类型
     * @return 列定义
     */
    private List<ColumnDef> columnsOf(RecruitImportTypeEnum type) {
        return type == RecruitImportTypeEnum.PLAN ? PLAN_COLUMNS : STANDARD_COLUMNS;
    }

    /**
     * 加载批次。
     *
     * @param batchId 批次ID
     * @return 批次
     */
    private RecruitImportBatch loadBatch(Long batchId) {
        if (batchId == null) {
            throw new ServiceException("导入批次ID不能为空");
        }
        RecruitImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new ServiceException("导入批次不存在");
        }
        return batch;
    }

    /**
     * 回填派生字段。
     *
     * @param vo 批次视图对象
     */
    private void fillBatchDerived(RecruitImportBatchVo vo) {
        if (vo == null) {
            return;
        }
        RecruitImportTypeEnum type = RecruitImportTypeEnum.find(vo.getImportType());
        vo.setImportTypeName(type == null ? vo.getImportType() : type.getDesc());
        vo.setStatusLabel(statusLabel(vo.getStatus()));
    }

    /**
     * 批次状态标签。
     *
     * @param code 状态编码
     * @return 中文标签
     */
    private String statusLabel(String code) {
        RecruitImportBatchStatusEnum status = RecruitImportBatchStatusEnum.find(code);
        return status == null ? code : status.getDesc();
    }

    /**
     * 取扩展名。
     *
     * @param fileName 文件名
     * @return 小写扩展名
     */
    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 预检结论的一句话说明。
     *
     * @param validation 校验结果
     * @param errorRows  错误行数
     * @return 说明
     */
    private String buildPreviewMessage(Validation validation, int errorRows) {
        if (validation.total() == 0) {
            return "文件里没有可导入的数据行，请检查是否只填了表头";
        }
        if (errorRows == 0) {
            return "共解析 " + validation.total() + " 行，全部通过校验，可以确认导入";
        }
        return "共解析 " + validation.total() + " 行，其中 " + errorRows
            + " 行存在问题不会导入，其余 " + (validation.total() - errorRows)
            + " 行可正常导入。可修正文件后重新上传，或直接确认导入有效行。";
    }

    /* ------------------------------------------------------------------ 内部类型 ------------------------------------------------------------------ */

    /**
     * 一次校验的汇总。
     *
     * @param total        数据行总数
     * @param issues       问题清单
     * @param errorRows    存在 error 的行号集合
     * @param standardRows 标准行（仅 STANDARD 类型）
     * @param planRows     计划行（仅 PLAN 类型）
     */
    private record Validation(int total, List<Issue> issues, Set<Integer> errorRows,
                              List<StandardRow> standardRows, List<PlanRow> planRows) {

        /**
         * error 级问题的行数（同一行多条只算一行）。
         *
         * @return 行数
         */
        int errorRowCount() {
            return errorRows.size();
        }

        /**
         * warning 级问题条数。
         *
         * @return 条数
         */
        int warningCount() {
            int count = 0;
            for (Issue issue : issues) {
                if (issue.severity() == RecruitImportSeverityEnum.WARNING) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * 一次落库的汇总。
     *
     * @param success  成功行数
     * @param failure  失败行数
     * @param issues   失败明细
     * @param messages 附加说明
     */
    private record ApplyResult(int success, int failure, List<Issue> issues, List<String> messages) {
    }

    /**
     * 名称目录：把表格里的「公司名称 / 部门名称 / 负责人」解析成 ID。
     *
     * <p>部门目录一次性载入（组织架构规模有限）；用户目录<b>按需</b>载入——
     * 只有当某一行真的填了非数字的负责人时才去查用户列表，避免每次导入都扫全量用户。</p>
     */
    private static final class Directories {

        /**
         * 部门名称 → 部门ID
         */
        private final Map<String, Long> deptByName = new LinkedHashMap<>();

        /**
         * 公司/部门 ID 集合（用于按需载入用户目录）
         */
        private final List<Long> deptIds = new ArrayList<>();

        /**
         * 用户服务（按需查询）
         */
        private final UserService userService;

        /**
         * 用户账号/姓名 → 用户ID（懒加载）
         */
        private Map<String, Long> userByKey;

        private Directories(UserService userService) {
            this.userService = userService;
        }

        /**
         * 构建目录。
         *
         * @param deptService 部门服务
         * @param userService 用户服务（按需查用户时使用）
         * @return 目录
         */
        static Directories of(DeptService deptService, UserService userService) {
            Directories dirs = new Directories(userService);
            List<DeptDTO> depts = deptService.selectDeptsByList();
            if (depts != null) {
                for (DeptDTO dept : depts) {
                    if (dept == null || StringUtils.isBlank(dept.getDeptName()) || dept.getDeptId() == null) {
                        continue;
                    }
                    dirs.deptByName.putIfAbsent(dept.getDeptName().trim(), dept.getDeptId());
                    dirs.deptIds.add(dept.getDeptId());
                }
            }
            return dirs;
        }

        /**
         * 按名称取部门ID。
         *
         * @param name 部门/公司名称
         * @return 部门ID；找不到返回 null
         */
        Long deptId(String name) {
            return StringUtils.isBlank(name) ? null : deptByName.get(name.trim());
        }

        /**
         * 按账号或姓名取用户ID。
         *
         * @param key 账号或姓名
         * @return 用户ID；找不到返回 null
         */
        Long userId(String key) {
            if (userService == null || StringUtils.isBlank(key)) {
                return null;
            }
            if (userByKey == null) {
                userByKey = new LinkedHashMap<>();
                if (!deptIds.isEmpty()) {
                    List<UserDTO> users = userService.selectUsersByDeptIds(deptIds);
                    if (users != null) {
                        for (UserDTO user : users) {
                            if (user == null || user.getUserId() == null) {
                                continue;
                            }
                            if (StringUtils.isNotBlank(user.getUserName())) {
                                userByKey.putIfAbsent(user.getUserName().trim().toLowerCase(), user.getUserId());
                            }
                            if (StringUtils.isNotBlank(user.getNickName())) {
                                userByKey.putIfAbsent(user.getNickName().trim(), user.getUserId());
                            }
                        }
                    }
                }
            }
            String trimmed = key.trim();
            Long byName = userByKey.get(trimmed);
            if (byName != null) {
                return byName;
            }
            return userByKey.get(trimmed.toLowerCase());
        }
    }

}
