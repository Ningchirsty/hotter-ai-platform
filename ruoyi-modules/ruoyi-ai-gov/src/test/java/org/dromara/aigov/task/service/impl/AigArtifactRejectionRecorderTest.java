package org.dromara.aigov.task.service.impl;

import org.dromara.aigov.task.domain.AigTaskArtifact;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.enums.AigArtifactValidationStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskArtifactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 被拒制品登记器的测试。
 *
 * <p><b>这里最重要的是那个注解断言</b>：被拒记录必须跑在 {@code REQUIRES_NEW} 事务里，
 * 否则调用方抛出的异常会把证据一起回滚掉——而那是这条路径存在的唯一理由。
 * 单测无法证明事务语义在数据库上真的那样表现（本模块的测试不连库），
 * 但可以钉住「意图没有被悄悄改掉」：少写这个注解、或写成默认传播，测试就会红。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigArtifactRejectionRecorderTest {

    private AigTaskArtifactMapper artifactMapper;
    private AigArtifactRejectionRecorder recorder;

    @BeforeEach
    void setUp() {
        artifactMapper = mock(AigTaskArtifactMapper.class);
        recorder = new AigArtifactRejectionRecorder(artifactMapper);
    }

    @Test
    @DisplayName("★ record 必须跑在独立事务里（REQUIRES_NEW）：否则调用方回滚会把证据带走")
    void recordRunsInItsOwnTransaction() throws Exception {
        Method method = AigArtifactRejectionRecorder.class.getMethod("record", AigTaskArtifactBo.class, String.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation, "缺少 @Transactional：被拒记录会跟着调用方的事务一起回滚");
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation(),
            "必须是 REQUIRES_NEW——被拒的原因要在调用方回滚之后依然存在");
        assertTrue(annotation.rollbackFor().length > 0, "要声明 rollbackFor，避免受检异常不回滚");
    }

    @Test
    @DisplayName("★ 超长/缺失字段一律截断补齐：证据行绝不能因为字段太长而写不进去")
    void oversizeFieldsAreTruncated() {
        AigTaskArtifactBo bo = new AigTaskArtifactBo();
        bo.setTaskId(1L);
        bo.setArtifactType("X".repeat(100));
        bo.setMimeType("M".repeat(400));
        bo.setSizeBytes(5L);
        bo.setSha256("H".repeat(200));
        bo.setStorageRef("R".repeat(900));

        recorder.record(bo, "明细".repeat(900));

        ArgumentCaptor<AigTaskArtifact> captor = ArgumentCaptor.forClass(AigTaskArtifact.class);
        verify(artifactMapper).insert(captor.capture());
        AigTaskArtifact row = captor.getValue();
        assertEquals(32, row.getArtifactType().length(), "artifact_type 是 varchar(32)");
        assertEquals(128, row.getMimeType().length(), "mime_type 是 varchar(128)");
        assertEquals(64, row.getSha256().length(), "sha256 是 char(64)");
        assertEquals(512, row.getStorageRef().length(), "storage_ref 是 varchar(512)");
        assertEquals(1000, row.getValidationDetail().length(), "validation_detail 是 varchar(1000)");
    }

    @Test
    @DisplayName("被拒行显式写 FAIL 与 hashVerified=N，字段缺失时补空串而不是 NULL")
    void failRowIsExplicit() {
        AigTaskArtifactBo bo = new AigTaskArtifactBo();
        bo.setTaskId(9L);

        recorder.record(bo, "制品 sha256 不能为空");

        ArgumentCaptor<AigTaskArtifact> captor = ArgumentCaptor.forClass(AigTaskArtifact.class);
        verify(artifactMapper).insert(captor.capture());
        AigTaskArtifact row = captor.getValue();
        assertEquals(AigArtifactValidationStatusEnum.FAIL.getCode(), row.getValidationStatus());
        assertEquals("N", row.getHashVerified(), "被拒行同样没被平台核对过");
        assertEquals(0, row.getAttemptNo(), "尝试次数缺失时补 0（列是 NOT NULL）");
        assertEquals(0L, row.getSizeBytes(), "大小缺失时补 0（列是 NOT NULL）");
        assertEquals("", row.getArtifactType(), "类型缺失时补空串（列是 NOT NULL）");
        assertEquals("0", row.getDelFlag());
        assertTrue(row.getRemark().contains("制品被拒"), row.getRemark());
    }

    @Test
    @DisplayName("入参整体为 null 也不该抛 NPE（拒收路径本身不能变成 500）")
    void nullBoDoesNotBlowUp() {
        when(artifactMapper.insert(any(AigTaskArtifact.class))).thenReturn(1);
        recorder.record(null, "入参不能为空");
        verify(artifactMapper).insert(any(AigTaskArtifact.class));
    }

}
