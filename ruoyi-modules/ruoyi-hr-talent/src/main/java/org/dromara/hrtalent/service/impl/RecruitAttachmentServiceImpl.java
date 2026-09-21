package org.dromara.hrtalent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.recruitment.AttachmentDownloadBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitAttachment;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitAttachmentVo;
import org.dromara.hrtalent.domainservice.AttachmentBizAccessChecker;
import org.dromara.hrtalent.enums.AttachmentTypeEnum;
import org.dromara.hrtalent.enums.DataLevelEnum;
import org.dromara.hrtalent.mapper.RecruitAttachmentMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitAttachmentService;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 招聘业务附件服务实现（SPEC-P3 §3.5 / 设计文档 §8.8、§9.4、§15.2、§15.3、§21.7）。
 *
 * <p><b>安全要点</b>：</p>
 * <ul>
 *     <li>数据库只保存 OSS 对象键（{@code oss_id}），不保存长期公网地址；</li>
 *     <li>下载与预览：用途必填 → 附件存在 → 业务记录资源级鉴权 → 写审计 → 流式输出，
 *     全程不产生签名地址；</li>
 *     <li>审计明细（{@code detail_json}）只写业务类型、业务ID、附件类型、版本号等非敏感字段，
 *     <b>不写</b>原始文件名（可能含姓名）、对象键、电话号码、简历正文与签名地址；</li>
 *     <li>日志只记录业务ID与计数，不记录对象键、文件名、用途之外的敏感上下文；</li>
 *     <li>删除一律逻辑删除：既不删数据行，也不删对象存储文件（保留期策略，§15.3）。</li>
 * </ul>
 *
 * <p><b>事务边界</b>：对象存储上传不与数据库事务完全原子（§21.7），因此顺序为
 * 「先上传对象 → 再写业务记录」，数据库写入失败时回滚事务并清理孤立对象。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitAttachmentServiceImpl implements IRecruitAttachmentService {

    /**
     * 是否当前版本：是。
     */
    private static final String CURRENT_FLAG_YES = "1";

    /**
     * 是否当前版本：否。
     */
    private static final String CURRENT_FLAG_NO = "0";

    /**
     * 允许承载附件的业务类型：应聘记录 / 面试 / 背调 / 录用资料 / 人才主档 / 人才跟进（P4 追加）。
     */
    private static final Set<String> SUPPORTED_BIZ_TYPES = Set.of(
        SensitiveAuditRecorder.BIZ_APPLICATION,
        SensitiveAuditRecorder.BIZ_INTERVIEW,
        SensitiveAuditRecorder.BIZ_BACKGROUND,
        SensitiveAuditRecorder.BIZ_TALENT,
        SensitiveAuditRecorder.BIZ_OFFER,
        SensitiveAuditRecorder.BIZ_FOLLOW_UP);

    /**
     * 允许的 MIME 类型白名单（与允许的扩展名口径一致）。
     * <p>浏览器对未知类型可能回传空值或 {@code application/octet-stream}，
     * 该情况按「无法判定」处理，仍受扩展名白名单与大小限制约束。</p>
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "application/rtf",
        "image/jpeg",
        "image/png",
        "image/gif",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-7z-compressed");

    /**
     * 无法判定 MIME 时的回传值（按扩展名白名单兜底）。
     */
    private static final Set<String> UNDETERMINED_MIME_TYPES = Set.of(
        "", "application/octet-stream", "binary/octet-stream");

    /**
     * 用途（purpose）最大长度，与 DDL {@code purpose varchar(255)} 一致。
     */
    private static final int PURPOSE_MAX_LENGTH = 255;

    /**
     * 原始文件名最大长度，与 DDL {@code original_name varchar(255)} 一致。
     */
    private static final int ORIGINAL_NAME_MAX_LENGTH = 255;

    /**
     * 附件 Mapper。
     */
    private final RecruitAttachmentMapper recruitAttachmentMapper;

    /**
     * 对象存储访问封装（只保存对象键，不保存公网地址）。
     */
    private final HrTalentOssHelper hrTalentOssHelper;

    /**
     * 招聘与人才管理业务配置（扩展名白名单、大小与数量上限）。
     */
    private final HrTalentProperties hrTalentProperties;

    /**
     * 敏感操作审计统一入口。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /**
     * 业务记录资源级鉴权扩展点；未注册实现时只做业务类型白名单校验并告警。
     */
    private final ObjectProvider<AttachmentBizAccessChecker> bizAccessCheckerProvider;

    /* ------------------------------------------------------------------ 上传 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(RecruitAttachmentBo bo, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String bizType = requireBizType(bo.getBizType());
        Long bizId = requireBizId(bo.getBizId());
        checkBizAccess(bizType, bizId);
        String fileType = resolveFileType(bo.getFileType());
        String securityLevel = resolveSecurityLevel(bo.getSecurityLevel());
        String originalName = resolveOriginalName(file.getOriginalFilename());
        String suffix = extractSuffix(originalName);
        validateExtension(suffix);
        validateMimeType(file.getContentType());
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new ServiceException("上传文件不能为空");
        }
        validateSize(content.length);
        validateCount(bizType, bizId);

        Long attachmentId = IdUtil.getSnowflakeNextId();
        int versionNo = nextVersionNo(bizType, bizId, fileType);
        // 对象键只由服务端生成，且以随机附件ID为路径段，不使用原文件名（§15.3）
        String ossId = hrTalentOssHelper.buildAttachmentKey(bizType, bizId, attachmentId, versionNo, suffix);

        // 1) 先上传对象，再写业务记录（§21.7：OSS 上传不与数据库事务完全原子）
        hrTalentOssHelper.put(ossId, content);
        try {
            // 2) 同一事务内：旧当前版本置否 + 插入新版本（首个版本无需置否）
            if (versionNo > 1) {
                demoteCurrentVersion(bizType, bizId, fileType);
            }
            RecruitAttachment entity = new RecruitAttachment();
            entity.setAttachmentId(attachmentId);
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setFileType(fileType);
            entity.setOssId(ossId);
            entity.setOriginalName(originalName);
            entity.setFileSuffix(suffix);
            entity.setFileSize((long) content.length);
            entity.setFileHash(sha256Hex(content));
            entity.setVersionNo(versionNo);
            entity.setCurrentFlag(CURRENT_FLAG_YES);
            entity.setSecurityLevel(securityLevel);
            entity.setUploadedBy(currentUserId());
            entity.setUploadedTime(LocalDateTime.now());
            entity.setRemark(bo.getRemark());
            int rows = recruitAttachmentMapper.insert(entity);
            if (rows == 0) {
                throw new ServiceException("附件登记失败，请重试");
            }
            log.info("新增招聘业务附件, attachmentId={}, bizType={}, bizId={}, fileType={}, versionNo={}",
                attachmentId, bizType, bizId, fileType, versionNo);
            return attachmentId;
        } catch (RuntimeException e) {
            // 数据库写入失败：清理已上传的孤立对象，避免产生无归属文件
            hrTalentOssHelper.delete(ossId);
            throw e;
        }
    }

    /* ------------------------------------------------------------------ 查询 ------------------------------------------------------------------ */

    @Override
    public PageResult<RecruitAttachmentVo> queryPage(RecruitAttachmentQueryBo bo, PageQuery pageQuery) {
        RecruitAttachmentQueryBo query = bo == null ? new RecruitAttachmentQueryBo() : bo;
        String bizType = requireBizType(query.getBizType());
        Long bizId = requireBizId(query.getBizId());
        // 列表同样属于附件访问，纳入业务记录鉴权范围（fail-closed）
        checkBizAccess(bizType, bizId);
        String currentFlag = StringUtils.isBlank(query.getCurrentFlag()) ? null : query.getCurrentFlag().trim();
        if (currentFlag != null && !CURRENT_FLAG_YES.equals(currentFlag) && !CURRENT_FLAG_NO.equals(currentFlag)) {
            throw new ServiceException("是否当前版本只能为 0 或 1");
        }
        if (StringUtils.isNotBlank(query.getFileType())) {
            resolveFileType(query.getFileType());
        }
        LambdaQueryWrapper<RecruitAttachment> wrapper = new LambdaQueryWrapper<RecruitAttachment>()
            .eq(RecruitAttachment::getBizType, bizType)
            .eq(RecruitAttachment::getBizId, bizId)
            .eq(StringUtils.isNotBlank(query.getFileType()), RecruitAttachment::getFileType, query.getFileType())
            .eq(currentFlag != null, RecruitAttachment::getCurrentFlag, currentFlag)
            .eq(StringUtils.isNotBlank(query.getSecurityLevel()), RecruitAttachment::getSecurityLevel, query.getSecurityLevel())
            .eq(query.getUploadedBy() != null, RecruitAttachment::getUploadedBy, query.getUploadedBy())
            .ge(query.getUploadedTimeBegin() != null, RecruitAttachment::getUploadedTime, query.getUploadedTimeBegin())
            .le(query.getUploadedTimeEnd() != null, RecruitAttachment::getUploadedTime, query.getUploadedTimeEnd())
            .orderByDesc(RecruitAttachment::getVersionNo)
            .orderByDesc(RecruitAttachment::getUploadedTime);
        Page<RecruitAttachmentVo> page = recruitAttachmentMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /* ------------------------------------------------------------------ 预览与下载 ------------------------------------------------------------------ */

    @Override
    public void preview(Long attachmentId, AttachmentDownloadBo bo, HttpServletResponse response) {
        stream(attachmentId, bo, response, true);
    }

    @Override
    public void download(Long attachmentId, AttachmentDownloadBo bo, HttpServletResponse response) {
        stream(attachmentId, bo, response, false);
    }

    /* ------------------------------------------------------------------ 删除 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] attachmentIds) {
        if (attachmentIds == null || attachmentIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(attachmentIds).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<RecruitAttachment> attachments = recruitAttachmentMapper.selectByIds(ids);
        if (attachments == null || attachments.isEmpty()) {
            throw new ServiceException("附件不存在或已删除");
        }
        for (RecruitAttachment attachment : attachments) {
            checkBizAccess(attachment.getBizType(), attachment.getBizId());
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DELETE,
                SensitiveAuditRecorder.BIZ_ATTACHMENT,
                attachment.getAttachmentId(), "附件逻辑删除", SensitiveAuditRecorder.RESULT_SUCCESS,
                auditDetail(attachment));
        }
        // @TableLogic：只更新 del_flag，不物理删除数据行；对象存储文件按保留期策略保留（§6、§15.3）
        recruitAttachmentMapper.deleteByIds(ids);
        log.info("逻辑删除招聘业务附件, count={}", ids.size());
    }

    /* ------------------------------------------------------------------ 内部方法：流式输出 ------------------------------------------------------------------ */

    /**
     * 预览/下载公共流程：用途校验 → 附件校验 → 业务记录鉴权 → 审计 → 流式输出。
     *
     * @param attachmentId 附件ID
     * @param bo           用途入参
     * @param response     HTTP 响应
     * @param inline       是否内联预览
     */
    private void stream(Long attachmentId, AttachmentDownloadBo bo, HttpServletResponse response, boolean inline) {
        String eventType = inline
            ? SensitiveAuditRecorder.EVENT_ATTACHMENT_PREVIEW
            : SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD;
        String purpose = bo == null || bo.getPurpose() == null ? null : bo.getPurpose().trim();
        if (StringUtils.isBlank(purpose)) {
            // 用途为空：记录拒绝审计后再抛错，保证「被拒绝的敏感访问」同样留痕
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose, SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("用途（purpose）不能为空，禁止预览或下载附件");
        }
        if (purpose.length() > PURPOSE_MAX_LENGTH) {
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose.substring(0, PURPOSE_MAX_LENGTH), SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("用途长度不能超过 " + PURPOSE_MAX_LENGTH + " 个字符");
        }
        RecruitAttachment attachment = recruitAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose, SensitiveAuditRecorder.RESULT_FAILED);
            throw new ServiceException("附件不存在或已删除");
        }
        try {
            checkBizAccess(attachment.getBizType(), attachment.getBizId());
        } catch (ServiceException e) {
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose, SensitiveAuditRecorder.RESULT_DENIED, auditDetail(attachment));
            throw e;
        }
        try {
            writeStream(attachment, response, inline);
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose, SensitiveAuditRecorder.RESULT_SUCCESS, auditDetail(attachment));
            log.info("附件{}完成, attachmentId={}, bizType={}, bizId={}", inline ? "预览" : "下载",
                attachmentId, attachment.getBizType(), attachment.getBizId());
        } catch (ServiceException e) {
            sensitiveAuditRecorder.record(eventType, SensitiveAuditRecorder.BIZ_ATTACHMENT, attachmentId,
                purpose, SensitiveAuditRecorder.RESULT_FAILED, auditDetail(attachment));
            throw e;
        }
    }

    /**
     * 以鉴权后的流式响应输出附件；<b>不</b>返回任何签名地址（§11.1）。
     *
     * @param attachment 附件实体
     * @param response   HTTP 响应
     * @param inline     是否内联预览
     */
    private void writeStream(RecruitAttachment attachment, HttpServletResponse response, boolean inline) {
        response.reset();
        response.setContentType(resolveContentType(attachment.getFileSuffix()));
        // 防止浏览器按内容嗅探把附件当脚本执行
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", contentDisposition(inline, attachment.getOriginalName()));
        if (attachment.getFileSize() != null && attachment.getFileSize() > 0) {
            response.setContentLengthLong(attachment.getFileSize());
        }
        try (ServletOutputStream out = response.getOutputStream()) {
            hrTalentOssHelper.get(attachment.getOssId(), out);
            out.flush();
        } catch (IOException e) {
            // 不记录对象键与文件名
            log.error("附件流式输出失败, attachmentId={}, exception={}", attachment.getAttachmentId(),
                e.getClass().getSimpleName());
            throw new ServiceException("附件读取失败");
        }
    }

    /* ------------------------------------------------------------------ 内部方法：校验 ------------------------------------------------------------------ */

    /**
     * 校验业务类型，未知编码一律拒绝（fail-safe）。
     *
     * @param bizType 业务类型
     * @return 规范化后的业务类型
     */
    private String requireBizType(String bizType) {
        String value = StringUtils.isBlank(bizType) ? null : bizType.trim().toLowerCase(Locale.ROOT);
        if (value == null) {
            throw new ServiceException("业务类型不能为空");
        }
        if (!SUPPORTED_BIZ_TYPES.contains(value)) {
            throw new ServiceException("不支持的附件业务类型：" + value);
        }
        return value;
    }

    /**
     * 校验业务对象ID。
     *
     * @param bizId 业务对象ID
     * @return 业务对象ID
     */
    private Long requireBizId(Long bizId) {
        if (bizId == null) {
            throw new ServiceException("业务对象ID不能为空");
        }
        return bizId;
    }

    /**
     * 业务记录资源级鉴权（<b>fail-closed</b>）。
     *
     * <p>按钮权限只能证明「有下载附件的资格」，不能证明「有权看这条业务记录」；
     * 因此取不到任何鉴权实现时<b>直接拒绝</b>，绝不放行（与 {@code TalentScopeDomainService}
     * 授权查询异常即拒绝的姿态一致）。默认实现见
     * {@link org.dromara.hrtalent.domainservice.DefaultAttachmentBizAccessChecker}。</p>
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     */
    private void checkBizAccess(String bizType, Long bizId) {
        AttachmentBizAccessChecker checker = bizAccessCheckerProvider.getIfAvailable();
        if (checker == null) {
            // 拒绝原因（不是放行告警）
            log.warn("附件业务记录鉴权实现缺失，按拒绝处理, bizType={}", bizType);
            throw new ServiceException("附件业务记录鉴权服务不可用，禁止访问");
        }
        checker.check(bizType, bizId);
    }

    /**
     * 解析附件类型编码：默认 other，未知编码拒绝，简历（resume）拒绝。
     *
     * @param fileType 附件类型编码，可为空
     * @return 规范化后的附件类型编码
     */
    private String resolveFileType(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return AttachmentTypeEnum.OTHER.getCode();
        }
        String value = fileType.trim().toLowerCase(Locale.ROOT);
        AttachmentTypeEnum item = AttachmentTypeEnum.find(value);
        if (item == null) {
            throw new ServiceException("附件类型不合法，请使用字典 recruit_attachment_type 的编码");
        }
        if (AttachmentTypeEnum.RESUME == item) {
            // §8.8：简历使用独立的 hr_talent_resume 版本表，通用附件表不重复保存简历业务记录
            throw new ServiceException("简历请使用人才简历版本表（hr_talent_resume）管理，通用附件表不保存简历记录");
        }
        return item.getCode();
    }

    /**
     * 解析安全级别：默认敏感，未知编码拒绝。
     *
     * @param securityLevel 安全级别编码，可为空
     * @return 规范化后的安全级别编码
     */
    private String resolveSecurityLevel(String securityLevel) {
        if (StringUtils.isBlank(securityLevel)) {
            return DataLevelEnum.SENSITIVE.getCode();
        }
        DataLevelEnum item = DataLevelEnum.find(securityLevel.trim().toLowerCase(Locale.ROOT));
        if (item == null) {
            throw new ServiceException("安全级别不合法，请使用字典 recruit_data_level 的编码");
        }
        return item.getCode();
    }

    /**
     * 归一化原始文件名：去掉客户端可能带上的路径段，并按列长度截断（保留后缀）。
     *
     * @param originalFilename 原始文件名
     * @return 归一化后的文件名
     */
    private String resolveOriginalName(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            throw new ServiceException("原始文件名不能为空");
        }
        String name = originalFilename.trim().replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            throw new ServiceException("原始文件名不能为空");
        }
        if (name.length() <= ORIGINAL_NAME_MAX_LENGTH) {
            return name;
        }
        String suffix = extractSuffix(name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        int maxBaseLength = ORIGINAL_NAME_MAX_LENGTH - suffix.length() - 1;
        if (maxBaseLength <= 0) {
            throw new ServiceException("原始文件名不合法");
        }
        return base.substring(0, maxBaseLength) + "." + suffix;
    }

    /**
     * 取文件后缀（小写、不含点）。
     *
     * @param fileName 文件名
     * @return 后缀
     */
    private String extractSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new ServiceException("文件缺少扩展名，无法上传");
        }
        String suffix = fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        if (suffix.isEmpty() || suffix.length() > 32) {
            throw new ServiceException("文件扩展名不合法");
        }
        return suffix;
    }

    /**
     * 校验扩展名是否在配置白名单内。
     *
     * @param suffix 文件后缀
     */
    private void validateExtension(String suffix) {
        if (!hrTalentProperties.isAttachmentExtensionAllowed(suffix)) {
            throw new ServiceException("不允许上传的附件类型：" + suffix);
        }
    }

    /**
     * 校验 MIME 类型；无法判定的回传值按扩展名白名单兜底。
     *
     * @param contentType 上传内容类型
     */
    private void validateMimeType(String contentType) {
        String value = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        if (UNDETERMINED_MIME_TYPES.contains(value)) {
            return;
        }
        if (!ALLOWED_MIME_TYPES.contains(value)) {
            throw new ServiceException("不允许上传的附件 MIME 类型：" + value);
        }
    }

    /**
     * 校验单文件大小上限。
     *
     * @param size 文件字节数
     */
    private void validateSize(long size) {
        long max = hrTalentProperties.getAttachmentMaxSize() == null
            ? Long.MAX_VALUE
            : hrTalentProperties.getAttachmentMaxSize().toBytes();
        if (size > max) {
            throw new ServiceException("附件大小超过上限 " + max + " 字节");
        }
    }

    /**
     * 校验单条业务记录的附件数量上限（不含已逻辑删除记录）。
     *
     * @param bizType 业务类型
     * @param bizId   业务对象ID
     */
    private void validateCount(String bizType, Long bizId) {
        int max = hrTalentProperties.getAttachmentMaxCount();
        if (max <= 0) {
            return;
        }
        long count = recruitAttachmentMapper.selectCount(new LambdaQueryWrapper<RecruitAttachment>()
            .eq(RecruitAttachment::getBizType, bizType)
            .eq(RecruitAttachment::getBizId, bizId));
        if (count >= max) {
            throw new ServiceException("该业务记录附件数量已达上限 " + max + " 个");
        }
    }

    /* ------------------------------------------------------------------ 内部方法：版本与文件 ------------------------------------------------------------------ */

    /**
     * 计算新版本号：同一业务对象同一附件类型的最大已用版本号 + 1。
     *
     * @param bizType  业务类型
     * @param bizId    业务对象ID
     * @param fileType 附件类型
     * @return 新版本号（首次为 1）
     */
    private int nextVersionNo(String bizType, Long bizId, String fileType) {
        Integer max = recruitAttachmentMapper.selectMaxVersionNo(bizType, bizId, fileType);
        return (max == null ? 0 : max) + 1;
    }

    /**
     * 将同一业务对象同一附件类型的旧当前版本标识置否（§9.4：不覆盖旧文件）。
     *
     * @param bizType  业务类型
     * @param bizId    业务对象ID
     * @param fileType 附件类型
     */
    private void demoteCurrentVersion(String bizType, Long bizId, String fileType) {
        LambdaUpdateWrapper<RecruitAttachment> wrapper = new LambdaUpdateWrapper<RecruitAttachment>()
            .eq(RecruitAttachment::getBizType, bizType)
            .eq(RecruitAttachment::getBizId, bizId)
            .eq(RecruitAttachment::getFileType, fileType)
            .eq(RecruitAttachment::getCurrentFlag, CURRENT_FLAG_YES)
            .set(RecruitAttachment::getCurrentFlag, CURRENT_FLAG_NO);
        recruitAttachmentMapper.update(null, wrapper);
    }

    /**
     * 读取上传内容。
     *
     * @param file 上传文件
     * @return 文件字节
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("附件内容读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("附件内容读取失败");
        }
    }

    /**
     * 计算 SHA-256 十六进制哈希（用于重复文件提示，非唯一约束）。
     *
     * @param content 文件字节
     * @return 64 位十六进制字符串
     */
    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // 理论不可达：JDK 必须提供 SHA-256
            throw new ServiceException("文件哈希计算失败");
        }
    }

    /* ------------------------------------------------------------------ 内部方法：响应头与审计明细 ------------------------------------------------------------------ */

    /**
     * 取响应内容类型。
     *
     * @param suffix 文件后缀
     * @return MIME 类型
     */
    private String resolveContentType(String suffix) {
        String value = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain;charset=UTF-8";
            case "rtf" -> "application/rtf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "zip" -> "application/zip";
            case "7z" -> "application/x-7z-compressed";
            default -> "application/octet-stream";
        };
    }

    /**
     * 构造 Content-Disposition（下载 attachment / 预览 inline）。
     *
     * @param inline          是否内联预览
     * @param originalFileName 原始文件名
     * @return Content-Disposition 值
     */
    private String contentDisposition(boolean inline, String originalFileName) {
        String disposition = inline ? "inline" : "attachment";
        String name = StringUtils.isBlank(originalFileName) ? "attachment" : originalFileName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + ";filename*=UTF-8''" + encoded;
    }

    /**
     * 构造审计明细：只含业务定位与版本等非敏感字段。
     *
     * <p><b>禁止</b>写入原始文件名（可能含姓名）、对象键、电话号码、简历正文与签名地址。</p>
     *
     * @param attachment 附件实体
     * @return 脱敏 JSON 明细
     */
    private String auditDetail(RecruitAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bizType", attachment.getBizType());
        detail.put("bizId", attachment.getBizId());
        detail.put("fileType", attachment.getFileType());
        detail.put("fileSuffix", attachment.getFileSuffix());
        detail.put("fileSize", attachment.getFileSize());
        detail.put("versionNo", attachment.getVersionNo());
        detail.put("currentFlag", attachment.getCurrentFlag());
        return toJson(detail);
    }

    /**
     * 构造脱敏 JSON 明细。
     * <p>不依赖 Spring 容器内的 JsonMapper，保证审计明细构造在任意上下文中都可用。</p>
     *
     * @param detail 键值对（值为 null 的键不输出）
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> detail) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append(jsonString(String.valueOf(value)));
            }
        }
        return json.append('}').toString();
    }

    /**
     * 转义 JSON 字符串值。
     *
     * @param value 原值
     * @return 带引号的转义字符串
     */
    private String jsonString(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * 取当前登录用户ID；无登录态时返回 null。
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

}
