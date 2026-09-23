package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交服务的派发时序测试。
 *
 * <p><b>钉住的问题</b>：调用方在事务里建完素材与任务后立刻派发，执行线程会按
 * {@code tenant+user} 反查素材，而那条素材在事务提交前还不可见，任务会以
 * {@code ASSET_NOT_FOUND} 失败。这个竞态在生产真实发生过（视觉工厂第二条候选），
 * 因此必须有一处测试明确要求：<b>有事务时不得在提交前派发</b>。</p>
 *
 * <p>不需要 DB、不需要 GPU、不需要登录上下文。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageTaskSubmissionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = Path.of("..", "..", "script");

    @Mock
    private ImageTaskRepository repository;

    @Mock
    private ImageTaskDispatchService dispatchService;

    private ImageTaskSubmissionService service;

    @BeforeEach
    void setUp() {
        ImageWorkflowContractRegistry registry = new ImageWorkflowContractRegistry(ROOT, MAPPER);
        registry.load();
        // ImageAssetStore 是具体类，直接 mock：本测试只关心派发时序，不需要真实存储
        service = new ImageTaskSubmissionService(
            registry, new ImageTemplatePreparer(MAPPER), repository,
            mock(ImageAssetStore.class), dispatchService, null);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("有事务时不立即派发，提交后才派发（避免执行线程读不到未提交素材）")
    void dispatchesOnlyAfterCommit() {
        when(dispatchService.dispatch(anyLong(), any()))
            .thenReturn(ImageTaskDispatchService.Outcome.ACCEPTED);

        TransactionSynchronizationManager.initSynchronization();
        ImageTaskSubmissionService.Submission submission =
            service.dispatchTask(7001L, "IMAGE-test", "000000", 42L, taskRow());

        verify(dispatchService, never()).dispatch(anyLong(), any());
        assertEquals("DEFERRED_AFTER_COMMIT", submission.outcome());
        assertEquals(ImageTaskStatus.QUEUED.name(), submission.status());

        // 模拟事务提交
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.forEach(TransactionSynchronization::afterCommit);

        verify(dispatchService, times(1)).dispatch(anyLong(), any());
    }

    @Test
    @DisplayName("无事务时立即派发（控制器路径行为不变）")
    void dispatchesImmediatelyWithoutTransaction() {
        when(dispatchService.dispatch(anyLong(), any()))
            .thenReturn(ImageTaskDispatchService.Outcome.QUEUE_FULL);

        ImageTaskSubmissionService.Submission submission =
            service.dispatchTask(7002L, "IMAGE-test2", "000000", 42L, taskRow());

        verify(dispatchService, times(1)).dispatch(anyLong(), any());
        assertEquals("QUEUE_FULL", submission.outcome());
        assertEquals(false, submission.accepted());
    }

    @Test
    @DisplayName("队列满时不抛异常，任务停在 QUEUED 交给读时刷新补派发")
    void queueFullStaysQueued() {
        when(dispatchService.dispatch(anyLong(), any()))
            .thenReturn(ImageTaskDispatchService.Outcome.QUEUE_FULL);

        ImageTaskSubmissionService.Submission submission =
            service.dispatchTask(7003L, "IMAGE-test3", "000000", 42L, taskRow());

        assertEquals(false, submission.accepted());
        assertEquals(ImageTaskStatus.QUEUED.name(), submission.status());
    }

    private Map<String, Object> taskRow() {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", 7001L);
        task.put("status", ImageTaskStatus.QUEUED.name());
        task.put("capability_code", "I2I");
        task.put("workflow_code", "wf-i2i-qwen21");
        task.put("prompt", "p");
        task.put("input_json", "{\"img\":123}");
        return task;
    }

}
