package org.dromara.ai.video.service;

import org.dromara.ai.video.api.VideoScenarioReporter;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 视频任务终态 → 平台任务回执的落库侧测试（增量 17，真 SQL + 隔离 H2）。
 *
 * <p>钉住"收口点真的会回报"这件事：①只有 {@code platform_task_id} 非空的派发任务才回报，
 * 页面直接提交的任务（NULL）不回报；②终态流转才回报，进行中流转不回报；
 * ③失败/超时/取消都按 FAILED 回报并带原因。</p>
 */
class VideoScenarioReportSqlTest {

    private JdbcTemplate jdbc;
    private VideoScenarioReporter reporter;
    private JdbcVideoTaskRepository repository;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE video_task (id BIGINT PRIMARY KEY, tenant_id VARCHAR(32), user_id BIGINT, "
            + "status VARCHAR(32), error_code VARCHAR(64), error_message VARCHAR(1024), "
            + "finished_time TIMESTAMP, update_time TIMESTAMP, del_flag VARCHAR(1) DEFAULT '0', "
            + "platform_task_id BIGINT)");
        reporter = mock(VideoScenarioReporter.class);
        repository = new JdbcVideoTaskRepository(jdbc);
        // 生产是 Spring 按类型可选注入；这里直接放进去，验证"注入后收口点会回报"。
        ReflectionTestUtils.setField(repository, "scenarioReporter", reporter);
    }

    @Test
    @DisplayName("★派发任务进终态：按 FAILED 回报并带原因；页面任务（无来源）不回报")
    void terminalFailureReportsOnlyDispatchedTask() {
        insert(1L, "RUNNING", 88L);
        insert(2L, "RUNNING", null);

        assertEquals(1, repository.markFailedIfActive(1L, "COMFY_FAILURE", "ComfyUI 执行失败"));
        assertEquals(1, repository.markFailedIfActive(2L, "COMFY_FAILURE", "ComfyUI 执行失败"));

        ArgumentCaptor<VideoTaskStatus> status = ArgumentCaptor.forClass(VideoTaskStatus.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(reporter).report(org.mockito.ArgumentMatchers.eq(88L),
            org.mockito.ArgumentMatchers.eq(1L), status.capture(), message.capture());
        assertEquals(VideoTaskStatus.FAILED, status.getValue());
        assertEquals("ComfyUI 执行失败", message.getValue());
        // 无来源平台任务ID的那条（id=2）不应产生任何回执
        verify(reporter, times(1)).report(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("进行中流转（QUEUED→RUNNING）不上报")
    void nonTerminalTransitionDoesNotReport() {
        insert(1L, "QUEUED", 88L);

        assertEquals(1, repository.transition(1L, VideoTaskStatus.QUEUED,
            VideoTaskStatus.RUNNING, null, null));

        verify(reporter, never()).report(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("超时流转（RUNNING→TIMEOUT）按 FAILED 回报")
    void timeoutTransitionReportsFailed() {
        insert(1L, "RUNNING", 88L);

        assertEquals(1, repository.transition(1L, VideoTaskStatus.RUNNING,
            VideoTaskStatus.TIMEOUT, "COMFY_TIMEOUT", "等待结果超时"));

        verify(reporter).report(org.mockito.ArgumentMatchers.eq(88L),
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(VideoTaskStatus.TIMEOUT),
            org.mockito.ArgumentMatchers.eq("等待结果超时"));
    }

    @Test
    @DisplayName("排队任务被取消（QUEUED→CANCELED）按 FAILED 回报")
    void cancelledQueuedTaskReportsFailed() {
        insert(1L, "QUEUED", 88L);

        assertEquals(1, repository.cancelQueued(1L, "tenant-a", 7L));

        verify(reporter).report(org.mockito.ArgumentMatchers.eq(88L),
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(VideoTaskStatus.CANCELED),
            any());
    }

    private void insert(long id, String status, Long platformTaskId) {
        jdbc.update("INSERT INTO video_task (id, tenant_id, user_id, status, platform_task_id) "
            + "VALUES (?, 'tenant-a', 7, ?, ?)", id, status, platformTaskId);
    }

}
