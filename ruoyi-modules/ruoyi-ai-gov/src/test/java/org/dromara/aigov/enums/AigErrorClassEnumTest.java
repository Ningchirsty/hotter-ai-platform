package org.dromara.aigov.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误分类的行为锁定测试。
 *
 * <p>分类结果直接决定「退避重试 / 转人工 / 熔断 Provider」三种完全不同的处置，
 * 因此两件事必须钉住：① 每个分类的处置含义不被随手改；
 * ② {@link AigErrorClassEnum#classify} 在信息不足时**保守返回 UNKNOWN**，
 * 不能把认不出的错误猜成「可重试」——猜错会让不可重试的错误被反复重试，
 * 既烧预算又把真正的故障埋在一堆重试日志里。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigErrorClassEnumTest {

    @Test
    @DisplayName("处置矩阵：可重试只有限流/超时/不可用三类")
    void retryableSetIsExact() {
        List<AigErrorClassEnum> retryable = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isRetryable)
            .toList();
        assertEquals(
            List.of(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.TIMEOUT, AigErrorClassEnum.UNAVAILABLE),
            retryable,
            "可重试集合一旦被扩大，参数错误与鉴权失败也会被反复重试");
    }

    @Test
    @DisplayName("处置矩阵：转人工只有参数/Schema、权限外发、结果不可解析三类")
    void needsHumanSetIsExact() {
        List<AigErrorClassEnum> needsHuman = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isNeedsHuman)
            .toList();
        assertEquals(
            List.of(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.POLICY_DENIED,
                AigErrorClassEnum.OUTPUT_UNPARSABLE),
            needsHuman,
            "应转人工的集合即设计 §13.3「不重试、转 NEED_HUMAN」的三种情形");
    }

    @Test
    @DisplayName("只有鉴权失败会熔断：密钥错了继续调用只会把账号打到风控")
    void onlyAuthFailureCircuitBreaks() {
        List<AigErrorClassEnum> breaking = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isCircuitBreak)
            .toList();
        assertEquals(List.of(AigErrorClassEnum.AUTH_FAILED), breaking);
    }

    @Test
    @DisplayName("不变式：不能同时「可重试」与「需转人工」——那会让重试循环永远不收敛")
    void retryableAndNeedsHumanAreMutuallyExclusive() {
        Arrays.stream(AigErrorClassEnum.values()).forEach(item -> assertFalse(
            item.isRetryable() && item.isNeedsHuman(),
            item.getCode() + " 同时可重试又需转人工，重试与转人工会互相抵消"));
    }

    @Test
    @DisplayName("结构化的错误码优先于 HTTP 状态与文本")
    void errorCodeWinsOverStatusAndText() {
        assertEquals(AigErrorClassEnum.TIMEOUT,
            AigErrorClassEnum.classify("TIMEOUT", 401, "unauthorized"),
            "调用器已明确上报分类时，不该再被状态码或文案覆盖");
        assertEquals(AigErrorClassEnum.TIMEOUT,
            AigErrorClassEnum.classify("timeout", null, null),
            "错误码匹配应大小写不敏感");
    }

    @Test
    @DisplayName("HTTP 状态码映射：401/403 鉴权、429 限流、408/504 超时、5xx 不可用、400/422 参数")
    void httpStatusMapping() {
        assertEquals(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.classify(null, 401, null));
        assertEquals(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.classify(null, 403, null));
        assertEquals(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.classify(null, 429, null));
        assertEquals(AigErrorClassEnum.TIMEOUT, AigErrorClassEnum.classify(null, 408, null));
        assertEquals(AigErrorClassEnum.TIMEOUT, AigErrorClassEnum.classify(null, 504, null));
        assertEquals(AigErrorClassEnum.UNAVAILABLE, AigErrorClassEnum.classify(null, 500, null));
        assertEquals(AigErrorClassEnum.UNAVAILABLE, AigErrorClassEnum.classify(null, 503, null));
        assertEquals(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.classify(null, 400, null));
        assertEquals(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.classify(null, 422, null));
    }

    @Test
    @DisplayName("文本兜底：拿不到状态码的链路（如经 snail-ai 转发）也要能归类")
    void textFallback() {
        assertEquals(AigErrorClassEnum.TIMEOUT, AigErrorClassEnum.classify(null, null, "Request Timeout"));
        assertEquals(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.classify(null, null, "429 Too Many Requests"));
        assertEquals(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.classify(null, null, "Invalid API key"));
        assertEquals(AigErrorClassEnum.UNAVAILABLE, AigErrorClassEnum.classify(null, null, "connection refused"));
        assertEquals(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.classify(null, null, "输出不符合Schema"));
        assertEquals(AigErrorClassEnum.TIMEOUT, AigErrorClassEnum.classify(null, null, "模型调用超时"));
    }

    @Test
    @DisplayName("信息不足必须保守：认不出就是 UNKNOWN，且 UNKNOWN 不可重试")
    void unknownIsConservative() {
        assertEquals(AigErrorClassEnum.UNKNOWN, AigErrorClassEnum.classify(null, null, null));
        assertEquals(AigErrorClassEnum.UNKNOWN, AigErrorClassEnum.classify(null, null, "  "));
        assertEquals(AigErrorClassEnum.UNKNOWN,
            AigErrorClassEnum.classify("context_length_exceeded", null, "模型返回了看不懂的东西"));
        assertEquals(AigErrorClassEnum.UNKNOWN, AigErrorClassEnum.classify("SOME_VENDOR_CODE", 302, "moved"));
        assertFalse(AigErrorClassEnum.UNKNOWN.isRetryable(),
            "认不出的错误不许重试：猜成可重试会让不可恢复的错误被反复重试");
    }

    @Test
    @DisplayName("find：能按 code 命中、大小写不敏感，未知返回 null")
    void findResolvesKnownCodesOnly() {
        assertEquals(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.find("RATE_LIMITED"));
        assertEquals(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.find(" rate_limited "));
        assertNull(AigErrorClassEnum.find("NOPE"));
        assertNull(AigErrorClassEnum.find(null));
    }

    @Test
    @DisplayName("每个分类都自带非空 code 与描述，避免审计里出现空白原因")
    void everyClassHasCodeAndDesc() {
        Arrays.stream(AigErrorClassEnum.values()).forEach(item -> {
            assertTrue(item.getCode() != null && !item.getCode().isBlank(), "code 不能为空");
            assertTrue(item.getDesc() != null && !item.getDesc().isBlank(), "描述不能为空");
        });
    }

}
