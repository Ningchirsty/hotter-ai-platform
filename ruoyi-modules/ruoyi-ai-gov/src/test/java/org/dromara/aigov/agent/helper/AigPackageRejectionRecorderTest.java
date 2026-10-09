package org.dromara.aigov.agent.helper;

import org.dromara.aigov.agent.domain.AigPackageRejection;
import org.dromara.aigov.agent.mapper.AigPackageRejectionMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 被拒证据登记器的测试。
 *
 * <p><b>最重要的是那个注解断言</b>：证据必须跑在 {@code REQUIRES_NEW} 里，
 * 否则调用方抛出的异常会把证据一起回滚掉——而"留痕"恰恰只在失败路径上才有意义。
 * 单测证明不了事务语义在数据库上的表现（本模块测试不连库），但能钉住"意图没被悄悄改掉"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPackageRejectionRecorderTest {

    private AigPackageRejectionMapper rejectionMapper;
    private AigPackageRejectionRecorder recorder;

    @BeforeEach
    void setUp() {
        rejectionMapper = mock(AigPackageRejectionMapper.class);
        recorder = new AigPackageRejectionRecorder(rejectionMapper);
    }

    @Test
    @DisplayName("★ record 必须跑在独立事务里（REQUIRES_NEW）：否则调用方回滚会把证据带走")
    void recordRunsInItsOwnTransaction() throws Exception {
        Method method = AigPackageRejectionRecorder.class.getMethod("record", String.class, String.class,
            String.class, byte[].class, String.class, String.class, String.class, String.class, Long.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation, "缺少 @Transactional：被拒证据会跟着调用方的事务一起回滚");
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation(),
            "必须是 REQUIRES_NEW——证据要在注册失败回滚之后依然存在");
        assertTrue(annotation.rollbackFor().length > 0, "要声明 rollbackFor，避免受检异常不回滚");
    }

    @Test
    @DisplayName("★ 超长字段一律截断：证据行不能因为字段畸形而写不进去（否则拒绝变 500）")
    void oversizeFieldsAreTruncated() {
        byte[] body = new byte[7];

        recorder.record("P".repeat(200), "V".repeat(100), "N".repeat(400), body, "H".repeat(200),
            "R".repeat(100), "RULES".repeat(300), "D".repeat(3000), 5L);

        ArgumentCaptor<AigPackageRejection> captor = ArgumentCaptor.forClass(AigPackageRejection.class);
        verify(rejectionMapper).insert(captor.capture());
        AigPackageRejection row = captor.getValue();
        assertEquals(64, row.getPackageCode().length(), "package_code varchar(64)");
        assertEquals(32, row.getPackageVersion().length(), "package_version varchar(32)");
        assertEquals(255, row.getBodyName().length(), "body_name varchar(255)");
        assertEquals(64, row.getBodySha256().length(), "body_sha256 char(64)");
        assertEquals(500, row.getHitRules().length(), "hit_rules varchar(500)");
        assertEquals(1000, row.getDetail().length(), "detail varchar(1000)");
        assertEquals(7L, row.getBodySize(), "包体字节数由服务端实算");
        assertNotNull(row.getCreateTime());
    }

    @Test
    @DisplayName("空值原样保留：连包体都没拿到时，空比空串更诚实")
    void nullsStayNull() {
        recorder.record(null, null, null, null, null,
            AigPackageRejectionRecorder.REASON_MANIFEST_INVALID, null, "Manifest 不是 JSON", null);

        ArgumentCaptor<AigPackageRejection> captor = ArgumentCaptor.forClass(AigPackageRejection.class);
        verify(rejectionMapper).insert(captor.capture());
        AigPackageRejection row = captor.getValue();
        assertNull(row.getPackageCode());
        assertNull(row.getPackageVersion());
        assertNull(row.getBodySize(), "没拿到包体时不该写成 0（0 是一个真实的空包体）");
        assertNull(row.getOperatorId(), "系统触发时为空");
        assertEquals(AigPackageRejectionRecorder.REASON_MANIFEST_INVALID, row.getRejectReason());
    }

    @Test
    @DisplayName("包体字节数按实际长度记录（事后要能回答「对方交了个多大的东西」）")
    void bodySizeIsRecorded() {
        recorder.record("demo.pkg", "1.0.0", "pkg.zip", new byte[1234], "hash",
            AigPackageRejectionRecorder.REASON_ARCHIVE_UNSAFE, "UNBOUNDED_CODE_EXECUTION", "d", 1L);

        ArgumentCaptor<AigPackageRejection> captor = ArgumentCaptor.forClass(AigPackageRejection.class);
        verify(rejectionMapper).insert(captor.capture());
        assertEquals(1234L, captor.getValue().getBodySize());
    }

}
