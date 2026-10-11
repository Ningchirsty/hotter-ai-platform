package org.dromara.aigov.workspace.launch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.launch.config.AigLaunchProperties;
import org.dromara.aigov.workspace.launch.domain.AigLaunchRecord;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTicket;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchCommitVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchPrepareVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchProblemVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchRecordVo;
import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.dromara.aigov.workspace.launch.helper.AigLaunchProjectPolicyNotYetEnforceable;
import org.dromara.aigov.workspace.launch.helper.AigLaunchRequestDigest;
import org.dromara.aigov.workspace.launch.helper.IAigLaunchTicketStore;
import org.dromara.aigov.workspace.launch.mapper.AigLaunchRecordMapper;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.mapper.AigRoleProfileMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.dromara.aigov.workspace.portal.domain.AigPortalActionContext;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动解析服务测试（增量 3）。
 *
 * <p>守的是启动链路里最不能出错的三件事：
 * <ol>
 *     <li><b>不通不发票</b>：校验没过就不该拿到票据（拿到票=能 commit）；</li>
 *     <li><b>票绑人不绑机器</b>：票被别人拿去用不了；过期票用不了且会被作废；</li>
 *     <li><b>幂等</b>：同一个幂等键重复提交只得到同一次启动，不建第二个任务。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchServiceImplTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    private IAigPortalService portalService;
    private IAigTaskService taskService;
    private AigLaunchRecordMapper recordMapper;
    private AigRoleProfileMapper roleProfileMapper;
    private AigScenarioMapper scenarioMapper;
    private AigScenarioVersionMapper scenarioVersionMapper;
    private AigLaunchServiceImpl service;
    private InMemoryTicketStore ticketStore;

    static final class InMemoryTicketStore implements IAigLaunchTicketStore {

        private final Map<String, AigLaunchTicket> tickets = new HashMap<>();

        @Override
        public void save(AigLaunchTicket ticket, Duration ttl) {
            tickets.put(ticket.ticketId(), ticket);
        }

        @Override
        public AigLaunchTicket load(String ticketId) {
            return tickets.get(ticketId);
        }

        @Override
        public void consume(String ticketId) {
            tickets.remove(ticketId);
        }
    }

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigLaunchRecord.class);
        TableInfoHelper.initTableInfo(assistant, AigScenario.class);
        TableInfoHelper.initTableInfo(assistant, AigScenarioVersion.class);
    }

    @BeforeEach
    void setUp() {
        portalService = mock(IAigPortalService.class);
        taskService = mock(IAigTaskService.class);
        recordMapper = mock(AigLaunchRecordMapper.class);
        roleProfileMapper = mock(AigRoleProfileMapper.class);
        scenarioMapper = mock(AigScenarioMapper.class);
        scenarioVersionMapper = mock(AigScenarioVersionMapper.class);
        ticketStore = new InMemoryTicketStore();
        // 项目权判定用**真实实现**：它当前是 fail-closed，而"判不了就拒绝"正是要被钉住的行为
        service = new AigLaunchServiceImpl(portalService, ticketStore, recordMapper, taskService,
            mock(IAigUserQuotaService.class), scenarioMapper,
            scenarioVersionMapper, roleProfileMapper,
            new AigLaunchProjectPolicyNotYetEnforceable(), new AigLaunchProperties());
    }

    @Test
    @DisplayName("带项目ID的启动被拒绝：项目权判定尚未接入时 fail-closed，而不是放行")
    void projectBindingIsRejectedUntilItCanBeChecked() {
        stubCard("NAVIGATION", "NAVIGATION", "AIGOV_TASK");
        AigLaunchRequestBo bo = request("key-1");
        bo.setProjectId(4242L);

        AigLaunchPrepareVo vo = service.prepare(bo, ACTOR);

        assertFalse(vo.getPassed());
        assertNull(vo.getTicketId(), "判不了项目权就不能发票");
        assertEquals(List.of(AigLaunchErrorEnum.PROJECT_ACCESS_DENIED.getCode()), codes(vo.getProblems()));
        // 文案必须如实：今天是"平台还判断不了"，不是"你没有权限"——后者会让员工以为自己被拒了权限
        String message = vo.getProblems().get(0).getMessage();
        assertTrue(message.contains("暂不可用"), "应说明判定尚未接入：" + message);
        assertFalse(message.contains("没有这个项目的访问权限"), "不能把「判不了」说成「你没权限」：" + message);
    }

    @Test
    @DisplayName("按任务查启动来源：只查自己发起的，并带上岗位名称供回跳入口显示")
    void findByTaskIdIsScopedToCurrentUser() {
        AigLaunchRecord record = existingRecord("key-1", "digest");
        record.setRoleCode("GRAPHIC_DESIGNER_AI");
        record.setActionCode("A1");
        when(recordMapper.selectOne(any())).thenReturn(record);
        AigRoleProfile profile = new AigRoleProfile();
        profile.setRoleCode("GRAPHIC_DESIGNER_AI");
        profile.setRoleName("平面设计 AI 工作台");
        when(roleProfileMapper.selectOne(any())).thenReturn(profile);

        AigLaunchRecordVo vo = service.findByTaskId(88L, ACTOR);

        assertEquals(88L, vo.getTaskId());
        assertEquals("T-88", vo.getTaskNo());
        assertEquals("平面设计 AI 工作台", vo.getRoleName());
        assertEquals("A1", vo.getActionCode());

        // 找不到（含"是别人发起的"）：一律同一句话，不确认任务是否存在
        when(recordMapper.selectOne(any())).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.findByTaskId(99L, ACTOR));
        assertTrue(e.getMessage().contains("找不到"));
    }

    @Test
    @DisplayName("校验通过才发票：QUICK 卡片缺任务类型 → 拿不到票据")
    void noTicketWhenChecksFail() {
        stubCard("QUICK", "QUICK_CAPABILITY", "cap/x");
        AigLaunchRequestBo bo = request("key-1");

        AigLaunchPrepareVo vo = service.prepare(bo, ACTOR);

        assertFalse(vo.getPassed());
        assertNull(vo.getTicketId(), "校验没过绝不能发票（拿到票就能 commit）");
        List<String> problems = codes(vo.getProblems());
        assertEquals(List.of(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING.getCode()), problems);
    }

    @Test
    @DisplayName("场景卡片：适配器有效时，把「交给谁跑」与场景结果页一起带出来")
    void scenarioCarriesWorkflowAdapterAndRouteKey() {
        stubCard("QUICK", "SCENARIO", "scenario://COMMERCE@1.0.0");
        stubScenario("COMMERCE", "1.0.0", "STABLE", "CREATIVE_EXISTING_FLOW", "CREATIVE_PROJECT");
        AigLaunchRequestBo bo = taskReadyRequest("key-1");

        AigLaunchPrepareVo vo = service.prepare(bo, ACTOR);

        assertTrue(vo.getPassed(), "场景可用且适配器有效就该通过：" + vo.getProblems());
        assertEquals("CREATIVE_EXISTING_FLOW", vo.getWorkflowAdapter());
        assertEquals("CREATIVE_PROJECT", vo.getScenarioRouteKey(),
            "界面上启动成功后要按这个键跳到场景自己的结果页（仍走 routeKey 白名单）");
    }

    @Test
    @DisplayName("场景卡片：适配器缺失或认不出 → 拒绝启动（否则就是「卡片能点、任务起不来」）")
    void scenarioWithoutUsableAdapterIsRejected() {
        stubCard("QUICK", "SCENARIO", "scenario://COMMERCE@1.0.0");
        for (String adapter : new String[]{null, "", "   ", "NOT_AN_ADAPTER"}) {
            stubScenario("COMMERCE", "1.0.0", "STABLE", adapter, "CREATIVE_PROJECT");

            AigLaunchPrepareVo vo = service.prepare(taskReadyRequest("key-1"), ACTOR);

            assertFalse(vo.getPassed(), "适配器「" + adapter + "」应被拒：" + vo.getProblems());
            assertEquals(List.of(AigLaunchErrorEnum.SCENE_VERSION_BLOCKED.getCode()), codes(vo.getProblems()));
            assertNull(vo.getTicketId(), "适配器不可用时不能发票");
        }
    }

    @Test
    @DisplayName("通过则发票，且票带 TTL 与请求摘要")
    void ticketIsIssuedOnSuccess() {
        stubCard("QUICK", "QUICK_CAPABILITY", "cap/x");
        AigLaunchRequestBo bo = request("key-1");
        bo.setTaskType("TEXT_GENERATION");
        bo.setDataLevel("INTERNAL");
        bo.setSnapshotJson("{\"a\":1}");

        AigLaunchPrepareVo vo = service.prepare(bo, ACTOR);

        assertTrue(vo.getPassed());
        assertNotNull(vo.getTicketId());
        assertNotNull(vo.getExpiresAt());
        assertTrue(vo.getWillCreateTask());
        AigLaunchTicket ticket = ticketStore.load(vo.getTicketId());
        assertEquals(9L, ticket.userId());
        assertEquals(102L, ticket.orgId());
        assertEquals("key-1", bo.getIdempotencyKey());
        assertNotNull(ticket.requestDigest());
    }

    @Test
    @DisplayName("岗位不可见 → ROLE_NOT_GRANTED（不确认岗位是否存在）")
    void invisibleRoleIsRejected() {
        when(portalService.listMyRoles(any())).thenReturn(List.of());
        when(portalService.resolveAction(any(), any(), any()))
            .thenThrow(new ServiceException("岗位不存在或当前没有对你开放的版本"));

        AigLaunchPrepareVo vo = service.prepare(request("key-1"), ACTOR);

        assertEquals(List.of(AigLaunchErrorEnum.ROLE_NOT_GRANTED.getCode()), codes(vo.getProblems()));
        assertNull(vo.getTicketId());
    }

    @Test
    @DisplayName("同键同内容 → 直接给出既有启动，不发票（否则确认会变成第二次启动）")
    void prepareReplaysExistingLaunch() {
        stubCard("NAVIGATION", "NAVIGATION", "AIGOV_TASK");
        AigLaunchRequestBo bo = request("key-1");
        AigLaunchRecord existing = existingRecord("key-1", digestOf(bo, "NAVIGATION", "AIGOV_TASK"));
        when(recordMapper.selectOne(any())).thenReturn(existing);

        AigLaunchPrepareVo vo = service.prepare(bo, ACTOR);

        assertTrue(vo.getPassed());
        assertNull(vo.getTicketId());
        assertEquals(77L, vo.getExistingLaunchId());
        assertEquals(88L, vo.getExistingTaskId());
    }

    @Test
    @DisplayName("同键换内容 → IDEMPOTENCY_CONFLICT（不能静默丢弃用户改后的输入）")
    void prepareRejectsIdempotencyConflict() {
        stubCard("NAVIGATION", "NAVIGATION", "AIGOV_TASK");
        when(recordMapper.selectOne(any())).thenReturn(existingRecord("key-1", "another-digest"));

        AigLaunchPrepareVo vo = service.prepare(request("key-1"), ACTOR);

        assertEquals(List.of(AigLaunchErrorEnum.IDEMPOTENCY_CONFLICT.getCode()), codes(vo.getProblems()));
        assertNull(vo.getTicketId());
    }

    @Test
    @DisplayName("没有票据 / 票据属于别人 / 票据过期：一律 LAUNCH_TICKET_EXPIRED")
    void commitValidatesTicket() {
        AigLaunchRequestBo noTicket = request("key-1");
        assertEquals(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()),
            codes(service.commit(noTicket, ACTOR).getProblems()));

        AigLaunchRequestBo unknown = request("key-1");
        unknown.setTicket("nope");
        assertEquals(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()),
            codes(service.commit(unknown, ACTOR).getProblems()));

        // 票绑人：别人的票在你这儿用不了
        ticketStore.save(ticket("t-1", 999L, LocalDateTime.now().plusMinutes(5), "d"), Duration.ofMinutes(5));
        AigLaunchRequestBo stolen = request("key-1");
        stolen.setTicket("t-1");
        assertEquals(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()),
            codes(service.commit(stolen, ACTOR).getProblems()));

        // 过期票：拒绝并作废
        ticketStore.save(ticket("t-2", 9L, LocalDateTime.now().minusSeconds(1), "d"), Duration.ofMinutes(5));
        AigLaunchRequestBo expired = request("key-1");
        expired.setTicket("t-2");
        assertEquals(List.of(AigLaunchErrorEnum.LAUNCH_TICKET_EXPIRED.getCode()),
            codes(service.commit(expired, ACTOR).getProblems()));
        assertNull(ticketStore.load("t-2"), "过期票应被作废");
    }

    @Test
    @DisplayName("commit 时内容与票不一致 → IDEMPOTENCY_CONFLICT（用户确认的是票里那份）")
    void commitRejectsChangedContent() {
        String digest = digestOf(request("key-1"), "NAVIGATION", "AIGOV_TASK");
        ticketStore.save(ticket("t-1", 9L, LocalDateTime.now().plusMinutes(5), digest), Duration.ofMinutes(5));
        AigLaunchRequestBo changed = request("key-1");
        changed.setTicket("t-1");
        changed.setProjectId(12345L);

        AigLaunchCommitVo vo = service.commit(changed, ACTOR);

        assertEquals(List.of(AigLaunchErrorEnum.IDEMPOTENCY_CONFLICT.getCode()), codes(vo.getProblems()));
        verify(taskService, never()).create(any());
    }

    @Test
    @DisplayName("commit 幂等：同一次启动已经落库 → 重放而不建第二个任务")
    void commitReplaysWithoutSecondTask() {
        stubCard("NAVIGATION", "NAVIGATION", "AIGOV_TASK");
        AigLaunchRequestBo bo = request("key-1");
        String digest = digestOf(bo, "NAVIGATION", "AIGOV_TASK");
        ticketStore.save(ticket("t-1", 9L, LocalDateTime.now().plusMinutes(5), digest), Duration.ofMinutes(5));
        bo.setTicket("t-1");
        when(recordMapper.selectOne(any())).thenReturn(existingRecord("key-1", digest));

        AigLaunchCommitVo vo = service.commit(bo, ACTOR);

        assertTrue(vo.getPassed());
        assertTrue(vo.getReplayed());
        assertEquals(88L, vo.getTaskId());
        verify(taskService, never()).create(any());
    }

    @Test
    @DisplayName("commit 正常路径：建任务 + 落启动记录 + 作废票据")
    void commitCreatesTaskAndRecord() {
        stubCard("QUICK", "QUICK_CAPABILITY", "cap/x");
        AigLaunchRequestBo bo = request("key-1");
        bo.setTaskType("TEXT_GENERATION");
        bo.setDataLevel("INTERNAL");
        bo.setSnapshotJson("{\"a\":1}");
        String digest = digestOf(bo, "QUICK", "cap/x", "TEXT_GENERATION", "{\"a\":1}");
        // 票里带的就是 prepare 时算出来的结论：目标、版本、模式都在票里（commit 以票为准）
        ticketStore.save(new AigLaunchTicket("t-1", 9L, 102L, "GRAPHIC_DESIGNER_AI", 7L, "A1",
            "QUICK", "QUICK_CAPABILITY", "cap/x", "creative", null, digest,
            LocalDateTime.now().plusMinutes(5)), Duration.ofMinutes(5));
        bo.setTicket("t-1");
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(taskService.create(any())).thenReturn(555L);
        AigTask task = new AigTask();
        task.setTaskId(555L);
        task.setTaskNo("T-555");
        when(taskService.getTask(555L)).thenReturn(task);

        AigLaunchCommitVo vo = service.commit(bo, ACTOR);

        assertTrue(vo.getPassed());
        assertFalse(vo.getReplayed());
        assertEquals(555L, vo.getTaskId());
        assertEquals("T-555", vo.getTaskNo());
        ArgumentCaptor<AigTaskCreateBo> captor = ArgumentCaptor.forClass(AigTaskCreateBo.class);
        verify(taskService).create(captor.capture());
        assertEquals("TEXT_GENERATION", captor.getValue().getTaskType());
        assertEquals("cap/x", captor.getValue().getCapabilityCode());
        assertEquals("key-1", captor.getValue().getIdempotencyKey(), "幂等键原样透传给任务域");
        ArgumentCaptor<AigLaunchRecord> recordCaptor = ArgumentCaptor.forClass(AigLaunchRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals("COMMITTED", recordCaptor.getValue().getLaunchStatus());
        assertEquals(9L, recordCaptor.getValue().getUserId());
        assertNull(ticketStore.load("t-1"), "票据是一次性的");
    }

    @Test
    @DisplayName("没有部门的用户用 0 哨兵写 org_id：NULL 会让幂等对这类人整类失效")
    void noDeptUserUsesSentinelOrg() {
        stubCard("NAVIGATION", "NAVIGATION", "AIGOV_TASK");
        AigPortalActor noDept = new AigPortalActor(9L, null, Set.of(), Set.of());
        AigLaunchRequestBo bo = request("key-1");
        String digest = digestOf(bo, "NAVIGATION", "AIGOV_TASK");
        ticketStore.save(new AigLaunchTicket("t-1", 9L, null, "GRAPHIC_DESIGNER_AI", 7L, "A1",
            "NAVIGATION", "NAVIGATION", "AIGOV_TASK", "creative", null, digest,
            LocalDateTime.now().plusMinutes(5)), Duration.ofMinutes(5));
        bo.setTicket("t-1");
        when(recordMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.commit(bo, noDept).getPassed());
        ArgumentCaptor<AigLaunchRecord> captor = ArgumentCaptor.forClass(AigLaunchRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getOrgId(), "无部门时必须写 0，不能写 NULL");
    }

    @Test
    @DisplayName("问题带码也带文案：文案只有后端一份来源，前端不再另写映射表")
    void problemsCarryCodeAndMessage() {
        stubCard("QUICK", "QUICK_CAPABILITY", "cap/x");
        AigLaunchPrepareVo vo = service.prepare(request("key-1"), ACTOR);

        assertFalse(vo.getPassed());
        assertEquals(1, vo.getProblems().size());
        AigLaunchProblemVo problem = vo.getProblems().get(0);
        assertEquals(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING.getCode(), problem.getCode());
        assertEquals(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING.getMessage(), problem.getMessage());
    }

    /**
     * 取问题里的码（断言用）。
     *
     * @param problems 问题
     * @return 码列表
     */
    private static List<String> codes(List<AigLaunchProblemVo> problems) {
        return problems.stream().map(AigLaunchProblemVo::getCode).toList();
    }

    /**
     * 造一个合规的启动请求。
     *
     * @param key 幂等键
     * @return 请求
     */
    private static AigLaunchRequestBo request(String key) {
        AigLaunchRequestBo bo = new AigLaunchRequestBo();
        bo.setRoleCode("GRAPHIC_DESIGNER_AI");
        bo.setActionCode("A1");
        bo.setIdempotencyKey(key);
        bo.setProjectType("creative");
        return bo;
    }

    /**
     * 桩掉"卡片可见且可用"。
     *
     * @param launchMode 启动方式
     * @param targetType 目标类型
     * @param targetRef  目标引用
     */
    private void stubCard(String launchMode, String targetType, String targetRef) {
        AigPortalRoleVo role = new AigPortalRoleVo();
        role.setRoleCode("GRAPHIC_DESIGNER_AI");
        when(portalService.listMyRoles(any())).thenReturn(List.of(role));
        when(portalService.resolveAction(any(), any(), any())).thenReturn(new AigPortalActionContext(
            7L, 1L, "GRAPHIC_DESIGNER_AI", "1.0.0", "A1", "做详情页", launchMode, targetType, targetRef,
            null, List.of()));
    }

    /**
     * 桩掉"这个场景版本此刻长这样"（适配器/结果页是本次要带出来的两件事）。
     *
     * @param code          场景编码
     * @param version       版本号
     * @param releaseStatus 发布状态
     * @param adapter       流程适配器（可空/可错，用于构造"不可用"）
     * @param routeKey      结果页跳转键
     */
    private void stubScenario(String code, String version, String releaseStatus, String adapter, String routeKey) {
        AigScenario scenario = new AigScenario();
        scenario.setScenarioId(5L);
        scenario.setScenarioCode(code);
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);

        AigScenarioVersion row = new AigScenarioVersion();
        row.setScenarioVersionId(6L);
        row.setScenarioId(5L);
        row.setVersion(version);
        row.setReleaseStatus(releaseStatus);
        row.setWorkflowAdapter(adapter);
        row.setRouteKey(routeKey);
        when(scenarioVersionMapper.selectOne(any())).thenReturn(row);
    }

    /**
     * 造一个"任务描述齐全"的请求（场景/能力类卡片都要这些字段）。
     *
     * @param key 幂等键
     * @return 请求
     */
    private static AigLaunchRequestBo taskReadyRequest(String key) {
        AigLaunchRequestBo bo = request(key);
        bo.setTaskType("TEXT_GENERATION");
        bo.setDataLevel("INTERNAL");
        bo.setSnapshotJson("{\"a\":1}");
        return bo;
    }

    /**
     * 算一份与请求一致的摘要。
     *
     * @param bo         请求
     * @param launchMode 启动方式
     * @param targetRef  目标引用
     * @return 摘要
     */
    private static String digestOf(AigLaunchRequestBo bo, String launchMode, String targetRef) {
        return digestOf(bo, launchMode, targetRef, bo.getTaskType(), bo.getSnapshotJson());
    }

    /**
     * 算一份与请求一致的摘要（可覆盖任务类型/快照，便于构造不一致的场景）。
     *
     * @param bo          请求
     * @param launchMode  启动方式
     * @param targetRef   目标引用
     * @param taskType    任务类型
     * @param snapshot   快照
     * @return 摘要
     */
    private static String digestOf(AigLaunchRequestBo bo, String launchMode, String targetRef,
                                   String taskType, String snapshot) {
        return AigLaunchRequestDigest.of(bo.getRoleCode(), 7L, "A1", targetRef, taskType,
            bo.getProjectType(), bo.getProjectId(), bo.getDataLevel(), snapshot, bo.getContext());
    }

    /**
     * 造一张票。
     *
     * @param ticketId  票据ID
     * @param userId    所属用户
     * @param expiresAt 过期时刻
     * @param digest    请求摘要
     * @return 票据
     */
    private static AigLaunchTicket ticket(String ticketId, Long userId, LocalDateTime expiresAt,
                                          String digest) {
        return new AigLaunchTicket(ticketId, userId, 102L, "GRAPHIC_DESIGNER_AI", 7L, "A1",
            "NAVIGATION", "NAVIGATION", "AIGOV_TASK", null, null, digest, expiresAt);
    }

    /**
     * 造一条既有启动记录。
     *
     * @param key    幂等键
     * @param digest 摘要
     * @return 记录
     */
    private static AigLaunchRecord existingRecord(String key, String digest) {
        AigLaunchRecord record = new AigLaunchRecord();
        record.setLaunchId(77L);
        record.setUserId(9L);
        record.setIdempotencyKey(key);
        record.setRequestDigest(digest);
        record.setTaskId(88L);
        record.setTaskNo("T-88");
        record.setLaunchStatus("COMMITTED");
        return record;
    }

}
