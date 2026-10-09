package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.task.config.AigArtifactProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskArtifact;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.domain.vo.AigTaskArtifactVo;
import org.dromara.aigov.task.enums.AigArtifactValidationStatusEnum;
import org.dromara.aigov.task.enums.AigTaskEventTypeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskArtifactMapper;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 制品账本服务的行为锁定测试。
 *
 * <p><b>它守的四件事</b>（都属于「出错时不报错」那一类）：</p>
 * <ol>
 *     <li><b>不合格的登记要留证据</b>：走独立事务的记录器写 FAIL 行，同时向调用方抛错——
 *         「只报错不留痕」与「只留痕不报错」都不行；</li>
 *     <li><b>通过行必须显式写 {@code hashVerified=N}</b>：留着 DDL 默认值会让读的人
 *         以为平台验过哈希；</li>
 *     <li><b>同一对象重复登记幂等</b>：不得产生第二行（重复计数会让「产出几份」变成错的）；</li>
 *     <li><b>事件与制品行同生共死</b>：制品入库必写 {@code AI_TASK_ARTIFACT_ADDED}；
 *         被拒时不得写。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskArtifactServiceImplTest {

    private static final String SHA = "9f2b1c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809";

    private AigTaskArtifactMapper artifactMapper;
    private IAigTaskService taskService;
    private AigArtifactRejectionRecorder rejectionRecorder;
    private AigTaskArtifactServiceImpl service;

    /**
     * 预热 MyBatis-Plus 的实体元数据，让 {@link LambdaQueryWrapper#getSqlSegment()} 能渲染出 SQL。
     *
     * <p>不连库也能做这件事：元数据只描述「字段 → 列名」。这一步顺带把实体上的
     * {@code @TableName}/{@code @TableId} 与字段名验证了一遍——列名写错时，
     * 断言里的 SQL 会露出来（否则要等真库上报 {@code Unknown column}）。</p>
     */
    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            AigTaskArtifact.class);
    }

    @BeforeEach
    void setUp() {
        artifactMapper = mock(AigTaskArtifactMapper.class);
        taskService = mock(IAigTaskService.class);
        rejectionRecorder = mock(AigArtifactRejectionRecorder.class);
        service = new AigTaskArtifactServiceImpl(artifactMapper, taskService,
            new AigArtifactProperties(), rejectionRecorder, JsonMapper.builder().build());
        when(artifactMapper.insert(any(AigTaskArtifact.class))).thenAnswer(inv -> {
            inv.<AigTaskArtifact>getArgument(0).setArtifactId(7001L);
            return 1;
        });
        when(artifactMapper.selectOne(any())).thenReturn(null);
    }

    private AigTask task(String status) {
        AigTask task = new AigTask();
        task.setTaskId(1L);
        task.setStatus(status);
        task.setAttemptNo(2);
        return task;
    }

    private AigTaskArtifactBo validBo() {
        AigTaskArtifactBo bo = new AigTaskArtifactBo();
        bo.setTaskId(1L);
        bo.setArtifactType("IMAGE");
        bo.setMimeType("image/png");
        bo.setSizeBytes(2048L);
        bo.setSha256(SHA);
        bo.setStorageRef("aig-private/artifact/1/out-9f2b1c4d.png");
        return bo;
    }

    @Test
    @DisplayName("★ 合法制品：入库 + 写事件；哈希来源显式标为「声明值」")
    void registerWritesRowAndEvent() {
        when(taskService.getTask(1L)).thenReturn(task(AigTaskStatusEnum.RUNNING.getCode()));

        AigTaskArtifactVo vo = service.register(validBo());

        assertEquals(7001L, vo.getArtifactId());
        assertEquals("生产方声明值（平台未回读重算）", vo.getHashVerifiedLabel(),
            "页面上只显示 sha256 时，读的人会以为平台验过——标签必须说清");
        assertEquals("通过", vo.getValidationStatusLabel());

        ArgumentCaptor<AigTaskArtifact> captor = ArgumentCaptor.forClass(AigTaskArtifact.class);
        verify(artifactMapper).insert(captor.capture());
        AigTaskArtifact stored = captor.getValue();
        assertEquals("N", stored.getHashVerified(), "必须显式写 N，不能靠 DDL 默认值");
        assertEquals(AigArtifactValidationStatusEnum.PASS.getCode(), stored.getValidationStatus());
        assertEquals(SHA, stored.getSha256());
        assertEquals(2, stored.getAttemptNo(), "不传尝试次数时取任务当前尝试次数");
        assertEquals("0", stored.getDelFlag());

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordEvent(eq(1L), eq(AigTaskEventTypeEnum.AI_TASK_ARTIFACT_ADDED),
            detail.capture(), payload.capture());
        assertTrue(detail.getValue().contains("7001"), detail.getValue());
        assertTrue(detail.getValue().contains("未回读重算"), "事件说明里也要写清哈希来源：" + detail.getValue());
        assertTrue(payload.getValue().contains("7001"), "载荷按契约给 artifactIds：" + payload.getValue());
    }

    @Test
    @DisplayName("★ 不合格制品：留 FAIL 证据（独立事务）并抛错，错误码是 ARTIFACT_INVALID")
    void rejectedArtifactLeavesEvidenceAndThrows() {
        when(taskService.getTask(1L)).thenReturn(task(AigTaskStatusEnum.RUNNING.getCode()));
        AigTaskArtifactBo bo = validBo();
        bo.setMimeType("text/html");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.register(bo));

        assertTrue(ex.getMessage().contains("ARTIFACT_INVALID"), ex.getMessage());
        assertTrue(ex.getMessage().contains("text/html"), "要给出字段级原因：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("FAIL"), "要让调用方知道已留痕：" + ex.getMessage());

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(rejectionRecorder).record(eq(bo), detail.capture());
        assertTrue(detail.getValue().contains("不在允许清单内"), detail.getValue());

        verify(artifactMapper, never()).insert(any(AigTaskArtifact.class));
        verify(taskService, never()).recordEvent(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("已取消/已拒绝的任务不再收制品，且不写校验证据（这不是校验失败）")
    void cancelledTaskRefusesArtifacts() {
        when(taskService.getTask(1L)).thenReturn(task(AigTaskStatusEnum.CANCELLED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.register(validBo()));

        assertTrue(ex.getMessage().contains("CANCELLED"), ex.getMessage());
        verify(rejectionRecorder, never()).record(any(), any());
        verify(artifactMapper, never()).insert(any(AigTaskArtifact.class));
    }

    @Test
    @DisplayName("同一任务同一对象同一哈希重复登记 → 幂等，不再插一行、不再写事件")
    void duplicateRegistrationIsIdempotent() {
        when(taskService.getTask(1L)).thenReturn(task(AigTaskStatusEnum.REVIEW_PENDING.getCode()));
        AigTaskArtifact existing = new AigTaskArtifact();
        existing.setArtifactId(6006L);
        existing.setValidationStatus(AigArtifactValidationStatusEnum.PASS.getCode());
        existing.setHashVerified("N");
        when(artifactMapper.selectOne(any())).thenReturn(existing);

        AigTaskArtifactVo vo = service.register(validBo());

        assertEquals(6006L, vo.getArtifactId(), "应返回既有制品ID");
        verify(artifactMapper, never()).insert(any(AigTaskArtifact.class));
        verify(taskService, never()).recordEvent(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("任务不存在 → 抛错，不写任何行（无从归因的制品不收）")
    void missingTaskIsRejected() {
        when(taskService.getTask(1L)).thenThrow(new ServiceException("任务不存在：1"));

        assertThrows(ServiceException.class, () -> service.register(validBo()));
        verify(artifactMapper, never()).insert(any(AigTaskArtifact.class));
        verify(rejectionRecorder, never()).record(any(), any());
    }

    @Test
    @DisplayName("★ 列表默认只取通过行；带上 includeRejected 才查被拒记录")
    void listFiltersRejectedByDefault() {
        AigTaskArtifact pass = new AigTaskArtifact();
        pass.setArtifactId(11L);
        pass.setValidationStatus(AigArtifactValidationStatusEnum.PASS.getCode());
        pass.setHashVerified("N");
        pass.setCreateTime(LocalDateTime.of(2026, 10, 9, 12, 0));
        AigTaskArtifact fail = new AigTaskArtifact();
        fail.setArtifactId(12L);
        fail.setValidationStatus(AigArtifactValidationStatusEnum.FAIL.getCode());
        fail.setHashVerified("N");
        fail.setValidationDetail("MIME 类型 text/html 不在允许清单内");
        when(artifactMapper.selectList(any())).thenReturn(List.of(fail, pass));

        List<AigTaskArtifactVo> onlyPass = service.listByTask(1L, false);
        assertEquals(2, onlyPass.size(), "这里给的是替身返回的全部行，筛选发生在 SQL 条件里");
        assertEquals("被拒", onlyPass.get(0).getValidationStatusLabel());
        assertEquals("MIME 类型 text/html 不在允许清单内", onlyPass.get(0).getValidationDetail());
        assertEquals("通过", onlyPass.get(1).getValidationStatusLabel());

        List<AigTaskArtifactVo> withRejected = service.listByTask(1L, true);
        assertEquals(2, withRejected.size(), "带上被拒记录时同样返回全部（筛选发生在 SQL 条件里）");

        ArgumentCaptor<LambdaQueryWrapper<AigTaskArtifact>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(artifactMapper, times(2)).selectList(captor.capture());
        String defaultSql = captor.getAllValues().get(0).getSqlSegment();
        String allSql = captor.getAllValues().get(1).getSqlSegment();
        assertTrue(defaultSql.contains("validation_status"),
            "默认查询必须带 validation_status 条件（否则被拒行会混进产出清单）：" + defaultSql);
        assertFalse(allSql.contains("validation_status"),
            "includeRejected=true 时不该再按结论过滤：" + allSql);
        assertTrue(defaultSql.contains("task_id"), defaultSql);
    }

    @Test
    @DisplayName("任务ID为空 → 抛错而不是查全表")
    void blankTaskIdIsRejected() {
        AigTaskArtifactBo bo = validBo();
        bo.setTaskId(null);
        assertThrows(ServiceException.class, () -> service.register(bo));
        assertThrows(ServiceException.class, () -> service.listByTask(null, false));
        assertNotNull(service, "服务本身不为空");
    }

}
