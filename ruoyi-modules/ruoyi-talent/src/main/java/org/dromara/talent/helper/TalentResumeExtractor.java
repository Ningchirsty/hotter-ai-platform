package org.dromara.talent.helper;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.domain.vo.ResumeFieldCandidateVo;
import org.dromara.talent.domain.vo.ResumeImportPreviewVo;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历本地抽取器：文本提取 + 规则抽取。
 * <p>
 * 合规与安全约定（SPEC §1 / §7）：
 * <ul>
 *     <li><b>全程进程内完成</b>：PDFBox / POI / 正则，不调用任何外部 AI、OCR 或 HTTP 服务；</li>
 *     <li>任何字段抽不到都只是「未识别」，<b>绝不</b>因单个字段失败抛异常或阻塞导入；</li>
 *     <li>PDF 提取失败（加密件、扫描件、损坏文件）一律吞掉异常返回空串；</li>
 *     <li>日志只记录文件名、字节数、抽取到的字段名与数量，<b>严禁</b>记录简历正文。</li>
 * </ul>
 *
 * @author talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentResumeExtractor {

    /**
     * 来源：文件名。
     */
    public static final String SOURCE_FILENAME = "FILENAME";

    /**
     * 来源：简历正文。
     */
    public static final String SOURCE_TEXT = "TEXT";

    /**
     * 来源：服务端缺省。
     */
    public static final String SOURCE_DEFAULT = "DEFAULT";

    /**
     * 无法提取文本的图片型简历扩展名。
     */
    private static final List<String> IMAGE_EXT = List.of("jpg", "jpeg", "png");

    /**
     * 暂不支持本地文本提取的旧版 Word 扩展名（HWPF 位于未引入的 poi-scratchpad，故仅做文件名解析）。
     */
    private static final List<String> UNSUPPORTED_TEXT_EXT = List.of("doc");

    /**
     * 旧版 .doc 不支持提取时的提示文案。
     */
    private static final String DOC_UNSUPPORTED_WARNING = "旧版 .doc 格式暂不支持文本提取，请另存为 .docx 或 PDF 后重试";

    /**
     * 图片型简历提示文案。
     */
    private static final String IMAGE_WARNING = "图片型简历无法提取文字，请手工补全字段";

    /**
     * 文件名尾部「城市 + 薪资区间」规则：group1=岗位段（下划线分隔）、group2=城市、group3/4=薪资上下限。
     * <p>
     * 样例：{@code 玩具_潮玩_快消品大区销售经理_深圳 8-13K} →
     * {@code 玩具_潮玩_快消品大区销售经理} / {@code 深圳} / {@code 8} / {@code 13}。
     */
    private static final Pattern FILENAME_TAIL = Pattern.compile("^(.*?)_?([^_\\s]*)\\s*(\\d+)\\s*-\\s*(\\d+)\\s*[Kk]\\s*$");

    /**
     * 文件名 {@code 【...】} 内部内容。
     */
    private static final Pattern FILENAME_BRACKET = Pattern.compile("【([^】]+)】");

    /**
     * 文件名中的姓名：{@code 】姓名 经验}。
     */
    private static final Pattern FILENAME_NAME = Pattern.compile("】\\s*([^\\s【】]+)");

    /**
     * 正文姓名：兼容「姓 名： X」式排版，且不吞掉紧随其后的「性别」首字。
     */
    private static final Pattern TEXT_NAME = Pattern.compile("姓\\s*名\\s*[:：]\\s*([^\\s性]+)");

    /**
     * 正文性别。
     */
    private static final Pattern TEXT_GENDER = Pattern.compile("性\\s*别\\s*[:：]\\s*(男|女)");

    /**
     * 正文学历。
     */
    private static final Pattern TEXT_EDUCATION = Pattern.compile("学\\s*历\\s*[:：]\\s*(\\S+)");

    /**
     * 正文出生年月。
     */
    private static final Pattern TEXT_BIRTH = Pattern.compile("出生年月\\s*[:：]\\s*(\\d{4})\\s*年\\s*(\\d{1,2})?\\s*月?");

    /**
     * 正文年龄（仅在无出生日期时使用）。
     */
    private static final Pattern TEXT_AGE = Pattern.compile("年\\s*龄\\s*[:：]\\s*(\\d{1,3})");

    /**
     * 正文手机号（前后不得紧邻数字）。
     */
    private static final Pattern TEXT_PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    /**
     * 正文邮箱（无捕获组，直接取 group 0）。
     */
    private static final Pattern TEXT_EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * 文件名经验：「10年以上」/「10 年经验」。
     */
    private static final Pattern FILENAME_EXPERIENCE = Pattern.compile("(\\d+\\s*年以上|\\d+\\s*年经验)");

    /**
     * 单值薪资：「8K」。
     */
    private static final Pattern FILENAME_SALARY_SINGLE = Pattern.compile("(?<![\\d-])(\\d+)\\s*[Kk]");

    /**
     * 形如 {@code %E8%AE%B8} 的 URL 编码片段，命中才尝试解码，避免误伤含 {@code +} 的原始文件名。
     */
    private static final Pattern URL_ENCODED = Pattern.compile("%[0-9a-fA-F]{2}");

    /**
     * 人才库配置（读取 import-max-text-length）。
     */
    private final TalentProperties talentProperties;

    /**
     * 从文件名 + 文件内容抽取候选字段。任何字段抽不到都返回空候选，不抛异常。
     *
     * @param originalFileName 原始文件名（可为 URL 编码）
     * @param bytes            文件字节内容
     * @param ext              扩展名（小写，无点）
     * @return 预览结果（含 importToken、候选字段与提示）
     */
    public ResumeImportPreviewVo extract(String originalFileName, byte[] bytes, String ext) {
        String fileName = decodeFileName(originalFileName);
        String suffix = StringUtils.isBlank(ext) ? extOf(fileName) : ext.toLowerCase(Locale.ROOT);
        String base = stripExtension(fileName);
        String inner = firstGroup(FILENAME_BRACKET, base);

        ResumeImportPreviewVo vo = new ResumeImportPreviewVo();
        vo.setImportToken(IdUtil.fastSimpleUUID());
        vo.setOriginalName(fileName);
        vo.setExt(suffix);
        vo.setSize(bytes == null ? 0L : (long) bytes.length);

        List<ResumeFieldCandidateVo> candidates = vo.getCandidates();
        List<String> warnings = vo.getWarnings();

        // 一、文件名解析（优先级最高，且不依赖文本提取成功）
        FilenameParts parts = parseFilename(base, inner);
        // 二、正文提取与解析
        String text = extractText(bytes, suffix);
        int maxTextLength = talentProperties.getImportMaxTextLength();
        if (maxTextLength > 0 && text.length() > maxTextLength) {
            text = text.substring(0, maxTextLength);
        }
        boolean textExtracted = StringUtils.isNotBlank(text);
        vo.setTextExtracted(textExtracted);
        vo.setTextLength(text.length());

        // 姓名：文件名 → 正文
        if (StringUtils.isNotBlank(parts.name)) {
            add(candidates, "name", "姓名", parts.name, 0.85, SOURCE_FILENAME, "来自文件名");
        } else {
            String name = firstGroup(TEXT_NAME, text);
            if (StringUtils.isNotBlank(name)) {
                add(candidates, "name", "姓名", name, 0.90, SOURCE_TEXT, "来自简历正文");
            }
        }

        // 性别：命中男/女给 1/2，否则按字典缺省 0
        String gender = "0";
        boolean genderFound = false;
        Matcher genderMatcher = TEXT_GENDER.matcher(text);
        if (genderMatcher.find()) {
            gender = "男".equals(genderMatcher.group(1)) ? "1" : "2";
            genderFound = true;
        }
        if (textExtracted) {
            add(candidates, "gender", "性别", gender, 0.95, SOURCE_TEXT,
                genderFound ? "来自简历正文" : "正文未识别到性别，默认未知，请核对");
        }

        // 学历
        String educationRaw = firstGroup(TEXT_EDUCATION, text);
        String education = mapEducation(educationRaw);
        if (StringUtils.isNotBlank(education)) {
            add(candidates, "education", "学历", education, 0.85, SOURCE_TEXT,
                "简历用词：" + StringUtils.substring(educationRaw, 0, 20));
        }

        // 出生日期 / 识别年龄
        Matcher birthMatcher = TEXT_BIRTH.matcher(text);
        String birthDate = null;
        if (birthMatcher.find()) {
            String year = birthMatcher.group(1);
            String month = birthMatcher.group(2);
            if (StringUtils.isNotBlank(month)) {
                birthDate = year + "-" + StringUtils.leftPad(month, 2, '0') + "-01";
                add(candidates, "birthDate", "出生日期", birthDate, 0.80, SOURCE_TEXT,
                    "按出生年月补全为当月 1 日，请核对");
            } else {
                birthDate = year;
                add(candidates, "birthDate", "出生日期", year, 0.80, SOURCE_TEXT,
                    "仅识别到年份，请补全月份");
                warnings.add("仅识别到出生年份（" + year + "），请补全出生月份");
            }
        }
        if (StringUtils.isBlank(birthDate)) {
            String age = firstGroup(TEXT_AGE, text);
            if (StringUtils.isNotBlank(age)) {
                add(candidates, "ageOnly", "识别年龄", age, 0.70, SOURCE_TEXT, "简历中仅识别到年龄，请补全出生日期");
            }
        }

        // 手机号
        String phone = firstGroup(TEXT_PHONE, text);
        if (StringUtils.isNotBlank(phone)) {
            add(candidates, "phone", "手机号", phone, 0.95, SOURCE_TEXT, "来自简历正文");
        }

        // 邮箱（非主档字段，仅预览 + 拼入备注）
        String email = firstGroup(TEXT_EMAIL, text);
        if (StringUtils.isNotBlank(email)) {
            add(candidates, "email", "邮箱", email, 0.95, SOURCE_TEXT, "非主档字段，确认后拼入备注");
        }

        // 岗位 / 区域 / 薪资 / 经验（均来自文件名）
        if (StringUtils.isNotBlank(parts.position)) {
            add(candidates, "position", "岗位", parts.position, 0.90, SOURCE_FILENAME, "来自文件名");
        }
        String region = regionByCity(parts.city);
        if (StringUtils.isNotBlank(region)) {
            add(candidates, "regionCode", "归属区域", region, 0.85, SOURCE_FILENAME, "根据文件名城市判定，请核对");
        } else {
            String textRegion = regionFromText(text);
            if (StringUtils.isNotBlank(textRegion)) {
                add(candidates, "regionCode", "归属区域", textRegion, 0.60, SOURCE_TEXT, "根据正文地址判定，请核对");
            } else {
                warnings.add("未能从文件名或正文判定归属区域，请手工选择");
            }
        }
        if (parts.salaryMin != null) {
            add(candidates, "expectSalaryMin", "期望薪资下限", String.valueOf(parts.salaryMin), 0.90, SOURCE_FILENAME, "来自文件名");
        }
        if (parts.salaryMax != null) {
            add(candidates, "expectSalaryMax", "期望薪资上限", String.valueOf(parts.salaryMax), 0.90, SOURCE_FILENAME, "来自文件名");
        }
        if (StringUtils.isNotBlank(parts.experience)) {
            add(candidates, "experienceText", "工作经验", parts.experience, 0.80, SOURCE_FILENAME,
                "非主档字段，确认后拼入备注");
        }

        // 联系日期：服务端缺省为导入当日
        add(candidates, "contactDate", "联系日期", LocalDate.now().toString(), null, SOURCE_DEFAULT, "服务端默认导入当日");

        if (IMAGE_EXT.contains(suffix)) {
            warnings.add(IMAGE_WARNING);
        } else if (UNSUPPORTED_TEXT_EXT.contains(suffix)) {
            warnings.add(DOC_UNSUPPORTED_WARNING);
        } else if (!textExtracted) {
            warnings.add("未能提取简历文字，仅完成文件名解析，请手工补全");
        }

        log.info("简历本地抽取完成, originalName={}, size={}, ext={}, textExtracted={}, textLength={}, fields={}",
            fileName, vo.getSize(), suffix, textExtracted, text.length(), fieldNames(candidates));
        return vo;
    }

    /**
     * 仅提取纯文本；不支持的类型或提取失败返回空串，不抛异常。
     * <p>
     * 支持：pdf（PDFBox 3.x）、docx（POI XWPF）、txt（UTF-8，失败按 GBK 兜底）。
     * 不支持：doc（HWPF 位于未引入的 poi-scratchpad）、jpg/jpeg/png 等图片型简历，返回空串。
     *
     * @param bytes 文件字节内容
     * @param ext   扩展名（小写，无点）
     * @return 纯文本，失败返回空串
     */
    public String extractText(byte[] bytes, String ext) {
        if (bytes == null || bytes.length == 0 || StringUtils.isBlank(ext)) {
            return "";
        }
        String suffix = ext.toLowerCase(Locale.ROOT);
        try {
            switch (suffix) {
                case "pdf":
                    return extractPdf(bytes);
                case "docx":
                    return extractDocx(bytes);
                case "txt":
                    return extractTxt(bytes);
                default:
                    // doc（HWPF 不可用）与 jpg/jpeg/png（无 OCR）均无法本地提取文字
                    return "";
            }
        } catch (Exception e) {
            // 加密 PDF / 扫描件 / 损坏文件：吞掉异常，只记异常类型，不记正文
            log.warn("简历文本提取失败, ext={}, size={}, exception={}", suffix, bytes.length, e.getClass().getSimpleName());
            return "";
        }
    }

    /**
     * 提取 PDF 文本（PDFBox 3.x：Loader.loadPDF）。
     *
     * @param bytes PDF 字节
     * @return 文本
     * @throws Exception 底层解析异常（由调用方统一吞掉）
     */
    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按位置排序，保证「姓 名：」「性 别：」等同一行的相对顺序稳定
            stripper.setSortByPosition(true);
            return StringUtils.defaultString(stripper.getText(document));
        }
    }

    /**
     * 提取 DOCX 文本（OOXML）。
     *
     * @param bytes DOCX 字节
     * @return 文本
     * @throws Exception 底层解析异常（由调用方统一吞掉）
     */
    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return StringUtils.defaultString(extractor.getText());
        }
    }

    /**
     * 提取纯文本文件内容：优先 UTF-8，解码出现替换字符时按 GBK 兜底。
     *
     * @param bytes 文件字节
     * @return 文本
     */
    private String extractTxt(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') < 0) {
            return utf8;
        }
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception e) {
            return utf8;
        }
    }

    /**
     * 解析文件名中的岗位 / 城市 / 薪资 / 经验。
     * <p>
     * 岗位为下划线连接的多个段，尾部可能紧跟「城市 薪资K」，故先按已验证规则剥离尾部，
     * 再把剩余段的下划线还原为 {@code /}。
     *
     * @param base  去扩展名的文件名
     * @param inner {@code 【...】} 内部内容（可为空）
     * @return 文件名解析结果
     */
    private FilenameParts parseFilename(String base, String inner) {
        FilenameParts parts = new FilenameParts();
        String scope = StringUtils.isNotBlank(inner) ? inner : base;

        Matcher tail = FILENAME_TAIL.matcher(scope);
        if (tail.matches()) {
            parts.position = joinSegments(tail.group(1));
            parts.city = StringUtils.trimToNull(tail.group(2));
            parts.salaryMin = multiplyK(tail.group(3));
            parts.salaryMax = multiplyK(tail.group(4));
        } else {
            // 兜底：无薪资后缀时按 _ 切分，末段是城市名则作为 city
            String[] segments = scope.split("_");
            String last = StringUtils.trimToNull(segments[segments.length - 1]);
            if (segments.length > 1 && StringUtils.isNotBlank(regionByCity(last))) {
                parts.city = last;
                parts.position = joinSegments(scope.substring(0, scope.lastIndexOf('_')));
            } else {
                parts.position = joinSegments(scope);
            }
            Matcher single = FILENAME_SALARY_SINGLE.matcher(scope);
            if (single.find()) {
                parts.salaryMin = multiplyK(single.group(1));
            }
        }
        parts.experience = StringUtils.trimToNull(firstGroup(FILENAME_EXPERIENCE, base));
        parts.name = StringUtils.trimToNull(firstGroup(FILENAME_NAME, base));
        if (StringUtils.isBlank(parts.city)) {
            parts.city = cityIn(scope);
        }
        return parts;
    }

    /**
     * 下划线分段还原为 {@code /}，并清理空段。
     *
     * @param raw 原始片段
     * @return 岗位文本，无有效段返回 null
     */
    private String joinSegments(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String item : raw.split("_")) {
            String segment = item.trim();
            if (StringUtils.isNotBlank(segment)) {
                segments.add(segment);
            }
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    /**
     * 学历用词 → 字典码。
     *
     * @param raw 简历中的学历原文
     * @return 字典码，无法识别返回 null
     */
    private String mapEducation(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (raw.contains("博士")) {
            return "DOCTOR";
        }
        if (raw.contains("硕士") || raw.contains("研究生") || raw.toUpperCase(Locale.ROOT).contains("MBA")) {
            return "MASTER";
        }
        if (raw.contains("本科") || raw.contains("学士")) {
            return "BACHELOR";
        }
        if (raw.contains("大专") || raw.contains("专科") || raw.contains("高职")) {
            return "COLLEGE";
        }
        if (raw.contains("高中") || raw.contains("中专") || raw.contains("技校") || raw.contains("职高")) {
            return "HIGH_SCHOOL";
        }
        // 可识别但不匹配字典者归入 OTHER
        return "OTHER";
    }

    /**
     * 城市名 → 区域编码。
     *
     * @param city 城市（可为 null）
     * @return 区域编码，无法判定返回 null
     */
    private String regionByCity(String city) {
        if (StringUtils.isBlank(city)) {
            return null;
        }
        if (city.contains("深圳")) {
            return "SZ";
        }
        if (city.contains("汕头")) {
            return "ST";
        }
        return null;
    }

    /**
     * 从正文地址判定区域（置信度更低，仅作兜底）。
     *
     * @param text 简历正文
     * @return 区域编码，无法判定返回 null
     */
    private String regionFromText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String address = firstGroup(Pattern.compile("(?:通\\s*讯\\s*地\\s*址|现\\s*居\\s*住\\s*地|所\\s*在\\s*地|地\\s*址)\\s*[:：]\\s*(\\S{2,60})"), text);
        String scope = StringUtils.isNotBlank(address) ? address : text;
        return regionByCity(cityIn(scope));
    }

    /**
     * 文本中出现的城市名（深圳优先于汕头）。
     *
     * @param scope 待检索文本
     * @return 城市名，未命中返回 null
     */
    private String cityIn(String scope) {
        if (StringUtils.isBlank(scope)) {
            return null;
        }
        if (scope.contains("深圳")) {
            return "深圳";
        }
        if (scope.contains("汕头")) {
            return "汕头";
        }
        return null;
    }

    /**
     * 薪资字符串（单位为 K）转整数元。
     *
     * @param value 数字字符串
     * @return 金额（元），非法返回 null
     */
    private Integer multiplyK(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim()) * 1000;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 追加候选字段（值为空则跳过）。
     *
     * @param candidates 候选清单
     * @param field      字段名
     * @param label      中文名
     * @param value      候选值
     * @param confidence 置信度
     * @param source     来源
     * @param hint       说明
     */
    private void add(List<ResumeFieldCandidateVo> candidates, String field, String label, String value,
                     Double confidence, String source, String hint) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        ResumeFieldCandidateVo candidate = new ResumeFieldCandidateVo();
        candidate.setField(field);
        candidate.setLabel(label);
        candidate.setValue(value);
        candidate.setConfidence(confidence);
        candidate.setSource(source);
        candidate.setHint(hint);
        candidates.add(candidate);
    }

    /**
     * 文件名容错解码：仅当出现 {@code %XX} 编码片段时尝试 {@link URLDecoder}，失败则用原值。
     * <p>
     * 刻意不做无条件解码，避免把原始文件名中的 {@code +} 误转成空格。
     *
     * @param originalFileName 原始文件名
     * @return 解码后的文件名
     */
    private String decodeFileName(String originalFileName) {
        String name = StringUtils.defaultString(originalFileName);
        if (!URL_ENCODED.matcher(name).find()) {
            return name;
        }
        try {
            return URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("简历文件名 URL 解码失败, 使用原值");
            return name;
        }
    }

    /**
     * 去扩展名。
     *
     * @param fileName 文件名
     * @return 去扩展名的文件名
     */
    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * 取扩展名（小写、无点）。
     *
     * @param fileName 文件名
     * @return 扩展名，无扩展名返回空串
     */
    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 取正则首个匹配结果：有捕获组取 group 1，无捕获组的表达式（如邮箱）回退到 group 0。
     *
     * @param pattern 正则
     * @param source  文本
     * @return 匹配内容，未命中返回 null
     */
    private String firstGroup(Pattern pattern, String source) {
        if (StringUtils.isBlank(source)) {
            return null;
        }
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group(0);
    }

    /**
     * 抽取到的字段名清单（仅字段名，不含值，用于日志）。
     *
     * @param candidates 候选清单
     * @return 字段名列表
     */
    private List<String> fieldNames(List<ResumeFieldCandidateVo> candidates) {
        List<String> names = new ArrayList<>(candidates.size());
        for (ResumeFieldCandidateVo candidate : candidates) {
            names.add(candidate.getField());
        }
        return names;
    }

    /**
     * 文件名解析结果（内部结构）。
     */
    private static final class FilenameParts {

        /**
         * 姓名。
         */
        private String name;

        /**
         * 岗位（下划线已还原为 /）。
         */
        private String position;

        /**
         * 城市。
         */
        private String city;

        /**
         * 期望薪资下限（元/月）。
         */
        private Integer salaryMin;

        /**
         * 期望薪资上限（元/月）。
         */
        private Integer salaryMax;

        /**
         * 工作经验原文。
         */
        private String experience;
    }

}
