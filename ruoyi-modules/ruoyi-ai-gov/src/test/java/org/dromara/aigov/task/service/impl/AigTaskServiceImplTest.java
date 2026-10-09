package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.config.AigCallbackProperties;
import org.dromara.aigov.task.domain.AigCallback;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskEvent;
import org.dromara.aigov.task.domain.AigTaskResult;
import org.dromara.aigov.task.domain.AigTaskSnapshot;
import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultSelectBo;
import org.dromara.aigov.task.domain.bo.AigTaskReviewBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskExecutionModeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.helper.AigTaskActorProvider;
import org.dromara.aigov.task.helper.AigTaskCallbackSigner;
import org.dromara.aigov.task.mapper.AigCallbackMapper;
import org.dromara.aigov.task.mapper.AigTaskEventMapper;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.mapper.AigTaskResultMapper;
import org.dromara.aigov.task.mapper.AigTaskSnapshotMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务编排服务的守卫行为测试。
 *
 * <p><b>这四类守卫是「不生效也不会报错」的高发区</b>：</p>
 * <ul>
 *     <li><b>创建幂等</b>：失效时表现为「用户点两次生成了两个任务」，
 *         而每个任务单独看都完全正常——只有对比列表才看得出多了；</li>
 *     <li><b>乐观锁</b>：失效时并发覆盖悄悄发生，最终状态取决于写库先后，
 *         事件流里出现互相矛盾的迁移；</li>
 *     <li><b>自动流程不得置 APPROVED</b>：失效时「谁选的」永远查不出来，
 *         而选错一张图去投放的代价很高；</li>
 *     <li><b>回调未验签不得推进状态</b>：失效时任何人只要猜到一个 jobId
 *         就能伪造「任务已完成」并塞入任意结果。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskServiceImplTest {

    private static final String PROVIDER = "bluocto";
    private static final String SECRET = "unit-test-secret";
    private static final String PAYLOAD = "{\"jobId\":\"j-1\",\"status\":\"finished\"}";
    private static final long USER_ID = 7L;

    private AigTaskMapper taskMapper;
    private AigTaskSnapshotMapper snapshotMapper;
    private AigTaskEventMapper eventMapper;
    private AigTaskResultMapper resultMapper;
    private AigCallbackMapper callbackMapper;
    private AigTaskCallbackSigner signer;
    private AigTaskActorProvider actorProvider;
    private AigTaskServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 服务内用 LambdaQueryWrapper，需要实体→列的 lambda 缓存；纯单测没有 Spring
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigTask.class);
        TableInfoHelper.initTableInfo(assistant, AigTaskEvent.class);
        TableInfoHelper.initTableInfo(assistant, AigCallback.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(AigTaskMapper.class);
        snapshotMapper = mock(AigTaskSnapshotMapper.class);
        eventMapper = mock(AigTaskEventMapper.class);
        resultMapper = mock(AigTaskResultMapper.class);
        callbackMapper = mock(AigCallbackMapper.class);
        AigCallbackProperties properties = new AigCallbackProperties();
        properties.getSecrets().put(PROVIDER, SECRET);
        signer = new AigTaskCallbackSigner(properties);
        actorProvider = mock(AigTaskActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(USER_ID);
        service = new AigTaskServiceImpl(taskMapper, snapshotMapper, eventMapper, resultMapper, callbackMapper,
            signer, properties, actorProvider);
        // 事件序号查询默认返回空（即下一条是 1）
        when(eventMapper.selectList(any())).thenReturn(List.of());
        // 模拟主键回填：真实 MyBatis-Plus 在 insert 时分配主键，mock 不会。
        // 不打这个桩，create 会因「未取回主键」直接失败——那是 mock 不真实，不是实现有问题
        when(taskMapper.insert(any(AigTask.class))).thenAnswer(invocation -> {
            invocation.<AigTask>getArgument(0).setTaskId(9001L);
            return 1;
        });
        when(snapshotMapper.insert(any(AigTaskSnapshot.class))).thenAnswer(invocation -> {
            invocation.<AigTaskSnapshot>getArgument(0).setSnapshotId(8001L);
            return 1;
        });
        // 默认认为更新命中一行：create 内部有「回填快照关联」的一次更新，
        // 未打桩的 mock 会返回 0，于是被实现当成回填失败而抛错——那看起来像实现的问题
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);
    }

    private static AigTaskCreateBo createBo(String dataLevel, String allowExternal, String idempotencyKey) {
        AigTaskCreateBo bo = new AigTaskCreateBo();
        bo.setTaskType("IMAGE_GENERATION");
        bo.setCapabilityCode("image_generation");
        bo.setProjectType("CREATIVE");
        bo.setProjectId(9001L);
        bo.setDataLevel(dataLevel);
        bo.setAllowExternal(allowExternal);
        bo.setIdempotencyKey(idempotencyKey);
        bo.setSnapshotJson("{\"facts\":\"v1\"}");
        return bo;
    }

    /**
     * 造一个分页参数。
     *
     * @return 分页参数
     */
    private static PageQuery pageQuery() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(10);
        return pageQuery;
    }

    /**
     * 造一个候选结果行。
     *
     * @param resultId 结果ID
     * @param taskId   所属任务ID
     * @param status   候选状态
     * @return 结果行
     */
    private static AigTaskResult candidate(long resultId, long taskId, String status) {
        AigTaskResult result = new AigTaskResult();
        result.setResultId(resultId);
        result.setTaskId(taskId);
        result.setCandidateStatus(status);
        result.setResultType("ASSET");
        return result;
    }

    /**
     * 造一个选定入参。
     *
     * @param taskId   任务ID
     * @param resultId 结果ID
     * @return 选定入参
     */
    private static AigTaskResultSelectBo selectBo(long taskId, long resultId) {
        AigTaskResultSelectBo bo = new AigTaskResultSelectBo();
        bo.setTaskId(taskId);
        bo.setResultId(resultId);
        bo.setRemark("这张构图最稳");
        return bo;
    }

    private static AigTask task(long taskId, String status, int attemptNo, int version) {
        AigTask task = new AigTask();
        task.setTaskId(taskId);
        task.setTaskNo("task-no-" + taskId);
        task.setStatus(status);
        task.setAttemptNo(attemptNo);
        task.setMaxAttempt(3);
        task.setVersion(version);
        task.setDataLevel("INTERNAL");
        task.setProviderCode(PROVIDER);
        task.setProviderJobId("j-1");
        return task;
    }

    private static AigTaskCallbackBo signedCallback() throws Exception {
        AigTaskCallbackBo bo = new AigTaskCallbackBo();
        bo.setProviderCode(PROVIDER);
        bo.setProviderJobId("j-1");
        bo.setEventId("evt-1");
        bo.setToStatus("SUCCEEDED");
        bo.setRawPayload(PAYLOAD);
        bo.setSignAlgorithm("HMAC-SHA256");
        bo.setSignature(signer().signHex(PROVIDER, PAYLOAD));
        return bo;
    }

    private static AigTaskCallbackSigner signer() {
        AigCallbackProperties properties = new AigCallbackProperties();
        properties.getSecrets().put(PROVIDER, SECRET);
        return new AigTaskCallbackSigner(properties);
    }

    // ------------------------------------------------------------------ 创建

    @Test
    @DisplayName("创建：冻结快照并写创建事件，任务初始为 DRAFT/version=0/attemptNo=0")
    void createFreezesSnapshotAndWritesCreatedEvent() {
        service.create(createBo("INTERNAL", "Y", null));

        ArgumentCaptor<AigTaskSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AigTaskSnapshot.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        AigTaskSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals("{\"facts\":\"v1\"}", snapshot.getSnapshotJson(), "快照内容必须原样入库");
        assertNotNull(snapshot.getSnapshotHash(), "必须算哈希：它是「快照没被改过」的唯一凭据");
        assertEquals(64, snapshot.getSnapshotHash().length(), "SHA-256 十六进制应为 64 位");
        assertEquals(1, snapshot.getSnapshotVersion(), "首次快照版本为 1");

        ArgumentCaptor<AigTask> taskCaptor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        AigTask saved = taskCaptor.getValue();
        assertEquals(AigTaskStatusEnum.DRAFT.getCode(), saved.getStatus());
        assertEquals(0, saved.getAttemptNo());
        assertEquals(0, saved.getVersion(), "乐观锁从 0 开始");
        assertEquals(32, saved.getTaskNo().length(), "任务号长度须与列宽一致（varchar(32)）");
        assertTrue(saved.getTaskNo().matches("[0-9a-f]{32}"), "任务号须不可猜测，不能用可枚举编号");

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        AigTaskEvent event = eventCaptor.getValue();
        assertEquals(1, event.getSequence(), "首条事件序号为 1");
        assertEquals("AI_TASK_CREATED", event.getEventType());
        assertEquals(AigTaskStatusEnum.DRAFT.getCode(), event.getToStatus());
    }

    @Test
    @DisplayName("创建幂等：命中幂等键时返回既有任务，不再建第二个")
    void createIsIdempotentByKey() {
        when(taskMapper.selectOne(any())).thenReturn(task(555L, "DRAFT", 0, 0));

        Long taskId = service.create(createBo("INTERNAL", "Y", "submit-1"));

        assertEquals(555L, taskId, "应返回既有任务ID");
        verify(taskMapper, never()).insert(any(AigTask.class));
        verify(snapshotMapper, never()).insert(any(AigTaskSnapshot.class));
        verify(eventMapper, never()).insert(any(AigTaskEvent.class));
    }

    @Test
    @DisplayName("★ 记录策略结论：两列落库 + 写 AI_TASK_POLICY_DECIDED 事件（治理台那一格此前永远是「-」）")
    void recordPolicyDecisionWritesColumnsAndEvent() {
        AigTask[] stored = fakeRow();
        stored[0] = task(9001L, "RUNNING", 4, 0);

        AigTask after = service.recordPolicyDecision(9001L, 4, "REJECT",
            "未配置该数据等级的路由策略", "NO_ROUTE_POLICY");

        ArgumentCaptor<AigTask> updateCaptor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(updateCaptor.capture());
        AigTask update = updateCaptor.getValue();
        assertEquals("REJECT", update.getPolicyResult());
        assertEquals("未配置该数据等级的路由策略", update.getPolicyReason());
        assertEquals(4, update.getVersion(), "要带乐观锁版本，否则并发下会覆盖别人的结论");

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        AigTaskEvent event = eventCaptor.getValue();
        assertEquals("AI_TASK_POLICY_DECIDED", event.getEventType());
        assertNull(event.getFromStatus(), "它不是状态迁移，from/to 必须为空");
        assertNull(event.getToStatus());
        assertTrue(event.getDetail().contains("REJECT"), event.getDetail());
        assertTrue(event.getDetail().contains("未配置该数据等级的路由策略"),
            "细因要翻成可读描述，而不是把 NO_ROUTE_POLICY 原样塞给读的人：" + event.getDetail());
        assertNotNull(event.getPayloadJson(), "载荷里放细因与错误码，供机器读");
        assertTrue(event.getPayloadJson().contains("NO_ROUTE_POLICY"), event.getPayloadJson());
        assertTrue(event.getPayloadJson().contains("POLICY_DENIED"),
            "拒绝要带错误码（与调用入口的落库口径一致）：" + event.getPayloadJson());
        assertEquals(9001L, after.getTaskId());
    }

    @Test
    @DisplayName("通过（PASS）不写错误码，但细因存在时仍要写细因")
    void passCarriesNoErrorCode() {
        AigTask[] stored = fakeRow();
        stored[0] = task(9001L, "RUNNING", 4, 0);

        service.recordPolicyDecision(9001L, 4, "PASS", "命中模型 flux-2-pro", null);

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertNull(eventCaptor.getValue().getPayloadJson(),
            "成功的策略决策没有错误码，也没有细因 → 载荷为空而不是编一个出来");
    }

    @Test
    @DisplayName("契约之外的细因不写进事件载荷（载荷是机器读的，塞私词会被当成词表成员）")
    void unknownReasonCodeIsNotPutIntoPayload() {
        AigTask[] stored = fakeRow();
        stored[0] = task(9001L, "RUNNING", 4, 0);

        service.recordPolicyDecision(9001L, 4, "REJECT", "自造的细因", "NO_POLICY_MADE_UP");

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        String payload = eventCaptor.getValue().getPayloadJson();
        assertNotNull(payload);
        assertFalse(payload.contains("NO_POLICY_MADE_UP"),
            "契约外的细因不得进入载荷：" + payload);
        assertTrue(payload.contains("POLICY_DENIED"), payload);
    }

    @Test
    @DisplayName("策略结论的乐观锁：版本不符必须响亮失败，不静默覆盖")
    void recordPolicyDecisionUsesOptimisticLock() {
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.recordPolicyDecision(9001L, 3, "PASS", "命中模型", null));

        assertTrue(ex.getMessage().contains("并发修改"), ex.getMessage());
        verify(eventMapper, never()).insert(any(AigTaskEvent.class));
    }

    @Test
    @DisplayName("★ 记录进度：列落库 + 写 AI_TASK_PROGRESSED（这一列与这个事件此前都没有写入方）")
    void recordProgressWritesColumnAndEvent() {
        // 服务会读两次任务：写前取旧进度（10）、写后回读（40）
        when(taskMapper.selectById(1L)).thenReturn(runningWithProgress(10), runningWithProgress(40));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigTask after = service.recordProgress(1L, 5, 40, "已出图 4/10");

        ArgumentCaptor<AigTask> updateCaptor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(updateCaptor.capture());
        AigTask update = updateCaptor.getValue();
        assertEquals(40, update.getProgress());
        assertEquals(5, update.getVersion(), "进度写入同样要走乐观锁");

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        AigTaskEvent event = eventCaptor.getValue();
        assertEquals("AI_TASK_PROGRESSED", event.getEventType());
        assertNull(event.getFromStatus(), "进度更新不是状态迁移，from/to 必须为空");
        assertNull(event.getToStatus());
        assertTrue(event.getDetail().contains("10% → 40%"),
            "说明里要给出变化前后，否则事后看不出是涨了还是回退了：" + event.getDetail());
        assertTrue(event.getDetail().contains("已出图 4/10"), event.getDetail());
        assertEquals("{\"progress\":40}", event.getPayloadJson(),
            "载荷按契约给 progress（0-100 的整数）");
        assertEquals(40, after.getProgress());
    }

    @Test
    @DisplayName("★ 同一个百分比重复回执不写：否则事件流会被固定间隔的重推刷成噪音")
    void recordProgressSkipsUnchangedValue() {
        when(taskMapper.selectById(1L)).thenReturn(runningWithProgress(40));

        AigTask after = service.recordProgress(1L, 5, 40, "轮询回执");

        assertEquals(40, after.getProgress());
        verify(taskMapper, never()).updateById(any(AigTask.class));
        verify(eventMapper, never()).insert(any(AigTaskEvent.class));
    }

    @Test
    @DisplayName("★ 进度可以回退（重试从低百分比重跑），不能被当成异常拦掉")
    void recordProgressAllowsGoingBackwards() {
        when(taskMapper.selectById(1L)).thenReturn(runningWithProgress(80), runningWithProgress(10));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.recordProgress(1L, 5, 10, "第 2 次尝试重新开始");

        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getDetail().contains("80% → 10%"),
            "回退也要如实记录：" + eventCaptor.getValue().getDetail());
    }

    @Test
    @DisplayName("进度越界一律拒绝（0-100 之外的值会让页面进度条失真）")
    void recordProgressRejectsOutOfRange() {
        assertThrows(ServiceException.class, () -> service.recordProgress(1L, 5, null, null));
        assertThrows(ServiceException.class, () -> service.recordProgress(1L, 5, -1, null));
        assertThrows(ServiceException.class, () -> service.recordProgress(1L, 5, 101, null));
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("★ 只有「正在执行」的任务接受进度：其它状态一律拒绝（迟到的回执不回写）")
    void recordProgressRejectsNonExecutingTask() {
        // 这份清单刻意包含 SUCCEEDED/FAILED：它们**不是**终态（还能走到复核/重试），
        // 但「跑到哪」对它们已无意义——第一版实现自己列终态清单，既漏了 APPROVED、
        // 又把 SUCCEEDED 当终态，两处都是凭印象。判断改走状态机的 isExecuting（唯一口径）
        for (String status : List.of("SUCCEEDED", "FAILED", "APPROVED", "REJECTED",
            "CANCELLED", "QUEUED", "NEED_HUMAN", "RETRY_WAIT")) {
            when(taskMapper.selectById(1L)).thenReturn(task(1L, status, 1, 6));
            ServiceException ex = assertThrows(ServiceException.class,
                () -> service.recordProgress(1L, 6, 10, "迟到的回执"), status + " 不该接受进度");
            assertTrue(ex.getMessage().contains("只有正在执行"), status + "：" + ex.getMessage());
        }
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("DISPATCHED（已派发等结果）也接受进度：异步 Provider 正是在这个阶段回报进度")
    void recordProgressAcceptsDispatched() {
        AigTask dispatched = task(1L, "DISPATCHED", 0, 5);
        dispatched.setProgress(null);
        AigTask done = task(1L, "DISPATCHED", 0, 6);
        done.setProgress(30);
        when(taskMapper.selectById(1L)).thenReturn(dispatched, done);
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigTask after = service.recordProgress(1L, 5, 30, "已提交上游");

        assertEquals(30, after.getProgress());
    }

    @Test
    @DisplayName("★ 成功的任务进度必须是 100：一列恒为 0 比没有这一列更坏")
    void successSetsProgressToHundred() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 0, 5), task(1L, "SUCCEEDED", 0, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.transition(1L, 5, AigTaskStatusEnum.SUCCEEDED, "调用成功", null);

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(100, captor.getValue().getProgress(),
            "已完成的任务在接口里显示 0%，读的人只能理解为「还没开始」");
    }

    /**
     * 造一个「运行中 + 指定进度」的任务行。
     *
     * @param progress 当前进度
     * @return 任务
     */
    private static AigTask runningWithProgress(int progress) {
        AigTask task = task(1L, "RUNNING", 0, 5);
        task.setProgress(progress);
        return task;
    }

    /**
     * 造一个「内存里的库」：insert 回填主键、updateById 把更新对象上的非空字段写回同一行。
     *
     * <p>用于 {@link #createDispatchedRegistersExternalTask()} 这类「先创建、再改状态」的路径——
     * 中间隔着一次 {@code loadTask}，用现成返回值硬塞会绕过真实时序（读回来的是哪一行、
     * 更新后状态有没有生效，都测不到）。</p>
     *
     * @return 长度为 1 的行容器（第 0 项即当前行）
     */
    private AigTask[] fakeRow() {
        AigTask[] stored = new AigTask[1];
        when(taskMapper.selectOne(any())).thenAnswer(invocation -> stored[0]);
        when(taskMapper.insert(any(AigTask.class))).thenAnswer(invocation -> {
            AigTask saved = invocation.getArgument(0);
            saved.setTaskId(9001L);
            stored[0] = saved;
            return 1;
        });
        when(taskMapper.selectById(any())).thenAnswer(invocation -> stored[0]);
        when(taskMapper.updateById(any(AigTask.class))).thenAnswer(invocation -> {
            AigTask update = invocation.getArgument(0);
            if (stored[0] != null) {
                if (update.getStatus() != null) {
                    stored[0].setStatus(update.getStatus());
                }
                if (update.getExecutionMode() != null) {
                    stored[0].setExecutionMode(update.getExecutionMode());
                }
                if (update.getProviderCode() != null) {
                    stored[0].setProviderCode(update.getProviderCode());
                }
                if (update.getProviderJobId() != null) {
                    stored[0].setProviderJobId(update.getProviderJobId());
                }
                if (update.getInputSnapshotId() != null) {
                    stored[0].setInputSnapshotId(update.getInputSnapshotId());
                }
            }
            return 1;
        });
        return stored;
    }

    @Test
    @DisplayName("★ 登记「业务域执行」的任务：直接落到 DISPATCHED、执行方=EXTERNAL、带上外部作业ID")
    void createDispatchedRegistersExternalTask() {
        fakeRow();

        AigTask task = service.createDispatched(createBo("INTERNAL", "Y", "creative-1"), "COMFYUI", "job-77");

        assertEquals(AigTaskStatusEnum.DISPATCHED.getCode(), task.getStatus(),
            "登记即「已派发」：执行已经交给业务域了，不是等平台排队");
        assertEquals(AigTaskExecutionModeEnum.EXTERNAL.getCode(), task.getExecutionMode(),
            "执行方必须是业务域：否则调度器会把它重新入队（再跑一遍）或判超时失败");
        assertEquals("COMFYUI", task.getProviderCode());
        assertEquals("job-77", task.getProviderJobId());
        // 创建事件 + 派发事件：事件流要如实说明「谁在执行、平台会不会碰它」
        ArgumentCaptor<AigTaskEvent> eventCaptor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper, times(2)).insert(eventCaptor.capture());
        AigTaskEvent dispatch = eventCaptor.getAllValues().get(1);
        assertEquals(AigTaskStatusEnum.DISPATCHED.getCode(), dispatch.getToStatus());
        assertTrue(dispatch.getDetail().contains("业务域"), dispatch.getDetail());
        assertTrue(dispatch.getDetail().contains("不会"), "要写明平台不会执行/不会扫描它：" + dispatch.getDetail());
    }

    @Test
    @DisplayName("★ 重复登记幂等，且**原样返回、不重置**已有任务（进度是业务域写的，不能被抹掉）")
    void createDispatchedIsIdempotentAndKeepsProgress() {
        AigTask[] stored = fakeRow();
        service.createDispatched(createBo("INTERNAL", "Y", "creative-2"), "COMFYUI", "job-88");
        // 业务域回写：内核已经跑到 RUNNING（真实进度）
        stored[0].setStatus(AigTaskStatusEnum.RUNNING.getCode());
        clearInvocations(taskMapper, eventMapper);

        AigTask again = service.createDispatched(createBo("INTERNAL", "Y", "creative-2"), "COMFYUI", "job-88");

        assertEquals(AigTaskStatusEnum.RUNNING.getCode(), again.getStatus(),
            "重复登记若重置状态，会把业务域已经写好的真实进度抹掉");
        verify(taskMapper, never()).insert(any(AigTask.class));
        verify(taskMapper, never()).updateById(any(AigTask.class));
        verify(eventMapper, never()).insert(any(AigTaskEvent.class));
    }

    @Test
    @DisplayName("登记必须给 Provider 编码：业务域回写与排障都要靠它定位任务")
    void createDispatchedRejectsBlankProvider() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.createDispatched(createBo("INTERNAL", "Y", "creative-x"), "  ", null));

        assertTrue(e.getMessage().contains("Provider 编码不能为空"), e.getMessage());
        verify(taskMapper, never()).insert(any(AigTask.class));
    }

    @Test
    @DisplayName("创建幂等：系统触发（无登录用户）也要真幂等——create_by 不能是 NULL，"
        + "否则唯一键不约束 NULL、等值查询永不成立，重复提交会变成两个任务")
    void createIsIdempotentForSystemSubmitter() {
        when(actorProvider.currentUserId()).thenReturn(null);
        // 库里已有什么，由「第一次 insert 写入的那一行」决定——这样这条测试测的是
        // 真实时序（先查空、写入、再查命中），而不是把一个现成的返回硬塞进去
        AigTask[] stored = new AigTask[1];
        when(taskMapper.selectOne(any())).thenAnswer(invocation -> stored[0]);
        when(taskMapper.insert(any(AigTask.class))).thenAnswer(invocation -> {
            AigTask saved = invocation.getArgument(0);
            saved.setTaskId(9001L);
            stored[0] = saved;
            return 1;
        });

        Long first = service.create(createBo("INTERNAL", "Y", "sys-submit-1"));
        Long second = service.create(createBo("INTERNAL", "Y", "sys-submit-1"));

        assertEquals(first, second, "同一幂等键的重复提交必须返回同一任务");
        verify(taskMapper, times(1)).insert(any(AigTask.class));

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(AigConstants.SYSTEM_SUBMITTER_ID, captor.getValue().getCreateBy(),
            "系统触发时 create_by 必须是 0（系统）而不是 NULL，否则库层唯一键形同不存在");

        // 查询条件里也必须带非空提交人：写 0 而查 NULL 的话，幂等只在库层成立、应用层永远命中不到
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AigTask>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper, times(2)).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<AigTask> idemWrapper = wrapperCaptor.getAllValues().get(1);
        // 参数是惰性填充的，必须先取一次 SQL 片段
        idemWrapper.getSqlSegment();
        assertTrue(idemWrapper.getSqlSegment().contains("create_by"),
            "幂等查询必须带上 create_by：" + idemWrapper.getSqlSegment());
        assertTrue(idemWrapper.getParamNameValuePairs().values().stream()
                .anyMatch(v -> AigConstants.SYSTEM_SUBMITTER_ID.equals(v)),
            "幂等查询的提交人参数不能为 NULL：" + idemWrapper.getParamNameValuePairs());
    }

    @Test
    @DisplayName("创建：严格级数据的外发许可被强制置 N（与路由硬约束同口径）")
    void createForcesStrictDataToNoExternal() {
        service.create(createBo("STRICT", "Y", null));

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("N", captor.getValue().getAllowExternal(),
            "严格级数据标为允许外发会造成假象：看任务的人以为发得出去，看审计的人发现没发");
        assertEquals("N", captor.getValue().getAllowExternal());
    }

    @Test
    @DisplayName("创建：未知任务类型/数据等级/空快照一律拒绝（不猜、不落库）")
    void createRejectsInvalidInput() {
        AigTaskCreateBo badType = createBo("INTERNAL", "N", null);
        badType.setTaskType("NOT_A_TYPE");
        assertThrows(ServiceException.class, () -> service.create(badType));

        AigTaskCreateBo badLevel = createBo("SECRET", "N", null);
        assertThrows(ServiceException.class, () -> service.create(badLevel));

        AigTaskCreateBo noSnapshot = createBo("INTERNAL", "N", null);
        noSnapshot.setSnapshotJson("  ");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(noSnapshot));
        assertTrue(ex.getMessage().contains("快照"), "实际=" + ex.getMessage());

        verify(taskMapper, never()).insert(any(AigTask.class));
    }

    // ------------------------------------------------------------------ 查询

    @Test
    @DisplayName("查询：回填状态与类型描述，且过滤条件真的进 where")
    void queryPageFillsLabelsAndFilters() {
        AigTaskVo row = new AigTaskVo();
        row.setTaskId(1L);
        row.setStatus("REVIEW_PENDING");
        row.setTaskType("IMAGE_GENERATION");
        Page<AigTaskVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(taskMapper.selectVoPage(any(), any())).thenReturn(page);

        AigTaskQueryBo query = new AigTaskQueryBo();
        query.setStatus("REVIEW_PENDING");
        query.setProjectType("CREATIVE");

        PageResult<AigTaskVo> result = service.queryPage(query, pageQuery());

        assertEquals(1, result.getTotal());
        AigTaskVo filled = result.getRows().iterator().next();
        assertEquals("待人工复核", filled.getStatusLabel(),
            "状态描述要在服务端回填：同一个状态在三处界面各写一份映射迟早会不一致，"
                + "而「已取消」显示成「已完成」是会被当真的");
        assertEquals("图像生成", filled.getTaskTypeLabel());

        ArgumentCaptor<LambdaQueryWrapper<AigTask>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("status"), "状态过滤必须进 where；实际=" + sql);
        assertTrue(sql.contains("project_type"), "业务域过滤必须进 where；实际=" + sql);
    }

    @Test
    @DisplayName("查询：不传条件时不加任何条件（避免把「不筛」写成「筛空值」）")
    void queryPageWithoutFilters() {
        Page<AigTaskVo> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(taskMapper.selectVoPage(any(), any())).thenReturn(page);

        service.queryPage(new AigTaskQueryBo(), pageQuery());

        ArgumentCaptor<LambdaQueryWrapper<AigTask>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertFalse(sql.contains("status"), "不传状态就不该出现该条件；实际=" + sql);
        assertFalse(sql.contains("project_type"), "实际=" + sql);
    }

    @Test
    @DisplayName("查询：平台惯例的 params[beginTime/endTime] 也要生效（前端 addDateRange 走这条）")
    void queryPageHonoursParamsDateRange() {
        Page<AigTaskVo> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(taskMapper.selectVoPage(any(), any())).thenReturn(page);
        AigTaskQueryBo query = new AigTaskQueryBo();
        query.setParams(Map.of("beginTime", "2026-10-01 00:00:00", "endTime", "2026-10-07 23:59:59"));

        service.queryPage(query, pageQuery());

        ArgumentCaptor<LambdaQueryWrapper<AigTask>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("create_time"), "时间范围必须进 where；实际=" + sql);
    }

    @Test
    @DisplayName("详情：任务+快照+事件流+候选结果一次给全，事件按序号升序")
    void getDetailAssemblesEverything() {
        AigTask current = task(1L, "RUNNING", 1, 4);
        current.setInputSnapshotId(77L);
        when(taskMapper.selectById(1L)).thenReturn(current);

        AigTaskSnapshot snapshot = new AigTaskSnapshot();
        snapshot.setSnapshotId(77L);
        snapshot.setSnapshotJson("{\"facts\":\"v1\"}");
        snapshot.setSnapshotHash("hash-abc");
        when(snapshotMapper.selectById(77L)).thenReturn(snapshot);

        AigTaskEvent first = new AigTaskEvent();
        first.setSequence(1);
        first.setEventType("AI_TASK_CREATED");
        AigTaskEvent second = new AigTaskEvent();
        second.setSequence(2);
        second.setEventType("AI_TASK_STATUS_CHANGED");
        when(eventMapper.selectList(any())).thenReturn(List.of(first, second));

        AigTaskResult candidate = new AigTaskResult();
        candidate.setResultId(9L);
        candidate.setCandidateStatus("CANDIDATE");
        when(resultMapper.selectList(any())).thenReturn(List.of(candidate));

        AigTaskDetailVo detail = service.getDetail(1L);

        assertEquals("执行中", detail.getTask().getStatusLabel());
        assertEquals("hash-abc", detail.getSnapshot().getSnapshotHash(), "快照哈希要与原文一起给出，视图才可自证");
        assertEquals(2, detail.getEvents().size());
        assertEquals(1, detail.getEvents().get(0).getSequence(), "事件必须按序号升序（倒序会让因果读起来是反的）");
        assertEquals("任务创建", detail.getEvents().get(0).getEventTypeLabel());
        assertEquals("候选（待人工选定）", detail.getResults().get(0).getCandidateStatusLabel());
    }

    @Test
    @DisplayName("详情：任务不存在时报错而不是返回空壳")
    void getDetailRejectsMissingTask() {
        when(taskMapper.selectById(404L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.getDetail(404L));
        assertThrows(ServiceException.class, () -> service.getDetail(null));
    }

    // ------------------------------------------------------------------ 人工选定交付物

    @Test
    @DisplayName("选定：把候选置为已选定并记录选定人（选错时这是唯一能追到的责任点）")
    void selectCandidateRecordsTheHuman() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        when(resultMapper.selectById(9L)).thenReturn(candidate(9L, 1L, "CANDIDATE"));
        when(resultMapper.selectList(any())).thenReturn(List.of());

        Long resultId = service.selectCandidate(selectBo(1L, 9L));

        assertEquals(9L, resultId);
        ArgumentCaptor<AigTaskResult> captor = ArgumentCaptor.forClass(AigTaskResult.class);
        verify(resultMapper).updateById(captor.capture());
        AigTaskResult update = captor.getValue();
        assertEquals("APPROVED", update.getCandidateStatus());
        assertEquals(USER_ID, update.getSelectedBy(), "必须记录选定人——一旦选错，这是唯一能追到的责任点");
        assertNotNull(update.getSelectedAt(), "选定时间也要记：事后判断「先选后改」靠它");
    }

    @Test
    @DisplayName("选定：任务必须处于「待人工复核」——未成功/已取消的任务不该产出交付物")
    void selectCandidateRequiresReviewPending() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "CANCELLED", 1, 3));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.selectCandidate(selectBo(1L, 9L)));

        assertTrue(ex.getMessage().contains("待人工复核"), "实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("CANCELLED"), "报错要带当前状态，便于定位；实际=" + ex.getMessage());
        verify(resultMapper, never()).updateById(any(AigTaskResult.class));
    }

    @Test
    @DisplayName("选定：候选必须属于该任务（防止拿 A 的 resultId 选到 B 名下）")
    void selectCandidateRejectsForeignResult() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        when(resultMapper.selectById(9L)).thenReturn(candidate(9L, 2L, "CANDIDATE"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.selectCandidate(selectBo(1L, 9L)));

        assertTrue(ex.getMessage().contains("不属于任务"), "实际=" + ex.getMessage());
        verify(resultMapper, never()).updateById(any(AigTaskResult.class));
    }

    @Test
    @DisplayName("选定：被自动质检筛除的候选不接受选定（否则一次顺手点击就能让已知不合格的候选成为交付物）")
    void selectCandidateRejectsFilteredOutCandidate() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        when(resultMapper.selectById(9L)).thenReturn(candidate(9L, 1L, "REJECTED"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.selectCandidate(selectBo(1L, 9L)));

        assertTrue(ex.getMessage().contains("已被自动质检筛除"), "实际=" + ex.getMessage());
        verify(resultMapper, never()).updateById(any(AigTaskResult.class));
    }

    @Test
    @DisplayName("选定是单选：选定一个会取消该任务此前的选定，避免两个「已选定」答不出交付哪一张")
    void selectCandidateIsSingleChoice() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        when(resultMapper.selectById(9L)).thenReturn(candidate(9L, 1L, "CANDIDATE"));
        when(resultMapper.selectList(any())).thenReturn(List.of(candidate(8L, 1L, "APPROVED")));

        service.selectCandidate(selectBo(1L, 9L));

        ArgumentCaptor<AigTaskResult> captor = ArgumentCaptor.forClass(AigTaskResult.class);
        verify(resultMapper, times(2)).updateById(captor.capture());
        AigTaskResult previousReset = captor.getAllValues().get(0);
        assertEquals(8L, previousReset.getResultId());
        assertEquals("CANDIDATE", previousReset.getCandidateStatus(),
            "此前被选中的候选要回到「候选」而不是「筛除」——它只是没被选中，不是不合格");
        assertNull(previousReset.getSelectedBy(), "撤回选定要一并清掉选定人，否则会留下「已撤回但仍显示某人选定」");
        assertEquals("APPROVED", captor.getAllValues().get(1).getCandidateStatus());
    }

    @Test
    @DisplayName("选定：已选定的候选重复选定直接返回（幂等，不产生多余写入与事件）")
    void selectCandidateIsIdempotent() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        when(resultMapper.selectById(9L)).thenReturn(candidate(9L, 1L, "APPROVED"));

        service.selectCandidate(selectBo(1L, 9L));

        verify(resultMapper, never()).updateById(any(AigTaskResult.class));
    }

    // ------------------------------------------------------------------ 状态迁移

    @Test
    @DisplayName("迁移：非法边被拒，报错要列出允许的目标（否则排障只知道不行）")
    void transitionRejectsIllegalEdge() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "DRAFT", 0, 0));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.transition(1L, 0,
            AigTaskStatusEnum.RUNNING, "跳级", null));

        assertTrue(ex.getMessage().contains("非法的状态迁移"), "实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("POLICY_CHECKING"), "应列出允许的目标；实际=" + ex.getMessage());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("迁移：乐观锁冲突时报错且不写事件（并发时宁可失败，不可互相覆盖）")
    void transitionFailsOnVersionConflict() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "DRAFT", 0, 0));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.transition(1L, 0,
            AigTaskStatusEnum.POLICY_CHECKING, "提交校验", null));

        assertTrue(ex.getMessage().contains("并发修改"), "实际=" + ex.getMessage());
        verify(eventMapper, never()).insert(any(AigTaskEvent.class));
    }

    @Test
    @DisplayName("迁移：成功时事件带上 from/to 与下一个序号")
    void transitionWritesEventWithFromTo() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "DRAFT", 0, 0), task(1L, "POLICY_CHECKING", 0, 1));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);
        // 真实 SQL 是 orderByDesc(sequence) + limit 1，即只回最大那一条——桩数据要与之同形，
        // 否则 get(0) 拿到的是最小值，序号断言就会错得看起来像实现有问题
        when(eventMapper.selectList(any())).thenReturn(List.of(existingEvent(2)));

        service.transition(1L, 0, AigTaskStatusEnum.POLICY_CHECKING, "提交校验", null);

        ArgumentCaptor<AigTaskEvent> captor = ArgumentCaptor.forClass(AigTaskEvent.class);
        verify(eventMapper).insert(captor.capture());
        AigTaskEvent event = captor.getValue();
        assertEquals("DRAFT", event.getFromStatus());
        assertEquals("POLICY_CHECKING", event.getToStatus());
        assertEquals(3, event.getSequence(), "序号应为当前最大值 + 1");
    }

    @Test
    @DisplayName("迁移：缺少期望版本直接拒绝——不允许无条件覆盖")
    void transitionRequiresExpectedVersion() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.transition(1L, null,
            AigTaskStatusEnum.POLICY_CHECKING, "x", null));

        assertTrue(ex.getMessage().contains("期望版本"), "实际=" + ex.getMessage());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("失败：错误分类与原因要落到 error_code/error_message 两列（此前这两列全表无人写入，治理台恒为空）")
    void failureWritesErrorColumns() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 2, 5), task(1L, "FAILED", 3, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.recordFailure(1L, 5, AigErrorClassEnum.AUTH_FAILED, "上游拒绝");

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(captor.capture());
        AigTask update = captor.getValue();
        assertEquals(AigErrorClassEnum.AUTH_FAILED.getCode(), update.getErrorCode(),
            "治理台的「错误码」列渲染的就是这一列，不写它那列永远为空");
        assertEquals("上游拒绝", update.getErrorMessage());
    }

    @Test
    @DisplayName("失败：没给分类时如实记 UNKNOWN（不假装知道），也不留空列")
    void failureWithoutClassRecordsUnknown() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 2, 5), task(1L, "FAILED", 3, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.transition(1L, 5, AigTaskStatusEnum.FAILED, "业务域回写失败", null, null, "内核挂了");

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(AigErrorClassEnum.UNKNOWN.getCode(), captor.getValue().getErrorCode());
        assertEquals("内核挂了", captor.getValue().getErrorMessage());
    }

    @Test
    @DisplayName("成功：要清空 error_code/error_message（成功还挂着上一次尝试的旧错误码比没有更坏）")
    void successClearsErrorColumns() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 0, 5), task(1L, "SUCCEEDED", 1, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.transition(1L, 5, AigTaskStatusEnum.SUCCEEDED, "调用成功", null);

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(captor.capture());
        // 用空串而不是 null：MyBatis-Plus 默认忽略 null 字段，传 null 根本改不动这一列
        assertEquals("", captor.getValue().getErrorCode());
        assertEquals("", captor.getValue().getErrorMessage());
    }

    @Test
    @DisplayName("其余状态不碰错误列（RETRY_WAIT/NEED_HUMAN 正需要保留失败原因）")
    void otherStatusesLeaveErrorColumnsUntouched() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "FAILED", 1, 5), task(1L, "RETRY_WAIT", 1, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        service.transition(1L, 5, AigTaskStatusEnum.RETRY_WAIT, "等待重试", null);

        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertNull(captor.getValue().getErrorCode(), "不碰它，让它保留着失败原因");
        assertNull(captor.getValue().getErrorMessage());
    }

    @Test
    @DisplayName("失败：迁移到 FAILED 时 attemptNo 加一（它是幂等键的一半，不计数会让重试变成重复提交）")
    void failureIncrementsAttemptNo() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 2, 5), task(1L, "FAILED", 3, 6));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigTaskStatusEnum resting = service.recordFailure(1L, 5, AigErrorClassEnum.AUTH_FAILED, "密钥无效");

        // 鉴权失败不重试也不强制转人工 → resting=FAILED 即「停在原地」，因此只有一次迁移（到 FAILED）
        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper, times(1)).updateById(captor.capture());
        AigTask update = captor.getValue();
        assertEquals(AigTaskStatusEnum.FAILED.getCode(), update.getStatus());
        assertEquals(3, update.getAttemptNo(), "本次尝试必须计入 attemptNo");
        assertEquals(AigTaskStatusEnum.FAILED, resting,
            "鉴权失败的正确动作是运维换密钥，不是反复重试同一个错密钥（会把账号打到风控）");
    }

    @Test
    @DisplayName("失败：可重试且还有余额时进入等待重试")
    void failureEntersRetryWaitWhenRetryable() {
        // 注意：每次 transition 会读两次任务（迁移前判源状态、迁移后回读），因此第二次迁移
        // 在桩里需要两条 FAILED —— 少一条会让它把「迁移后的状态」当成源状态，
        // 于是被状态机正确地判为非法自迁移，而报错看起来像实现有问题
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 0, 5), task(1L, "FAILED", 1, 6),
            task(1L, "FAILED", 1, 6), task(1L, "RETRY_WAIT", 1, 7));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigTaskStatusEnum resting = service.recordFailure(1L, 5, AigErrorClassEnum.TIMEOUT, "超时");

        assertEquals(AigTaskStatusEnum.RETRY_WAIT, resting);
        verify(taskMapper, times(2)).updateById(any(AigTask.class));
    }

    // ------------------------------------------------------------------ 结果回写

    @Test
    @DisplayName("结果回写：自动流程置 APPROVED 被拒（选定哪一张必须有人担）")
    void recordResultRejectsApproved() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        AigTaskResultBo bo = new AigTaskResultBo();
        bo.setTaskId(1L);
        bo.setResultType("ASSET");
        bo.setAssetId(88L);
        bo.setCandidateStatus("APPROVED");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordResult(bo));

        assertTrue(ex.getMessage().contains("只筛除、不放行"), "实际=" + ex.getMessage());
        verify(resultMapper, never()).insert(any(AigTaskResult.class));
    }

    @Test
    @DisplayName("结果回写：CANDIDATE 正常入库")
    void recordResultAcceptsCandidate() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 3));
        AigTaskResultBo bo = new AigTaskResultBo();
        bo.setTaskId(1L);
        bo.setResultType("ASSET");
        bo.setAssetId(88L);

        service.recordResult(bo);

        ArgumentCaptor<AigTaskResult> captor = ArgumentCaptor.forClass(AigTaskResult.class);
        verify(resultMapper).insert(captor.capture());
        assertEquals("CANDIDATE", captor.getValue().getCandidateStatus(), "未指定时默认候选，不是「已选定」");
    }

    // ------------------------------------------------------------------ 人工复核

    @Test
    @DisplayName("复核：拒绝时必须填写意见（没有理由的拒绝既无法申诉也无法改进）")
    void reviewRequiresCommentOnReject() {
        AigTaskReviewBo bo = new AigTaskReviewBo();
        bo.setTaskId(1L);
        bo.setExpectedVersion(2);
        bo.setApproved(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.review(bo));

        assertTrue(ex.getMessage().contains("复核意见"), "实际=" + ex.getMessage());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("复核：通过后状态为 APPROVED 并记录复核人")
    void reviewApprovesAndRecordsReviewer() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "REVIEW_PENDING", 1, 2), task(1L, "APPROVED", 1, 3));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);
        AigTaskReviewBo bo = new AigTaskReviewBo();
        bo.setTaskId(1L);
        bo.setExpectedVersion(2);
        bo.setApproved(true);

        service.review(bo);

        // 两次 updateById：第一次是状态迁移（REVIEW_PENDING→APPROVED），第二次写复核人/意见
        ArgumentCaptor<AigTask> captor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper, times(2)).updateById(captor.capture());
        AigTask reviewUpdate = captor.getAllValues().get(1);
        assertEquals("APPROVED", reviewUpdate.getReviewStatus());
        assertEquals(USER_ID, reviewUpdate.getReviewedBy(), "复核人必须记录，否则「谁批的」查不出来");
    }

    // ------------------------------------------------------------------ 回调

    @Test
    @DisplayName("回调：未配置密钥 → 拒绝且绝不推进状态，但必须记账")
    void callbackWithoutSecretIsRejectedAndNeverAdvances() throws Exception {
        AigTaskCallbackBo bo = signedCallback();
        bo.setProviderCode("unconfigured-provider");

        AigCallbackVo vo = service.handleCallback(bo);

        assertEquals("REJECTED_UNSIGNED", vo.getProcessResult());
        assertTrue(vo.getDetail().contains("未配置"), "原因要能区分「改配置」与「疑似攻击」；实际=" + vo.getDetail());
        verify(taskMapper, never()).updateById(any(AigTask.class));
        ArgumentCaptor<AigCallback> captor = ArgumentCaptor.forClass(AigCallback.class);
        verify(callbackMapper).insert(captor.capture());
        assertEquals("N", captor.getValue().getSignatureVerified(), "未验签必须如实记为 N");
    }

    @Test
    @DisplayName("回调：载荷被篡改 → 拒绝且不推进状态")
    void callbackWithTamperedPayloadIsRejected() throws Exception {
        AigTaskCallbackBo bo = signedCallback();
        bo.setRawPayload(PAYLOAD.replace("finished", "failed"));

        AigCallbackVo vo = service.handleCallback(bo);

        assertEquals("REJECTED_UNSIGNED", vo.getProcessResult());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("回调：验签通过且迁移合法 → ACCEPTED 并推进状态")
    void callbackAdvancesWhenSignatureValid() throws Exception {
        when(taskMapper.selectOne(any())).thenReturn(task(1L, "RUNNING", 1, 4));
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 1, 4), task(1L, "SUCCEEDED", 1, 5));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigCallbackVo vo = service.handleCallback(signedCallback());

        assertEquals("ACCEPTED", vo.getProcessResult(), "实际=" + vo.getDetail());
        assertEquals("SUCCEEDED", vo.getTaskStatus());
        assertTrue(vo.isAccepted());
        verify(taskMapper).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("回调：重复投递 → DUPLICATE 且不再推进（账本一行一个事件，不是一行一次投递）")
    void callbackIsIdempotentOnRepeatedDelivery() throws Exception {
        AigCallback existing = new AigCallback();
        existing.setTaskId(1L);
        existing.setProcessResult("ACCEPTED");
        when(callbackMapper.selectOne(any())).thenReturn(existing);

        AigCallbackVo vo = service.handleCallback(signedCallback());

        assertEquals("DUPLICATE", vo.getProcessResult());
        assertTrue(vo.isDuplicate());
        assertEquals(1L, vo.getTaskId());
        verify(taskMapper, never()).updateById(any(AigTask.class));
        verify(callbackMapper, never()).insert(any(AigCallback.class));
    }

    @Test
    @DisplayName("回调：定位不到任务 → TASK_NOT_FOUND，仍留痕（否则「对方在推什么」无从查起）")
    void callbackWithoutTaskIsRecorded() throws Exception {
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectOne(any())).thenReturn(null);

        AigCallbackVo vo = service.handleCallback(signedCallback());

        assertEquals("TASK_NOT_FOUND", vo.getProcessResult());
        verify(taskMapper, never()).updateById(any(AigTask.class));
        ArgumentCaptor<AigCallback> captor = ArgumentCaptor.forClass(AigCallback.class);
        verify(callbackMapper).insert(captor.capture());
        assertEquals("TASK_NOT_FOUND", captor.getValue().getProcessResult());
    }

    @Test
    @DisplayName("回调：终态任务的完成回调 → ORDER_STALE 且不推进（迟到的成功不得盖掉已取消的任务）")
    void callbackOnTerminalTaskIsStale() throws Exception {
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectOne(any())).thenReturn(task(1L, "CANCELLED", 1, 9));

        AigCallbackVo vo = service.handleCallback(signedCallback());

        assertEquals("ORDER_STALE", vo.getProcessResult(), "实际=" + vo.getDetail());
        assertTrue(vo.getDetail().contains("顺序过期") || vo.getDetail().contains("不允许迁移"),
            "说明要讲清楚为什么没推进；实际=" + vo.getDetail());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("回调：未知目标状态 → 拒绝（协议映射由适配层负责，本层不猜）")
    void callbackWithUnknownTargetStatusIsRejected() throws Exception {
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectOne(any())).thenReturn(task(1L, "RUNNING", 1, 4));
        AigTaskCallbackBo bo = signedCallback();
        bo.setToStatus("WHATEVER");

        AigCallbackVo vo = service.handleCallback(bo);

        assertEquals("ORDER_STALE", vo.getProcessResult());
        verify(taskMapper, never()).updateById(any(AigTask.class));
    }

    @Test
    @DisplayName("★ 回调声明的失败原因必须落到任务上：此前 bo.getErrorCode() 被整条丢掉")
    void callbackFailureCarriesDeclaredErrorClass() throws Exception {
        when(taskMapper.selectOne(any())).thenReturn(task(1L, "RUNNING", 1, 4));
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 1, 4), task(1L, "FAILED", 2, 5));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        AigCallbackVo vo = service.handleCallback(failedCallback("TIMEOUT", "上游超时"));

        assertEquals("ACCEPTED", vo.getProcessResult(), "实际=" + vo.getDetail());
        assertEquals("FAILED", vo.getTaskStatus());
        // 停在 FAILED 的处置由错误分类决定：UNKNOWN 会停在原地，TIMEOUT 才谈得上重试。
        // 丢掉声明码 → 记成 UNKNOWN，运维看到的就是「不知道为什么会超时」
        ArgumentCaptor<AigTask> updateCaptor = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(updateCaptor.capture());
        assertEquals(AigErrorClassEnum.TIMEOUT.getCode(), updateCaptor.getValue().getErrorCode(),
            "回调说 TIMEOUT，就必须记 TIMEOUT（而不是 UNKNOWN）");
        assertTrue(updateCaptor.getValue().getErrorMessage().contains("TIMEOUT"),
            "error_message 里要保留对方的原始声明：" + updateCaptor.getValue().getErrorMessage());

        // 原始声明同时留在回调账本：任务行记归类结果，账本记「对方到底说了什么」
        ArgumentCaptor<AigCallback> ledgerCaptor = ArgumentCaptor.forClass(AigCallback.class);
        verify(callbackMapper).insert(ledgerCaptor.capture());
        assertTrue(ledgerCaptor.getValue().getDetail().contains("声明错误码=TIMEOUT"),
            "实际=" + ledgerCaptor.getValue().getDetail());
    }

    @Test
    @DisplayName("★ Provider 自有错误码走文本兜底归类；认不出则如实记 UNKNOWN（不猜）")
    void callbackVendorCodeIsClassifiedThenFallsBackToUnknown() throws Exception {
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 1, 4), task(1L, "FAILED", 2, 5));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);

        when(taskMapper.selectOne(any())).thenReturn(task(1L, "RUNNING", 1, 4));
        service.handleCallback(failedCallback("invalid api key", "上游拒绝"));
        ArgumentCaptor<AigTask> first = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(first.capture());
        assertEquals(AigErrorClassEnum.AUTH_FAILED.getCode(), first.getValue().getErrorCode(),
            "自有码认不出时按文本兜底（invalid api key → AUTH_FAILED），这是既有 classify 的能力");

        clearInvocations(taskMapper, callbackMapper);
        when(callbackMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "RUNNING", 1, 4), task(1L, "FAILED", 2, 5));
        when(taskMapper.updateById(any(AigTask.class))).thenReturn(1);
        service.handleCallback(failedCallback("vendor_code_9", "就是坏掉了"));
        ArgumentCaptor<AigTask> second = ArgumentCaptor.forClass(AigTask.class);
        verify(taskMapper).updateById(second.capture());
        assertEquals(AigErrorClassEnum.UNKNOWN.getCode(), second.getValue().getErrorCode(),
            "全认不出时记 UNKNOWN——「失败但不知道为什么」必须显式可查，不能留空");
    }

    /**
     * 造一条「失败」回调（对 PAYLOAD 重新签名，保证验签通过）。
     *
     * @param errorCode 声明的错误码（可空）
     * @param detail    说明（可空）
     * @return 回调入参
     */
    private AigTaskCallbackBo failedCallback(String errorCode, String detail) {
        AigTaskCallbackBo bo = new AigTaskCallbackBo();
        bo.setProviderCode("bluocto");
        bo.setProviderJobId("j-1");
        bo.setEventId("evt-fail-" + errorCode + "-" + detail);
        bo.setToStatus(AigTaskStatusEnum.FAILED.getCode());
        bo.setErrorCode(errorCode);
        bo.setDetail(detail);
        bo.setRawPayload(PAYLOAD);
        bo.setSignAlgorithm(AigTaskCallbackSigner.ALGORITHM);
        bo.setSignature(signer.signHex("bluocto", PAYLOAD));
        return bo;
    }

    /**
     * 造一条既有事件（用于序号推算）。
     *
     * @param sequence 序号
     * @return 事件
     */
    private static AigTaskEvent existingEvent(int sequence) {
        AigTaskEvent event = new AigTaskEvent();
        event.setTaskId(1L);
        event.setSequence(sequence);
        return event;
    }

}
