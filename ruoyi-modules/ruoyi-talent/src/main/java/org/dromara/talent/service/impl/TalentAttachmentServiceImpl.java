package org.dromara.talent.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentAttachment;
import org.dromara.talent.domain.vo.TlTalentAttachmentVo;
import org.dromara.talent.enums.AttachmentTypeEnum;
import org.dromara.talent.enums.AuditActionEnum;
import org.dromara.talent.enums.AuditTargetTypeEnum;
import org.dromara.talent.enums.GrantPermissionEnum;
import org.dromara.talent.enums.ScanStatusEnum;
import org.dromara.talent.helper.TalentAuditRecorder;
import org.dromara.talent.helper.TalentOssHelper;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlTalentAttachmentMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentAttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 人才附件服务实现。
 * <p>
 * 安全口径（SPEC §4 / §6.2 / §6.3 / §6.4）：
 * <ul>
 *     <li>上传：授权 + 区域可写 + 扩展名白名单 + 大小上限 + MIME 与扩展名一致性 + 版本管理；</li>
 *     <li>下载：先加载附件与人才 → 单条授权 → {@code scan_status == CLEAN} → 流式输出（不下发预签名 URL）；</li>
 *     <li>无论下载成功失败都落审计。</li>
 * </ul>
 *
 * @author talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentAttachmentServiceImpl implements ITalentAttachmentService {

    /**
     * 扩展名 → 允许的 MIME 集合（用于"扩展名与 MIME 一致性"校验）。
     */
    private static final Map<String, List<String>> MIME_BY_EXT = buildMimeMap();

    /**
     * 允许放行的"通用" MIME：浏览器 / 客户端常以该值上传，不能据此判定不一致。
     */
    private static final List<String> GENERIC_MIME = List.of("application/octet-stream", "");

    /**
     * 未启用扫描时的说明文案。
     */
    private static final String SCAN_DISABLED_REMARK = "scanning disabled by config";

    /**
     * 附件 Mapper。
     */
    private final TlTalentAttachmentMapper attachmentMapper;

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

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
     * 人才库配置（扫描开关等）。
     */
    private final TalentProperties talentProperties;

    /**
     * 附件列表。
     *
     * @param talentId 人才ID
     * @return 附件列表
     */
    @Override
    public List<TlTalentAttachmentVo> listByTalent(Long talentId) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        LambdaQueryWrapper<TlTalentAttachment> wrapper = new LambdaQueryWrapper<TlTalentAttachment>()
            .eq(TlTalentAttachment::getTalentId, talentId)
            .orderByDesc(TlTalentAttachment::getIsCurrent)
            .orderByDesc(TlTalentAttachment::getCreateTime);
        List<TlTalentAttachmentVo> list = attachmentMapper.selectVoList(wrapper);
        if (CollUtil.isEmpty(list)) {
            return list;
        }
        boolean canDownload = scopeHelper.isDownloadableRole();
        for (TlTalentAttachmentVo vo : list) {
            vo.setDownloadable(ScanStatusEnum.CLEAN.getCode().equals(vo.getScanStatus())
                && (canDownload || scopeHelper.hasGrant(talentId, GrantPermissionEnum.DOWNLOAD)));
        }
        return list;
    }

    /**
     * 上传附件新版本。
     *
     * @param talentId       人才ID
     * @param attachmentType 附件类型
     * @param file           文件
     * @return 附件ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long talentId, String attachmentType, MultipartFile file) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canWriteRegion(talent.getRegionCode())) {
            throw new ServiceException("无权向该区域的人才档案上传附件");
        }
        validateAttachmentType(attachmentType);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (file.getSize() > TalentConstants.MAX_FILE_SIZE) {
            throw new ServiceException("文件大小超过 25MB 限制");
        }
        String originalName = StringUtils.defaultString(file.getOriginalFilename());
        String ext = resolveExt(originalName);
        if (!TalentConstants.ALLOWED_EXT.contains(ext)) {
            throw new ServiceException("不支持的文件类型：" + ext);
        }
        checkMime(ext, file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("文件读取失败");
        }
        if (bytes.length > TalentConstants.MAX_FILE_SIZE) {
            throw new ServiceException("文件大小超过 25MB 限制");
        }
        String fileHash = DigestUtil.sha256Hex(bytes);

        // 先取雪花 ID，保证对象键稳定（上传失败不会留下无主记录）
        Long attachmentId = IdUtil.getSnowflakeNextId();
        int version = nextVersion(talentId, attachmentType);
        if (version > 1) {
            demoteCurrent(talentId, attachmentType);
        }
        String objectKey = ossHelper.buildAttachmentKey(talentId, attachmentId, version, ext);
        ossHelper.put(objectKey, new ByteArrayInputStream(bytes), bytes.length);

        boolean scanEnabled = talentProperties.isVirusScanEnabled();
        TlTalentAttachment entity = new TlTalentAttachment();
        entity.setAttachmentId(attachmentId);
        entity.setTalentId(talentId);
        entity.setObjectKey(objectKey);
        entity.setAttachmentType(attachmentType);
        entity.setOriginalName(StringUtils.substring(originalName, 0, 255));
        entity.setFileExt(ext);
        entity.setMimeType(StringUtils.substring(StringUtils.defaultString(file.getContentType()), 0, 128));
        entity.setFileSize((long) bytes.length);
        entity.setFileHash(fileHash);
        entity.setVersion(version);
        entity.setIsCurrent("1");
        entity.setScanStatus(scanEnabled ? ScanStatusEnum.PENDING.getCode() : ScanStatusEnum.CLEAN.getCode());
        entity.setScanTime(scanEnabled ? null : LocalDateTime.now());
        entity.setScanRemark(scanEnabled ? null : SCAN_DISABLED_REMARK);
        attachmentMapper.insert(entity);

        auditRecorder.record(AuditActionEnum.UPLOAD, AuditTargetTypeEnum.ATTACHMENT, attachmentId, talentId, true,
            scanEnabled ? "上传人才附件" : "上传人才附件；virus scan disabled");
        return attachmentId;
    }

    /**
     * 受控流式下载。
     *
     * @param attachmentId 附件ID
     * @param response     HTTP 响应
     */
    @Override
    public void download(Long attachmentId, HttpServletResponse response) {
        TlTalentAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        Long talentId = attachment == null ? null : attachment.getTalentId();
        boolean success = false;
        String reason = null;
        try {
            if (attachment == null || "1".equals(attachment.getDelFlag())) {
                throw new ServiceException("附件不存在");
            }
            TlTalent talent = loadTalent(attachment.getTalentId());
            // 二次校验：区域 / 单条授权 + 扫描状态（仅 CLEAN 可下载）
            scopeHelper.checkAttachmentDownloadable(talent, attachment);
            if (StringUtils.isBlank(attachment.getObjectKey())) {
                throw new ServiceException("附件对象缺失");
            }
            response.setContentType(StringUtils.isBlank(attachment.getMimeType())
                ? "application/octet-stream" : attachment.getMimeType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + FileUtils.percentEncode(StringUtils.defaultIfBlank(attachment.getOriginalName(), "attachment")));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (attachment.getFileSize() != null) {
                response.setContentLengthLong(attachment.getFileSize());
            }
            ossHelper.get(attachment.getObjectKey(), response.getOutputStream());
            response.flushBuffer();
            success = true;
            reason = "下载人才附件";
        } catch (ServiceException e) {
            reason = e.getMessage();
            throw e;
        } catch (Exception e) {
            log.error("人才附件下载失败, attachmentId={}, exception={}", attachmentId, e.getClass().getSimpleName());
            reason = "附件下载失败";
            throw new ServiceException("附件下载失败");
        } finally {
            // 无论成功失败都要落审计
            auditRecorder.recordDownload(attachmentId, talentId, success, reason);
        }
    }

    /**
     * 逻辑删除附件（保留对象存储中的文件，便于追溯）。
     *
     * @param attachmentId 附件ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long attachmentId) {
        TlTalentAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (attachment == null || "1".equals(attachment.getDelFlag())) {
            throw new ServiceException("附件不存在");
        }
        TlTalent talent = loadTalent(attachment.getTalentId());
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canWriteRegion(talent.getRegionCode())) {
            throw new ServiceException("无权删除该区域人才档案的附件");
        }
        // @TableLogic 逻辑删除
        attachmentMapper.deleteById(attachmentId);
        auditRecorder.record(AuditActionEnum.DELETE, AuditTargetTypeEnum.ATTACHMENT, attachmentId, talent.getTalentId(), true, "删除人才附件");
    }

    /**
     * 扫描回调。
     *
     * @param attachmentId 附件ID
     * @param scanStatus   扫描状态
     * @param remark       扫描说明
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScanStatus(Long attachmentId, String scanStatus, String remark) {
        if (ScanStatusEnum.find(scanStatus) == null) {
            throw new ServiceException("非法的扫描状态");
        }
        TlTalentAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new ServiceException("附件不存在");
        }
        TlTalentAttachment entity = new TlTalentAttachment();
        entity.setAttachmentId(attachmentId);
        entity.setScanStatus(scanStatus);
        entity.setScanTime(LocalDateTime.now());
        entity.setScanRemark(StringUtils.substring(StringUtils.defaultString(remark), 0, 255));
        attachmentMapper.updateById(entity);
    }

    /**
     * 校验附件类型。
     *
     * @param attachmentType 附件类型
     */
    private void validateAttachmentType(String attachmentType) {
        if (StringUtils.isBlank(attachmentType) || AttachmentTypeEnum.find(attachmentType) == null) {
            throw new ServiceException("非法的附件类型");
        }
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
     * MIME 与扩展名一致性校验（白名单外或通用类型放行，明确不匹配时拒绝）。
     *
     * @param ext         扩展名
     * @param contentType 请求 MIME
     */
    private void checkMime(String ext, String contentType) {
        String mime = StringUtils.defaultString(contentType).toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');
        if (semi > 0) {
            mime = mime.substring(0, semi).trim();
        }
        if (GENERIC_MIME.contains(mime)) {
            return;
        }
        List<String> allowed = MIME_BY_EXT.get(ext);
        if (allowed == null) {
            throw new ServiceException("不支持的文件类型：" + ext);
        }
        if (!allowed.contains(mime)) {
            throw new ServiceException("文件类型与扩展名不匹配");
        }
    }

    /**
     * 计算下一个版本号。
     *
     * @param talentId       人才ID
     * @param attachmentType 附件类型
     * @return 新版本号
     */
    private int nextVersion(Long talentId, String attachmentType) {
        LambdaQueryWrapper<TlTalentAttachment> wrapper = new LambdaQueryWrapper<TlTalentAttachment>()
            .eq(TlTalentAttachment::getTalentId, talentId)
            .eq(TlTalentAttachment::getAttachmentType, attachmentType);
        List<TlTalentAttachment> list = attachmentMapper.selectList(wrapper);
        int max = 0;
        for (TlTalentAttachment item : list) {
            max = Math.max(max, item.getVersion() == null ? 0 : item.getVersion());
        }
        return max + 1;
    }

    /**
     * 将同类型历史版本置为非当前版本。
     *
     * @param talentId       人才ID
     * @param attachmentType 附件类型
     */
    private void demoteCurrent(Long talentId, String attachmentType) {
        TlTalentAttachment demote = new TlTalentAttachment();
        demote.setIsCurrent("0");
        attachmentMapper.update(demote, new LambdaUpdateWrapper<TlTalentAttachment>()
            .eq(TlTalentAttachment::getTalentId, talentId)
            .eq(TlTalentAttachment::getAttachmentType, attachmentType)
            .eq(TlTalentAttachment::getIsCurrent, "1"));
    }

    /**
     * 加载人才，不存在直接抛业务异常。
     *
     * @param talentId 人才ID
     * @return 人才实体
     */
    private TlTalent loadTalent(Long talentId) {
        if (talentId == null) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        TlTalent talent = talentMapper.selectById(talentId);
        if (talent == null || "1".equals(talent.getDelFlag())) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        return talent;
    }

    /**
     * 构建扩展名 → MIME 映射。
     *
     * @return 映射表
     */
    private static Map<String, List<String>> buildMimeMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("pdf", List.of("application/pdf"));
        map.put("doc", List.of("application/msword"));
        map.put("docx", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip", "application/x-zip-compressed"));
        map.put("jpg", List.of("image/jpeg", "image/jpg"));
        map.put("jpeg", List.of("image/jpeg", "image/jpg"));
        map.put("png", List.of("image/png"));
        return Map.copyOf(map);
    }

}
