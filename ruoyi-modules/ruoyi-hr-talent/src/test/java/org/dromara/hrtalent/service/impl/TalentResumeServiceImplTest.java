package org.dromara.hrtalent.service.impl;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.talent.ParseConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.ResumeDownloadBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeUploadBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkQueryBo;
import org.dromara.hrtalent.domain.entity.TalentParseResult;
import org.dromara.hrtalent.domain.entity.TalentParseTask;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentResume;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentParseTaskVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentResumeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.event.ResumeUploadedEvent;
import org.dromara.hrtalent.mapper.TalentParseResultMapper;
import org.dromara.hrtalent.mapper.TalentParseTaskMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.mapper.TalentResumeMapper;
import org.dromara.hrtalent.service.talent.ITalentExperienceService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.service.talent.ITalentResumeService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历版本与解析复核领域规则单元测试（SPEC-P4 §2.1 / §4）。
 *
 * <p><b>覆盖的硬性规则</b>：</p>
 * <ul>
 *     <li>上传：扩展名/MIME/大小/来源校验、<b>新版本不覆盖旧文件</b>（版本号递增 + 旧当前版本置否）、
 *     重复文件提示、资源级鉴权失败即拒绝且不写对象存储；</li>
 *     <li>下载：用途必填并写 {@code denied} 审计、资源级鉴权在写出字节之前完成、
 *     鉴权通过后先写审计再流式输出、<b>不产生任何预签名地址</b>；</li>
 *     <li>解析：只创建异步任务（请求内不解析）、引擎未启用时任务置 {@code failed} 并给中文提示、
 *     已存在进行中任务时复用；</li>
 *     <li>复核：低置信度默认不勾选、越权 {@code resultId} 被拒绝、
 *     正式字段只有在人工确认后才写入主档、经历类字段只记结论不静默丢弃。</li>
 * </ul>
 *
 * <p><b>说明</b>：Mapper 与服务替身全部使用 JDK 动态代理 / 匿名子类手工构造，
 * 不依赖 Mockito（当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentResumeServiceImplTest {

    /**
     * 固定人才主档ID。
     */
    private static final Long TALENT_ID = 1001L;

    /**
     * 业务配置（真实实例，便于切换解析开关与大小上限）。
     */
    private final HrTalentProperties properties = new HrTalentProperties();

    /**
     * 内存简历库。
     */
    private final List<TalentResume> resumes = new ArrayList<>();

    /**
     * 内存解析任务库。
     */
    private final List<TalentParseTask> tasks = new ArrayList<>();

    /**
     * 内存候选解析结果库。
     */
    private final List<TalentParseResult> results = new ArrayList<>();

    /**
     * 捕获的审计事件（格式：eventType|result）。
     */
    private final List<String> audits = new ArrayList<>();

    /**
     * 捕获的对象存储写入键。
     */
    private final List<String> ossPuts = new ArrayList<>();

    /**
     * 捕获的对象存储读取键。
     */
    private final List<String> ossGets = new ArrayList<>();

    /**
     * 捕获的预签名调用键（用于断言「绝不产生签名地址」）。
     */
    private final List<String> ossPresigns = new ArrayList<>();

    /**
     * 捕获的领域事件。
     */
    private final List<Object> events = new ArrayList<>();

    /**
     * 捕获的主档更新入参。
     */
    private final List<TalentProfileBo> profileUpdates = new ArrayList<>();

    /**
     * 捕获的响应头。
     */
    private final Map<String, String> responseHeaders = new HashMap<>();

    /**
     * 简历 Mapper 替身每次 {@code selectList} 的返回。
     */
    private List<TalentResume> resumeSelectListResult = new ArrayList<>();

    /**
     * 简历 Mapper 替身返回的最大版本号。
     */
    private Integer maxVersionNo = 0;

    /**
     * 简历 Mapper 替身 {@code update} 的调用次数。
     */
    private int resumeUpdateCount;

    /**
     * 候选结果 Mapper 替身 {@code selectCount} 的返回（待复核条数）。
     */
    private long pendingReviewCount;

    /**
     * 主档 Mapper 替身 {@code update} 的调用次数。
     */
    private int profileMapperUpdateCount;

    /**
     * 人才可见性：非空时 {@code requireVisible} 抛出该异常（模拟不可见）。
     */
    private ServiceException visibilityFailure;

    /**
     * 可见范围条件：是否超管（决定 {@code scope.unlimited()}）。
     */
    private boolean scopeUnlimited;

    /**
     * 可见范围条件：{@code visible(target, scope)} 的返回。
     */
    private boolean scopeVisible = true;

    /**
     * 共享授权级别：是否已授予附件级（attachment）。
     */
    private boolean attachmentGranted;

    /**
     * 捕获的授权级别校验请求（断言「职责范围内不查授权级别」）。
     */
    private final List<TalentPermissionLevelEnum> checkedLevels = new ArrayList<>();

    /**
     * 经历域替身：{@code listEducation} 的返回（去重检查用）。
     */
    private List<TalentEducationVo> existingEducations = new ArrayList<>();

    /**
     * 经历域替身：{@code listWork} 的返回（去重检查用）。
     */
    private List<TalentWorkVo> existingWorks = new ArrayList<>();

    /**
     * 经历域替身：{@code listProject} 的返回（去重检查用）。
     */
    private List<TalentProjectVo> existingProjects = new ArrayList<>();

    /**
     * 捕获的正式教育经历新增入参（B 线经历域）。
     */
    private final List<TalentEducationBo> createdEducations = new ArrayList<>();

    /**
     * 捕获的正式工作经历新增入参（B 线经历域）。
     */
    private final List<TalentWorkBo> createdWorks = new ArrayList<>();

    /**
     * 捕获的正式项目经历新增入参（B 线经历域）。
     */
    private final List<TalentProjectBo> createdProjects = new ArrayList<>();

    /**
     * 当前人才主档快照。
     */
    private TalentProfile profile;

    /**
     * 被测简历服务。
     */
    private TalentResumeServiceImpl resumeService;

    /**
     * 被测解析服务。
     */
    private TalentParseServiceImpl parseService;

    /**
     * 注册被测实体在 MyBatis-Plus 中的表元数据。
     *
     * <p>脱离 Spring 容器时 {@code TableInfoHelper} 尚无实体缓存，而 Lambda 条件构造器会立即解析列名，
     * 因此显式注册，仅用于让替身测试能构造 SQL 片段，不涉及任何数据库连接。</p>
     */
    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(TalentResume.class, TalentParseTask.class,
            TalentParseResult.class, TalentProfile.class)) {
            // 显式再装一次 Lambda 列缓存：initTableInfo 命中已有 TableInfo 时会提前返回而不重装缓存
            LambdaUtils.installCache(TableInfoHelper.initTableInfo(assistant, entity));
        }
    }

    @BeforeEach
    void setUp() {
        resumes.clear();
        tasks.clear();
        results.clear();
        audits.clear();
        ossPuts.clear();
        ossGets.clear();
        ossPresigns.clear();
        events.clear();
        profileUpdates.clear();
        responseHeaders.clear();
        resumeSelectListResult = new ArrayList<>();
        maxVersionNo = 0;
        resumeUpdateCount = 0;
        pendingReviewCount = 0;
        profileMapperUpdateCount = 0;
        visibilityFailure = null;
        scopeUnlimited = false;
        scopeVisible = true;
        attachmentGranted = false;
        checkedLevels.clear();
        existingEducations = new ArrayList<>();
        existingWorks = new ArrayList<>();
        existingProjects = new ArrayList<>();
        createdEducations.clear();
        createdWorks.clear();
        createdProjects.clear();

        profile = new TalentProfile();
        profile.setTalentId(TALENT_ID);
        profile.setName("张三");
        profile.setVersion(3);
        profile.setTalentStatus("active");
        profile.setVisibilityType("group");
        profile.setDelFlag("0");

        TalentResumeMapper resumeMapper = resumeMapperStub();
        ITalentProfileService profileService = profileServiceStub();
        resumeService = new TalentResumeServiceImpl(resumeMapper, profileMapperStub(), profileService,
            scopeServiceStub(), ossHelperStub(), properties, auditRecorderStub(), eventPublisherStub());
        parseService = new TalentParseServiceImpl(taskMapperStub(), resultMapperStub(), resumeMapper,
            resumeServiceStub(), profileService, experienceServiceStub(), properties);
    }

    /* ------------------------------------------------------------------ 上传规则 ------------------------------------------------------------------ */

    @Test
    @DisplayName("上传：不允许的扩展名被拒绝并提示 HR_RESUME_001")
    void shouldRejectDisallowedExtension() {
        MultipartFile file = file("resume.exe", "application/octet-stream", "hello");

        ServiceException ex = assertThrows(ServiceException.class, () -> resumeService.upload(TALENT_ID, null, file));
        assertEquals(HrTalentErrorCode.MSG_HR_RESUME_001, ex.getMessage());
        assertTrue(ossPuts.isEmpty(), "校验失败时不得写入对象存储");
    }

    @Test
    @DisplayName("上传：超过大小上限被拒绝")
    void shouldRejectOversizeFile() {
        properties.setAttachmentMaxSize(DataSize.ofBytes(4));
        MultipartFile file = file("resume.pdf", "application/pdf", "0123456789");

        ServiceException ex = assertThrows(ServiceException.class, () -> resumeService.upload(TALENT_ID, null, file));
        assertEquals(HrTalentErrorCode.MSG_HR_RESUME_001, ex.getMessage());
        assertTrue(ossPuts.isEmpty());
    }

    @Test
    @DisplayName("上传：未知来源编码被拒绝")
    void shouldRejectUnknownSourceType() {
        TalentResumeUploadBo bo = new TalentResumeUploadBo();
        bo.setSourceType("third_party");
        MultipartFile file = file("resume.pdf", "application/pdf", "hello");

        ServiceException ex = assertThrows(ServiceException.class, () -> resumeService.upload(TALENT_ID, bo, file));
        assertEquals("不支持的简历来源：third_party", ex.getMessage());
    }

    @Test
    @DisplayName("上传：空文件被拒绝")
    void shouldRejectEmptyFile() {
        ServiceException ex = assertThrows(ServiceException.class, () -> resumeService.upload(TALENT_ID, null, null));
        assertEquals("上传文件不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("上传：人才不可见时拒绝且不写对象存储")
    void shouldRejectUploadWhenTalentInvisible() {
        visibilityFailure = new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_002);
        MultipartFile file = file("resume.pdf", "application/pdf", "hello");

        ServiceException ex = assertThrows(ServiceException.class, () -> resumeService.upload(TALENT_ID, null, file));
        assertEquals(HrTalentErrorCode.MSG_HR_TALENT_002, ex.getMessage());
        assertTrue(ossPuts.isEmpty());
        assertTrue(resumes.isEmpty());
    }

    @Test
    @DisplayName("上传：默认创建新版本且不覆盖旧文件")
    void shouldCreateNewVersionWithoutOverwriting() throws Exception {
        maxVersionNo = 2;
        String content = "张三的简历内容";
        MultipartFile file = file("张三-简历.pdf", "application/pdf", content);

        TalentResumeVo vo = resumeService.upload(TALENT_ID, null, file);

        assertEquals(1, resumes.size());
        TalentResume saved = resumes.get(0);
        assertEquals(3, saved.getVersionNo(), "版本号必须在旧最大版本号上单调递增");
        assertEquals("1", saved.getCurrentFlag());
        assertEquals("pending", saved.getScanStatus());
        assertEquals("pending", saved.getParseStatus());
        assertEquals("upload", saved.getSourceType());
        assertEquals(sha256(content), saved.getFileHash());
        assertTrue(saved.getOssId().contains("/resume/" + TALENT_ID + "/" + saved.getResumeId() + "/v3/original.pdf"),
            "对象键必须包含人才、简历与版本分段");
        assertFalse(saved.getOssId().contains("张三"), "对象键不得包含原文件名");
        assertNotNull(vo);
        assertEquals("/talent/resumes/" + saved.getResumeId() + "/download", vo.getDownloadApi());
        assertTrue(resumeUpdateCount > 0, "旧当前版本必须被置否");
        assertEquals(1, profileMapperUpdateCount, "必须同步主档当前简历指针");
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof ResumeUploadedEvent);
        ResumeUploadedEvent event = (ResumeUploadedEvent) events.get(0);
        assertEquals(3, event.versionNo());
        assertEquals(TALENT_ID, event.talentId());
    }

    @Test
    @DisplayName("上传：同人才同哈希文件给出重复提示但不阻断")
    void shouldHintDuplicateFile() throws Exception {
        maxVersionNo = 1;
        String content = "重复简历内容";
        TalentResume exist = resume(2001L, 1, "0");
        exist.setFileHash(sha256(content));
        resumes.add(exist);
        resumeSelectListResult = List.of(exist);
        MultipartFile file = file("resume.pdf", "application/pdf", content);

        TalentResumeVo vo = resumeService.upload(TALENT_ID, null, file);

        assertEquals(2001L, vo.getDuplicateResumeId());
        assertNotNull(vo.getDuplicateFileHint());
        assertTrue(vo.getDuplicateFileHint().contains("内容完全相同"));
        assertEquals(2, resumes.size(), "重复文件仍应保存为新版本");
    }

    /* ------------------------------------------------------------------ 下载规则 ------------------------------------------------------------------ */

    @Test
    @DisplayName("下载：用途为空时拒绝并写 denied 审计")
    void shouldRejectDownloadWithoutPurpose() {
        TalentResume exist = resume(3001L, 1, "1");
        resumes.add(exist);
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("   ");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resumeService.download(3001L, bo, responseStub(new ByteArrayOutputStream())));
        assertEquals("用途（purpose）不能为空，禁止下载简历", ex.getMessage());
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_DENIED), audits);
        assertTrue(ossGets.isEmpty(), "用途为空时不得读取对象存储");
    }

    @Test
    @DisplayName("下载：人才不可见时拒绝并写 denied 审计")
    void shouldRejectDownloadWhenTalentInvisible() {
        resumes.add(resume(3002L, 1, "1"));
        visibilityFailure = new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_002);
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("面试评估");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resumeService.download(3002L, bo, responseStub(new ByteArrayOutputStream())));
        assertEquals(HrTalentErrorCode.MSG_HR_TALENT_002, ex.getMessage());
        assertTrue(audits.contains(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_DENIED));
        assertTrue(ossGets.isEmpty(), "鉴权失败时不得读取对象存储");
        assertTrue(ossPresigns.isEmpty(), "绝不产生对象存储签名地址");
    }

    @Test
    @DisplayName("下载：鉴权通过后先写审计再流式输出，不产生签名地址")
    void shouldStreamAfterAuthzAndAudit() {
        TalentResume exist = resume(3003L, 2, "1");
        exist.setOriginalName("张三-简历.pdf");
        exist.setFileSuffix("pdf");
        exist.setOssId("hr-talent-private/resume/1001/3003/v2/original.pdf");
        extracts.put(3003L, "PDF-BYTES");
        resumes.add(exist);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("用人部门评估");

        resumeService.download(3003L, bo, responseStub(captured));

        assertEquals("PDF-BYTES", captured.toString(StandardCharsets.UTF_8));
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_SUCCESS), audits, "鉴权通过后必须先写成功审计");
        assertEquals(List.of(exist.getOssId()), ossGets);
        assertTrue(ossPresigns.isEmpty(), "下载不得使用预签名地址");
        assertEquals("attachment;filename*=UTF-8''%E5%BC%A0%E4%B8%89-%E7%AE%80%E5%8E%86.pdf",
            responseHeaders.get("Content-Disposition"));
        assertEquals("application/pdf", responseHeaders.get("Content-Type"));
        assertEquals("nosniff", responseHeaders.get("X-Content-Type-Options"));
    }

    @Test
    @DisplayName("下载：简历不存在时写 failed 审计")
    void shouldAuditFailedWhenResumeMissing() {
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("查看");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resumeService.download(9999L, bo, responseStub(new ByteArrayOutputStream())));
        assertEquals("简历不存在或已删除", ex.getMessage());
        assertTrue(audits.contains(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_FAILED));
    }

    /* ------------------------------------------------------------------ 下载授权级别闸门（§8.19 + §6） ------------------------------------------------------------------ */

    @Test
    @DisplayName("下载授权级别：显式共享仅 summary 级授权时拒绝下载")
    void shouldDenyDownloadWhenOnlySummaryGrant() {
        resumes.add(resume(3201L, 1, "1"));
        // 可见性为「显式授权」，且未授予附件级：可见范围能过，但下载必须被授权级别挡住
        profile.setVisibilityType("explicit");
        attachmentGranted = false;
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("外部推荐评估");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resumeService.download(3201L, bo, responseStub(new ByteArrayOutputStream())));
        assertEquals(TalentScopeDomainService.DENY_PERMISSION_LEVEL, ex.getMessage());
        assertEquals(List.of(TalentPermissionLevelEnum.ATTACHMENT), checkedLevels,
            "显式共享必须校验附件级授权");
        assertTrue(audits.contains(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_DENIED), "级别不足必须留 denied 审计");
        assertTrue(ossGets.isEmpty(), "授权级别不足时不得读取对象存储");
        assertTrue(ossPresigns.isEmpty());
    }

    @Test
    @DisplayName("下载授权级别：显式共享且已授予 attachment 级时放行")
    void shouldAllowDownloadWhenAttachmentGranted() {
        TalentResume exist = resume(3202L, 1, "1");
        exist.setOssId("hr-talent-private/resume/1001/3202/v1/original.pdf");
        extracts.put(3202L, "PDF-BYTES");
        resumes.add(exist);
        profile.setVisibilityType("explicit");
        attachmentGranted = true;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("已获授权的团队共享");

        resumeService.download(3202L, bo, responseStub(captured));

        assertEquals("PDF-BYTES", captured.toString(StandardCharsets.UTF_8));
        assertEquals(List.of(TalentPermissionLevelEnum.ATTACHMENT), checkedLevels);
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_SUCCESS), audits);
    }

    @Test
    @DisplayName("下载授权级别：职责范围内可见（非 explicit）不查授权级别即放行")
    void shouldAllowDownloadForDutyScopeVisibility() {
        TalentResume exist = resume(3203L, 1, "1");
        exist.setOssId("hr-talent-private/resume/1001/3203/v1/original.pdf");
        extracts.put(3203L, "PDF-BYTES");
        resumes.add(exist);
        // department 可见性 + 可见范围内 → 招聘专员/负责人按原口径放行
        profile.setVisibilityType("department");
        scopeVisible = true;
        attachmentGranted = false;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("本部门候选人评估");

        resumeService.download(3203L, bo, responseStub(captured));

        assertEquals("PDF-BYTES", captured.toString(StandardCharsets.UTF_8));
        assertTrue(checkedLevels.isEmpty(), "职责范围内可见不得再要求显式共享授权级别");
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD + "|"
            + SensitiveAuditRecorder.RESULT_SUCCESS), audits);
    }

    @Test
    @DisplayName("下载授权级别：超管直接放行，不查授权级别")
    void shouldAllowDownloadForSuperAdmin() {
        TalentResume exist = resume(3204L, 1, "1");
        exist.setOssId("hr-talent-private/resume/1001/3204/v1/original.pdf");
        extracts.put(3204L, "PDF-BYTES");
        resumes.add(exist);
        profile.setVisibilityType("explicit");
        scopeUnlimited = true;
        attachmentGranted = false;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ResumeDownloadBo bo = new ResumeDownloadBo();
        bo.setPurpose("平台管理");

        resumeService.download(3204L, bo, responseStub(captured));

        assertEquals("PDF-BYTES", captured.toString(StandardCharsets.UTF_8));
        assertTrue(checkedLevels.isEmpty(), "超管无需授权级别校验");
    }

    /* ------------------------------------------------------------------ 当前版本 ------------------------------------------------------------------ */

    @Test
    @DisplayName("指定当前简历：已是当前版本时幂等返回")
    void shouldBeIdempotentWhenAlreadyCurrent() {
        resumes.add(resume(4001L, 1, "1"));

        resumeService.setCurrent(4001L);

        assertEquals(0, resumeUpdateCount);
        assertEquals(0, profileMapperUpdateCount);
    }

    @Test
    @DisplayName("指定当前简历：切换版本并同步主档指针")
    void shouldSwitchCurrentVersion() {
        resumes.add(resume(4002L, 2, "0"));

        resumeService.setCurrent(4002L);

        assertTrue(resumeUpdateCount > 0);
        assertEquals(1, profileMapperUpdateCount);
    }

    /* ------------------------------------------------------------------ 解析任务 ------------------------------------------------------------------ */

    @Test
    @DisplayName("解析：引擎未启用时任务置 failed 并给出中文提示，且不执行解析")
    void shouldFailTaskWhenEngineDisabled() {
        resumes.add(resume(5001L, 1, "1"));
        properties.setResumeParseEnabled(false);

        Long taskId = parseService.createTask(5001L);

        assertEquals(1, tasks.size());
        TalentParseTask task = tasks.get(0);
        assertEquals(taskId, task.getTaskId());
        assertEquals("failed", task.getTaskStatus());
        assertEquals(HrTalentErrorCode.HR_RESUME_002, task.getErrorCode());
        assertTrue(task.getErrorMessage().contains("解析引擎未启用"));
        assertEquals(0, task.getRetryCount());
        assertEquals("internal", task.getParserType());
        assertTrue(results.isEmpty(), "请求内不得执行任何解析");
        assertTrue(ossGets.isEmpty(), "请求内不得读取简历文件");
    }

    @Test
    @DisplayName("解析：引擎启用时任务停在 pending，不执行 OCR 或大模型调用")
    void shouldKeepTaskPendingWhenEngineEnabled() {
        resumes.add(resume(5002L, 1, "1"));
        properties.setResumeParseEnabled(true);
        properties.setResumeParserVersion("hr-resume-parser/9.9.9");

        Long taskId = parseService.createTask(5002L);

        TalentParseTask task = tasks.get(0);
        assertEquals(taskId, task.getTaskId());
        assertEquals("pending", task.getTaskStatus());
        assertEquals("hr-resume-parser/9.9.9", task.getParserVersion());
        assertTrue(ossGets.isEmpty());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("解析：已有进行中任务时复用，不重复排队")
    void shouldReuseActiveTask() {
        resumes.add(resume(5003L, 1, "1"));
        TalentParseTask active = new TalentParseTask();
        active.setTaskId(7777L);
        active.setResumeId(5003L);
        active.setTaskStatus("pending");
        tasks.add(active);

        Long taskId = parseService.createTask(5003L);

        assertEquals(7777L, taskId);
        assertEquals(1, tasks.size());
    }

    /* ------------------------------------------------------------------ 人工复核 ------------------------------------------------------------------ */

    @Test
    @DisplayName("复核：低置信度与缺失置信度默认不勾选，复核标签正确")
    void shouldComputeDefaultSelectedByConfidence() {
        resumes.add(resume(6001L, 1, "1"));
        TalentParseTask task = task(9001L, 6001L, "pending");
        tasks.add(task);
        results.add(parseResult(1L, 9001L, "basic.name", "张三", "张三", new BigDecimal("0.95"), "pending"));
        results.add(parseResult(2L, 9001L, "basic.gender", "男", "男", new BigDecimal("0.60"), "pending"));
        results.add(parseResult(3L, 9001L, "basic.email", "a@b.com", "a@b.com", null, "rejected"));

        TalentParseTaskVo vo = parseService.getTask(9001L);

        assertEquals(3, vo.getResults().size());
        assertTrue(vo.getResults().get(0).getDefaultSelected(), "高置信度应默认勾选");
        assertFalse(vo.getResults().get(1).getDefaultSelected(), "低置信度默认不勾选");
        assertFalse(vo.getResults().get(2).getDefaultSelected(), "置信度缺失默认不勾选");
        // 复核标签统一由字典 talent_resume_review_status 经 @Translation 在出参翻译阶段产出，
        // 纯单测（无 Spring 容器、无翻译切面）断言的是稳定编码本身，标签由字典保证：
        // pending→待复核、reviewing→复核中、confirmed→已确认、rejected→已否决
        assertEquals("pending", vo.getResults().get(0).getReviewStatus());
        assertEquals("rejected", vo.getResults().get(2).getReviewStatus());
        assertEquals(TalentParseServiceImpl.DEFAULT_CONFIDENCE_THRESHOLD, new BigDecimal("0.85"));
    }

    @Test
    @DisplayName("复核：不属于该任务的 resultId 被拒绝")
    void shouldRejectForeignResultId() {
        resumes.add(resume(6002L, 1, "1"));
        tasks.add(task(9002L, 6002L, "pending"));
        results.add(parseResult(11L, 9002L, "basic.name", "张三", "张三", new BigDecimal("0.99"), "pending"));
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(12L, true, null)));

        ServiceException ex = assertThrows(ServiceException.class, () -> parseService.confirm(9002L, bo));
        assertEquals("解析结果不属于该任务，请刷新后重试", ex.getMessage());
        assertTrue(profileUpdates.isEmpty());
    }

    @Test
    @DisplayName("复核：只把勾选字段写入正式档案，经历字段经 B 线经历域落正式经历表")
    void shouldApplyOnlyAcceptedProfileFields() {
        resumes.add(resume(6003L, 1, "1"));
        tasks.add(task(9003L, 6003L, "pending"));
        results.add(parseResult(21L, 9003L, "basic.name", "张三丰", "张三丰", new BigDecimal("0.90"), "pending"));
        results.add(parseResult(22L, 9003L, "education[0].school_name", "某大学", "某大学",
            new BigDecimal("0.40"), "pending"));
        results.add(parseResult(23L, 9003L, "contact.phone", "13800000000", "13800000000",
            new BigDecimal("0.50"), "pending"));
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(
            item(21L, true, null),
            item(22L, true, null),
            item(23L, false, null)));

        TalentParseTaskVo vo = parseService.confirm(9003L, bo);

        assertEquals(1, profileUpdates.size(), "只允许一次主档更新且仅含勾选字段");
        TalentProfileBo update = profileUpdates.get(0);
        assertEquals("张三丰", update.getName());
        assertEquals(3, update.getVersion(), "必须携带主档乐观锁版本号");
        assertEquals(TALENT_ID, update.getTalentId());
        assertTrue(StringUtils.isBlank(update.getPhone()), "未勾选字段不得写入正式档案");
        // 经历字段：勾选的 school_name 通过 B 线经历域写入正式教育经历，并标来源 parse + 简历版本
        assertEquals(1, createdEducations.size(), "勾选的经历字段必须落到正式经历表");
        TalentEducationBo education = createdEducations.get(0);
        assertEquals("某大学", education.getSchoolName());
        assertEquals(ITalentExperienceService.SOURCE_PARSE, education.getSourceType());
        assertEquals(6003L, education.getResumeId(), "解析类来源必须携带来源简历版本ID");
        assertEquals("confirmed", results.get(0).getReviewStatus());
        assertEquals("confirmed", results.get(1).getReviewStatus());
        assertTrue(results.get(1).getRemark().contains("已写入教育经历"),
            "复核结论必须保留可追溯的落库结果说明");
        assertEquals("rejected", results.get(2).getReviewStatus());
        // 中文标签由字典 talent_resume_review_status 在出参翻译阶段产出（单测只校验编码）
        assertEquals("confirmed", vo.getResults().get(0).getReviewStatus());
        assertTrue(vo.getResults().stream().anyMatch(r -> "rejected".equals(r.getReviewStatus())));
    }

    @Test
    @DisplayName("复核：同一经历下标的多字段合并为一条正式经历")
    void shouldMergeExperienceFieldsByIndex() {
        resumes.add(resume(6006L, 1, "1"));
        tasks.add(task(9006L, 6006L, "pending"));
        results.add(parseResult(51L, 9006L, "education[0].school_name", "某大学", "某大学",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(52L, 9006L, "education[0].major", "计算机", "计算机",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(53L, 9006L, "education[0].start_date", "2016-09-01", "2016-09-01",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(54L, 9006L, "work[0].company_name", "甲公司", "甲公司",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(55L, 9006L, "work[0].position_name", "后端工程师", "后端工程师",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(56L, 9006L, "project[0].project_name", "订单系统", "订单系统",
            new BigDecimal("0.90"), "pending"));
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(51L, true, null), item(52L, true, null), item(53L, true, null),
            item(54L, true, null), item(55L, true, null), item(56L, true, null)));

        parseService.confirm(9006L, bo);

        assertEquals(1, createdEducations.size(), "education[0] 只生成一条教育经历");
        assertEquals("某大学", createdEducations.get(0).getSchoolName());
        assertEquals("计算机", createdEducations.get(0).getMajor());
        assertEquals(java.time.LocalDate.of(2016, 9, 1), createdEducations.get(0).getStartDate());
        assertEquals(1, createdWorks.size(), "work[0] 只生成一条工作经历");
        assertEquals("甲公司", createdWorks.get(0).getCompanyName());
        assertEquals("后端工程师", createdWorks.get(0).getPositionName());
        assertEquals(1, createdProjects.size(), "project[0] 只生成一条项目经历");
        assertEquals("订单系统", createdProjects.get(0).getProjectName());
        assertEquals(ITalentExperienceService.SOURCE_PARSE, createdWorks.get(0).getSourceType());
        assertEquals(6006L, createdProjects.get(0).getResumeId());
        assertTrue(createdEducations.get(0).getResumeId().equals(6006L));
    }

    @Test
    @DisplayName("复核：缺少经历主字段时不生成半空记录，只写中文提示")
    void shouldSkipExperienceWithoutAnchorField() {
        resumes.add(resume(6007L, 1, "1"));
        tasks.add(task(9007L, 6007L, "pending"));
        results.add(parseResult(61L, 9007L, "education[0].major", "计算机", "计算机",
            new BigDecimal("0.90"), "pending"));
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(61L, true, null)));

        parseService.confirm(9007L, bo);

        assertTrue(createdEducations.isEmpty(), "缺学校名称不得创建教育经历");
        assertEquals("confirmed", results.get(0).getReviewStatus());
        assertTrue(results.get(0).getRemark().contains("缺少学校名称"));
    }

    @Test
    @DisplayName("复核：同简历同主字段但开始日期不同时两条都写入（同一公司两段任职不丢数据）")
    void shouldKeepBothExperiencesWhenStartDateDiffers() {
        resumes.add(resume(6008L, 1, "1"));
        tasks.add(task(9008L, 6008L, "pending"));
        results.add(parseResult(71L, 9008L, "work[0].company_name", "甲公司", "甲公司",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(72L, 9008L, "work[0].start_date", "2022-05-01", "2022-05-01",
            new BigDecimal("0.90"), "pending"));
        // 已存在的第一段任职：同一公司，但入职日期是 2018-03-01
        TalentWorkVo exist = new TalentWorkVo();
        exist.setResumeId(6008L);
        exist.setCompanyName("甲公司");
        exist.setStartDate(java.time.LocalDate.of(2018, 3, 1));
        existingWorks = List.of(exist);
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(71L, true, null), item(72L, true, null)));

        parseService.confirm(9008L, bo);

        assertEquals(1, createdWorks.size(), "同公司不同入职日期的第二段任职必须写入，不得被去重丢掉");
        assertEquals("甲公司", createdWorks.get(0).getCompanyName());
        assertEquals(java.time.LocalDate.of(2022, 5, 1), createdWorks.get(0).getStartDate());
        assertEquals(ITalentExperienceService.SOURCE_PARSE, createdWorks.get(0).getSourceType());
        assertEquals(6008L, createdWorks.get(0).getResumeId());
    }

    @Test
    @DisplayName("复核：同简历同主字段且开始日期相同时才跳过重复写入")
    void shouldSkipDuplicateExperienceWithSameStartDate() {
        resumes.add(resume(6010L, 1, "1"));
        tasks.add(task(9010L, 6010L, "pending"));
        results.add(parseResult(91L, 9010L, "work[0].company_name", "甲公司", "甲公司",
            new BigDecimal("0.90"), "pending"));
        results.add(parseResult(92L, 9010L, "work[0].start_date", "2018-03-01", "2018-03-01",
            new BigDecimal("0.90"), "pending"));
        TalentWorkVo exist = new TalentWorkVo();
        exist.setResumeId(6010L);
        exist.setCompanyName("甲公司");
        exist.setStartDate(java.time.LocalDate.of(2018, 3, 1));
        existingWorks = List.of(exist);
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(91L, true, null), item(92L, true, null)));

        parseService.confirm(9010L, bo);

        assertTrue(createdWorks.isEmpty(), "同主字段且同开始日期视为重复确认，不得重复写入");
        assertTrue(results.get(0).getRemark().contains("跳过重复写入"));
    }

    @Test
    @DisplayName("复核：缺少开始日期时按「仅主字段同名」回退去重并在备注注明")
    void shouldFallbackToNameOnlyDedupWhenStartDateMissing() {
        resumes.add(resume(6011L, 1, "1"));
        tasks.add(task(9011L, 6011L, "pending"));
        results.add(parseResult(93L, 9011L, "education[0].school_name", "某大学", "某大学",
            new BigDecimal("0.90"), "pending"));
        TalentEducationVo exist = new TalentEducationVo();
        exist.setResumeId(6011L);
        exist.setSchoolName("某大学");
        existingEducations = List.of(exist);
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(93L, true, null)));

        parseService.confirm(9011L, bo);

        assertTrue(createdEducations.isEmpty(), "双方开始日期均为空时按回退口径判定为重复");
        assertTrue(results.get(0).getRemark().contains("未提供开始日期"), "回退口径必须在备注中注明");
    }

    @Test
    @DisplayName("复核：未勾选的经历字段绝不写入正式经历表")
    void shouldNotWriteExperienceWhenRejected() {
        resumes.add(resume(6009L, 1, "1"));
        tasks.add(task(9009L, 6009L, "pending"));
        results.add(parseResult(81L, 9009L, "education[0].school_name", "某大学", "某大学",
            new BigDecimal("0.90"), "pending"));
        pendingReviewCount = 0L;
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(81L, false, null)));

        parseService.confirm(9009L, bo);

        assertTrue(createdEducations.isEmpty(), "未确认的经历不得写入正式表（§8.14）");
        assertEquals("rejected", results.get(0).getReviewStatus());
    }

    @Test
    @DisplayName("复核：勾选字段的确认值不能为空")
    void shouldRejectBlankAcceptedValue() {
        resumes.add(resume(6004L, 1, "1"));
        tasks.add(task(9004L, 6004L, "pending"));
        results.add(parseResult(31L, 9004L, "basic.name", "张三", null, new BigDecimal("0.90"), "pending"));
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(31L, true, null)));

        ServiceException ex = assertThrows(ServiceException.class, () -> parseService.confirm(9004L, bo));
        assertEquals("字段 basic.name 的确认值不能为空", ex.getMessage());
        assertTrue(profileUpdates.isEmpty());
    }

    @Test
    @DisplayName("复核：确认值非法时给出明确中文提示且不更新主档")
    void shouldRejectInvalidTypedValue() {
        resumes.add(resume(6005L, 1, "1"));
        tasks.add(task(9005L, 6005L, "pending"));
        results.add(parseResult(41L, 9005L, "basic.birth_date", "1990-01-01", "不是日期",
            new BigDecimal("0.90"), "pending"));
        ParseConfirmBo bo = new ParseConfirmBo();
        bo.setItems(List.of(item(41L, true, null)));

        ServiceException ex = assertThrows(ServiceException.class, () -> parseService.confirm(9005L, bo));
        assertTrue(ex.getMessage().contains("不是合法日期"));
        assertTrue(profileUpdates.isEmpty());
    }

    /* ------------------------------------------------------------------ 测试替身 ------------------------------------------------------------------ */

    /**
     * 简历 Mapper 动态代理替身。
     *
     * @return 替身
     */
    private TalentResumeMapper resumeMapperStub() {
        return (TalentResumeMapper) Proxy.newProxyInstance(
            TalentResumeMapper.class.getClassLoader(),
            new Class<?>[]{TalentResumeMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> resumes.stream()
                    .filter(r -> r.getResumeId().equals(args[0]))
                    .findFirst().orElse(null);
                case "selectVoById" -> voOf((Long) args[0]);
                case "selectMaxVersionNo" -> maxVersionNo;
                case "selectList" -> resumeSelectListResult;
                case "insert" -> {
                    resumes.add((TalentResume) args[0]);
                    yield 1;
                }
                case "update", "updateById" -> {
                    resumeUpdateCount++;
                    yield 1;
                }
                case "selectCount" -> 0L;
                case "toString" -> "TalentResumeMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才主档 Mapper 动态代理替身（只需支持冗余指针更新）。
     *
     * @return 替身
     */
    private TalentProfileMapper profileMapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> profile;
                case "update", "updateById" -> {
                    profileMapperUpdateCount++;
                    yield 1;
                }
                case "toString" -> "TalentProfileMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才主档服务动态代理替身（资源级鉴权与主档更新）。
     *
     * @return 替身
     */
    private ITalentProfileService profileServiceStub() {
        return (ITalentProfileService) Proxy.newProxyInstance(
            ITalentProfileService.class.getClassLoader(),
            new Class<?>[]{ITalentProfileService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "requireVisible" -> {
                    if (visibilityFailure != null) {
                        throw visibilityFailure;
                    }
                    yield profile;
                }
                case "update" -> {
                    profileUpdates.add((TalentProfileBo) args[0]);
                    yield null;
                }
                case "toString" -> "TalentProfileServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才可见范围领域服务替身：只覆盖下载鉴权用到的三个公开判定方法。
     *
     * @return 替身
     */
    private TalentScopeDomainService scopeServiceStub() {
        return new TalentScopeDomainService(null) {
            @Override
            public ScopeCondition currentScope() {
                return new ScopeCondition(scopeUnlimited, false, 9L, 10L,
                    Set.of(), Set.of(), Set.of(), Set.of());
            }

            @Override
            public boolean visible(TalentScopeTarget target, ScopeCondition scope) {
                return scopeVisible;
            }

            @Override
            public void checkPermissionLevel(Long talentId, TalentPermissionLevelEnum requiredLevel) {
                checkedLevels.add(requiredLevel);
                if (!attachmentGranted) {
                    throw new ServiceException(TalentScopeDomainService.DENY_PERMISSION_LEVEL);
                }
            }
        };
    }

    /**
     * 经历域（B 线）动态代理替身：捕获正式经历新增入参，列表接口用于去重检查。
     *
     * @return 替身
     */
    private ITalentExperienceService experienceServiceStub() {
        return (ITalentExperienceService) Proxy.newProxyInstance(
            ITalentExperienceService.class.getClassLoader(),
            new Class<?>[]{ITalentExperienceService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "createEducation" -> {
                    createdEducations.add((TalentEducationBo) args[1]);
                    yield 7001L;
                }
                case "createWork" -> {
                    createdWorks.add((TalentWorkBo) args[1]);
                    yield 8001L;
                }
                case "createProject" -> {
                    createdProjects.add((TalentProjectBo) args[1]);
                    yield 9001L;
                }
                case "listEducation" -> existingEducations;
                case "listWork" -> existingWorks;
                case "listProject" -> existingProjects;
                case "toString" -> "TalentExperienceServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 简历服务动态代理替身（供解析域做资源级鉴权）。
     *
     * @return 替身
     */
    private ITalentResumeService resumeServiceStub() {
        return (ITalentResumeService) Proxy.newProxyInstance(
            ITalentResumeService.class.getClassLoader(),
            new Class<?>[]{ITalentResumeService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "requireVisibleResume" -> {
                    if (visibilityFailure != null) {
                        throw visibilityFailure;
                    }
                    yield resumes.stream()
                        .filter(r -> r.getResumeId().equals(args[0]))
                        .findFirst()
                        .orElseThrow(() -> new ServiceException("简历不存在或已删除"));
                }
                case "toString" -> "TalentResumeServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 解析任务 Mapper 动态代理替身。
     *
     * @return 替身
     */
    private TalentParseTaskMapper taskMapperStub() {
        return (TalentParseTaskMapper) Proxy.newProxyInstance(
            TalentParseTaskMapper.class.getClassLoader(),
            new Class<?>[]{TalentParseTaskMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> tasks.stream()
                    .filter(t -> t.getTaskId().equals(args[0]))
                    .findFirst().orElse(null);
                case "selectVoById" -> taskVoOf((Long) args[0]);
                case "selectOne" -> tasks.stream()
                    .filter(t -> "pending".equals(t.getTaskStatus()) || "running".equals(t.getTaskStatus()))
                    .findFirst().orElse(null);
                case "insert" -> {
                    tasks.add((TalentParseTask) args[0]);
                    yield 1;
                }
                case "update", "updateById" -> 1;
                case "toString" -> "TalentParseTaskMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 候选解析结果 Mapper 动态代理替身。
     *
     * @return 替身
     */
    private TalentParseResultMapper resultMapperStub() {
        return (TalentParseResultMapper) Proxy.newProxyInstance(
            TalentParseResultMapper.class.getClassLoader(),
            new Class<?>[]{TalentParseResultMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList" -> results;
                case "selectCount" -> pendingReviewCount;
                case "update", "updateById" -> 1;
                case "insert" -> 1;
                case "toString" -> "TalentParseResultMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 对象存储封装替身：只记录读写键，绝不联网。
     *
     * @return 替身
     */
    private HrTalentOssHelper ossHelperStub() {
        return new HrTalentOssHelper(properties) {
            @Override
            public void put(String key, byte[] data) {
                ossPuts.add(key);
            }

            @Override
            public void put(String key, java.io.InputStream in, long size) {
                ossPuts.add(key);
            }

            @Override
            public void get(String key, OutputStream out) {
                ossGets.add(key);
                String content = extracts.get(contentByKey(key));
                if (content == null) {
                    return;
                }
                try {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new ServiceException("简历读取失败");
                }
            }

            @Override
            public String presign(String key) {
                ossPresigns.add(key);
                return "https://invalid.example.com/" + key;
            }

            @Override
            public String presign(String key, java.time.Duration ttl) {
                ossPresigns.add(key);
                return "https://invalid.example.com/" + key;
            }

            @Override
            public void delete(String key) {
                // 单测不追踪删除
            }
        };
    }

    /**
     * 审计记录器替身：捕获事件类型与结果，不访问数据库。
     *
     * @return 替身
     */
    private SensitiveAuditRecorder auditRecorderStub() {
        return new SensitiveAuditRecorder(null) {
            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result) {
                audits.add(eventType + "|" + result);
            }

            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result,
                               String detailJson) {
                audits.add(eventType + "|" + result);
            }
        };
    }

    /**
     * 领域事件发布器替身。
     *
     * @return 替身
     */
    private ApplicationEventPublisher eventPublisherStub() {
        return (ApplicationEventPublisher) Proxy.newProxyInstance(
            ApplicationEventPublisher.class.getClassLoader(),
            new Class<?>[]{ApplicationEventPublisher.class},
            (proxy, method, args) -> {
                if ("publishEvent".equals(method.getName())) {
                    events.add(args[0]);
                    return null;
                }
                if ("toString".equals(method.getName())) {
                    return "EventPublisherStub";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                return defaultValue(method.getReturnType());
            });
    }

    /**
     * HTTP 响应动态代理替身：捕获状态头与输出字节。
     *
     * @param target 输出目标
     * @return 替身
     */
    private HttpServletResponse responseStub(ByteArrayOutputStream target) {
        ServletOutputStream out = new ServletOutputStream() {
            @Override
            public void write(int b) {
                target.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // 单测无需异步写出
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[]{HttpServletResponse.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getOutputStream":
                        return out;
                    case "setContentType":
                        responseHeaders.put("Content-Type", String.valueOf(args[0]));
                        return null;
                    case "setHeader":
                        responseHeaders.put(String.valueOf(args[0]), String.valueOf(args[1]));
                        return null;
                    case "setContentLengthLong":
                        responseHeaders.put("Content-Length", String.valueOf(args[0]));
                        return null;
                    case "reset":
                        return null;
                    case "toString":
                        return "HttpServletResponseStub";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }

    /* ------------------------------------------------------------------ 测试数据构造 ------------------------------------------------------------------ */

    /**
     * 对象键 → 文件内容（供流式输出断言使用）。
     */
    private final Map<Long, String> extracts = new HashMap<>();

    /**
     * 构造一个内存中的简历实体。
     *
     * @param resumeId    简历ID
     * @param versionNo   版本号
     * @param currentFlag 是否当前版本
     * @return 简历实体
     */
    private TalentResume resume(Long resumeId, int versionNo, String currentFlag) {
        TalentResume entity = new TalentResume();
        entity.setResumeId(resumeId);
        entity.setTalentId(TALENT_ID);
        entity.setVersionNo(versionNo);
        entity.setCurrentFlag(currentFlag);
        entity.setOssId("hr-talent-private/resume/" + TALENT_ID + "/" + resumeId + "/v" + versionNo + "/original.pdf");
        entity.setOriginalName("resume.pdf");
        entity.setFileSuffix("pdf");
        entity.setFileSize(9L);
        entity.setParseStatus("pending");
        entity.setReviewStatus("pending");
        entity.setSourceType("upload");
        entity.setUploadedBy(9L);
        entity.setUploadedTime(LocalDateTime.now());
        entity.setDelFlag("0");
        return entity;
    }

    /**
     * 构造简历视图（供 Mapper 替身返回）。
     *
     * @param resumeId 简历ID
     * @return 视图对象
     */
    private TalentResumeVo voOf(Long resumeId) {
        return resumes.stream()
            .filter(r -> r.getResumeId().equals(resumeId))
            .findFirst()
            .map(r -> {
                TalentResumeVo vo = new TalentResumeVo();
                vo.setResumeId(r.getResumeId());
                vo.setTalentId(r.getTalentId());
                vo.setVersionNo(r.getVersionNo());
                vo.setCurrentFlag(r.getCurrentFlag());
                vo.setOriginalName(r.getOriginalName());
                vo.setFileSuffix(r.getFileSuffix());
                vo.setFileSize(r.getFileSize());
                vo.setFileHash(r.getFileHash());
                vo.setParseStatus(r.getParseStatus());
                vo.setReviewStatus(r.getReviewStatus());
                return vo;
            })
            .orElse(null);
    }

    /**
     * 构造内存中的解析任务。
     *
     * @param taskId     任务ID
     * @param resumeId   简历ID
     * @param taskStatus 任务状态
     * @return 解析任务
     */
    private TalentParseTask task(Long taskId, Long resumeId, String taskStatus) {
        TalentParseTask task = new TalentParseTask();
        task.setTaskId(taskId);
        task.setResumeId(resumeId);
        task.setTalentId(TALENT_ID);
        task.setTaskStatus(taskStatus);
        task.setParserType("internal");
        task.setRetryCount(0);
        task.setDelFlag("0");
        return task;
    }

    /**
     * 构造任务视图（供 Mapper 替身返回）。
     *
     * @param taskId 任务ID
     * @return 视图对象
     */
    private TalentParseTaskVo taskVoOf(Long taskId) {
        return tasks.stream()
            .filter(t -> t.getTaskId().equals(taskId))
            .findFirst()
            .map(t -> {
                TalentParseTaskVo vo = new TalentParseTaskVo();
                vo.setTaskId(t.getTaskId());
                vo.setResumeId(t.getResumeId());
                vo.setTalentId(t.getTalentId());
                vo.setTaskStatus(t.getTaskStatus());
                vo.setParserType(t.getParserType());
                vo.setParserVersion(t.getParserVersion());
                vo.setRetryCount(t.getRetryCount());
                vo.setErrorCode(t.getErrorCode());
                vo.setErrorMessage(t.getErrorMessage());
                return vo;
            })
            .orElse(null);
    }

    /**
     * 构造候选解析结果。
     *
     * @param resultId         结果ID
     * @param taskId           任务ID
     * @param fieldPath        字段路径
     * @param rawValue         原始值
     * @param normalizedValue  标准化值
     * @param confidence       置信度
     * @param reviewStatus     复核状态
     * @return 解析结果
     */
    private TalentParseResult parseResult(Long resultId, Long taskId, String fieldPath, String rawValue,
                                          String normalizedValue, BigDecimal confidence, String reviewStatus) {
        TalentParseResult result = new TalentParseResult();
        result.setResultId(resultId);
        result.setTaskId(taskId);
        result.setFieldPath(fieldPath);
        result.setRawValue(rawValue);
        result.setNormalizedValue(normalizedValue);
        result.setConfidence(confidence);
        result.setSourceLocation("page:1");
        result.setReviewStatus(reviewStatus);
        result.setDelFlag("0");
        return result;
    }

    /**
     * 构造确认项。
     *
     * @param resultId 解析结果ID
     * @param accepted 是否勾选
     * @param value    人工修正值
     * @return 确认项
     */
    private ParseConfirmBo.Item item(Long resultId, boolean accepted, String value) {
        ParseConfirmBo.Item item = new ParseConfirmBo.Item();
        item.setResultId(resultId);
        item.setAccepted(accepted);
        item.setNormalizedValue(value);
        return item;
    }

    /**
     * 构造上传文件替身。
     *
     * @param name        文件名
     * @param contentType MIME
     * @param content     内容
     * @return 文件替身
     */
    private MultipartFile file(String name, String contentType, String content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return name;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return content == null || content.isEmpty();
            }

            @Override
            public long getSize() {
                return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
            }

            @Override
            public byte[] getBytes() {
                return content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public java.io.InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(getBytes());
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException("单测不需要落地文件");
            }
        };
    }

    /**
     * 计算与生产实现一致的 SHA-256 十六进制哈希。
     *
     * @param content 内容
     * @return 哈希
     * @throws Exception 算法不可用（理论不可达）
     */
    private String sha256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 由对象键反查简历ID（供读取内容映射使用）。
     *
     * @param key 对象键
     * @return 简历ID；无法解析返回 -1
     */
    private Long contentByKey(String key) {
        for (TalentResume resume : resumes) {
            if (resume.getOssId() != null && resume.getOssId().equals(key)) {
                return resume.getResumeId();
            }
        }
        return -1L;
    }

    /**
     * 取返回类型的默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        return 0;
    }

}
