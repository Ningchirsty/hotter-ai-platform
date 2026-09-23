package org.dromara.hrtalent.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 招聘数据导入的 Excel 读取器（按<b>表头名</b>定位列）。
 *
 * <p><b>为什么按表头名而不是按列序</b>：用户会插列、调列序、删掉暂时不填的列。
 * 按列序读的话，插一列就会把整张表读串，而且错得毫无提示——「计划人数」列读到「岗位名称」，
 * 表现是几百行数字不合法的错误，用户根本猜不到原因。按表头名读，列序变了也不受影响，
 * 缺列则明确报「缺少必需列 X」。</p>
 *
 * <p><b>行号口径</b>：{@code rowNo} 用 Excel 里看到的 1 起行号（含表头行），
 * 这样用户在错误清单里看到「第 7 行」能直接定位到表格里的第 7 行。</p>
 *
 * <p><b>按内容而不是按位置定位表</b>：模板带了第二张「填写说明」表，用户也可能自己加一行大标题。
 * 早期实现固定读第 1 张表的第 1 个非空行，于是「填写说明」被排到前面、或数据表上方多了一行标题时，
 * 解析端读到的是「列名/是否必填/填写说明」这类表头，报出来的却是「缺少必需列 X、Y、Z」——
 * 用户盯着一个明明有这些列的文件，完全无从下手。现在改为<b>在所有工作表里找与目标列最匹配的表头行</b>，
 * 并在确实找不到时把「读了哪张表、看到什么表头」一并报出来，让报错本身自带诊断信息。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
public class RecruitImportExcelReader {

    /**
     * 单次导入允许的最大数据行数。
     * <p>导入是同步解析 + 逐行落库的操作，不设上限时一个几万行的文件会把请求拖死；
     * 超限时明确告知，让用户拆分文件，而不是让请求超时。</p>
     */
    public static final int MAX_DATA_ROWS = 2000;

    /**
     * 每张工作表向下找表头行时最多看多少行。
     * <p>容忍用户在最上方放标题、制表日期之类的说明行，但不去扫描整张表——
     * 扫得越深越容易把某一行数据误判成表头。</p>
     */
    private static final int HEADER_SCAN_LIMIT = 20;

    /**
     * 「填写说明」类工作表的特征词。命中时给出更具体的指引，
     * 而不是笼统地说「缺少必需列」。
     */
    private static final List<String> HELP_SHEET_MARKERS = List.of("列名", "是否必填", "填写说明");

    /**
     * 日期单元格的输出格式
     */
    private static final DateTimeFormatter DATE_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 一行数据（行号 + 表头 → 文本值）。
     *
     * @param rowNo  Excel 中的 1 起行号
     * @param values 表头名 → 单元格文本（已去空白；缺列不出现）
     */
    public record RowData(int rowNo, Map<String, String> values) {
    }

    /**
     * 一个工作表的数据。
     *
     * @param sheetName 工作表名
     * @param headers   表头（保持出现顺序）
     * @param rows      数据行（已跳过全空行）
     */
    public record SheetData(String sheetName, List<String> headers, List<RowData> rows) {
    }

    /**
     * 解析 Excel 字节。
     *
     * @param bytes           文件字节
     * @param expectedHeaders 该导入类型的全部列名（含选填），用于挑选最匹配的工作表与表头行
     * @param requiredHeaders 该导入类型的必填列名，缺一不可
     * @return 工作表数据
     * @throws ServiceException 文件不可解析、无工作表、找不到表头、缺少必需列或行数超限
     */
    public SheetData read(byte[] bytes, List<String> expectedHeaders, Set<String> requiredHeaders) {
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("导入文件内容为空");
        }
        if (requiredHeaders == null || requiredHeaders.isEmpty()) {
            throw new ServiceException("导入类型未定义必需列，请联系管理员");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ServiceException("导入文件里没有工作表");
            }
            DataFormatter formatter = new DataFormatter();

            // 在所有工作表里找「与目标列重合最多」的那一行表头。
            // 不按工作表顺序取第一个非空行：模板自带「填写说明」表，用户也常自己加大标题行。
            Candidate best = null;
            List<Candidate> all = new ArrayList<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                Candidate candidate = bestHeaderRow(s, sheet, formatter, expectedHeaders);
                all.add(candidate);
                if (candidate != null && (best == null || candidate.score() > best.score())) {
                    best = candidate;
                }
                if (best != null && best.score() >= expectedHeaders.size()) {
                    // 已经整列匹配，再找下去只会更差
                    break;
                }
            }

            if (best == null) {
                throw new ServiceException("导入文件里没有找到表头行（所有工作表的前 "
                    + HEADER_SCAN_LIMIT + " 行都是空的）");
            }

            List<String> headers = best.headers();
            // 重复列名只在「最终选定的表头行」上判定。
            // 不能在扫描每一行时就判：数据行里出现两个相同的值（比如人数与天数都是 3）太正常了。
            Set<String> seen = new LinkedHashSet<>();
            for (String header : headers) {
                if (!seen.add(header)) {
                    throw new ServiceException("第 " + (best.rowIndex() + 1) + " 行表头「" + header
                        + "」重复，请删掉重复列后重试");
                }
            }
            if (!headers.containsAll(requiredHeaders)) {
                throw new ServiceException(buildMissingColumnMessage(headers, requiredHeaders, all));
            }

            Sheet sheet = workbook.getSheetAt(best.sheetIndex());
            Row headerRow = sheet.getRow(best.rowIndex());
            int lastCell = headerRow.getLastCellNum();

            List<RowData> rows = new ArrayList<>();
            for (int r = best.rowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int c = 0; c < lastCell; c++) {
                    String header = headerAt(headerRow, formatter, c);
                    if (StringUtils.isBlank(header)) {
                        continue;
                    }
                    String text = cellText(row.getCell(c), formatter).trim();
                    values.put(header, text);
                    if (StringUtils.isNotBlank(text)) {
                        allBlank = false;
                    }
                }
                if (allBlank) {
                    continue;
                }
                if (rows.size() >= MAX_DATA_ROWS) {
                    throw new ServiceException("单次导入最多 " + MAX_DATA_ROWS
                        + " 行，当前文件超过该上限，请拆分后分批导入");
                }
                rows.add(new RowData(r + 1, values));
            }
            log.info("导入文件解析完成, sheet={}, headerRow={}, headerCount={}, dataRows={}",
                sheet.getSheetName(), best.rowIndex() + 1, headers.size(), rows.size());
            return new SheetData(sheet.getSheetName(), headers, rows);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("导入文件解析失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("无法解析该文件，请确认是 .xlsx / .xls 格式且未损坏");
        }
    }

    /**
     * 一张工作表的表头候选：表头行下标与解析出的表头。
     *
     * @param sheetIndex 工作表下标（0 起）
     * @param sheetName  工作表名
     * @param rowIndex   表头行下标（0 起）
     * @param headers    该行的非空单元格文本（保持列序）
     * @param score      与目标列的重合个数
     */
    private record Candidate(int sheetIndex, String sheetName, int rowIndex,
                             List<String> headers, int score) {
    }

    /**
     * 在一张工作表里找最像表头的那一行。
     *
     * @param sheetIndex      工作表下标（0 起；POI 5 已移除 {@code Sheet#getSheetIndex}，由调用方传入）
     * @param sheet           工作表
     * @param formatter       单元格格式化器
     * @param expectedHeaders 目标列名
     * @return 最佳候选；整张表前若干行全空时返回 null
     */
    private Candidate bestHeaderRow(int sheetIndex, Sheet sheet, DataFormatter formatter,
                                    List<String> expectedHeaders) {
        Candidate best = null;
        int last = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SCAN_LIMIT - 1);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<String> headers = new ArrayList<>();
            int lastCell = row.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                String header = cellText(row.getCell(c), formatter).trim();
                if (StringUtils.isBlank(header)) {
                    continue;
                }
                headers.add(header);
            }
            if (headers.isEmpty()) {
                continue;
            }
            int score = 0;
            for (String header : headers) {
                if (expectedHeaders.contains(header)) {
                    score++;
                }
            }
            if (best == null || score > best.score()) {
                best = new Candidate(sheetIndex, sheet.getSheetName(), r, headers, score);
            }
        }
        return best;
    }

    /**
     * 组装「缺少必需列」的报错。
     *
     * <p>关键在于把<b>实际读到了什么</b>一并说出来：只说「缺少 X」用户会盯着一个明明有 X 的文件发呆；
     * 说清「读的是哪张表、那张表的表头是什么」，用户一眼就能看出是填错了工作表还是多了一行标题。</p>
     *
     * @param headers         实际采用的表头
     * @param requiredHeaders 必填列名
     * @param candidates      各工作表的候选情况
     * @return 报错文案
     */
    private String buildMissingColumnMessage(List<String> headers, Set<String> requiredHeaders,
                                            List<Candidate> candidates) {
        List<String> missing = new ArrayList<>();
        for (String required : requiredHeaders) {
            if (!headers.contains(required)) {
                missing.add(required);
            }
        }
        StringBuilder sb = new StringBuilder("导入文件缺少必需列：").append(String.join("、", missing));

        // 命中「填写说明」工作表时给出直达结论的指引，而不是让用户自己去比对表头
        if (isHelpSheet(headers)) {
            sb.append("。检测到该文件解析到的是模板的「填写说明」工作表，"
                + "请把数据填在「数据」工作表后再上传，或直接用页面上的「下载模板」重新下载");
            return sb.toString();
        }

        sb.append("。实际解析到的表头：").append(String.join("、", headers));
        for (Candidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            sb.append("；工作表「").append(candidate.sheetName()).append("」第 ")
                .append(candidate.rowIndex() + 1).append(" 行：")
                .append(String.join("、", candidate.headers()));
        }
        sb.append("。请用页面上的「下载模板」重新填报");
        return sb.toString();
    }

    /**
     * 判断一组表头是否属于模板的「填写说明」工作表。
     *
     * <p>不能只比对说明表的原样表头：用户把说明表内容复制进新工作簿后，
     * 读到的行是「公司名称 / 必填 / 填写说明文本」这种形态，既不是原表头也不是数据行。
     * 但「必填 / 选填 / 填写说明…」这几列本身就是说明表的指纹——正常数据表里不会出现，
     * 所以按指纹识别，而不是要求整行等于说明表表头。</p>
     *
     * @param headers 表头
     * @return 是说明表返回 true
     */
    private boolean isHelpSheet(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String header : headers) {
            if (HELP_SHEET_MARKERS.contains(header)
                || "必填".equals(header) || "选填".equals(header)
                || header.startsWith("填写说明")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取某列的表头名。
     *
     * @param headerRow 表头行
     * @param formatter 格式化器
     * @param column    0 起列下标
     * @return 表头名；空列返回空串
     */
    private String headerAt(Row headerRow, DataFormatter formatter, int column) {
        return headerRow == null ? "" : cellText(headerRow.getCell(column), formatter).trim();
    }

    /**
     * 读取单元格文本。
     *
     * <p>日期型单元格统一输出 {@code yyyy-MM-dd}：默认格式化器会按单元格自身的显示格式输出，
     * 用户把日期列设成「2026年8月」时就会得到「2026年8月」，解析端反而难处理。</p>
     *
     * @param cell      单元格（可空）
     * @param formatter 格式化器
     * @return 文本（非 null）
     */
    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_OUT);
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? "" : text;
    }

}
