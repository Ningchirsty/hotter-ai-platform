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
 * <p><b>只读第一个工作表</b>：模板的第二个表是「填写说明」，不参与解析。</p>
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
     * @param bytes 文件字节
     * @return 工作表数据
     * @throws ServiceException 文件不可解析、无工作表、无表头或行数超限
     */
    public SheetData read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("导入文件内容为空");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ServiceException("导入文件里没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int headerRowIndex = findHeaderRow(sheet, formatter);
            if (headerRowIndex < 0) {
                throw new ServiceException("导入文件里没有找到表头行（第一行不能为空）");
            }
            Row headerRow = sheet.getRow(headerRowIndex);
            List<String> headers = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            int lastCell = headerRow.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                String header = cellText(headerRow.getCell(c), formatter).trim();
                if (StringUtils.isBlank(header)) {
                    continue;
                }
                if (!seen.add(header)) {
                    throw new ServiceException("表头「" + header + "」重复，请删掉重复列后重试");
                }
                headers.add(header);
            }
            if (headers.isEmpty()) {
                throw new ServiceException("导入文件里没有找到任何表头");
            }

            List<RowData> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
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
                sheet.getSheetName(), headerRowIndex + 1, headers.size(), rows.size());
            return new SheetData(sheet.getSheetName(), headers, rows);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("导入文件解析失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("无法解析该文件，请确认是 .xlsx / .xls 格式且未损坏");
        }
    }

    /**
     * 找到表头行：第一个存在非空单元格的行。
     *
     * @param sheet     工作表
     * @param formatter 单元格格式化器
     * @return 0 起的行下标；找不到返回 -1
     */
    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.getLastCellNum(); c++) {
                if (StringUtils.isNotBlank(cellText(row.getCell(c), formatter))) {
                    return r;
                }
            }
        }
        return -1;
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
