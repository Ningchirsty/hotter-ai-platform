package org.dromara.hrtalent.helper;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入 Excel 读取器单元测试。
 *
 * <p><b>这个测试针对的是一类真实事故</b>：早期实现固定读「第 1 张工作表的第 1 个非空行」当作表头。
 * 于是下面三种再普通不过的用法都会失败，而且报的是「缺少必需列 公司名称、计划月份、岗位名称、计划人数」——
 * 用户盯着一个明明写着这些列的文件，完全不知道发生了什么：</p>
 * <ol>
 *   <li>模板自带的「填写说明」表被排到第一个工作表（用户在 Excel 里拖动过标签）；</li>
 *   <li>用户在数据表最上方加了一行大标题；</li>
 *   <li>用户以为「填写说明」就是模板，直接照它填。</li>
 * </ol>
 * <p>现在读取器按<b>内容</b>选表：在所有工作表里找与目标列最重合的一行当表头；
 * 真找不到时，报错必须带上「读到的是哪张表、表头是什么」，让用户能自己判断问题在哪。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitImportExcelReaderTest {

    /**
     * 计划导入的全部列（与 RecruitImportServiceImpl.PLAN_COLUMNS 保持一致）。
     */
    private static final List<String> PLAN_HEADERS = List.of(
        "公司名称", "计划月份", "岗位名称", "计划人数", "用工部门名称",
        "招聘期限标准天数", "紧急程度", "任务负责人", "是否允许结转", "备注");

    /**
     * 计划导入的必填列。
     */
    private static final Set<String> PLAN_REQUIRED = Set.of("公司名称", "计划月份", "岗位名称", "计划人数");

    /**
     * 模板「填写说明」表的表头（取自 RecruitImportServiceImpl#writeTemplate）。
     */
    private static final List<String> HELP_HEADERS = List.of("列名", "是否必填", "填写说明");

    /**
     * 被测读取器。
     */
    private final RecruitImportExcelReader reader = new RecruitImportExcelReader();

    /**
     * 写一行单元格。
     *
     * @param sheet 工作表
     * @param rowNo 0 起行号
     * @param cells 单元格文本
     */
    private void writeRow(Sheet sheet, int rowNo, Object... cells) {
        Row row = sheet.createRow(rowNo);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) {
                continue;
            }
            row.createCell(i).setCellValue(String.valueOf(cells[i]));
        }
    }

    /**
     * 按「数据表 + 填写说明表」的形态生成模板字节。
     *
     * @param titleRow        数据表上方是否加一行标题
     * @param helpFirst       是否把「填写说明」排成第一个工作表
     * @param dataHeader      数据表表头（null 表示用标准表头）
     * @param includeDataRows 是否写数据行
     * @return xlsx 字节
     */
    private byte[] template(boolean titleRow, boolean helpFirst, List<String> dataHeader,
                            boolean includeDataRows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<String> headers = dataHeader == null ? PLAN_HEADERS : dataHeader;
            Sheet data = wb.createSheet("数据");
            int r = 0;
            if (titleRow) {
                writeRow(data, r++, "子公司月度招聘计划表");
            }
            writeRow(data, r, headers.toArray());
            int headerRowNo = r;
            if (includeDataRows) {
                writeRow(data, headerRowNo + 1, "深圳总公司", "2026-08", "前端开发工程师", "3");
                writeRow(data, headerRowNo + 2, "长沙分公司", "2026-09", "市场专员", "2");
            }
            Sheet help = wb.createSheet("填写说明");
            writeRow(help, 0, "【数据请填在「数据」工作表】本表只是列说明，不要在本表填数据");
            writeRow(help, 1, HELP_HEADERS.toArray());
            for (int i = 0; i < headers.size(); i++) {
                writeRow(help, i + 2, headers.get(i), "必填", "填写说明文本");
            }
            if (helpFirst) {
                wb.setSheetOrder("填写说明", 0);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 生成「用户以为填写说明就是模板、把它另存成了一个独立工作簿」的字节。
     * <p>这种文件里根本没有数据表，只有说明表的内容。</p>
     *
     * @return xlsx 字节
     */
    private byte[] helpCopiedWorkbook() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet help = wb.createSheet("Sheet1");
            writeRow(help, 0, HELP_HEADERS.toArray());
            for (int i = 0; i < PLAN_HEADERS.size(); i++) {
                writeRow(help, i + 1, PLAN_HEADERS.get(i), "必填", "填写说明文本");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 调用读取器。
     *
     * @param bytes xlsx 字节
     * @return 解析结果
     */
    private RecruitImportExcelReader.SheetData read(byte[] bytes) {
        return reader.read(bytes, PLAN_HEADERS, PLAN_REQUIRED);
    }

    @Test
    @DisplayName("标准模板：读到「数据」表、表头与行号都正确")
    void shouldReadStandardTemplate() {
        RecruitImportExcelReader.SheetData sheet = read(template(false, false, null, true));
        assertEquals("数据", sheet.sheetName());
        assertEquals(PLAN_HEADERS, sheet.headers());
        assertEquals(2, sheet.rows().size());
        // 行号口径：Excel 里看到的 1 起行号（表头在第 1 行，数据从第 2 行开始）
        assertEquals(2, sheet.rows().get(0).rowNo());
        assertEquals("深圳总公司", sheet.rows().get(0).values().get("公司名称"));
        assertEquals("3", sheet.rows().get(0).values().get("计划人数"));
    }

    @Test
    @DisplayName("「填写说明」被排到第一个工作表时，仍应找到「数据」表")
    void shouldStillFindDataSheetWhenHelpSheetIsFirst() {
        RecruitImportExcelReader.SheetData sheet = read(template(false, true, null, true));
        assertEquals("数据", sheet.sheetName(),
            "说明表排在前面时不能把说明表当数据表，否则会误报「缺少必需列」");
        assertEquals(PLAN_HEADERS, sheet.headers());
        assertEquals(2, sheet.rows().size());
    }

    @Test
    @DisplayName("数据表上方多一行标题时，应跳过标题定位到真正的表头行")
    void shouldSkipTitleRow() {
        RecruitImportExcelReader.SheetData sheet = read(template(true, false, null, true));
        assertEquals("数据", sheet.sheetName());
        assertEquals(PLAN_HEADERS, sheet.headers(), "标题行不能被当成表头");
        assertEquals(2, sheet.rows().size());
        // 标题占第 1 行、表头占第 2 行 → 数据从第 3 行开始，行号必须与 Excel 里看到的一致
        assertEquals(3, sheet.rows().get(0).rowNo());
        assertEquals(4, sheet.rows().get(1).rowNo());
    }

    @Test
    @DisplayName("只有表头没有数据行时返回 0 行，而不是报「缺少必需列」")
    void shouldReturnEmptyRowsForHeaderOnlyTemplate() {
        RecruitImportExcelReader.SheetData sheet = read(template(false, false, null, false));
        assertEquals("数据", sheet.sheetName());
        assertEquals(0, sheet.rows().size());
    }

    @Test
    @DisplayName("数据行里出现重复取值时不能被误判成重复表头")
    void shouldNotTreatDuplicateDataValuesAsDuplicateHeaders() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("数据");
            writeRow(data, 0, PLAN_HEADERS.toArray());
            // 「计划人数」与「招聘期限标准天数」都填 3：数据行里同值很常见
            writeRow(data, 1, "深圳总公司", "2026-08", "前端开发工程师", "3", "研发部门", "3");
            wb.write(out);
            RecruitImportExcelReader.SheetData sheet = read(out.toByteArray());
            assertEquals(1, sheet.rows().size());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("误把「填写说明」当模板时，报错要直接点明问题和工作表归属")
    void shouldTellUserTheFileIsTheHelpSheet() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> read(helpCopiedWorkbook()));
        assertTrue(ex.getMessage().contains("填写说明"),
            "报错应点明解析到的是「填写说明」表，而不是只罗列缺少哪些列。实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("数据"),
            "报错应指引用户把数据填到「数据」表。实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("确实缺列时，报错要带上实际读到的表头，便于用户自查")
    void shouldReportActualHeadersWhenColumnMissing() {
        // 删掉「计划人数」列
        List<String> broken = PLAN_HEADERS.stream().filter(h -> !"计划人数".equals(h)).toList();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> read(template(false, false, broken, true)));
        assertTrue(ex.getMessage().contains("计划人数"),
            "应指出缺少的列名。实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("实际解析到的表头"),
            "应带上实际读到的表头，否则用户无从下手。实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("公司名称"),
            "实际表头内容应出现在报错里。实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("空文件与非 Excel 内容要给出各自明确的原因")
    void shouldRejectEmptyAndGarbage() {
        assertTrue(assertThrows(ServiceException.class, () -> read(new byte[0]))
            .getMessage().contains("内容为空"));
        assertTrue(assertThrows(ServiceException.class, () -> read("not an excel".getBytes()))
            .getMessage().contains("无法解析"));
    }

}
