package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.recruitment.AttachmentDownloadBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitAttachment;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domainservice.AttachmentBizAccessChecker;
import org.dromara.hrtalent.domainservice.DefaultAttachmentBizAccessChecker;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.AttachmentTypeEnum;
import org.dromara.hrtalent.enums.DataLevelEnum;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitAttachmentMapper;
import org.dromara.hrtalent.mapper.RecruitBackgroundMapper;
import org.dromara.hrtalent.mapper.RecruitInterviewMapper;
import org.dromara.hrtalent.mapper.RecruitSensitiveAuditMapper;
import org.dromara.hrtalent.mapper.TalentFollowUpMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招聘业务附件服务单元测试。
 *
 * <p>覆盖 SPEC-P3 §3.5 的硬性规则：用途必填（拒绝并留痕）、业务类型白名单、
 * 简历不得写入通用附件表、扩展名/MIME/大小/数量限制、版本号递增与旧当前版本置否、
 * 逻辑删除不物理删除（也不删除对象存储文件）、业务记录鉴权失败必须拒绝。</p>
 *
 * <p><b>说明</b>：Mapper 与 ObjectProvider 替身使用 JDK 动态代理手工构造，
 * OSS 封装使用匿名子类替换读写方法，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitAttachmentServiceImplTest {

    /**
     * 内存中的附件数据，按主键索引。
     */
    private final Map<Long, RecruitAttachment> store = new HashMap<>();

    /**
     * 记录 {@code insert} 写入的实体。
     */
    private final List<RecruitAttachment> inserted = new ArrayList<>();

    /**
     * 记录写入对象存储的对象键。
     */
    private final List<String> putKeys = new ArrayList<>();

    /**
     * 记录被删除的对象存储对象键（逻辑删除场景必须为空）。
     */
    private final List<String> deletedOssKeys = new ArrayList<>();

    /**
     * 记录 {@code deleteByIds} 的ID集合。
     */
    private final List<Long> deletedIds = new ArrayList<>();

    /**
     * 记录写入的审计记录。
     */
    private final List<RecruitSensitiveAudit> audits = new ArrayList<>();

    /**
     * 是否调用了旧当前版本置否的更新。
     */
    private boolean currentVersionDemoted;

    /**
     * 已使用的最大版本号（Mapper 替身返回值）。
     */
    private int maxVersionNo;

    /**
     * 现存附件数量（Mapper 替身返回值）。
     */
    private long existingCount;

    /**
     * 业务记录鉴权替身，默认未注册实现（null）。
     */
    private AttachmentBizAccessChecker bizAccessChecker;

    /**
     * 业务配置。
     */
    private HrTalentProperties properties;

    /**
     * 被测服务。
     */
    private RecruitAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        // Lambda 条件构造器需要 MyBatis-Plus 的实体列缓存；纯单元测试没有 Spring 上下文，故显式初始化
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), StringUtils.EMPTY),
            RecruitAttachment.class);
        store.clear();
        inserted.clear();
        putKeys.clear();
        deletedOssKeys.clear();
        deletedIds.clear();
        audits.clear();
        currentVersionDemoted = false;
        maxVersionNo = 0;
        existingCount = 0;
        // 默认放行替身：本类主要验证附件域自身规则；鉴权规则单独用 DefaultAttachmentBizAccessChecker 用例覆盖
        bizAccessChecker = (bizType, bizId) -> {
        };
        properties = new HrTalentProperties();
        service = new RecruitAttachmentServiceImpl(
            attachmentMapperStub(), ossStub(), properties, auditRecorderStub(), checkerProviderStub());
    }

    /* ------------------------------------------------------------------ 用途与审计 ------------------------------------------------------------------ */

    @Test
    @DisplayName("下载用途为空时拒绝并写入 denied 审计")
    void shouldRejectDownloadWithBlankPurpose() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.download(1L, new AttachmentDownloadBo(), null));

        assertTrue(ex.getMessage().contains("用途"));
        assertEquals(1, audits.size());
        assertEquals(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD, audits.get(0).getEventType());
        assertEquals(SensitiveAuditRecorder.BIZ_ATTACHMENT, audits.get(0).getBizType());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("预览用途为空时拒绝并写入 denied 审计")
    void shouldRejectPreviewWithBlankPurpose() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview(9L, new AttachmentDownloadBo(), null));

        assertTrue(ex.getMessage().contains("用途"));
        assertEquals(SensitiveAuditRecorder.EVENT_ATTACHMENT_PREVIEW, audits.get(0).getEventType());
        assertEquals(9L, audits.get(0).getBizId());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("用途超过 255 字符时拒绝")
    void shouldRejectTooLongPurpose() {
        AttachmentDownloadBo bo = new AttachmentDownloadBo();
        bo.setPurpose("x".repeat(256));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.download(1L, bo, null));
        assertTrue(ex.getMessage().contains("用途长度"));
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("附件不存在时下载写 failed 审计并拒绝")
    void shouldFailWhenAttachmentMissing() {
        AttachmentDownloadBo bo = new AttachmentDownloadBo();
        bo.setPurpose("候选人筛选");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.download(404L, bo, null));
        assertTrue(ex.getMessage().contains("附件不存在"));
        assertEquals(SensitiveAuditRecorder.RESULT_FAILED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("业务记录鉴权失败时拒绝下载并写 denied 审计")
    void shouldDenyWhenBizAccessCheckerRejects() {
        store.put(1L, attachment("application", 100L, "portfolio", 1, "v".repeat(64)));
        bizAccessChecker = (bizType, bizId) -> {
            throw new ServiceException("无权查看该人才");
        };
        AttachmentDownloadBo bo = new AttachmentDownloadBo();
        bo.setPurpose("候选人筛选");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.download(1L, bo, null));
        assertEquals("无权查看该人才", ex.getMessage());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
        // 拒绝发生在写出任何字节之前
        assertTrue(deletedOssKeys.isEmpty());
    }

    @Test
    @DisplayName("未注册任何业务记录鉴权实现时按拒绝处理（fail-closed，不得放行）")
    void shouldDenyWhenNoBizAccessCheckerRegistered() {
        bizAccessChecker = null;
        store.put(1L, attachment("application", 100L, "portfolio", 1, "v".repeat(64)));
        AttachmentDownloadBo bo = new AttachmentDownloadBo();
        bo.setPurpose("候选人筛选");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.download(1L, bo, null));
        assertTrue(ex.getMessage().contains("鉴权服务不可用"));
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
        assertEquals(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD, audits.get(0).getEventType());
    }

    @Test
    @DisplayName("默认鉴权器：能解析出人才但人才不可见时拒绝下载并写 denied 审计")
    void shouldDenyWhenTalentResolvedButNotVisible() {
        RecruitApplication application = application(55L, 77L);
        TalentProfile profile = profile(77L, "owner");
        ScopeStub scope = new ScopeStub(new ServiceException("无权查看该人才"));
        bizAccessChecker = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, application),
            mapperStub(RecruitInterviewMapper.class, null),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, null),
            mapperStub(TalentProfileMapper.class, profile),
            scope);
        store.put(1L, attachment("application", 55L, "portfolio", 1, "v".repeat(64)));
        AttachmentDownloadBo bo = new AttachmentDownloadBo();
        bo.setPurpose("候选人筛选");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.download(1L, bo, null));

        assertEquals("无权查看该人才", ex.getMessage());
        // 链路解析确实落到正确的人才ID上（application 55 → talent 77）
        assertNotNull(scope.captured);
        assertEquals(77L, scope.captured.talentId());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
        assertEquals(SensitiveAuditRecorder.EVENT_ATTACHMENT_DOWNLOAD, audits.get(0).getEventType());
        assertTrue(deletedOssKeys.isEmpty());
    }

    @Test
    @DisplayName("默认鉴权器：业务记录不存在、链路断或业务类型不认识时一律拒绝")
    void shouldDenyWhenTalentCannotBeResolved() {
        ScopeStub scope = new ScopeStub(null);
        DefaultAttachmentBizAccessChecker checker = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, null),
            mapperStub(RecruitInterviewMapper.class, null),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, null),
            mapperStub(TalentProfileMapper.class, null),
            scope);

        // 应聘记录不存在
        assertThrows(ServiceException.class, () -> checker.check("application", 404L));
        // 业务类型不认识
        assertThrows(ServiceException.class, () -> checker.check("job", 1L));
        // 业务定位缺失
        assertThrows(ServiceException.class, () -> checker.check(null, 1L));
        assertThrows(ServiceException.class, () -> checker.check("application", null));
        // 跟进记录不存在（P4 追加：解析不出人才必须 fail-closed）
        assertThrows(ServiceException.class, () -> checker.check("follow_up", 404L));

        // 面试存在但应聘记录不存在：链路断
        RecruitInterview interview = new RecruitInterview();
        interview.setInterviewId(5L);
        interview.setApplicationId(404L);
        DefaultAttachmentBizAccessChecker brokenLink = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, null),
            mapperStub(RecruitInterviewMapper.class, interview),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, null),
            mapperStub(TalentProfileMapper.class, null),
            scope);
        assertThrows(ServiceException.class, () -> brokenLink.check("interview", 5L));

        // 应聘记录存在但人才主档不存在（已删除或数据异常）
        DefaultAttachmentBizAccessChecker noTalent = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, application(55L, 77L)),
            mapperStub(RecruitInterviewMapper.class, null),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, null),
            mapperStub(TalentProfileMapper.class, null),
            scope);
        assertThrows(ServiceException.class, () -> noTalent.check("application", 55L));

        // 全链路不可解析时绝不允许调用可见范围判定（即不得放行）
        assertNull(scope.captured);
    }

    @Test
    @DisplayName("默认鉴权器：面试 → 应聘 → 人才链路解析成功时按人才可见范围判定")
    void shouldResolveTalentThroughInterviewChain() {
        RecruitInterview interview = new RecruitInterview();
        interview.setInterviewId(5L);
        interview.setApplicationId(55L);
        ScopeStub scope = new ScopeStub(null);
        DefaultAttachmentBizAccessChecker checker = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, application(55L, 77L)),
            mapperStub(RecruitInterviewMapper.class, interview),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, null),
            mapperStub(TalentProfileMapper.class, profile(77L, "department")),
            scope);

        checker.check("interview", 5L);

        assertNotNull(scope.captured);
        assertEquals(77L, scope.captured.talentId());
        assertEquals(3L, scope.captured.ownerDeptId());
    }

    @Test
    @DisplayName("默认鉴权器：跟进记录 → 人才链路解析成功时按人才可见范围判定（P4 追加）")
    void shouldResolveTalentThroughFollowUp() {
        TalentFollowUp followUp = new TalentFollowUp();
        followUp.setFollowId(9L);
        followUp.setTalentId(77L);
        ScopeStub scope = new ScopeStub(null);
        DefaultAttachmentBizAccessChecker checker = new DefaultAttachmentBizAccessChecker(
            mapperStub(RecruitApplicationMapper.class, null),
            mapperStub(RecruitInterviewMapper.class, null),
            mapperStub(RecruitBackgroundMapper.class, null),
            mapperStub(TalentFollowUpMapper.class, followUp),
            mapperStub(TalentProfileMapper.class, profile(77L, "owner")),
            scope);

        checker.check("follow_up", 9L);

        // bizId 是跟进记录ID，必须解析成跟进记录上的人才ID（不是把 9 当人才ID）
        assertNotNull(scope.captured);
        assertEquals(77L, scope.captured.talentId());
    }

    /* ------------------------------------------------------------------ 上传校验 ------------------------------------------------------------------ */

    @Test
    @DisplayName("业务类型不在白名单时拒绝上传")
    void shouldRejectUnknownBizType() {
        RecruitAttachmentBo bo = bo("unknown", 1L, AttachmentTypeEnum.PORTFOLIO.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("作品集.pdf", "application/pdf", 8)));
        assertEquals("不支持的附件业务类型：unknown", ex.getMessage());
    }

    @Test
    @DisplayName("简历不得写入通用附件表")
    void shouldRejectResumeFileType() {
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.RESUME.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("张三-简历.pdf", "application/pdf", 8)));
        assertTrue(ex.getMessage().contains("简历"));
        assertTrue(inserted.isEmpty());
    }

    @Test
    @DisplayName("未知附件类型编码被拒绝")
    void shouldRejectUnknownFileType() {
        RecruitAttachmentBo bo = bo("application", 1L, "certificate");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("材料.pdf", "application/pdf", 8)));
        assertTrue(ex.getMessage().contains("附件类型不合法"));
    }

    @Test
    @DisplayName("扩展名不在白名单时拒绝上传")
    void shouldRejectDisallowedExtension() {
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.OTHER.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("payload.exe", "application/octet-stream", 8)));
        assertTrue(ex.getMessage().contains("不允许上传的附件类型"));
        assertTrue(putKeys.isEmpty());
    }

    @Test
    @DisplayName("MIME 类型不在白名单时拒绝上传")
    void shouldRejectDisallowedMimeType() {
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.OTHER.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("材料.pdf", "application/x-msdownload", 8)));
        assertTrue(ex.getMessage().contains("MIME"));
        assertTrue(putKeys.isEmpty());
    }

    @Test
    @DisplayName("超过单文件大小上限时拒绝上传")
    void shouldRejectOversizeFile() {
        properties.setAttachmentMaxSize(DataSize.ofBytes(4));
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.OTHER.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("材料.pdf", "application/pdf", 8)));
        assertTrue(ex.getMessage().contains("附件大小超过上限"));
    }

    @Test
    @DisplayName("超过单条业务记录附件数量上限时拒绝上传")
    void shouldRejectWhenCountExceeded() {
        properties.setAttachmentMaxCount(1);
        existingCount = 1;
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.OTHER.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.upload(bo, file("材料.pdf", "application/pdf", 8)));
        assertTrue(ex.getMessage().contains("附件数量已达上限"));
        assertTrue(putKeys.isEmpty());
    }

    /* ------------------------------------------------------------------ 上传版本 ------------------------------------------------------------------ */

    @Test
    @DisplayName("首次上传生成 v1 当前版本，对象键不含原文件名")
    void shouldCreateFirstVersion() {
        RecruitAttachmentBo bo = bo("application", 100L, AttachmentTypeEnum.PORTFOLIO.getCode());
        bo.setRemark("作品集");

        Long attachmentId = service.upload(bo, file("张三-作品集.pdf", "application/pdf", 16));

        assertNotNull(attachmentId);
        assertEquals(1, inserted.size());
        RecruitAttachment entity = inserted.get(0);
        assertEquals(attachmentId, entity.getAttachmentId());
        assertEquals("application", entity.getBizType());
        assertEquals(100L, entity.getBizId());
        assertEquals("portfolio", entity.getFileType());
        assertEquals(1, entity.getVersionNo());
        assertEquals("1", entity.getCurrentFlag());
        assertEquals("pdf", entity.getFileSuffix());
        assertEquals(16L, entity.getFileSize());
        assertEquals(64, entity.getFileHash().length());
        assertEquals(DataLevelEnum.SENSITIVE.getCode(), entity.getSecurityLevel());
        // 数据库只保存对象键，且对象键以随机附件ID为路径段，不使用原文件名
        assertEquals(1, putKeys.size());
        assertEquals(entity.getOssId(), putKeys.get(0));
        assertTrue(entity.getOssId().startsWith("hr-talent-private/attachment/application/100/" + attachmentId + "/v1/"));
        assertFalse(entity.getOssId().contains("张三"));
        assertFalse(currentVersionDemoted);
    }

    @Test
    @DisplayName("同业务对象同附件类型再次上传时版本递增并置否旧当前版本")
    void shouldIncreaseVersionAndDemoteOldVersion() {
        maxVersionNo = 1;
        RecruitAttachmentBo bo = bo("application", 100L, AttachmentTypeEnum.PORTFOLIO.getCode());

        service.upload(bo, file("作品集-v2.pdf", "application/pdf", 16));

        RecruitAttachment entity = inserted.get(0);
        assertEquals(2, entity.getVersionNo());
        assertEquals("1", entity.getCurrentFlag());
        assertTrue(currentVersionDemoted);
        assertTrue(entity.getOssId().contains("/v2/"));
    }

    @Test
    @DisplayName("数据库写入失败时清理已上传的孤立对象")
    void shouldCleanObjectWhenInsertFails() {
        RecruitAttachmentMapper failingMapper = failingInsertMapperStub();
        service = new RecruitAttachmentServiceImpl(
            failingMapper, ossStub(), properties, auditRecorderStub(), checkerProviderStub());
        RecruitAttachmentBo bo = bo("application", 1L, AttachmentTypeEnum.OTHER.getCode());

        assertThrows(ServiceException.class, () -> service.upload(bo, file("材料.pdf", "application/pdf", 8)));
        assertEquals(1, putKeys.size());
        assertEquals(putKeys, deletedOssKeys);
    }

    /* ------------------------------------------------------------------ 查询与删除 ------------------------------------------------------------------ */

    @Test
    @DisplayName("列表查询缺少业务对象ID时拒绝")
    void shouldRejectListWithoutBizId() {
        RecruitAttachmentQueryBo query = new RecruitAttachmentQueryBo();
        query.setBizType("application");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.queryPage(query, new PageQuery()));
        assertEquals("业务对象ID不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("列表查询附件类型非法时拒绝")
    void shouldRejectListWithUnknownFileType() {
        RecruitAttachmentQueryBo query = new RecruitAttachmentQueryBo();
        query.setBizType("application");
        query.setBizId(1L);
        query.setFileType("certificate");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.queryPage(query, new PageQuery()));
        assertTrue(ex.getMessage().contains("附件类型不合法"));
    }

    @Test
    @DisplayName("删除为逻辑删除：不物理删除对象文件，并写入删除审计")
    void shouldLogicallyDeleteWithoutRemovingObject() {
        RecruitAttachment attachment = attachment("application", 100L, "portfolio", 1, "f".repeat(64));
        attachment.setOssId("hr-talent-private/attachment/application/100/1/v1/original.pdf");
        store.put(1L, attachment);

        service.remove(new Long[]{1L});

        assertEquals(List.of(1L), deletedIds);
        assertTrue(deletedOssKeys.isEmpty(), "逻辑删除不得删除对象存储文件");
        assertEquals(1, audits.size());
        assertEquals(SensitiveAuditRecorder.EVENT_ATTACHMENT_DELETE, audits.get(0).getEventType());
        assertEquals(SensitiveAuditRecorder.RESULT_SUCCESS, audits.get(0).getResult());
        // 审计明细不得包含对象键与原始文件名
        assertNotNull(audits.get(0).getDetailJson());
        assertFalse(audits.get(0).getDetailJson().contains("hr-talent-private"));
        assertFalse(audits.get(0).getDetailJson().contains("original.pdf"));
    }

    @Test
    @DisplayName("删除不存在的附件时拒绝")
    void shouldRejectRemoveWhenMissing() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.remove(new Long[]{404L}));
        assertTrue(ex.getMessage().contains("附件不存在"));
        assertTrue(deletedIds.isEmpty());
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造附件 Mapper 的动态代理替身。
     *
     * @return Mapper 替身
     */
    private RecruitAttachmentMapper attachmentMapperStub() {
        return (RecruitAttachmentMapper) Proxy.newProxyInstance(
            RecruitAttachmentMapper.class.getClassLoader(),
            new Class<?>[]{RecruitAttachmentMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> store.get((Long) args[0]);
                case "selectByIds" -> new ArrayList<>(store.values());
                case "selectCount" -> existingCount;
                case "selectMaxVersionNo" -> maxVersionNo;
                case "insert" -> {
                    RecruitAttachment entity = (RecruitAttachment) args[0];
                    inserted.add(entity);
                    store.put(entity.getAttachmentId(), entity);
                    yield 1;
                }
                case "update" -> {
                    currentVersionDemoted = true;
                    yield 1;
                }
                case "deleteByIds" -> {
                    deletedIds.addAll((Collection<Long>) args[0]);
                    yield 1;
                }
                case "toString" -> "RecruitAttachmentMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造插入即失败的附件 Mapper 替身，用于验证孤立对象清理。
     *
     * @return Mapper 替身
     */
    private RecruitAttachmentMapper failingInsertMapperStub() {
        return (RecruitAttachmentMapper) Proxy.newProxyInstance(
            RecruitAttachmentMapper.class.getClassLoader(),
            new Class<?>[]{RecruitAttachmentMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectMaxVersionNo" -> 0;
                case "selectCount" -> 0L;
                case "insert" -> throw new ServiceException("附件登记失败，请重试");
                case "toString" -> "FailingRecruitAttachmentMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造对象存储访问替身（不访问真实 OSS）。
     *
     * @return OSS 封装替身
     */
    private HrTalentOssHelper ossStub() {
        return new HrTalentOssHelper(properties) {
            @Override
            public void put(String key, byte[] data) {
                putKeys.add(key);
            }

            @Override
            public void put(String key, InputStream in, long size) {
                putKeys.add(key);
            }

            @Override
            public void delete(String key) {
                deletedOssKeys.add(key);
            }

            @Override
            public void get(String key, OutputStream out) {
                // 单元测试不读取真实对象
            }
        };
    }

    /**
     * 构造审计记录器替身，把插入的审计记录收集到内存。
     *
     * @return 审计记录器
     */
    private SensitiveAuditRecorder auditRecorderStub() {
        RecruitSensitiveAuditMapper mapper = (RecruitSensitiveAuditMapper) Proxy.newProxyInstance(
            RecruitSensitiveAuditMapper.class.getClassLoader(),
            new Class<?>[]{RecruitSensitiveAuditMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    audits.add((RecruitSensitiveAudit) args[0]);
                    yield 1;
                }
                case "toString" -> "RecruitSensitiveAuditMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
        return new SensitiveAuditRecorder(mapper);
    }

    /**
     * 构造业务记录鉴权扩展点提供者替身。
     *
     * @return ObjectProvider 替身
     */
    @SuppressWarnings("unchecked")
    private ObjectProvider<AttachmentBizAccessChecker> checkerProviderStub() {
        return (ObjectProvider<AttachmentBizAccessChecker>) Proxy.newProxyInstance(
            ObjectProvider.class.getClassLoader(),
            new Class<?>[]{ObjectProvider.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getIfAvailable" -> bizAccessChecker;
                case "getObject" -> bizAccessChecker;
                case "toString" -> "AttachmentBizAccessCheckerProviderStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
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

    /* ------------------------------------------------------------------ 测试数据 ------------------------------------------------------------------ */

    /**
     * 构造上传入参。
     *
     * @param bizType  业务类型
     * @param bizId    业务对象ID
     * @param fileType 附件类型
     * @return 上传入参
     */
    private RecruitAttachmentBo bo(String bizType, Long bizId, String fileType) {
        RecruitAttachmentBo bo = new RecruitAttachmentBo();
        bo.setBizType(bizType);
        bo.setBizId(bizId);
        bo.setFileType(fileType);
        return bo;
    }

    /**
     * 构造上传文件替身。
     *
     * @param originalFilename 原始文件名
     * @param contentType      内容类型
     * @param size             字节数
     * @return 文件替身
     */
    private MultipartFile file(String originalFilename, String contentType, int size) {
        return new FakeFile(originalFilename, contentType, size);
    }

    /**
     * 构造一条最小可用的附件实体。
     *
     * @param bizType  业务类型
     * @param bizId    业务对象ID
     * @param fileType 附件类型
     * @param version  版本号
     * @param hash     文件哈希
     * @return 附件实体
     */
    private RecruitAttachment attachment(String bizType, Long bizId, String fileType, int version, String hash) {
        RecruitAttachment attachment = new RecruitAttachment();
        attachment.setAttachmentId(1L);
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setFileType(fileType);
        attachment.setOssId("hr-talent-private/attachment/" + bizType + "/" + bizId + "/1/v" + version + "/original.pdf");
        attachment.setOriginalName("材料.pdf");
        attachment.setFileSuffix("pdf");
        attachment.setFileSize(8L);
        attachment.setFileHash(hash);
        attachment.setVersionNo(version);
        attachment.setCurrentFlag("1");
        attachment.setSecurityLevel(DataLevelEnum.SENSITIVE.getCode());
        return attachment;
    }

    /**
     * 构造一条最小可用的应聘记录（只用于鉴权链路解析）。
     *
     * @param applicationId 应聘记录ID
     * @param talentId      人才ID
     * @return 应聘记录实体
     */
    private RecruitApplication application(Long applicationId, Long talentId) {
        RecruitApplication application = new RecruitApplication();
        application.setApplicationId(applicationId);
        application.setTalentId(talentId);
        return application;
    }

    /**
     * 构造一条最小可用的人才主档（只用于可见范围快照装配）。
     *
     * @param talentId       人才ID
     * @param visibilityType 可见范围编码
     * @return 人才主档实体
     */
    private TalentProfile profile(Long talentId, String visibilityType) {
        TalentProfile profile = new TalentProfile();
        profile.setTalentId(talentId);
        profile.setOwnerId(9L);
        profile.setOwnerDeptId(3L);
        profile.setVisibilityType(visibilityType);
        profile.setTalentStatus("active");
        profile.setDelFlag("0");
        return profile;
    }

    /**
     * 构造只读 Mapper 替身：{@code selectById} 返回给定值，其余方法返回类型默认值。
     *
     * @param type  Mapper 接口
     * @param value selectById 返回值
     * @param <T>   Mapper 类型
     * @return Mapper 替身
     */
    @SuppressWarnings("unchecked")
    private <T> T mapperStub(Class<T> type, Object value) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> value;
                case "toString" -> type.getSimpleName() + "Stub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才可见范围领域服务替身：记录被判定的人才快照，按需抛拒绝异常。
     *
     * @author hr-talent
     */
    private static final class ScopeStub extends TalentScopeDomainService {

        /**
         * 需要抛出的拒绝异常；为 null 表示判定通过。
         */
        private final ServiceException deny;

        /**
         * 最近一次被判定的人才快照。
         */
        private TalentScopeTarget captured;

        /**
         * 构造替身。
         *
         * @param deny 拒绝异常，可为 null
         */
        private ScopeStub(ServiceException deny) {
            // 替身不消费授权扩展点，故传 null
            super(null);
            this.deny = deny;
        }

        @Override
        public void checkTalentVisible(TalentScopeTarget target) {
            this.captured = target;
            if (deny != null) {
                throw deny;
            }
        }
    }

    /**
     * 内存文件替身（不依赖 Mockito 的 MultipartFile 实现）。
     *
     * @author hr-talent
     */
    private static final class FakeFile implements MultipartFile {

        /**
         * 表单字段名。
         */
        private final String name = "file";

        /**
         * 原始文件名。
         */
        private final String originalFilename;

        /**
         * 内容类型。
         */
        private final String contentType;

        /**
         * 文件内容。
         */
        private final byte[] content;

        /**
         * 声明的大小（用于模拟超出上限的入参）。
         */
        private final long declaredSize;

        /**
         * 构造文件替身。
         *
         * @param originalFilename 原始文件名
         * @param contentType      内容类型
         * @param declaredSize     声明大小
         */
        private FakeFile(String originalFilename, String contentType, long declaredSize) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.declaredSize = declaredSize;
            this.content = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return declaredSize;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("测试替身不支持写出文件");
        }
    }

    @Test
    @DisplayName("附件类型枚举编码稳定，未知编码返回空")
    void shouldExposeStableAttachmentTypeCodes() {
        assertEquals(AttachmentTypeEnum.RESUME, AttachmentTypeEnum.find(AttachmentTypeEnum.RESUME.getCode()));
        assertEquals("other", AttachmentTypeEnum.find(AttachmentTypeEnum.OTHER.getCode()).getCode());
        assertNull(AttachmentTypeEnum.find("certificate"));
    }

}
