package org.dromara.aigov.task.live;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.aigov.task.config.AigCallbackProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskEvent;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.helper.AigTaskActorProvider;
import org.dromara.aigov.task.helper.AigTaskCallbackSigner;
import org.dromara.aigov.task.mapper.AigCallbackMapper;
import org.dromara.aigov.task.mapper.AigTaskEventMapper;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.mapper.AigTaskResultMapper;
import org.dromara.aigov.task.mapper.AigTaskSnapshotMapper;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.service.impl.AigTaskExecutorImpl;
import org.dromara.aigov.task.service.impl.AigTaskServiceImpl;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.dromara.aigov.workspace.scenario.config.AigScenarioDispatchProperties;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.enums.AigScenarioReportOutcomeEnum;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.dromara.aigov.workspace.scenario.service.impl.AigScenarioFlowDispatcherImpl;
import org.dromara.aigov.workspace.scenario.service.impl.AigScenarioReportServiceImpl;
import org.dromara.aigov.workspace.scenario.service.impl.AigScenarioVersionResolverImpl;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;

/**
 * 场景派发的**隔离环境端到端干跑**（增量 20）。
 *
 * <h3>它补的是哪条断链</h3>
 * <p>此前这条链只有 <b>Mockito 单测</b>（executor 的依赖全被替换）与 <b>SQL 探针</b>
 * （只验 DDL/唯一键），从没有一次"真实 Spring 之外的完整链路 + 真实数据库"跑通。
 * 本用例把真实实现装配起来跑一遍：
 * {@code AigTaskServiceImpl}（真库：任务/快照/事件）→ {@code AigTaskExecutorImpl}
 * （真实场景分支）→ {@code AigScenarioVersionResolverImpl}（真库：场景版本）→
 * {@code AigScenarioFlowDispatcherImpl} → 抽头 {@link AigScenarioFlowPort} →
 * {@code AigScenarioReportServiceImpl} 回执收尾。</p>
 *
 * <h3>为什么是"外部库 + 抽头域端口"</h3>
 * <ul>
 *     <li><b>真实数据库</b>：临时 MariaDB（跑之前由脚本建表/播种），验的是真实 SQL、
 *         乐观锁、逻辑删除、快照哈希校验，而不是替身。</li>
 *     <li><b>域端口用抽头</b>：真实内容/视频/创作端口各自要拉起它们整套服务（内容闸门、
 *         视频契约、创意项目），那属于"整个应用"的范畴。这里钉住的是<b>平台这一侧的契约</b>：
 *         派发请求里带了什么、受理后任务停在哪、域回执如何收尾。抽头会把它收到的请求原样留给断言。</li>
 * </ul>
 *
 * <h3>怎么跑</h3>
 * <p>没给 {@code -Daig.live.jdbc} 时整类 **假设失败（skipped）**——CI 不受影响；
 * 本地用 {@code .tools/tmp/run-scenario-e2e.ps1} 起临时库、建表、播种后带上该参数运行。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioDispatchE2ELiveTest {

    private static final String SCENARIO_CODE = "LIVE_SCENARIO";
    private static final Long SCENARIO_ID = 900001L;
    private static final Long VERSION_ID = 900011L;
    private static final Long SUBMITTER = 4242L;

    private static IAigTaskService taskService;
    private static IAigTaskExecutor executor;
    private static IAigScenarioReportService reportService;
    private static AigTaskMapper taskMapper;
    private static AigTaskEventMapper eventMapper;
    private static JdbcTemplate jdbc;

    /** 抽头域端口：记录它收到的派发请求，并回一个受理结果。 */
    private static final AtomicReference<AigScenarioFlowRequest> CAPTURED = new AtomicReference<>();

    @BeforeAll
    static void setUp() {
        String url = System.getProperty("aig.live.jdbc", System.getenv("AIG_LIVE_JDBC"));
        Assumptions.assumeTrue(url != null && !url.isBlank(),
            "需要 -Daig.live.jdbc=<url> 指向隔离库才会真的跑（CI 里跳过）");
        String user = System.getProperty("aig.live.user", "root");
        String password = System.getProperty("aig.live.password", "");

        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        jdbc = new JdbcTemplate(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("live", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        for (Class<?> mapper : List.of(AigTaskMapper.class, AigTaskSnapshotMapper.class,
            AigTaskEventMapper.class, AigTaskResultMapper.class, AigCallbackMapper.class,
            AigScenarioMapper.class, AigScenarioVersionMapper.class)) {
            configuration.addMapper(mapper);
        }
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        SqlSession session = factory.openSession(true);

        taskMapper = session.getMapper(AigTaskMapper.class);
        eventMapper = session.getMapper(AigTaskEventMapper.class);
        AigTaskCallbackSigner signer = mock(AigTaskCallbackSigner.class);
        AigTaskActorProvider actor = new AigTaskActorProvider() {
            @Override
            public Long currentUserId() {
                return SUBMITTER;
            }

            @Override
            public String currentUserName() {
                return "live-submitter";
            }
        };
        taskService = new AigTaskServiceImpl(taskMapper, session.getMapper(AigTaskSnapshotMapper.class),
            eventMapper, session.getMapper(AigTaskResultMapper.class), session.getMapper(AigCallbackMapper.class),
            signer, new AigCallbackProperties(), actor);

        AigScenarioVersionResolverImpl resolver = new AigScenarioVersionResolverImpl(
            session.getMapper(AigScenarioMapper.class), session.getMapper(AigScenarioVersionMapper.class));

        AigScenarioFlowPort stubPort = new AigScenarioFlowPort() {
            @Override
            public String adapter() {
                return "CONTENT_EXISTING_FLOW";
            }

            @Override
            public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
                CAPTURED.set(request);
                return AigScenarioFlowResult.accepted("live-external-ref");
            }
        };
        AigScenarioFlowDispatcherImpl dispatcher = new AigScenarioFlowDispatcherImpl(List.of(stubPort));

        AigScenarioDispatchProperties dispatchProperties = new AigScenarioDispatchProperties();
        dispatchProperties.setEnabled(true);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        executor = new AigTaskExecutorImpl(taskService, mock(IAigInvokeService.class),
            dispatchProperties, resolver, dispatcher, jsonMapper);
        reportService = new AigScenarioReportServiceImpl(taskService, jsonMapper);
    }

    @Test
    @DisplayName("★端到端干跑：建任务→入队→执行→派发（真实库）→域回执收尾，事件流可复盘")
    void dispatchThenReportClosesTheTask() {
        seedScenario();

        // 1) 建任务：真库写任务 + 输入快照 + 创建事件
        AigTaskCreateBo create = new AigTaskCreateBo();
        create.setTaskType("IMAGE_GENERATION");
        create.setScenarioCode(SCENARIO_CODE);
        create.setProjectType("CONTENT");
        create.setProjectId(7001L);
        create.setDataLevel("INTERNAL");
        create.setAllowExternal("N");
        create.setIdempotencyKey("live-e2e-1");
        create.setSnapshotJson("{\"deliverableType\":\"ECOM_DETAIL\",\"productId\":123}");
        create.setRemark("隔离环境端到端干跑");
        Long taskId = taskService.create(create);
        Assertions.assertNotNull(taskId, "任务应创建成功并回填雪花主键");
        Assertions.assertEquals(AigTaskStatusEnum.DRAFT.getCode(),
            taskMapper.selectById(taskId).getStatus(), "新任务应落 DRAFT");

        // 2) 真实状态机推进：DRAFT→POLICY_CHECKING→QUEUED（不替身）
        AigTask afterPolicy = taskService.transition(taskId, taskMapper.selectById(taskId).getVersion(),
            AigTaskStatusEnum.POLICY_CHECKING, "策略检查通过", null);
        taskService.transition(taskId, afterPolicy.getVersion(), AigTaskStatusEnum.QUEUED, "入队", null);

        // 3) 执行：走真实场景分支（aigov.scenario.dispatch.enabled=true）
        AigTaskExecuteBo execute = new AigTaskExecuteBo();
        execute.setTaskId(taskId);
        executor.execute(execute);

        // 4) 派发受理后：任务保持 RUNNING，派发快照留 externalRef（等域回执）
        AigTask dispatched = taskMapper.selectById(taskId);
        Assertions.assertEquals(AigTaskStatusEnum.RUNNING.getCode(), dispatched.getStatus(),
            "受理后应保持 RUNNING 等回执（交接完成 ≠ 作业完成）");
        Assertions.assertTrue(dispatched.getRouteSnapshot().contains("ACCEPTED"),
            "派发快照应记 ACCEPTED：" + dispatched.getRouteSnapshot());
        Assertions.assertTrue(dispatched.getRouteSnapshot().contains("live-external-ref"),
            "派发快照应留 externalRef：" + dispatched.getRouteSnapshot());

        AigScenarioFlowRequest captured = CAPTURED.get();
        Assertions.assertNotNull(captured, "域端口应被真实调用");
        Assertions.assertEquals(taskId, captured.getTaskId());
        Assertions.assertEquals(SCENARIO_CODE, captured.getScenarioCode());
        Assertions.assertEquals("1.0.0", captured.getScenarioVersion(), "应解析出真实库里的 STABLE 版本");
        Assertions.assertEquals("CONTENT_EXISTING_FLOW", captured.getAdapter());
        Assertions.assertEquals("CONTENT", captured.getProjectType());
        Assertions.assertEquals(SUBMITTER, captured.getRequesterId());
        Assertions.assertTrue(captured.getSnapshotJson().contains("ECOM_DETAIL"),
            "快照应原样交给域：" + captured.getSnapshotJson());

        // 5) 域回执：真实回执服务收尾
        AigScenarioReportBo report = new AigScenarioReportBo();
        report.setPlatformTaskId(taskId);
        report.setOutcome(AigScenarioReportOutcomeEnum.SUCCEEDED.getCode());
        report.setExternalRef("live-external-ref");
        report.setMessage("内容任务已接手并可作业");
        Assertions.assertTrue(reportService.report(report), "首次回执应真的改变状态");

        AigTask done = taskMapper.selectById(taskId);
        Assertions.assertEquals(AigTaskStatusEnum.SUCCEEDED.getCode(), done.getStatus(), "回执应把任务收尾为 SUCCEEDED");

        // 6) 幂等：终态任务的重复回执是无操作
        Assertions.assertFalse(reportService.report(report), "终态任务的重复回执应幂等无操作");

        // 7) 事件流可复盘：创建 / RUNNING / 回执 都在
        List<AigTaskEvent> events = eventMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<AigTaskEvent>lambdaQuery()
                .eq(AigTaskEvent::getTaskId, taskId)
                .orderByAsc(AigTaskEvent::getSequence));
        Assertions.assertTrue(events.size() >= 4, "事件流应完整：创建+入队+运行+收尾，实际=" + events.size());
        Assertions.assertTrue(events.get(events.size() - 1).getToStatus() != null
            && events.get(events.size() - 1).getToStatus().contains("SUCCEEDED"),
            "最后一条事件应落在 SUCCEEDED：" + events.get(events.size() - 1).getToStatus());
    }

    /**
     * 播种场景与 STABLE 版本（幂等：先删后插，方便重复干跑）。
     */
    private static void seedScenario() {
        jdbc.update("delete from aig_scenario_version where scenario_id = ?", SCENARIO_ID);
        jdbc.update("delete from aig_scenario where scenario_id = ?", SCENARIO_ID);
        jdbc.update("insert into aig_scenario (scenario_id, scenario_code, scenario_name, status, del_flag) "
            + "values (?, ?, '隔离干跑场景', '0', '0')", SCENARIO_ID, SCENARIO_CODE);
        jdbc.update("insert into aig_scenario_version (scenario_version_id, scenario_id, version, "
                + "release_status, workflow_adapter, status, del_flag) values (?, ?, '1.0.0', 'STABLE', "
                + "'CONTENT_EXISTING_FLOW', '0', '0')",
            VERSION_ID, SCENARIO_ID);
    }

}
