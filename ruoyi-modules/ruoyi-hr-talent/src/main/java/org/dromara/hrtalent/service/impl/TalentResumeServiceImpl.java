package org.dromara.hrtalent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.talent.ResumeDownloadBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeUploadBo;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentResume;
import org.dromara.hrtalent.domain.vo.talent.TalentResumeVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.ResumeParseStatusEnum;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.event.ResumeUploadedEvent;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.mapper.TalentResumeMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.service.talent.ITalentResumeService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 人才简历版本服务实现（SPEC-P4 §2.1，设计文档 §8.13、§8.8、§11.1、§21.7）。
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *     <li>上传：校验扩展名/MIME/大小 → 计算 SHA-256（重复文件提示）→ 生成新版本号 →
 *     写入私有对象存储 → 同一事务内「旧当前版本置否 + 插入新版本 + 同步主档当前简历ID」，
 *     数据库失败时清理已上传的孤立对象（§21.7：OSS 上传不与数据库事务完全原子）；</li>
 *     <li><b>绝不覆盖旧文件</b>：对象键以随机简历ID与版本号分段，旧对象保持可追溯；</li>
 *     <li>读取（列表/版本/下载）一律<b>先</b>做人才资源级鉴权，鉴权收敛在
 *     {@code ITalentProfileService#requireVisible}（内部即 {@code TalentScopeDomainService#checkTalentVisible}），
 *     本服务不另写授权规则（§11.1）；</li>
 *     <li><b>下载另设「授权级别」闸门</b>（§8.19 + §6）：可见范围只回答「能不能看到这条人才」，
 *     不回答「能不能取走简历文件」。因此下载时：超管直接放行；通过集团共享/归属公司/归属部门/人才负责人
 *     等<b>职责范围内</b>方式可见的放行；<b>可见性为「显式授权」（{@code explicit}）时，必须再校验共享授权级别
 *     达到 {@code attachment}（附件级）</b>——只被授予 summary（脱敏摘要）或 detail 的用户，
 *     即便持有 {@code talent:resume:download} 按钮权限也不得下载，避免把 summary/detail/attachment
 *     三级共享授权拉平；</li>
 *     <li>下载顺序固定为「用途校验 → 资源级鉴权（含授权级别闸门）→ 写审计 → 流式输出」，
 *     不使用预签名地址，响应体不出现对象存储地址（§8.8、§11.1）；</li>
 *     <li>审计明细只写简历ID、版本号、后缀与大小，<b>不写</b>原始文件名（可能含姓名）、对象键与正文。</li>
 * </ul>
 *
 * <p><b>注意</b>：本服务只维护 {@code hr_talent_resume}，<b>不</b>向 {@code hr_recruit_attachment}
 * 登记任何记录（§8.8）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentResumeServiceImpl implements ITalentResumeService {

    /**
     * 是否当前版本：是。
     */
    public static final String CURRENT_FLAG_YES = "1";

    /**
     * 是否当前版本：否。
     */
    public static final String CURRENT_FLAG_NO = "0";

    /**
     * 文件安全扫描初始状态（生产接入恶意文件扫描后由扫描任务推进，§8.8）。
     */
    public static final String SCAN_STATUS_PENDING = "pending";

    /**
     * 默认简历来源。
     */
    public static final String SOURCE_TYPE_UPLOAD = "upload";

    /**
     * 允许的简历来源编码（稳定编码，未知编码拒绝，fail-safe）。
     */
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of("upload", "import", "mail", "application");

    /**
     * 允许的 MIME 类型白名单；简历以文档与图片为主。
     * <p>浏览器对未知类型可能回传空值或 {@code application/octet-stream}，
     * 该情况按「无法判定」处理，仍受扩展名白名单与大小限制约束。</p>
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "application/rtf",
        "image/jpeg",
        "image/png",
        "image/gif",
        "application/zip",
        "application/x-zip-compressed");

    /**
     * 无法判定 MIME 时的回传值（按扩展名白名单兜底）。
     */
    private static final Set<String> UNDETERMINED_MIME_TYPES = Set.of(
        "", "application/octet-stream", "binary/octet-stream");

    /**
     * 用途（purpose）最大长度，与审计表 {@code purpose varchar(255)} 一致。
     */
    private static final int PURPOSE_MAX_LENGTH = 255;

    /**
     * 原始文件名最大长度，与 DDL {@code original_name varchar(255)} 一致。
     */
    private static final int ORIGINAL_NAME_MAX_LENGTH = 255;

    /**
     * 简历版本 Mapper。
     */
    private final TalentResumeMapper talentResumeMapper;

    /**
     * 人才主档 Mapper（仅用于同步冗余的「当前简历」指针）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才主档服务（人才资源级鉴权的唯一入口，内部委托 TalentScopeDomainService）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才可见范围与共享授权级别的唯一权威领域服务（本服务只调用其公开判定方法，不复制规则）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 对象存储访问封装（只保存对象键，不保存公网地址）。
     */
    private final HrTalentOssHelper hrTalentOssHelper;

    /**
     * 招聘与人才管理业务配置（扩展名白名单与大小上限）。
     */
    private final HrTalentProperties hrTalentProperties;

    /**
     * 敏感操作审计统一入口。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /**
     * 领域事件发布器（发布 {@link ResumeUploadedEvent}）。
     */
    private final ApplicationEventPublisher eventPublisher;

    /* ------------------------------------------------------------------ 列表 ------------------------------------------------------------------ */

    @Override
    public List<TalentResumeVo> listByTalent(Long talentId, TalentResumeQueryBo bo) {
        // 列表同样属于简历访问：先做资源级鉴权（fail-closed），再查库
        talentProfileService.requireVisible(talentId);
        return queryVersions(talentId, bo);
    }

    @Override
    public List<TalentResumeVo> versions(Long resumeId) {
        TalentResume resume = requireVisibleResume(resumeId);
        return queryVersions(resume.getTalentId(), null);
    }

    /* ------------------------------------------------------------------ 上传 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TalentResumeVo upload(Long talentId, TalentResumeUploadBo bo, MultipartFile file) {
        // 1) 资源级鉴权：不可见的人才一律拒绝上传
        talentProfileService.requireVisible(talentId);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String sourceType = resolveSourceType(bo == null ? null : bo.getSourceType());
        String originalName = resolveOriginalName(file.getOriginalFilename());
        String suffix = extractSuffix(originalName);
        // 2) 扩展名 / MIME / 大小校验，失败统一提示 HR_RESUME_001
        requireExtension(suffix);
        requireMimeType(file.getContentType());
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new ServiceException("上传文件不能为空");
        }
        requireSize(content.length);
        String fileHash = sha256Hex(content);

        Long resumeId = IdUtil.getSnowflakeNextId();
        int versionNo = nextVersionNo(talentId);
        // 3) 对象键只由服务端生成：以人才ID + 简历ID + 版本号分段，不使用原文件名（§15.3）
        String ossId = hrTalentOssHelper.buildResumeKey(talentId, resumeId, versionNo, suffix);
        // 重复文件提示：同一人才已存在内容完全相同的简历（非阻断，file_hash 无唯一约束）
        TalentResume duplicate = findDuplicate(talentId, fileHash);

        // 4) 先上传对象，再写业务记录（§21.7）
        hrTalentOssHelper.put(ossId, content);
        try {
            // 5) 同一事务内：旧当前版本置否 + 插入新版本 + 同步主档当前简历指针
            demoteCurrentVersions(talentId);
            TalentResume entity = new TalentResume();
            entity.setResumeId(resumeId);
            entity.setTalentId(talentId);
            entity.setVersionNo(versionNo);
            entity.setOssId(ossId);
            entity.setOriginalName(originalName);
            entity.setFileSuffix(suffix);
            entity.setFileSize((long) content.length);
            entity.setFileHash(fileHash);
            entity.setCurrentFlag(CURRENT_FLAG_YES);
            entity.setScanStatus(SCAN_STATUS_PENDING);
            entity.setParseStatus(ResumeParseStatusEnum.PENDING.getCode());
            entity.setReviewStatus(ResumeParseStatusEnum.PENDING.getCode());
            entity.setSourceType(sourceType);
            entity.setUploadedBy(currentUserId());
            entity.setUploadedTime(LocalDateTime.now());
            entity.setRemark(bo == null ? null : bo.getRemark());
            int rows = talentResumeMapper.insert(entity);
            if (rows == 0) {
                throw new ServiceException("简历登记失败，请重试");
            }
            syncProfileCurrentResume(talentId, resumeId);
            // 6) 事务内发布领域事件：安全扫描、解析任务与完整度计算的接入点（§21.6）
            eventPublisher.publishEvent(new ResumeUploadedEvent(resumeId, talentId, versionNo, currentUserId()));
            log.info("新增人才简历版本, resumeId={}, talentId={}, versionNo={}, size={}",
                resumeId, talentId, versionNo, content.length);
        } catch (RuntimeException e) {
            // 数据库写入失败：清理已上传的孤立对象，避免产生无归属文件
            hrTalentOssHelper.delete(ossId);
            throw e;
        }
        TalentResumeVo vo = talentResumeMapper.selectVoById(resumeId);
        if (vo == null) {
            throw new ServiceException("简历登记失败，请重试");
        }
        if (duplicate != null) {
            vo.setDuplicateResumeId(duplicate.getResumeId());
            vo.setDuplicateFileHint("该人才已存在内容完全相同的简历（第 " + duplicate.getVersionNo()
                + " 版），已按新版本保存，请确认是否为重复上传");
        }
        return vo;
    }

    /* ------------------------------------------------------------------ 当前版本 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrent(Long resumeId) {
        TalentResume resume = requireVisibleResume(resumeId);
        if (CURRENT_FLAG_YES.equals(resume.getCurrentFlag())) {
            log.info("简历已是当前版本, resumeId={}", resumeId);
            return;
        }
        demoteCurrentVersions(resume.getTalentId());
        LambdaUpdateWrapper<TalentResume> wrapper = new LambdaUpdateWrapper<TalentResume>()
            .eq(TalentResume::getResumeId, resumeId)
            .set(TalentResume::getCurrentFlag, CURRENT_FLAG_YES);
        int rows = talentResumeMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("指定当前简历失败，请刷新后重试");
        }
        syncProfileCurrentResume(resume.getTalentId(), resumeId);
        log.info("切换人才当前简历, talentId={}, resumeId={}, versionNo={}",
            resume.getTalentId(), resumeId, resume.getVersionNo());
    }

    /* ------------------------------------------------------------------ 下载 ------------------------------------------------------------------ */

    @Override
    public void download(Long resumeId, ResumeDownloadBo bo, HttpServletResponse response) {
        String purpose = bo == null || bo.getPurpose() == null ? null : bo.getPurpose().trim();
        if (StringUtils.isBlank(purpose)) {
            // 用途为空：先写 denied 审计再拒绝，保证被拒绝的敏感访问同样留痕（§11.1）
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
                SensitiveAuditRecorder.BIZ_TALENT, null, purpose, SensitiveAuditRecorder.RESULT_DENIED,
                detail(null, resumeId));
            throw new ServiceException("用途（purpose）不能为空，禁止下载简历");
        }
        if (purpose.length() > PURPOSE_MAX_LENGTH) {
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
                SensitiveAuditRecorder.BIZ_TALENT, null, purpose.substring(0, PURPOSE_MAX_LENGTH),
                SensitiveAuditRecorder.RESULT_DENIED, detail(null, resumeId));
            throw new ServiceException("用途长度不能超过 " + PURPOSE_MAX_LENGTH + " 个字符");
        }
        TalentResume resume = talentResumeMapper.selectById(resumeId);
        if (resume == null) {
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
                SensitiveAuditRecorder.BIZ_TALENT, null, purpose, SensitiveAuditRecorder.RESULT_FAILED,
                detail(null, resumeId));
            throw new ServiceException("简历不存在或已删除");
        }
        // 资源级鉴权 + 授权级别闸门：不可见或授权级别不足一律拒绝并写 denied 审计
        try {
            checkDownloadAuthorization(resume);
        } catch (ServiceException e) {
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
                SensitiveAuditRecorder.BIZ_TALENT, resume.getTalentId(), purpose,
                SensitiveAuditRecorder.RESULT_DENIED, detail(resume, resumeId));
            throw e;
        }
        // 鉴权通过 → 先写审计 → 再流式返回（SPEC-P4 §2.1 固定顺序）
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
            SensitiveAuditRecorder.BIZ_TALENT, resume.getTalentId(), purpose,
            SensitiveAuditRecorder.RESULT_SUCCESS, detail(resume, resumeId));
        try {
            writeStream(resume, response);
        } catch (RuntimeException e) {
            // 输出阶段失败：追加一条 failed 留痕，便于区分「已授权但传输失败」
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD,
                SensitiveAuditRecorder.BIZ_TALENT, resume.getTalentId(), purpose,
                SensitiveAuditRecorder.RESULT_FAILED, detail(resume, resumeId));
            throw e;
        }
        log.info("简历受控下载完成, resumeId={}, talentId={}, versionNo={}",
            resumeId, resume.getTalentId(), resume.getVersionNo());
    }

    /* ------------------------------------------------------------------ 鉴权入口 ------------------------------------------------------------------ */

    @Override
    public TalentResume requireVisibleResume(Long resumeId) {
        if (resumeId == null) {
            throw new ServiceException("简历ID不能为空");
        }
        TalentResume resume = talentResumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new ServiceException("简历不存在或已删除");
        }
        // 鉴权唯一权威：经人才主档服务收敛到 TalentScopeDomainService#checkTalentVisible
        talentProfileService.requireVisible(resume.getTalentId());
        return resume;
    }

    /**
     * 简历下载的鉴权口径（设计文档 §8.19 三级共享授权 + §6 角色职责范围）。
     *
     * <p><b>为什么下载要比列表多一道闸门</b>：人才可见范围（summary 级显式授权即可放行）只回答
     * 「能不能看到这条人才」，不回答「能不能取走简历文件」；§8.19 明确「共享只扩大查看范围，
     * <b>不自动授予</b>电话明文、附件下载、背调和导出权限」。所以：
     * 只要访问<b>完全依赖共享授权</b>（可见性为 {@code explicit}），下载就必须达到
     * {@link TalentPermissionLevelEnum#ATTACHMENT}（附件级）。</p>
     *
     * <p><b>为什么不无脑要求 attachment 级</b>：{@code ITalentProfileService} 的可见性判定里
     * 集团共享、归属公司、归属部门、人才负责人都是<b>职责范围内</b>的正常可见（§6 语义：
     * 职责范围内可看，仅当访问完全依赖共享授权时才要求授权级别）。若对所有人一律要求显式附件授权，
     * 会把正常职责范围内的招聘专员/负责人也挡在门外。</p>
     *
     * <p>判定顺序（不修改 {@code TalentScopeDomainService}，只调用其公开 API）：</p>
     * <ol>
     *     <li>可见性闸门：{@code ITalentProfileService#requireVisible}（内部即
     *     {@code TalentScopeDomainService#checkTalentVisible}），不可见直接抛错；</li>
     *     <li>超管（{@code scope.unlimited()}）放行；</li>
     *     <li>可见且可见性<b>不是</b> {@code explicit} → 职责范围内，放行；</li>
     *     <li>其余（显式授权）→ {@code checkPermissionLevel(talentId, ATTACHMENT)}，级别不足抛中文提示。</li>
     * </ol>
     *
     * @param resume 简历实体（必须是已通过主键查询得到的记录）
     * @throws ServiceException 人才不可见或共享授权级别不足
     */
    private void checkDownloadAuthorization(TalentResume resume) {
        // 第一道闸门：人才资源级鉴权（可见范围），唯一权威仍是 TalentScopeDomainService
        TalentProfile profile = talentProfileService.requireVisible(resume.getTalentId());
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        if (scope.unlimited()) {
            // 超管：全平台放行
            return;
        }
        TalentScopeDomainService.TalentScopeTarget target = new TalentScopeDomainService.TalentScopeTarget(
            profile.getTalentId(), profile.getOwnerId(), profile.getOwnerDeptId(),
            profile.getVisibilityType(), profile.getTalentStatus(), profile.getDelFlag());
        if (!TalentVisibilityTypeEnum.EXPLICIT.getCode().equals(profile.getVisibilityType())
            && talentScopeDomainService.visible(target, scope)) {
            // 集团共享 / 归属公司 / 归属部门 / 人才负责人：职责范围内可见，按原口径放行
            return;
        }
        // 显式授权：完全依赖共享授权，下载简历必须达到附件级（§8.19）
        talentScopeDomainService.checkPermissionLevel(profile.getTalentId(), TalentPermissionLevelEnum.ATTACHMENT);
    }

    /* ------------------------------------------------------------------ 内部方法：查询与版本 ------------------------------------------------------------------ */

    /**
     * 按人才查询简历版本列表。
     *
     * <p><b>调用前提</b>：调用方必须已完成该人才资源级鉴权，本方法不再重复鉴权。</p>
     *
     * @param talentId 人才主档ID
     * @param bo       可选过滤条件
     * @return 简历版本列表（按版本号倒序）
     */
    private List<TalentResumeVo> queryVersions(Long talentId, TalentResumeQueryBo bo) {
        TalentResumeQueryBo query = bo == null ? new TalentResumeQueryBo() : bo;
        String currentFlag = StringUtils.isBlank(query.getCurrentFlag()) ? null : query.getCurrentFlag().trim();
        if (currentFlag != null && !CURRENT_FLAG_YES.equals(currentFlag) && !CURRENT_FLAG_NO.equals(currentFlag)) {
            throw new ServiceException("是否当前版本只能为 0 或 1");
        }
        LambdaQueryWrapper<TalentResume> wrapper = new LambdaQueryWrapper<TalentResume>()
            .eq(TalentResume::getTalentId, talentId)
            .eq(currentFlag != null, TalentResume::getCurrentFlag, currentFlag)
            .eq(StringUtils.isNotBlank(query.getParseStatus()), TalentResume::getParseStatus, query.getParseStatus())
            .eq(StringUtils.isNotBlank(query.getReviewStatus()), TalentResume::getReviewStatus, query.getReviewStatus())
            .eq(StringUtils.isNotBlank(query.getScanStatus()), TalentResume::getScanStatus, query.getScanStatus())
            .eq(StringUtils.isNotBlank(query.getFileSuffix()), TalentResume::getFileSuffix, query.getFileSuffix())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentResume::getSourceType, query.getSourceType())
            .eq(query.getUploadedBy() != null, TalentResume::getUploadedBy, query.getUploadedBy())
            .ge(query.getUploadedTimeBegin() != null, TalentResume::getUploadedTime, query.getUploadedTimeBegin())
            .le(query.getUploadedTimeEnd() != null, TalentResume::getUploadedTime, query.getUploadedTimeEnd())
            .orderByDesc(TalentResume::getVersionNo)
            .orderByDesc(TalentResume::getResumeId);
        return talentResumeMapper.selectVoList(wrapper);
    }

    /**
     * 计算新版本号：同一人才已使用的最大版本号 + 1（含逻辑删除记录，不复用版本号）。
     *
     * @param talentId 人才主档ID
     * @return 新版本号（首次为 1）
     */
    private int nextVersionNo(Long talentId) {
        Integer max = talentResumeMapper.selectMaxVersionNo(talentId);
        return (max == null ? 0 : max) + 1;
    }

    /**
     * 将同一人才的旧当前版本标识置否（§8.13：新版本不覆盖旧文件）。
     *
     * @param talentId 人才主档ID
     */
    private void demoteCurrentVersions(Long talentId) {
        LambdaUpdateWrapper<TalentResume> wrapper = new LambdaUpdateWrapper<TalentResume>()
            .eq(TalentResume::getTalentId, talentId)
            .eq(TalentResume::getCurrentFlag, CURRENT_FLAG_YES)
            .set(TalentResume::getCurrentFlag, CURRENT_FLAG_NO);
        talentResumeMapper.update(null, wrapper);
    }

    /**
     * 查找同一人才中内容完全相同的既有简历（重复上传提示，非阻断）。
     *
     * @param talentId 人才主档ID
     * @param fileHash 文件哈希
     * @return 命中的最新版本；无命中返回 null
     */
    private TalentResume findDuplicate(Long talentId, String fileHash) {
        if (StringUtils.isBlank(fileHash)) {
            return null;
        }
        List<TalentResume> hits = talentResumeMapper.selectList(new LambdaQueryWrapper<TalentResume>()
            .eq(TalentResume::getTalentId, talentId)
            .eq(TalentResume::getFileHash, fileHash)
            .orderByDesc(TalentResume::getVersionNo));
        return hits == null || hits.isEmpty() ? null : hits.get(0);
    }

    /**
     * 同步主档冗余字段 {@code current_resume_id} 与 {@code resume_update_time}。
     *
     * <p><b>说明</b>：人才的权威当前简历仍以 {@code hr_talent_resume.current_flag} 为准；
     * 这里同步的是人才详情页展示用的冗余指针（设计文档 §9.2 人才主档字段）。
     * 使用条件更新而非乐观锁更新，避免与主档编辑事务相互干扰。</p>
     *
     * @param talentId 人才主档ID
     * @param resumeId 简历版本ID
     */
    private void syncProfileCurrentResume(Long talentId, Long resumeId) {
        LambdaUpdateWrapper<TalentProfile> wrapper = new LambdaUpdateWrapper<TalentProfile>()
            .eq(TalentProfile::getTalentId, talentId)
            .set(TalentProfile::getCurrentResumeId, resumeId)
            .set(TalentProfile::getResumeUpdateTime, LocalDateTime.now());
        talentProfileMapper.update(null, wrapper);
    }

    /* ------------------------------------------------------------------ 内部方法：流式输出 ------------------------------------------------------------------ */

    /**
     * 以鉴权后的流式响应输出简历；<b>不</b>返回任何对象存储或签名地址（§11.1）。
     *
     * @param resume   简历实体
     * @param response HTTP 响应
     */
    private void writeStream(TalentResume resume, HttpServletResponse response) {
        response.reset();
        response.setContentType(resolveContentType(resume.getFileSuffix()));
        // 防止浏览器按内容嗅探把简历当脚本执行
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", contentDisposition(resume.getOriginalName()));
        if (resume.getFileSize() != null && resume.getFileSize() > 0) {
            response.setContentLengthLong(resume.getFileSize());
        }
        try (ServletOutputStream out = response.getOutputStream()) {
            hrTalentOssHelper.get(resume.getOssId(), out);
            out.flush();
        } catch (IOException e) {
            // 不记录对象键与文件名
            log.error("简历流式输出失败, resumeId={}, exception={}", resume.getResumeId(),
                e.getClass().getSimpleName());
            throw new ServiceException("简历读取失败");
        }
    }

    /* ------------------------------------------------------------------ 内部方法：校验 ------------------------------------------------------------------ */

    /**
     * 解析简历来源编码：默认 {@code upload}，未知编码拒绝（fail-safe）。
     *
     * @param sourceType 来源编码，可为空
     * @return 规范化后的来源编码
     */
    private String resolveSourceType(String sourceType) {
        if (StringUtils.isBlank(sourceType)) {
            return SOURCE_TYPE_UPLOAD;
        }
        String value = sourceType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SOURCE_TYPES.contains(value)) {
            throw new ServiceException("不支持的简历来源：" + value);
        }
        return value;
    }

    /**
     * 归一化原始文件名：去掉客户端可能带上的路径段，并按列长度截断（保留后缀）。
     *
     * @param originalFilename 原始文件名
     * @return 归一化后的文件名
     */
    private String resolveOriginalName(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
        String name = originalFilename.trim().replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
        if (name.length() <= ORIGINAL_NAME_MAX_LENGTH) {
            return name;
        }
        String suffix = extractSuffix(name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        int maxBaseLength = ORIGINAL_NAME_MAX_LENGTH - suffix.length() - 1;
        if (maxBaseLength <= 0) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
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
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
        String suffix = fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        if (suffix.isEmpty() || suffix.length() > 32) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
        return suffix;
    }

    /**
     * 校验扩展名是否在配置白名单内。
     * <p>简历与通用附件复用同一份白名单配置 {@code hrtalent.attachment-allow-extensions}。</p>
     *
     * @param suffix 文件后缀
     */
    private void requireExtension(String suffix) {
        if (!hrTalentProperties.isAttachmentExtensionAllowed(suffix)) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
    }

    /**
     * 校验 MIME 类型；无法判定的回传值按扩展名白名单兜底。
     *
     * @param contentType 上传内容类型
     */
    private void requireMimeType(String contentType) {
        String value = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        if (UNDETERMINED_MIME_TYPES.contains(value)) {
            return;
        }
        if (!ALLOWED_MIME_TYPES.contains(value)) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
    }

    /**
     * 校验单文件大小上限。
     *
     * @param size 文件字节数
     */
    private void requireSize(long size) {
        long max = hrTalentProperties.getAttachmentMaxSize() == null
            ? Long.MAX_VALUE
            : hrTalentProperties.getAttachmentMaxSize().toBytes();
        if (size > max) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_RESUME_001);
        }
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
            log.error("简历内容读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("简历内容读取失败");
        }
    }

    /**
     * 计算 SHA-256 十六进制哈希（重复文件提示用，非唯一约束）。
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
            case "txt" -> "text/plain;charset=UTF-8";
            case "rtf" -> "application/rtf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /**
     * 构造 Content-Disposition（简历一律以附件方式下载，避免浏览器内联渲染）。
     *
     * @param originalFileName 原始文件名
     * @return Content-Disposition 值
     */
    private String contentDisposition(String originalFileName) {
        String name = StringUtils.isBlank(originalFileName) ? "resume" : originalFileName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment;filename*=UTF-8''" + encoded;
    }

    /**
     * 构造审计明细：只含定位与版本等非敏感字段。
     *
     * <p><b>禁止</b>写入原始文件名（可能含姓名）、对象键、联系方式与简历正文。</p>
     *
     * @param resume   简历实体，可为空（用途校验失败时尚无实体）
     * @param resumeId 简历版本ID
     * @return 脱敏 JSON 明细
     */
    private String detail(TalentResume resume, Long resumeId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("resumeId", resume == null ? resumeId : resume.getResumeId());
        if (resume != null) {
            detail.put("talentId", resume.getTalentId());
            detail.put("versionNo", resume.getVersionNo());
            detail.put("fileSuffix", resume.getFileSuffix());
            detail.put("fileSize", resume.getFileSize());
            detail.put("currentFlag", resume.getCurrentFlag());
        }
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
                json.append('"').append(String.valueOf(value)
                    .replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return json.append('}').toString();
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
