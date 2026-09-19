package org.dromara.talent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlParseField;
import org.dromara.talent.domain.TlParseTask;
import org.dromara.talent.domain.bo.ResumeImportConfirmBo;
import org.dromara.talent.domain.bo.TlTalentBo;
import org.dromara.talent.domain.vo.ResumeFieldCandidateVo;
import org.dromara.talent.domain.vo.ResumeImportPreviewVo;
import org.dromara.talent.enums.AttachmentTypeEnum;
import org.dromara.talent.enums.ParseTaskStatusEnum;
import org.dromara.talent.helper.TalentPhoneHelper;
import org.dromara.talent.helper.TalentResumeExtractor;
import org.dromara.talent.mapper.TlParseFieldMapper;
import org.dromara.talent.mapper.TlParseTaskMapper;
import org.dromara.talent.service.ITalentAttachmentService;
import org.dromara.talent.service.ITalentProfileService;
import org.dromara.talent.service.ITalentResumeImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 导入简历直接建档服务实现。
 * <p>
 * 关键口径：
 * <ul>
 *     <li><b>复用既有安全逻辑</b>：建档走 {@link ITalentProfileService#create(TlTalentBo)}
 *     （内部含区域合法性与 {@code TalentScopeHelper.canWriteRegion}、手机号
 *     {@code TalentPhoneHelper.normalize/hash/tail4}、重复预检与人才编号唯一索引重试）；
 *     附件落库走 {@link ITalentAttachmentService#upload}（含可写区域、扩展名白名单、大小与
 *     MIME 一致性、版本管理与 UPLOAD 审计），本类不另写建档或上传 SQL。</li>
 *     <li>临时文件名为 {@code <token>.<ext>}，同名 {@code <token>.meta} 记录原始文件名与 MIME；
 *     预览、确认成功、确认失败、超 TTL 四条路径都会清理，不引入定时任务。</li>
 *     <li>日志只记录文件名、字节数、字段名，<b>禁止</b>记录简历正文与完整手机号。</li>
 * </ul>
 *
 * @author talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentResumeImportServiceImpl implements ITalentResumeImportService {

    /**
     * 临时文件元信息后缀（仅存原始文件名与 MIME，不存简历正文副本之外的任何内容）。
     */
    private static final String META_SUFFIX = ".meta";

    /**
     * tl_parse_field 文本列长度上限。
     */
    private static final int FIELD_VALUE_MAX_LENGTH = 500;

    /**
     * 导入凭证格式：32 位十六进制（{@code IdUtil.fastSimpleUUID()}）。
     * <p>
     * 必须强校验，否则凭证中的 {@code ..} / 路径分隔符会构造出临时目录之外的路径。
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    /**
     * 本地抽取器。
     */
    private final TalentResumeExtractor resumeExtractor;

    /**
     * 人才库配置（导入开关、临时目录、TTL、文本上限）。
     */
    private final TalentProperties talentProperties;

    /**
     * 人才主档服务（建档唯一入口，含全部安全校验）。
     */
    private final ITalentProfileService profileService;

    /**
     * 附件服务（上传唯一入口，含对象存储写入、版本管理与审计）。
     */
    private final ITalentAttachmentService attachmentService;

    /**
     * 手机号工具（留痕脱敏）。
     */
    private final TalentPhoneHelper phoneHelper;

    /**
     * 解析任务 Mapper。
     */
    private final TlParseTaskMapper parseTaskMapper;

    /**
     * 解析字段 Mapper。
     */
    private final TlParseFieldMapper parseFieldMapper;

    /**
     * 上传预览。
     *
     * @param file 简历文件
     * @return 预览结果
     */
    @Override
    public ResumeImportPreviewVo preview(MultipartFile file) {
        if (!talentProperties.isResumeImportEnabled()) {
            throw new ServiceException("简历导入功能未启用");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (file.getSize() > TalentConstants.MAX_FILE_SIZE) {
            throw new ServiceException("文件大小超过 25MB 限制");
        }
        String originalName = sanitizeFileName(StringUtils.defaultString(file.getOriginalFilename()));
        String ext = resolveExt(originalName);
        if (!TalentConstants.ALLOWED_EXT.contains(ext)) {
            throw new ServiceException("不支持的文件类型：" + ext);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("文件读取失败");
        }
        if (bytes.length > TalentConstants.MAX_FILE_SIZE) {
            throw new ServiceException("文件大小超过 25MB 限制");
        }

        // 顺带清理超 TTL 的临时文件（不引入定时任务）
        cleanupExpiredTemp();

        ResumeImportPreviewVo preview = resumeExtractor.extract(originalName, bytes, ext);
        writeTemp(preview.getImportToken(), ext, bytes, originalName, file.getContentType());
        log.info("简历导入预览成功, originalName={}, size={}, textExtracted={}, candidateCount={}",
            originalName, bytes.length, preview.getTextExtracted(),
            preview.getCandidates() == null ? 0 : preview.getCandidates().size());
        return preview;
    }

    /**
     * 确认建档。
     *
     * @param bo 确认入参
     * @return [talentId, attachmentId]
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long[] confirm(ResumeImportConfirmBo bo) {
        if (!talentProperties.isResumeImportEnabled()) {
            throw new ServiceException("简历导入功能未启用");
        }
        if (bo == null || StringUtils.isBlank(bo.getImportToken())) {
            throw new ServiceException("导入凭证不能为空");
        }
        String token = bo.getImportToken().trim();
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            // 凭证只可能是服务端生成的 32 位十六进制 UUID，格式不符直接拒绝（防路径穿越）
            throw new ServiceException("导入会话不存在或已过期，请重新上传简历");
        }
        Path tempFile = findTempFile(token);
        if (tempFile == null) {
            throw new ServiceException("导入会话不存在或已过期，请重新上传简历");
        }
        if (bo.getTalent() == null) {
            throw new ServiceException("人才信息不能为空");
        }
        if (isExpired(tempFile)) {
            deleteTemp(token);
            throw new ServiceException("导入会话已过期，请重新上传简历");
        }
        try {
            String ext = extOfTokenFile(tempFile);
            byte[] bytes = readBytes(tempFile);
            String[] meta = readMeta(token);
            String originalName = StringUtils.isNotBlank(meta[0]) ? meta[0] : token + "." + ext;
            String contentType = meta[1];

            // 重新本地抽取：仅用于 tl_parse_field 留痕（parsed_value / confidence），不落库缓存
            ResumeImportPreviewVo preview = resumeExtractor.extract(originalName, bytes, ext);

            TlTalentBo talentBo = bo.getTalent();
            if (talentBo.getContactDate() == null) {
                // SPEC §3：联系日期缺省由服务端填导入当日
                talentBo.setContactDate(LocalDate.now());
            }
            // 建档唯一入口：区域可写校验 + 手机号 normalize/hash/tail4 + 重复预检 + 人才编号重试
            Long talentId = profileService.create(talentBo);
            // 附件唯一入口：可写区域校验 + 扩展名/大小/MIME + 对象存储写入 + UPLOAD 审计
            Long attachmentId = attachmentService.upload(talentId, AttachmentTypeEnum.RESUME.getCode(),
                new BytesMultipartFile(bytes, originalName, contentType));
            writeParseTrace(talentId, attachmentId, preview, talentBo);
            log.info("简历导入建档成功, talentId={}, attachmentId={}, ext={}, size={}",
                talentId, attachmentId, ext, bytes.length);
            return new Long[]{talentId, attachmentId};
        } finally {
            // 成功与失败都必须清理临时文件
            deleteTemp(token);
        }
    }

    /**
     * 写解析留痕：tl_parse_task 一条 + tl_parse_field 每个候选字段一行。
     *
     * @param talentId     人才ID
     * @param attachmentId 附件ID
     * @param preview      本次抽取结果
     * @param talentBo     用户最终提交值
     */
    private void writeParseTrace(Long talentId, Long attachmentId, ResumeImportPreviewVo preview, TlTalentBo talentBo) {
        LocalDateTime now = LocalDateTime.now();
        TlParseTask task = new TlParseTask();
        task.setTalentId(talentId);
        task.setAttachmentId(attachmentId);
        task.setStatus(ParseTaskStatusEnum.SUCCESS.getCode());
        task.setParserVersion(TalentConstants.PARSER_VERSION_LOCAL);
        task.setRetryCount(0);
        task.setStartTime(now);
        task.setFinishTime(now);
        parseTaskMapper.insert(task);

        List<ResumeFieldCandidateVo> candidates = preview == null ? null : preview.getCandidates();
        if (candidates == null || candidates.isEmpty() || task.getTaskId() == null) {
            return;
        }
        Long operatorId = LoginHelper.getUserId();
        List<TlParseField> rows = new ArrayList<>(candidates.size());
        for (ResumeFieldCandidateVo candidate : candidates) {
            if (candidate == null || StringUtils.isBlank(candidate.getField())) {
                continue;
            }
            TlParseField row = new TlParseField();
            row.setTaskId(task.getTaskId());
            row.setFieldName(candidate.getField());
            row.setParsedValue(StringUtils.substring(safeValue(candidate.getField(), candidate.getValue()),
                0, FIELD_VALUE_MAX_LENGTH));
            row.setConfidence(toConfidence(candidate.getConfidence()));
            row.setConfirmedValue(StringUtils.substring(
                firstNotBlank(safeValue(candidate.getField(), confirmedValueOf(candidate.getField(), talentBo)),
                    safeValue(candidate.getField(), candidate.getValue())), 0, FIELD_VALUE_MAX_LENGTH));
            row.setConfirmStatus("1");
            row.setConfirmBy(operatorId);
            row.setConfirmTime(now);
            rows.add(row);
        }
        for (TlParseField row : rows) {
            parseFieldMapper.insert(row);
        }
    }

    /**
     * 用户在确认表单中最终提交的字段值（与 tl_talent 列名 camelCase 对齐）。
     *
     * @param field    字段名
     * @param talentBo 提交的人才信息
     * @return 值，非主档字段返回 null
     */
    private String confirmedValueOf(String field, TlTalentBo talentBo) {
        if (talentBo == null || StringUtils.isBlank(field)) {
            return null;
        }
        switch (field) {
            case "name":
                return talentBo.getName();
            case "gender":
                return talentBo.getGender();
            case "education":
                return talentBo.getEducation();
            case "birthDate":
                return talentBo.getBirthDate() == null ? null : talentBo.getBirthDate().toString();
            case "ageOnly":
                return talentBo.getAgeOnly() == null ? null : String.valueOf(talentBo.getAgeOnly());
            case "phone":
                return talentBo.getPhone();
            case "position":
                return talentBo.getPosition();
            case "regionCode":
                return talentBo.getRegionCode();
            case "expectSalaryMin":
                return talentBo.getExpectSalaryMin() == null ? null : String.valueOf(talentBo.getExpectSalaryMin());
            case "expectSalaryMax":
                return talentBo.getExpectSalaryMax() == null ? null : String.valueOf(talentBo.getExpectSalaryMax());
            case "contactDate":
                return talentBo.getContactDate() == null ? null : talentBo.getContactDate().toString();
            default:
                // email / experienceText 不属于 tl_talent 字段，保留解析值
                return null;
        }
    }

    /**
     * 留痕值脱敏：手机号明文严禁落到未加密的 tl_parse_field 文本列。
     *
     * @param field 字段名
     * @param value 原始值
     * @return 可安全入库的值
     */
    private String safeValue(String field, String value) {
        if (!"phone".equals(field) || StringUtils.isBlank(value)) {
            return value;
        }
        return phoneHelper.mask(value);
    }

    /**
     * 置信度转 BigDecimal（保留 4 位小数，与 decimal(5,4) 对齐）。
     *
     * @param confidence 置信度
     * @return BigDecimal，为空返回 null
     */
    private BigDecimal toConfidence(Double confidence) {
        if (confidence == null) {
            return null;
        }
        return BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 取第一个非空值。
     *
     * @param first  首选值
     * @param second 备选值
     * @return 非空值
     */
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    /**
     * 写临时文件与元信息文件。
     *
     * @param token        导入凭证
     * @param ext          扩展名
     * @param bytes        文件内容
     * @param originalName 原始文件名
     * @param contentType  请求 MIME
     */
    private void writeTemp(String token, String ext, byte[] bytes, String originalName, String contentType) {
        try {
            Path dir = tempDir();
            Files.createDirectories(dir);
            Files.write(dir.resolve(token + "." + ext), bytes);
            String meta = sanitizeFileName(StringUtils.defaultString(originalName)) + "\n"
                + StringUtils.defaultString(contentType) + "\n";
            Files.write(dir.resolve(token + META_SUFFIX), meta.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("简历导入临时文件写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("临时文件写入失败");
        }
    }

    /**
     * 读取元信息（原始文件名、MIME）。
     *
     * @param token 导入凭证
     * @return [原始文件名, MIME]
     */
    private String[] readMeta(String token) {
        Path metaPath = tempDir().resolve(token + META_SUFFIX);
        if (!Files.isRegularFile(metaPath)) {
            return new String[]{null, null};
        }
        try {
            List<String> lines = Files.readAllLines(metaPath, StandardCharsets.UTF_8);
            String name = lines.isEmpty() ? null : StringUtils.trimToNull(lines.get(0));
            String type = lines.size() < 2 ? null : StringUtils.trimToNull(lines.get(1));
            return new String[]{name, type};
        } catch (IOException e) {
            log.warn("简历导入临时元信息读取失败, exception={}", e.getClass().getSimpleName());
            return new String[]{null, null};
        }
    }

    /**
     * 按 {@code <token>.<ext>} 定位临时文件（扩展名限定在白名单内，天然排除 .meta）。
     *
     * @param token 导入凭证
     * @return 临时文件路径，不存在返回 null
     */
    private Path findTempFile(String token) {
        Path dir = tempDir();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        for (String ext : TalentConstants.ALLOWED_EXT) {
            Path candidate = dir.resolve(token + "." + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 清理超过 TTL 的临时文件（含 .meta），只在 preview 时顺带执行。
     */
    private void cleanupExpiredTemp() {
        Path dir = tempDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        long ttlMillis = Math.max(1, talentProperties.getImportTempTtlMinutes()) * 60_000L;
        Instant deadline = Instant.now().minusMillis(ttlMillis);
        int removed = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> stale = stream.filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant().isBefore(deadline);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (Path path : stale) {
                if (deleteQuietly(path)) {
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("简历导入临时目录扫描失败, exception={}", e.getClass().getSimpleName());
        }
        if (removed > 0) {
            log.info("简历导入临时文件过期清理完成, removed={}", removed);
        }
    }

    /**
     * 删除某次导入的临时文件（内容 + 元信息），失败只记日志。
     *
     * @param token 导入凭证
     */
    private void deleteTemp(String token) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        Path dir = tempDir();
        deleteQuietly(dir.resolve(token + META_SUFFIX));
        for (String ext : TalentConstants.ALLOWED_EXT) {
            deleteQuietly(dir.resolve(token + "." + ext));
        }
    }

    /**
     * 静默删除文件。
     *
     * @param path 路径
     * @return 是否确实删除了文件
     */
    private boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("简历导入临时文件删除失败, exception={}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 临时文件是否超过 TTL。
     *
     * @param path 临时文件
     * @return 是否过期
     */
    private boolean isExpired(Path path) {
        try {
            long minutes = talentProperties.getImportTempTtlMinutes();
            Instant deadline = Files.getLastModifiedTime(path).toInstant().plus(Math.max(1, minutes), ChronoUnit.MINUTES);
            return Instant.now().isAfter(deadline);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 读取临时文件字节。
     *
     * @param path 路径
     * @return 字节数组
     */
    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ServiceException("临时文件读取失败，请重新上传简历");
        }
    }

    /**
     * 取临时文件的扩展名。
     *
     * @param path 临时文件
     * @return 扩展名（小写无点）
     */
    private String extOfTokenFile(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 临时目录。
     *
     * @return 目录路径
     */
    private Path tempDir() {
        return Path.of(talentProperties.getImportTempDir());
    }

    /**
     * 解析扩展名（小写、无点）。
     *
     * @param originalName 原始文件名
     * @return 扩展名
     */
    private String resolveExt(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            throw new ServiceException("文件缺少扩展名");
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 文件名清洗：去掉换行等控制字符，限制长度（与 tl_talent_attachment.original_name 对齐）。
     *
     * @param name 原始文件名
     * @return 清洗后的文件名
     */
    private String sanitizeFileName(String name) {
        return StringUtils.substring(StringUtils.defaultString(name).replaceAll("[\\r\\n\\t]", ""), 0, 255);
    }

    /**
     * 内存中的简历文件包装，用于把临时文件重新送入统一的附件上传入口。
     */
    private static final class BytesMultipartFile implements MultipartFile {

        /**
         * 文件内容。
         */
        private final byte[] bytes;

        /**
         * 原始文件名。
         */
        private final String originalName;

        /**
         * 请求 MIME。
         */
        private final String contentType;

        /**
         * 构造。
         *
         * @param bytes        文件内容
         * @param originalName 原始文件名
         * @param contentType  请求 MIME
         */
        private BytesMultipartFile(byte[] bytes, String originalName, String contentType) {
            this.bytes = bytes;
            this.originalName = originalName;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0L : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }

}
