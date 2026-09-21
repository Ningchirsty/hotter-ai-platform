package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.entity.TalentEducation;
import org.dromara.hrtalent.domain.entity.TalentProject;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentWork;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;
import org.dromara.hrtalent.mapper.TalentEducationMapper;
import org.dromara.hrtalent.mapper.TalentProjectMapper;
import org.dromara.hrtalent.mapper.TalentWorkMapper;
import org.dromara.hrtalent.service.talent.ITalentExperienceService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 教育与工作/项目经历领域规则单元测试（SPEC-P4 §2.2 B 线、设计文档 §8.14）。
 *
 * <p>覆盖的硬性规则：</p>
 * <ol>
 *     <li><b>可见范围闸门先行</b>：人才不可见时任何写入都被拒绝且不落库；</li>
 *     <li><b>归属防越权</b>：人才ID以路径为准（覆盖伪造入参），跨人才的经历ID不可编辑/删除；</li>
 *     <li><b>日期区间</b>：开始日期不得晚于结束日期；</li>
 *     <li><b>在职口径</b>：{@code current_flag='1'} 时离职日期在新增与编辑中都被归一化为 null；</li>
 *     <li><b>来源类型</b>：只接受 manual/import/resume/parse，解析类来源必须携带来源简历版本ID（§8.14）；</li>
 *     <li><b>项目关联</b>：关联的工作经历必须属于同一人才。</li>
 * </ol>
 *
 * <p><b>替身方式</b>：Mapper 与人才主档服务都用 JDK 动态代理手工构造，不使用 Mockito
 * （本机 JVM 禁止 Mockito 以自附加方式装载 Byte Buddy Agent）。
 * {@code LambdaUpdateWrapper} 的列名解析依赖 MyBatis-Plus 的 {@code TableInfo} 缓存，
 * 因此 {@link #initTableInfo()} 手动为三个实体注册表信息，模拟 Mapper 扫描的结果。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentExperienceServiceImplTest {

    /**
     * 路径上的人才主档ID（测试中的「当前人才」）。
     */
    private static final Long TALENT_ID = 1001L;

    /**
     * 其他人才主档ID（用于越权用例）。
     */
    private static final Long OTHER_TALENT_ID = 2002L;

    /**
     * 匹配 SET 子句里列的参数占位符，用于断言某个列被显式写成了 null。
     */
    private static final Pattern SET_PARAM = Pattern.compile("end_date\\s*=\\s*#\\{[^}]*\\.(MPGENVAL\\d+)}");

    /**
     * 内存中的教育经历，按主键索引。
     */
    private final Map<Long, TalentEducation> educationStore = new HashMap<>();

    /**
     * 内存中的工作经历，按主键索引。
     */
    private final Map<Long, TalentWork> workStore = new HashMap<>();

    /**
     * 内存中的项目经历，按主键索引。
     */
    private final Map<Long, TalentProject> projectStore = new HashMap<>();

    /**
     * 实际入库（insert）的教育经历。
     */
    private final List<TalentEducation> insertedEducations = new ArrayList<>();

    /**
     * 实际入库（insert）的工作经历。
     */
    private final List<TalentWork> insertedWorks = new ArrayList<>();

    /**
     * 实际入库（insert）的项目经历。
     */
    private final List<TalentProject> insertedProjects = new ArrayList<>();

    /**
     * 人才是否可见（false 时主档服务抛「无权查看该人才」）。
     */
    private boolean visible = true;

    /**
     * 删除前 selectCount 的返回值。
     */
    private long countResult = 1L;

    /**
     * 是否执行过逻辑删除。
     */
    private boolean deleteCalled;

    /**
     * 捕获到的编辑条件（用于断言 SET 子句）。
     */
    private LambdaUpdateWrapper<?> capturedUpdate;

    /**
     * 捕获到的删除条件。
     */
    private LambdaQueryWrapper<?> capturedDelete;

    /**
     * 列表查询替身返回值。
     */
    private List<TalentEducationVo> educationVoList = List.of();

    /**
     * 被测服务。
     */
    private TalentExperienceServiceImpl service;

    /**
     * 为三个实体注册 MyBatis-Plus 表信息（模拟 Mapper 扫描），使 Lambda 条件构造器可解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("org.dromara.hrtalent.mapper");
        TableInfoHelper.initTableInfo(assistant, TalentEducation.class);
        TableInfoHelper.initTableInfo(assistant, TalentWork.class);
        TableInfoHelper.initTableInfo(assistant, TalentProject.class);
    }

    @BeforeEach
    void setUp() {
        educationStore.clear();
        workStore.clear();
        projectStore.clear();
        insertedEducations.clear();
        insertedWorks.clear();
        insertedProjects.clear();
        educationVoList = List.of();
        visible = true;
        countResult = 1L;
        deleteCalled = false;
        capturedUpdate = null;
        capturedDelete = null;
        service = new TalentExperienceServiceImpl(
            educationMapper(), workMapper(), projectMapper(), profileService());
    }

    /* ------------------------------------------------------------------ 可见范围闸门 ------------------------------------------------------------------ */

    @Test
    @DisplayName("人才不可见时新增教育经历被拒绝，且不写库")
    void shouldRejectWhenTalentInvisible() {
        visible = false;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createEducation(TALENT_ID, educationBo()));

        assertEquals("无权查看该人才", ex.getMessage());
        assertTrue(insertedEducations.isEmpty(), "可见范围校验未通过时不得写库");
    }

    @Test
    @DisplayName("人才不可见时读取列表同样被拒绝")
    void shouldRejectListWhenTalentInvisible() {
        visible = false;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.listEducation(TALENT_ID, null));

        assertEquals("无权查看该人才", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 教育经历 ------------------------------------------------------------------ */

    @Test
    @DisplayName("入学日期晚于毕业日期时拒绝新增教育经历")
    void shouldRejectReversedEducationDates() {
        TalentEducationBo bo = educationBo();
        bo.setStartDate(LocalDate.of(2020, 9, 1));
        bo.setEndDate(LocalDate.of(2018, 6, 30));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createEducation(TALENT_ID, bo));

        assertEquals("入学日期不能晚于毕业日期", ex.getMessage());
        assertTrue(insertedEducations.isEmpty());
    }

    @Test
    @DisplayName("新增教育经历：归属人才取路径，来源/全日制/排序号按服务端口径兜底")
    void shouldCreateEducationWithServerSideDefaults() {
        TalentEducationBo bo = educationBo();
        // 伪造归属人才：服务端必须以路径上的 talentId 覆盖
        bo.setTalentId(9999L);
        bo.setFullTimeFlag(null);
        bo.setSortNo(null);
        bo.setSourceType(null);

        Long id = service.createEducation(TALENT_ID, bo);

        assertNotNull(id);
        TalentEducation saved = insertedEducations.get(0);
        assertEquals(TALENT_ID, saved.getTalentId(), "归属人才必须以路径为准");
        assertEquals("manual", saved.getSourceType());
        assertEquals("1", saved.getFullTimeFlag());
        assertEquals(0, saved.getSortNo());
        assertNull(saved.getResumeId());
    }

    @Test
    @DisplayName("解析来源缺少来源简历版本ID时拒绝新增（§8.14）")
    void shouldRejectResumeSourceWithoutResumeId() {
        TalentEducationBo bo = educationBo();
        bo.setSourceType(ITalentExperienceService.SOURCE_RESUME);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createEducation(TALENT_ID, bo));

        assertTrue(ex.getMessage().contains("必须关联来源简历版本ID"), ex.getMessage());
    }

    @Test
    @DisplayName("携带来源简历版本ID的解析来源可以新增")
    void shouldAcceptResumeSourceWithResumeId() {
        TalentEducationBo bo = educationBo();
        bo.setSourceType(ITalentExperienceService.SOURCE_RESUME);
        bo.setResumeId(7001L);

        service.createEducation(TALENT_ID, bo);

        TalentEducation saved = insertedEducations.get(0);
        assertEquals("resume", saved.getSourceType());
        assertEquals(7001L, saved.getResumeId());
    }

    @Test
    @DisplayName("未知来源类型被拒绝")
    void shouldRejectUnknownSourceType() {
        TalentEducationBo bo = educationBo();
        bo.setSourceType("ocr");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createEducation(TALENT_ID, bo));

        assertEquals("来源类型不合法，只允许 manual/import/resume/parse", ex.getMessage());
    }

    @Test
    @DisplayName("编辑不属于该人才的教育经历被拒绝")
    void shouldRejectUpdateWhenEducationNotOwned() {
        TalentEducation other = new TalentEducation();
        other.setEducationId(2001L);
        other.setTalentId(OTHER_TALENT_ID);
        educationStore.put(2001L, other);
        TalentEducationBo bo = educationBo();
        bo.setEducationId(2001L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateEducation(TALENT_ID, bo));

        assertEquals("教育经历不存在或不属于该人才", ex.getMessage());
        assertNull(capturedUpdate, "归属校验未通过时不得执行更新");
    }

    @Test
    @DisplayName("删除条件带 talent_id：混入他人经历ID也不会被删掉")
    void shouldScopeDeleteByTalentId() {
        TalentEducation own = new TalentEducation();
        own.setEducationId(4001L);
        own.setTalentId(TALENT_ID);
        educationStore.put(4001L, own);

        service.removeEducation(TALENT_ID, new Long[]{4001L, 4002L});

        assertTrue(deleteCalled);
        assertTrue(capturedDelete.getCustomSqlSegment().contains("talent_id"),
            "删除条件必须限定归属人才：" + capturedDelete.getCustomSqlSegment());
    }

    @Test
    @DisplayName("要删除的经历都不存在时给出中文提示且不执行删除")
    void shouldThrowWhenNothingToDelete() {
        countResult = 0L;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.removeEducation(TALENT_ID, new Long[]{4001L}));

        assertEquals("教育经历不存在或不属于该人才", ex.getMessage());
        assertFalse(deleteCalled);
    }

    @Test
    @DisplayName("空ID数组直接返回，不执行删除")
    void shouldIgnoreEmptyDeleteIds() {
        service.removeEducation(TALENT_ID, new Long[0]);

        assertFalse(deleteCalled);
    }

    @Test
    @DisplayName("列表查询返回服务层装配的 VO")
    void shouldListEducation() {
        TalentEducationVo vo = new TalentEducationVo();
        vo.setEducationId(5001L);
        vo.setTalentId(TALENT_ID);
        educationVoList = List.of(vo);

        List<TalentEducationVo> list = service.listEducation(TALENT_ID, null);

        assertEquals(1, list.size());
        assertEquals(5001L, list.get(0).getEducationId());
    }

    @Test
    @DisplayName("分页查询教育经历：空结果返回 total=0")
    void shouldQueryEducationPage() {
        PageResult<TalentEducationVo> result = service.queryEducationPage(TALENT_ID, null, null);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRows().isEmpty());
    }

    /* ------------------------------------------------------------------ 工作经历 ------------------------------------------------------------------ */

    @Test
    @DisplayName("工作经历：在职时离职日期被服务端归一化为空")
    void shouldClearEndDateWhenWorkIsCurrent() {
        TalentWorkBo bo = workBo();
        bo.setCurrentFlag("1");
        bo.setEndDate(LocalDate.of(2026, 1, 1));

        service.createWork(TALENT_ID, bo);

        TalentWork saved = insertedWorks.get(0);
        assertEquals("1", saved.getCurrentFlag());
        assertNull(saved.getEndDate(), "在职经历的离职日期必须为空");
    }

    @Test
    @DisplayName("工作经历：离职状态允许离职日期为空")
    void shouldKeepNullEndDateWhenWorkIsNotCurrent() {
        TalentWorkBo bo = workBo();
        bo.setCurrentFlag("0");
        bo.setEndDate(null);

        service.createWork(TALENT_ID, bo);

        TalentWork saved = insertedWorks.get(0);
        assertEquals("0", saved.getCurrentFlag());
        assertNull(saved.getEndDate());
        assertEquals("manual", saved.getSourceType());
        assertEquals(TALENT_ID, saved.getTalentId());
    }

    @Test
    @DisplayName("工作经历：非法在职标志被拒绝")
    void shouldRejectInvalidCurrentFlag() {
        TalentWorkBo bo = workBo();
        bo.setCurrentFlag("yes");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createWork(TALENT_ID, bo));

        assertEquals("是否当前在职只能为 0 或 1", ex.getMessage());
    }

    @Test
    @DisplayName("工作经历：入职日期晚于离职日期被拒绝")
    void shouldRejectReversedWorkDates() {
        TalentWorkBo bo = workBo();
        bo.setStartDate(LocalDate.of(2020, 1, 1));
        bo.setEndDate(LocalDate.of(2019, 1, 1));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createWork(TALENT_ID, bo));

        assertEquals("入职日期不能晚于离职日期", ex.getMessage());
    }

    @Test
    @DisplayName("工作经历编辑：在职时把离职日期显式写为 null")
    void shouldClearEndDateOnWorkUpdate() {
        workStore.put(3001L, work(3001L, TALENT_ID));
        TalentWorkBo bo = workBo();
        bo.setWorkId(3001L);
        bo.setCurrentFlag("1");
        bo.setEndDate(LocalDate.of(2026, 1, 1));

        service.updateWork(TALENT_ID, bo);

        assertNotNull(capturedUpdate);
        String sqlSet = capturedUpdate.getSqlSet();
        Matcher matcher = SET_PARAM.matcher(sqlSet);
        assertTrue(matcher.find(), "SET 子句必须包含 end_date：" + sqlSet);
        assertNull(capturedUpdate.getParamNameValuePairs().get(matcher.group(1)),
            "在职时 end_date 必须显式写为 null：" + sqlSet);
    }

    @Test
    @DisplayName("工作经历编辑：跨人才的工作经历ID被拒绝")
    void shouldRejectUpdateWhenWorkNotOwned() {
        workStore.put(3002L, work(3002L, OTHER_TALENT_ID));
        TalentWorkBo bo = workBo();
        bo.setWorkId(3002L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateWork(TALENT_ID, bo));

        assertEquals("工作经历不存在或不属于该人才", ex.getMessage());
        assertNull(capturedUpdate);
    }

    /* ------------------------------------------------------------------ 项目经历 ------------------------------------------------------------------ */

    @Test
    @DisplayName("项目经历：关联的工作经历必须属于同一人才")
    void shouldRejectProjectWorkRefOfOtherTalent() {
        workStore.put(3002L, work(3002L, OTHER_TALENT_ID));
        TalentProjectBo bo = projectBo();
        bo.setWorkId(3002L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createProject(TALENT_ID, bo));

        assertEquals("关联的工作经历不存在或不属于该人才", ex.getMessage());
        assertTrue(insertedProjects.isEmpty());
    }

    @Test
    @DisplayName("项目经历：关联同一人才的工作经历可以新增")
    void shouldCreateProjectWithOwnedWorkRef() {
        workStore.put(3001L, work(3001L, TALENT_ID));
        TalentProjectBo bo = projectBo();
        bo.setWorkId(3001L);

        service.createProject(TALENT_ID, bo);

        TalentProject saved = insertedProjects.get(0);
        assertEquals(TALENT_ID, saved.getTalentId());
        assertEquals(3001L, saved.getWorkId());
        assertEquals("manual", saved.getSourceType());
    }

    @Test
    @DisplayName("项目经历：开始日期晚于结束日期被拒绝")
    void shouldRejectReversedProjectDates() {
        TalentProjectBo bo = projectBo();
        bo.setStartDate(LocalDate.of(2022, 1, 1));
        bo.setEndDate(LocalDate.of(2021, 1, 1));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createProject(TALENT_ID, bo));

        assertEquals("项目开始日期不能晚于项目结束日期", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 教育经历 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentEducationMapper educationMapper() {
        return (TalentEducationMapper) Proxy.newProxyInstance(
            TalentEducationMapper.class.getClassLoader(),
            new Class<?>[]{TalentEducationMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> educationStore.get((Long) args[0]);
                case "selectVoById" -> educationVo((Long) args[0]);
                case "insert" -> {
                    TalentEducation entity = (TalentEducation) args[0];
                    entity.setEducationId(5001L + insertedEducations.size());
                    educationStore.put(entity.getEducationId(), entity);
                    insertedEducations.add(entity);
                    yield 1;
                }
                case "update" -> {
                    capturedUpdate = (LambdaUpdateWrapper<?>) args[1];
                    yield 1;
                }
                case "selectCount" -> countResult;
                case "delete" -> {
                    capturedDelete = (LambdaQueryWrapper<?>) args[0];
                    deleteCalled = true;
                    yield 1;
                }
                case "selectVoList" -> educationVoList;
                case "selectVoPage" -> new Page<TalentEducationVo>();
                default -> objectMethod(proxy, method, args);
            });
    }

    /**
     * 工作经历 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentWorkMapper workMapper() {
        return (TalentWorkMapper) Proxy.newProxyInstance(
            TalentWorkMapper.class.getClassLoader(),
            new Class<?>[]{TalentWorkMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> workStore.get((Long) args[0]);
                case "selectVoById" -> workVo((Long) args[0]);
                case "insert" -> {
                    TalentWork entity = (TalentWork) args[0];
                    entity.setWorkId(3001L + insertedWorks.size());
                    workStore.put(entity.getWorkId(), entity);
                    insertedWorks.add(entity);
                    yield 1;
                }
                case "update" -> {
                    capturedUpdate = (LambdaUpdateWrapper<?>) args[1];
                    yield 1;
                }
                case "selectCount" -> countResult;
                case "delete" -> {
                    capturedDelete = (LambdaQueryWrapper<?>) args[0];
                    deleteCalled = true;
                    yield 1;
                }
                case "selectVoList" -> List.of();
                default -> objectMethod(proxy, method, args);
            });
    }

    /**
     * 项目经历 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentProjectMapper projectMapper() {
        return (TalentProjectMapper) Proxy.newProxyInstance(
            TalentProjectMapper.class.getClassLoader(),
            new Class<?>[]{TalentProjectMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> projectStore.get((Long) args[0]);
                case "selectVoById" -> projectVo((Long) args[0]);
                case "insert" -> {
                    TalentProject entity = (TalentProject) args[0];
                    entity.setProjectId(6001L + insertedProjects.size());
                    projectStore.put(entity.getProjectId(), entity);
                    insertedProjects.add(entity);
                    yield 1;
                }
                case "update" -> {
                    capturedUpdate = (LambdaUpdateWrapper<?>) args[1];
                    yield 1;
                }
                case "selectCount" -> countResult;
                case "delete" -> {
                    capturedDelete = (LambdaQueryWrapper<?>) args[0];
                    deleteCalled = true;
                    yield 1;
                }
                case "selectVoList" -> List.of();
                default -> objectMethod(proxy, method, args);
            });
    }

    /**
     * 人才主档服务替身：只实现 {@code requireVisible}（可见范围闸门）。
     *
     * @return 服务替身
     */
    private ITalentProfileService profileService() {
        return (ITalentProfileService) Proxy.newProxyInstance(
            ITalentProfileService.class.getClassLoader(),
            new Class<?>[]{ITalentProfileService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "requireVisible" -> {
                    if (!visible) {
                        throw new ServiceException("无权查看该人才");
                    }
                    TalentProfile profile = new TalentProfile();
                    profile.setTalentId((Long) args[0]);
                    yield profile;
                }
                default -> objectMethod(proxy, method, args);
            });
    }

    /**
     * 处理 {@code Object} 方法与未实现方法的默认返回值。
     *
     * @param proxy  代理对象
     * @param method 被调用的方法
     * @param args   参数
     * @return 默认返回值
     */
    private Object objectMethod(Object proxy, java.lang.reflect.Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "Stub:" + method.getDeclaringClass().getSimpleName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    /**
     * 取返回类型的默认值（未实现的 Mapper 方法不应被调用，返回默认值以便快速暴露问题）。
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

    /**
     * 由内存实体构造教育经历 VO（只填归属校验所需字段）。
     *
     * @param educationId 教育经历ID
     * @return VO，不存在返回 null
     */
    private TalentEducationVo educationVo(Long educationId) {
        TalentEducation entity = educationStore.get(educationId);
        if (entity == null) {
            return null;
        }
        TalentEducationVo vo = new TalentEducationVo();
        vo.setEducationId(entity.getEducationId());
        vo.setTalentId(entity.getTalentId());
        return vo;
    }

    /**
     * 由内存实体构造工作经历 VO（只填归属校验所需字段）。
     *
     * @param workId 工作经历ID
     * @return VO，不存在返回 null
     */
    private TalentWorkVo workVo(Long workId) {
        TalentWork entity = workStore.get(workId);
        if (entity == null) {
            return null;
        }
        TalentWorkVo vo = new TalentWorkVo();
        vo.setWorkId(entity.getWorkId());
        vo.setTalentId(entity.getTalentId());
        return vo;
    }

    /**
     * 由内存实体构造项目经历 VO（只填归属校验所需字段）。
     *
     * @param projectId 项目经历ID
     * @return VO，不存在返回 null
     */
    private TalentProjectVo projectVo(Long projectId) {
        TalentProject entity = projectStore.get(projectId);
        if (entity == null) {
            return null;
        }
        TalentProjectVo vo = new TalentProjectVo();
        vo.setProjectId(entity.getProjectId());
        vo.setTalentId(entity.getTalentId());
        return vo;
    }

    /**
     * 构造最小可用的工作经历实体。
     *
     * @param workId   工作经历ID
     * @param talentId 人才主档ID
     * @return 工作经历实体
     */
    private TalentWork work(Long workId, Long talentId) {
        TalentWork work = new TalentWork();
        work.setWorkId(workId);
        work.setTalentId(talentId);
        work.setCurrentFlag("0");
        return work;
    }

    /**
     * 构造最小可用的教育经历入参。
     *
     * @return 教育经历入参
     */
    private TalentEducationBo educationBo() {
        TalentEducationBo bo = new TalentEducationBo();
        bo.setSchoolName("华南理工大学");
        bo.setMajor("软件工程");
        bo.setEducation("bachelor");
        bo.setStartDate(LocalDate.of(2014, 9, 1));
        bo.setEndDate(LocalDate.of(2018, 6, 30));
        return bo;
    }

    /**
     * 构造最小可用的工作经历入参。
     *
     * @return 工作经历入参
     */
    private TalentWorkBo workBo() {
        TalentWorkBo bo = new TalentWorkBo();
        bo.setCompanyName("某某科技有限公司");
        bo.setDepartmentName("研发中心");
        bo.setPositionName("高级后端工程师");
        bo.setStartDate(LocalDate.of(2018, 7, 1));
        return bo;
    }

    /**
     * 构造最小可用的项目经历入参。
     *
     * @return 项目经历入参
     */
    private TalentProjectBo projectBo() {
        TalentProjectBo bo = new TalentProjectBo();
        bo.setProjectName("招聘与人才管理一体化系统");
        bo.setProjectRole("后端负责人");
        bo.setStartDate(LocalDate.of(2025, 1, 1));
        bo.setEndDate(LocalDate.of(2025, 12, 31));
        return bo;
    }

}
