package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.talent.TalentExportCreateBo;
import org.dromara.hrtalent.domain.bo.talent.TalentExportQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.entity.TalentExportTask;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentProfileTag;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.domain.vo.talent.TalentExportRowVo;
import org.dromara.hrtalent.domain.vo.talent.TalentExportTaskVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.TalentExportTaskMapper;
import org.dromara.hrtalent.mapper.TalentProfileChangeMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.mapper.TalentProfileTagMapper;
import org.dromara.hrtalent.mapper.TalentTagMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.HrTalentOssHelper;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.dromara.hrtalent.support.TalentScopeGrantProvider;
import org.dromara.system.api.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 人才导出任务与手机号后四位检索单元测试（SPEC-P4 §2.6 F 线、§4 自检）。
 *
 * <p><b>覆盖的硬性规则</b>：</p>
 * <ul>
 *     <li>敏感台账：<b>用途必填</b>（为空拒绝并写 {@code denied} 审计）、需要<b>独立权限</b>；</li>
 *     <li>普通台账：无需用途即可导出，任务落入 {@code hr_talent_export_task} 且状态为 success；</li>
 *     <li>结果文件过期 → 下载被拒绝（且不读取私有对象存储）；受控下载用途必填；</li>
 *     <li>导出记录与结果文件<b>不含</b>对象存储永久地址（只提供系统内受控下载地址）；</li>
 *     <li>超过单次导出行数上限 → 直接拒绝，不静默截断；</li>
 *     <li>手机号后四位：写入侧口径（{@code TalentContactCodec.phoneTail4}）与检索侧
 *     {@code phone_tail4} 精确匹配能够闭环命中；完整手机号不落筛选条件快照；</li>
 *     <li>资料完整度筛选按约定 fail-fast（不加列、不静默返回错误结果）。</li>
 * </ul>
 *
 * <p><b>说明</b>：Mapper 与服务替身全部使用 JDK 动态代理 / 匿名子类手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）；
 * 被测代码刻意不经过 {@code MapstructUtils} / {@code JsonUtils}（二者静态初始化依赖 Spring 容器）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentExportServiceImplTest {

    /**
     * 固定导出任务ID。
     */
    private static final Long TASK_ID = 9001L;

    /**
     * 业务配置（真实实例，便于切换导出行数上限）。
     */
    private final HrTalentProperties properties = new HrTalentProperties();

    /**
     * 内存导出任务表。
     */
    private final List<TalentExportTask> tasks = new ArrayList<>();

    /**
     * 内存人才主档表（导出检索的数据源）。
     */
    private final List<TalentProfile> talentTable = new ArrayList<>();

    /**
     * 捕获的审计事件（格式：eventType|result|purpose）。
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
     * 捕获的对象存储预签名调用（用于断言「绝不产生签名地址」）。
     */
    private final List<String> ossPresigns = new ArrayList<>();

    /**
     * 内存对象存储内容（对象键 → 字节）。
     */
    private final Map<String, byte[]> ossObjects = new HashMap<>();

    /**
     * 捕获的响应头。
     */
    private final Map<String, String> responseHeaders = new HashMap<>();

    /**
     * 是否具备敏感台账独立权限（控制可见范围服务替身）。
     */
    private boolean sensitivePermission;

    /**
     * 是否集团级管理员（控制可见范围服务替身）。
     */
    private boolean groupAdmin = true;

    /**
     * 真实人才主档服务收到的最后一次检索条件包装器。
     */
    private LambdaQueryWrapper<TalentProfile> lastSearchWrapper;

    /**
     * 导出任务更新次数（过期状态推进）。
     */
    private int taskUpdateCount;

    /**
     * 被测导出服务。
     */
    private TalentExportServiceImpl exportService;

    /**
     * 真实人才主档服务（用于验证 §8.17 检索条件构造与完整度 fail-fast）。
     */
    private TalentProfileServiceImpl profileService;

    /**
     * 注册被测实体在 MyBatis-Plus 中的表元数据（Lambda 条件解析需要）。
     */
    @BeforeAll
    static void initTableMetadata() {
        MybatisTableInfoTestSupport.init(TalentProfile.class, TalentExportTask.class,
            TalentProfileTag.class, TalentTag.class);
    }

    @BeforeEach
    void setUp() {
        tasks.clear();
        talentTable.clear();
        audits.clear();
        ossPuts.clear();
        ossGets.clear();
        ossPresigns.clear();
        ossObjects.clear();
        responseHeaders.clear();
        sensitivePermission = false;
        groupAdmin = true;
        lastSearchWrapper = null;
        taskUpdateCount = 0;

        exportService = new TalentExportServiceImpl(taskMapperStub(), profileServiceStub(),
            profileTagMapperStub(), tagMapperStub(), scopeServiceStub(), ossHelperStub(), properties,
            auditRecorderStub(), new RecruitBusinessNoGenerator(), emptyProvider());
        profileService = new TalentProfileServiceImpl(profileMapperStub(), (TalentProfileChangeMapper) proxyOf(
            TalentProfileChangeMapper.class, null), (RecruitApplicationMapper) proxyOf(
            RecruitApplicationMapper.class, null), null, scopeServiceStub(), new RecruitBusinessNoGenerator(),
            null, auditRecorderStub());
    }

    /* ------------------------------------------------------------------ 敏感台账 ------------------------------------------------------------------ */

    @Test
    @DisplayName("敏感台账：用途为空 → 拒绝并写 denied 审计，且不落任务、不写对象存储")
    void shouldRejectSensitiveExportWithoutPurpose() {
        TalentExportCreateBo bo = createBo("sensitive", "   ");
        bo.getFilters().setPhone("13800001234");

        ServiceException ex = assertThrows(ServiceException.class, () -> exportService.createExport(bo));

        assertEquals("敏感台账导出必须填写用途（purpose）", ex.getMessage());
        assertEquals(List.of("export|denied|null"), audits);
        assertTrue(tasks.isEmpty(), "被拒绝的导出不得落任务记录");
        assertTrue(ossPuts.isEmpty(), "被拒绝的导出不得写入对象存储");
    }

    @Test
    @DisplayName("敏感台账：缺少独立权限 → 拒绝并写 denied 审计")
    void shouldRejectSensitiveExportWithoutIndependentPermission() {
        sensitivePermission = false;
        TalentExportCreateBo bo = createBo("sensitive", "背景核实");

        ServiceException ex = assertThrows(ServiceException.class, () -> exportService.createExport(bo));

        assertTrue(ex.getMessage().contains("无权导出敏感台账"));
        assertTrue(audits.contains("export|denied|背景核实"));
        assertTrue(ossPuts.isEmpty());
    }

    @Test
    @DisplayName("敏感台账：具备独立权限且用途非空 → 允许导出并写成功审计")
    void shouldAllowSensitiveExportWithPermissionAndPurpose() {
        sensitivePermission = true;
        talentTable.add(profile(1L, "TAL001", "张三", "13800001234"));

        Long taskId = exportService.createExport(createBo("sensitive", "背景核实"));

        assertNotNull(taskId);
        assertEquals(1, tasks.size());
        assertEquals("sensitive", tasks.get(0).getExportType());
        assertEquals("背景核实", tasks.get(0).getPurpose());
        assertEquals("success", tasks.get(0).getStatus());
        assertEquals(1, ossPuts.size());
        assertTrue(audits.contains("export|success|背景核实"));
    }

    /* ------------------------------------------------------------------ 普通台账 ------------------------------------------------------------------ */

    @Test
    @DisplayName("普通台账：无需用途即可导出，任务落库为 success 且带过期时间")
    void shouldExportNormalLedgerWithoutPurpose() {
        talentTable.add(profile(1L, "TAL001", "张三", "13800001234"));
        TalentExportCreateBo bo = createBo("normal", null);
        bo.getFilters().setPhone("13800001234");

        Long taskId = exportService.createExport(bo);

        assertNotNull(taskId);
        assertEquals(1, tasks.size());
        TalentExportTask saved = tasks.get(0);
        assertEquals("normal", saved.getExportType());
        assertNull(saved.getPurpose());
        assertEquals("success", saved.getStatus());
        assertEquals(1, saved.getRecordCount());
        assertNotNull(saved.getExpireTime(), "必须写入结果文件过期时间");
        assertTrue(saved.getExpireTime().isAfter(LocalDateTime.now()));
        assertNotNull(saved.getOssId());
        assertTrue(saved.getOssId().startsWith("hr-talent-private/exports/"), "结果文件必须写入私有前缀");
        assertNotNull(saved.getTaskNo());
        // 筛选条件快照禁止出现完整手机号明文
        assertFalse(saved.getScopeJson().contains("13800001234"));
        assertTrue(saved.getScopeJson().contains("phoneExactMatch"));
        assertTrue(saved.getFieldsJson().contains("talentNo"));
        assertTrue(audits.contains("export|success|null"));
    }

    @Test
    @DisplayName("导出：超过单次行数上限 → 直接拒绝，不静默截断")
    void shouldRejectOversizeExport() {
        properties.setExportMaxRows(1);
        talentTable.add(profile(1L, "TAL001", "张三", "13800001234"));
        talentTable.add(profile(2L, "TAL002", "李四", "13800005678"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.createExport(createBo("normal", null)));

        assertTrue(ex.getMessage().contains("超过单次导出上限 1 条"));
        assertTrue(audits.contains("export|denied|null"));
        assertTrue(tasks.isEmpty());
        assertTrue(ossPuts.isEmpty());
    }

    @Test
    @DisplayName("导出：无匹配记录时拒绝并写 failed 审计")
    void shouldRejectEmptyExport() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.createExport(createBo("normal", null)));

        assertEquals("当前筛选条件下没有可导出的人才记录", ex.getMessage());
        assertTrue(audits.contains("export|failed|null"));
        assertTrue(tasks.isEmpty());
    }

    @Test
    @DisplayName("导出：字段清单只接受白名单，未知字段被拒绝")
    void shouldRejectUnknownExportField() {
        TalentExportCreateBo bo = createBo("normal", null);
        bo.setFields(List.of("talentNo", "phone"));

        ServiceException ex = assertThrows(ServiceException.class, () -> exportService.createExport(bo));

        assertEquals("不支持的导出字段：phone", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 受控下载 ------------------------------------------------------------------ */

    @Test
    @DisplayName("下载：用途为空 → 拒绝并写 denied 审计，不读取对象存储")
    void shouldRejectDownloadWithoutPurpose() {
        tasks.add(task("success", LocalDateTime.now().plusDays(1)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.download(TASK_ID, "   ", responseStub(new ByteArrayOutputStream())));

        assertEquals("下载导出文件必须填写用途（purpose）", ex.getMessage());
        assertTrue(audits.contains("export|denied|null"));
        assertTrue(ossGets.isEmpty(), "用途为空时不得读取对象存储");
    }

    @Test
    @DisplayName("下载：结果文件已过期 → 拒绝并推进任务状态，不读取对象存储")
    void shouldRejectExpiredDownload() {
        tasks.add(task("success", LocalDateTime.now().minusDays(1)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.download(TASK_ID, "对账", responseStub(new ByteArrayOutputStream())));

        assertEquals("导出文件已过期，请重新导出", ex.getMessage());
        assertTrue(audits.contains("export|denied|对账"));
        assertTrue(taskUpdateCount > 0, "过期任务状态必须被推进");
        assertTrue(ossGets.isEmpty(), "过期文件不得读取对象存储");
        assertTrue(ossPresigns.isEmpty(), "绝不产生对象存储签名地址");
    }

    @Test
    @DisplayName("下载：鉴权与用途通过后流式输出并写成功审计，不产生签名地址")
    void shouldDownloadSuccessfully() {
        TalentExportTask task = task("success", LocalDateTime.now().plusDays(1));
        tasks.add(task);
        ossObjects.put(task.getOssId(), "XLSX-BYTES".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        exportService.download(TASK_ID, "用人部门归档", responseStub(captured));

        assertEquals("XLSX-BYTES", captured.toString(StandardCharsets.UTF_8));
        assertEquals(List.of(task.getOssId()), ossGets);
        assertTrue(audits.contains("export|success|用人部门归档"));
        assertTrue(ossPresigns.isEmpty(), "下载不得使用预签名地址");
        assertTrue(responseHeaders.get("Content-disposition").contains("talent-ledger.xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8",
            responseHeaders.get("Content-Type"));
    }

    @Test
    @DisplayName("下载：任务不存在时写 failed 审计")
    void shouldAuditFailedWhenTaskMissing() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.download(8888L, "对账", responseStub(new ByteArrayOutputStream())));

        assertEquals("导出任务不存在或已删除", ex.getMessage());
        assertTrue(audits.contains("export|failed|对账"));
    }

    @Test
    @DisplayName("下载：敏感台账在下钻时仍需独立权限")
    void shouldRejectSensitiveDownloadWithoutPermission() {
        TalentExportTask task = task("success", LocalDateTime.now().plusDays(1));
        task.setExportType("sensitive");
        tasks.add(task);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> exportService.download(TASK_ID, "对账", responseStub(new ByteArrayOutputStream())));

        assertTrue(ex.getMessage().contains("无权下载敏感台账"));
        assertTrue(ossGets.isEmpty());
    }

    /* ------------------------------------------------------------------ 不泄露对象存储地址 ------------------------------------------------------------------ */

    @Test
    @DisplayName("导出记录不含对象存储永久地址：VO 无对象标识，列表只给系统内受控地址")
    void shouldNotExposeOssAddressInExportRecord() {
        assertTrue(Arrays.stream(TalentExportTaskVo.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("oss")),
            "导出任务 VO 不得暴露对象存储标识");
        talentTable.add(profile(1L, "TAL001", "张三", "13800001234"));

        Long taskId = exportService.createExport(createBo("normal", null));
        PageResult<TalentExportTaskVo> page = exportService.queryPage(new TalentExportQueryBo(),
            new PageQuery(10, 1));

        assertEquals(1, page.getTotal());
        TalentExportTaskVo vo = new ArrayList<>(page.getRows()).get(0);
        assertEquals(taskId, vo.getTaskId());
        assertEquals("/talent/exports/" + taskId + "/download", vo.getDownloadApi());
        assertFalse(vo.getDownloadApi().contains("http"), "受控下载地址必须是系统内相对地址");
        assertFalse(vo.getDownloadApi().contains("hr-talent-private"));

        // 结果文件内容同样不得包含对象键或任何 http 地址
        byte[] content = ossObjects.get(tasks.get(0).getOssId());
        assertNotNull(content);
        String raw = new String(content, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("hr-talent-private"), "导出文件不得写入对象键");
        assertFalse(raw.contains("http"), "导出文件不得写入任何公网地址");
        assertTrue(ossPresigns.isEmpty());
    }

    @Test
    @DisplayName("导出行：普通台账姓名脱敏且不含联系方式；敏感台账只写系统内受控地址")
    void shouldBuildRowsWithMaskingAndControlledAddress() throws Exception {
        TalentProfile profile = profile(11L, "TAL011", "赵六", "13700009999");
        profile.setCurrentResumeId(555L);
        profile.setExpectedSalaryMin(new BigDecimal("20000"));
        profile.setExpectedSalaryMax(new BigDecimal("30000"));
        Method buildRows = TalentExportServiceImpl.class.getDeclaredMethod("buildRows", List.class, boolean.class);
        buildRows.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<TalentExportRowVo> normalRows = (List<TalentExportRowVo>) buildRows.invoke(exportService,
            List.of(profile), false);
        TalentExportRowVo normal = normalRows.get(0);
        assertFalse("赵六".equals(normal.getName()), "普通台账姓名必须脱敏");
        assertTrue(normal.getName().contains("*"));
        assertNull(normal.getPhone(), "普通台账不得包含电话");
        assertNull(normal.getEmail(), "普通台账不得包含邮箱");

        @SuppressWarnings("unchecked")
        List<TalentExportRowVo> sensitiveRows = (List<TalentExportRowVo>) buildRows.invoke(exportService,
            List.of(profile), true);
        TalentExportRowVo sensitive = sensitiveRows.get(0);
        assertEquals("赵六", sensitive.getName());
        assertEquals("13700009999", sensitive.getPhone());
        assertEquals("/talent/profiles/11", sensitive.getProfileAccess());
        assertEquals("/talent/resumes/555/download", sensitive.getResumeAccess());
        assertEquals("20000-30000", sensitive.getSalary());
        assertFalse(sensitive.getProfileAccess().contains("http"));
        assertFalse(sensitive.getResumeAccess().contains("http"));
    }

    /* ------------------------------------------------------------------ 手机号后四位检索 ------------------------------------------------------------------ */

    @Test
    @DisplayName("手机号后四位：写入侧口径正确（规范化后取末四位）")
    void shouldComputePhoneTail4() {
        assertEquals("1234", TalentContactCodec.phoneTail4("13800001234"));
        assertEquals("1234", TalentContactCodec.phoneTail4("138-0000-1234"));
        assertEquals("1234", TalentContactCodec.phoneTail4("+8613800001234"));
        assertEquals("1234", TalentContactCodec.phoneTail4(" 13800001234 "));
        assertNull(TalentContactCodec.phoneTail4("123"), "有效数字不足 4 位时返回 null");
        assertNull(TalentContactCodec.phoneTail4("   "));
        assertNull(TalentContactCodec.phoneTail4(null));
    }

    @Test
    @DisplayName("手机号后四位：写入 phone_tail4 后可按后四位检索命中（真实条件构造器）")
    void shouldSearchTalentByPhoneTail4() {
        TalentProfile matched = profile(7L, "TAL007", "李四", "13800001234");
        // 与 TalentProfileServiceImpl#create / #update 完全相同的写入口径
        matched.setPhoneTail4(TalentContactCodec.phoneTail4("13800001234"));
        TalentProfile other = profile(8L, "TAL008", "王五", "13900005678");
        other.setPhoneTail4(TalentContactCodec.phoneTail4("13900005678"));
        talentTable.add(matched);
        talentTable.add(other);

        TalentProfileQueryBo bo = new TalentProfileQueryBo();
        bo.setPhoneTail4("1234");
        List<TalentProfile> result = profileService.searchForExport(bo, 10);

        assertEquals(1, result.size());
        assertEquals("TAL007", result.get(0).getTalentNo());
        assertNotNull(lastSearchWrapper);
        assertTrue(lastSearchWrapper.getSqlSegment().contains("phone_tail4"),
            "检索条件必须落到 phone_tail4 列");
        assertTrue(lastSearchWrapper.getParamNameValuePairs().values().stream()
            .map(String::valueOf).anyMatch("1234"::equals), "后四位必须作为参数精确匹配");
    }

    @Test
    @DisplayName("手机号后四位：后四位不匹配时不返回结果")
    void shouldMissWhenPhoneTail4NotMatched() {
        TalentProfile profile = profile(7L, "TAL007", "李四", "13800001234");
        profile.setPhoneTail4(TalentContactCodec.phoneTail4("13800001234"));
        talentTable.add(profile);

        TalentProfileQueryBo bo = new TalentProfileQueryBo();
        bo.setPhoneTail4("9999");

        assertTrue(profileService.searchForExport(bo, 10).isEmpty());
    }

    @Test
    @DisplayName("资料完整度筛选：按约定 fail-fast（不加列、不静默返回错误结果）")
    void shouldRejectCompletenessFilter() {
        talentTable.add(profile(1L, "TAL001", "张三", "13800001234"));
        TalentProfileQueryBo bo = new TalentProfileQueryBo();
        bo.setMinCompleteness(80);

        ServiceException ex = assertThrows(ServiceException.class, () -> profileService.searchForExport(bo, 10));

        assertTrue(ex.getMessage().contains("资料完整度筛选暂不支持"));
    }

    /* ------------------------------------------------------------------ 测试替身 ------------------------------------------------------------------ */

    /**
     * 构造导出创建入参。
     *
     * @param exportType 导出类型
     * @param purpose    用途
     * @return 入参
     */
    private TalentExportCreateBo createBo(String exportType, String purpose) {
        TalentExportCreateBo bo = new TalentExportCreateBo();
        bo.setExportType(exportType);
        bo.setPurpose(purpose);
        bo.setFilters(new TalentProfileQueryBo());
        return bo;
    }

    /**
     * 构造内存人才主档。
     *
     * @param talentId 人才ID
     * @param talentNo 人才编号
     * @param name     姓名
     * @param phone    电话明文
     * @return 主档实体
     */
    private TalentProfile profile(Long talentId, String talentNo, String name, String phone) {
        TalentProfile profile = new TalentProfile();
        profile.setTalentId(talentId);
        profile.setTalentNo(talentNo);
        profile.setName(name);
        profile.setPhoneCipher(phone);
        profile.setPhoneHash(TalentContactCodec.phoneHash(phone));
        profile.setHighestEducation("bachelor");
        profile.setCurrentCity("上海");
        profile.setExpectedPosition("后端工程师");
        profile.setTalentStatus("active");
        profile.setDelFlag("0");
        return profile;
    }

    /**
     * 构造内存导出任务。
     *
     * @param status     状态
     * @param expireTime 过期时间
     * @return 任务实体
     */
    private TalentExportTask task(String status, LocalDateTime expireTime) {
        TalentExportTask task = new TalentExportTask();
        task.setTaskId(TASK_ID);
        task.setTaskNo("EXP20260921000000000-001");
        task.setExportType("normal");
        task.setStatus(status);
        task.setOssId("hr-talent-private/exports/" + TASK_ID + "/talent-ledger.xlsx");
        task.setFileName("talent-ledger.xlsx");
        task.setRecordCount(1);
        task.setExpireTime(expireTime);
        task.setFinishedTime(LocalDateTime.now());
        task.setDelFlag("0");
        return task;
    }

    /**
     * 导出任务 Mapper 动态代理替身。
     *
     * @return 替身
     */
    private TalentExportTaskMapper taskMapperStub() {
        return (TalentExportTaskMapper) Proxy.newProxyInstance(
            TalentExportTaskMapper.class.getClassLoader(),
            new Class<?>[]{TalentExportTaskMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> tasks.stream()
                    .filter(t -> t.getTaskId().equals(args[0]))
                    .findFirst().orElse(null);
                case "selectVoPage" -> {
                    Page<TalentExportTaskVo> page = new Page<>(1, 10, tasks.size());
                    page.setRecords(tasks.stream().map(this::taskVoOf).toList());
                    yield page;
                }
                case "insert" -> {
                    tasks.add((TalentExportTask) args[0]);
                    yield 1;
                }
                case "update" -> {
                    taskUpdateCount++;
                    yield 1;
                }
                case "toString" -> "TalentExportTaskMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 实体 → 导出任务 VO（替身内部手工装配，避免 MapStruct 依赖 Spring 容器）。
     *
     * @param task 任务实体
     * @return 视图对象
     */
    private TalentExportTaskVo taskVoOf(TalentExportTask task) {
        TalentExportTaskVo vo = new TalentExportTaskVo();
        vo.setTaskId(task.getTaskId());
        vo.setTaskNo(task.getTaskNo());
        vo.setExportType(task.getExportType());
        vo.setScopeJson(task.getScopeJson());
        vo.setFieldsJson(task.getFieldsJson());
        vo.setExportedBy(task.getExportedBy());
        vo.setPurpose(task.getPurpose());
        vo.setRecordCount(task.getRecordCount());
        vo.setFileName(task.getFileName());
        vo.setStatus(task.getStatus());
        vo.setExpireTime(task.getExpireTime());
        vo.setFinishedTime(task.getFinishedTime());
        return vo;
    }

    /**
     * 人才主档服务替身：按后四位过滤内存表，并记录调用参数。
     *
     * @return 替身
     */
    private ITalentProfileService profileServiceStub() {
        return (ITalentProfileService) Proxy.newProxyInstance(
            ITalentProfileService.class.getClassLoader(),
            new Class<?>[]{ITalentProfileService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "searchForExport" -> {
                    TalentProfileQueryBo bo = (TalentProfileQueryBo) args[0];
                    int limit = (Integer) args[1];
                    String tail4 = bo == null ? null : bo.getPhoneTail4();
                    yield talentTable.stream()
                        .filter(p -> tail4 == null || tail4.equals(p.getPhoneTail4()))
                        .limit(limit)
                        .toList();
                }
                case "toString" -> "TalentProfileServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才主档 Mapper 动态代理替身（供真实检索条件构造器使用）。
     *
     * @return 替身
     */
    private TalentProfileMapper profileMapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectVisibleTalentIds" -> talentTable.stream().map(TalentProfile::getTalentId).toList();
                case "selectList" -> {
                    lastSearchWrapper = (LambdaQueryWrapper<TalentProfile>) args[0];
                    // MP 的条件片段是懒生成的：先取一次 SQL 片段以填充参数表，替身才能读到条件值
                    lastSearchWrapper.getSqlSegment();
                    String tail4 = lastSearchWrapper.getParamNameValuePairs().values().stream()
                        .map(String::valueOf)
                        .filter(value -> value.matches("\\d{4}"))
                        .findFirst().orElse(null);
                    yield talentTable.stream()
                        .filter(p -> tail4 == null || tail4.equals(p.getPhoneTail4()))
                        .toList();
                }
                case "toString" -> "TalentProfileMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才标签关系 Mapper 替身（导出测试不涉及标签）。
     *
     * @return 替身
     */
    private TalentProfileTagMapper profileTagMapperStub() {
        return (TalentProfileTagMapper) proxyOf(TalentProfileTagMapper.class, List.of());
    }

    /**
     * 人才标签字典 Mapper 替身。
     *
     * @return 替身
     */
    private TalentTagMapper tagMapperStub() {
        return (TalentTagMapper) proxyOf(TalentTagMapper.class, List.of());
    }

    /**
     * 通用「返回固定列表」的 Mapper 替身。
     *
     * @param type     Mapper 类型
     * @param returned selectList/selectByIds 的返回值
     * @return 替身
     */
    private Object proxyOf(Class<?> type, List<?> returned) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList", "selectByIds" -> returned;
                case "toString" -> type.getSimpleName() + "Stub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 人才可见范围领域服务替身：只覆写权限与角色判定，不触碰 Sa-Token 上下文。
     *
     * @return 替身
     */
    private TalentScopeDomainService scopeServiceStub() {
        ObjectProvider<TalentScopeGrantProvider> provider = emptyProvider();
        return new TalentScopeDomainService(provider) {
            @Override
            public boolean hasMenuPermission(String permission) {
                return sensitivePermission;
            }

            @Override
            public boolean isGroupLevelAdmin() {
                return groupAdmin;
            }

            @Override
            public QueryWrapper<TalentProfile> visibleTalentWrapper() {
                // 检索条件构造测试不需要真实登录态：返回一个恒真的可见范围条件
                return new QueryWrapper<TalentProfile>().apply("(p.del_flag = '0')");
            }
        };
    }

    /**
     * 空的对象提供者替身。
     *
     * @param <T> 提供者类型
     * @return 替身
     */
    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> emptyProvider() {
        return (ObjectProvider<T>) Proxy.newProxyInstance(ObjectProvider.class.getClassLoader(),
            new Class<?>[]{ObjectProvider.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getIfAvailable", "getIfUnique", "getObject" -> null;
                case "toString" -> "ObjectProviderStub";
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
                ossObjects.put(key, data);
            }

            @Override
            public void put(String key, java.io.InputStream in, long size) {
                ossPuts.add(key);
            }

            @Override
            public void get(String key, OutputStream out) {
                ossGets.add(key);
                byte[] data = ossObjects.get(key);
                if (data == null) {
                    return;
                }
                try {
                    out.write(data);
                } catch (java.io.IOException e) {
                    throw new ServiceException("导出文件读取失败");
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
     * 审计记录器替身：捕获事件类型、结果与用途。
     *
     * @return 替身
     */
    private SensitiveAuditRecorder auditRecorderStub() {
        return new SensitiveAuditRecorder(null) {
            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result) {
                audits.add(eventType + "|" + result + "|" + purpose);
            }

            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result,
                               String detailJson) {
                audits.add(eventType + "|" + result + "|" + purpose);
            }
        };
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
