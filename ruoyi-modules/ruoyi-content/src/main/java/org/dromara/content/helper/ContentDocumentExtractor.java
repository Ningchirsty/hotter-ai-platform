package org.dromara.content.helper;

import org.dromara.content.enums.ContentFileKindEnum;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 本地文档抽取器：把资料文件读成「标签-值对」。
 *
 * <p>覆盖范围与设计文档一致：Excel（xls/xlsx）、Word（docx）、PDF 文本层。
 * <b>不处理</b>图片（需 OCR）与旧版 {@code .doc}（需 poi-scratchpad），
 * 二者返回 {@code extracted=false} 并带上可读原因——「明确告知跳过」比
 * 「悄悄返回空」更符合设计文档 §1 的交互原则。</p>
 *
 * <p><b>全程进程内完成</b>，不访问任何网络。调用方经治理层的
 * {@code document_parse} 能力进来，因此每次抽取都会留下审计。</p>
 *
 * @author content
 */
@Component
public class ContentDocumentExtractor {

    /**
     * 单元格/段落内的连续空白归一为单个空格
     */
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    /**
     * 判断该类型是否支持本地抽取（与 {@link ContentFileKindEnum#parseable()} 呼应）。
     *
     * @param ext 扩展名
     * @return 是否支持
     */
    public boolean supports(String ext) {
        ContentFileKindEnum kind = ContentFileKindEnum.ofExt(ext);
        if (kind == ContentFileKindEnum.IMAGE) {
            return false;
        }
        if (kind == ContentFileKindEnum.WORD) {
            // 仅 docx；doc 需 poi-scratchpad
            return "docx".equalsIgnoreCase(normalizeExt(ext));
        }
        return kind.isParseable();
    }

    /**
     * 抽取文档为标签-值对。
     *
     * @param bytes 文件字节
     * @param ext   扩展名
     * @return 抽取结果（失败/跳过时 extracted=false 且带原因，不抛异常）
     */
    public ExtractedDocument extract(byte[] bytes, String ext) {
        ExtractedDocument doc = new ExtractedDocument();
        if (bytes == null || bytes.length == 0) {
            doc.setSkipReason("文件内容为空");
            return doc;
        }
        String e = normalizeExt(ext);
        try {
            switch (e) {
                case "xlsx", "xls" -> extractExcel(bytes, doc);
                case "docx" -> extractDocx(bytes, doc);
                case "pdf" -> extractPdf(bytes, doc);
                case "doc" -> doc.setSkipReason("旧版 .doc 格式暂不支持本地解析，请另存为 .docx 或 PDF 后重试");
                case "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff" ->
                    doc.setSkipReason("图片型资料需要 OCR，当前版本不做文字提取，仅归档");
                case "txt", "csv" -> extractText(bytes, doc);
                default -> doc.setSkipReason("暂不支持解析该类型：" + (StringUtils.isBlank(e) ? "未知扩展名" : e));
            }
        } catch (Exception ex) {
            // 加密、损坏、格式不符等一律转成可读原因，绝不抛给接口层
            doc.setExtracted(false);
            doc.setSkipReason("解析失败（" + ex.getClass().getSimpleName() + "），请确认文件未加密且未损坏");
            doc.getPairs().clear();
        }
        return doc;
    }

    /**
     * 抽取 Excel：逐行读取，把「标签格 + 相邻值格」配成一对；
     * 同一格内写成「标签：值」的也直接拆开。
     *
     * @param bytes 字节
     * @param doc   结果
     * @throws Exception 底层异常
     */
    private void extractExcel(byte[] bytes, ExtractedDocument doc) throws Exception {
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(normalize(fmt.formatCellValue(cell)));
                    }
                    // 丢掉整行空白
                    if (cells.stream().allMatch(String::isBlank)) {
                        continue;
                    }
                    String locator = sheet.getSheetName() + " 第" + (row.getRowNum() + 1) + "行";
                    text.append(String.join(" | ", cells)).append('\n');
                    appendRowPairs(doc, cells, locator);
                }
            }
        }
        doc.setText(text.toString());
        doc.setExtracted(true);
    }

    /**
     * 把一行单元格配成标签-值对。
     * <p>规则与简历表格解析同口径：不带冒号的裸标签与相邻的裸值配对，
     * 已成「标签：值」的单元格单独成对。</p>
     *
     * @param doc     结果
     * @param cells   已归一的单元格文本
     * @param locator 定位
     */
    private void appendRowPairs(ExtractedDocument doc, List<String> cells, String locator) {
        // 先处理「单格内含冒号」的形式
        List<String> bare = new ArrayList<>();
        for (String c : cells) {
            if (c.isBlank()) {
                continue;
            }
            int idx = firstColon(c);
            if (idx > 0 && idx < c.length() - 1) {
                doc.addPair(c.substring(0, idx), c.substring(idx + 1), locator);
            } else {
                bare.add(c);
            }
        }
        int i = 0;
        while (i < bare.size()) {
            if (i + 1 < bare.size()) {
                doc.addPair(bare.get(i), bare.get(i + 1), locator);
                i += 2;
            } else {
                i += 1;
            }
        }
    }

    /**
     * 抽取 DOCX：按文档结构遍历（段落 + 表格）。
     * <p>与简历解析同样不使用整篇 {@code XWPFWordExtractor.getText()}——
     * 它把表格单元格用制表符拼平，标签与值分列时无法配对。</p>
     *
     * @param bytes 字节
     * @param doc   结果
     * @throws Exception 底层异常
     */
    private void extractDocx(byte[] bytes, ExtractedDocument doc) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            int tableIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String line = normalize(paragraph.getText());
                    if (line.isBlank()) {
                        continue;
                    }
                    text.append(line).append('\n');
                    int idx = firstColon(line);
                    if (idx > 0 && idx < line.length() - 1) {
                        doc.addPair(line.substring(0, idx), line.substring(idx + 1), "正文");
                    }
                } else if (element instanceof XWPFTable table) {
                    tableIndex++;
                    int rowNo = 0;
                    for (XWPFTableRow row : table.getRows()) {
                        rowNo++;
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(normalize(cell.getText()));
                        }
                        if (cells.stream().allMatch(String::isBlank)) {
                            continue;
                        }
                        text.append(String.join(" | ", cells)).append('\n');
                        appendRowPairs(doc, cells, "表格" + tableIndex + " 第" + rowNo + "行");
                    }
                }
            }
        }
        doc.setText(text.toString());
        doc.setExtracted(true);
    }

    /**
     * 抽取 PDF 文本层。
     *
     * @param bytes 字节
     * @param doc   结果
     * @throws Exception 底层异常
     */
    private void extractPdf(byte[] bytes, ExtractedDocument doc) throws Exception {
        StringBuilder text = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String raw = StringUtils.defaultString(stripper.getText(document));
            int lineNo = 0;
            for (String line : raw.split("\\r?\\n")) {
                lineNo++;
                String norm = normalize(line);
                if (norm.isBlank()) {
                    continue;
                }
                text.append(norm).append('\n');
                int idx = firstColon(norm);
                if (idx > 0 && idx < norm.length() - 1) {
                    doc.addPair(norm.substring(0, idx), norm.substring(idx + 1), "PDF 第" + lineNo + "行");
                }
            }
        }
        doc.setText(text.toString());
        doc.setExtracted(true);
    }

    /**
     * 抽取纯文本。
     *
     * @param bytes 字节
     * @param doc   结果
     */
    private void extractText(byte[] bytes, ExtractedDocument doc) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            utf8 = new String(bytes, Charset.forName("GBK"));
        }
        int lineNo = 0;
        StringBuilder text = new StringBuilder();
        for (String line : utf8.split("\\r?\\n")) {
            lineNo++;
            String norm = normalize(line);
            if (norm.isBlank()) {
                continue;
            }
            text.append(norm).append('\n');
            int idx = firstColon(norm);
            if (idx > 0 && idx < norm.length() - 1) {
                doc.addPair(norm.substring(0, idx), norm.substring(idx + 1), "第" + lineNo + "行");
            }
        }
        doc.setText(text.toString());
        doc.setExtracted(true);
    }

    /**
     * 归一：连续空白压成单空格并去首尾。
     *
     * @param text 原文
     * @return 归一文本
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /**
     * 取中英文冒号中较靠前的位置。
     *
     * @param text 文本
     * @return 下标，无冒号返回 -1
     */
    private int firstColon(String text) {
        int a = text.indexOf('：');
        int b = text.indexOf(':');
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * 归一扩展名。
     *
     * @param ext 扩展名
     * @return 小写、无点
     */
    private String normalizeExt(String ext) {
        if (ext == null) {
            return "";
        }
        String e = ext.trim().toLowerCase(Locale.ROOT);
        return e.startsWith(".") ? e.substring(1) : e;
    }

}
