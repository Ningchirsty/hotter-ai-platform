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
    @DisplayName("处置矩阵：可重试＝限流/超时/不可用 + 工具调用失败（V2 追加）")
    void retryableSetIsExact() {
        List<AigErrorClassEnum> retryable = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isRetryable)
            .toList();
        assertEquals(
            List.of(AigErrorClassEnum.RATE_LIMITED, AigErrorClassEnum.TIMEOUT,
                AigErrorClassEnum.UNAVAILABLE, AigErrorClassEnum.TOOL_FAILED),
            retryable,
            "可重试集合一旦被扩大，参数错误与鉴权失败也会被反复重试；"
                + "TOOL_FAILED（V2 追加）进来是因为工具失败通常是瞬时的，"
                + "但它**不换候选**——换模型解决不了工具挂（见 worthFallback 那一条）");
    }

    @Test
    @DisplayName("处置矩阵：转人工＝参数/Schema、权限外发、结果不可解析、额度不足、需要审批 + 制品校验/安全隔离（V2 追加）")
    void needsHumanSetIsExact() {
        List<AigErrorClassEnum> needsHuman = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isNeedsHuman)
            .toList();
        assertEquals(
            List.of(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.POLICY_DENIED,
                AigErrorClassEnum.APPROVAL_REQUIRED, AigErrorClassEnum.QUOTA_EXCEEDED,
                AigErrorClassEnum.OUTPUT_UNPARSABLE, AigErrorClassEnum.ARTIFACT_INVALID,
                AigErrorClassEnum.SECURITY_QUARANTINE),
            needsHuman,
            "应转人工的集合即设计 §13.3「不重试、转 NEED_HUMAN」的三种情形，"
                + "外加额度不足——它要人去做充值/申请预算这个具体动作，"
                + "而且处置完成后任务可以重排继续；"
                + "外加需要调用审批（C3）——它同样要人去做一件具体的事（提交申请/批准），"
                + "做完之后同一份预案就能继续跑；"
                + "外加 V2 追加的两类：制品校验失败（人在回路才能判断这份制品能不能用）、"
                + "安全隔离（命中注入/越权信号时必须有人看一眼，不能自动放行）");
    }

    @Test
    @DisplayName("会熔断的三类：密钥错了只会把账号打到风控、额度未恢复调用必然同样失败、安全隔离要立刻掐断")
    void circuitBreakingClassesAreExact() {
        List<AigErrorClassEnum> breaking = Arrays.stream(AigErrorClassEnum.values())
            .filter(AigErrorClassEnum::isCircuitBreak)
            .toList();
        assertEquals(List.of(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.QUOTA_EXCEEDED,
                AigErrorClassEnum.SECURITY_QUARANTINE), breaking,
            "SECURITY_QUARANTINE（V2 追加）是第三种要求熔断的：它的默认假设是"
                + "「这条链路已被污染」，继续调用只会把同一份可疑输入送到更多 Provider");
    }

    @Test
    @DisplayName("不变式：不能同时「可重试」与「需转人工」——那会让重试循环永远不收敛")
    void retryableAndNeedsHumanAreMutuallyExclusive() {
        Arrays.stream(AigErrorClassEnum.values()).forEach(item -> assertFalse(
            item.isRetryable() && item.isNeedsHuman(),
            item.getCode() + " 同时可重试又需转人工，重试与转人工会互相抵消"));
    }

    @Test
    @DisplayName("额度不足与鉴权失败必须分开：两者都走 403、都该熔断，但要人做的事完全不同")
    void quotaIsNotAuthFailure() {
        // 真实文案（bluocto 余额为 0 时的原话）：状态码是 403，但原因在计费侧
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED, AigErrorClassEnum.classify(
                null, 403, "外部图像 API 返回 HTTP 403：用户额度不足, 剩余额度: ＄0.000000"));
        // 402 Payment Required 语义唯一，不看文案
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED, AigErrorClassEnum.classify(null, 402, null));
        // 429 + 额度文案：也不能因为状态码是限流就判成限流（那会无意义地退避重试）
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED,
            AigErrorClassEnum.classify(null, 429, "insufficient_quota"));
        // 401 + 额度文案同理
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED,
            AigErrorClassEnum.classify(null, 401, "You exceeded your current quota"));
        // 拿不到状态码时的文本兜底
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED,
            AigErrorClassEnum.classify(null, null, "余额不足，请充值"));
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED,
            AigErrorClassEnum.classify(null, null, "credit balance too low"));
        // 调用器直接上报分类最可靠
        assertEquals(AigErrorClassEnum.QUOTA_EXCEEDED,
            AigErrorClassEnum.classify("QUOTA_EXCEEDED", 403, "whatever"));

        // 反向：真正的鉴权失败与限流不能被额度规则抢走
        assertEquals(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.classify(null, 403, "invalid api key"));
        assertEquals(AigErrorClassEnum.AUTH_FAILED, AigErrorClassEnum.classify(null, 401, "unauthorized"));
        assertEquals(AigErrorClassEnum.RATE_LIMITED,
            AigErrorClassEnum.classify(null, 429, "too many requests"));
        assertEquals(AigErrorClassEnum.RATE_LIMITED,
            AigErrorClassEnum.classify(null, null, "请求过于频繁，已被限流"));

        // 处置差别只有一处，但正是它决定任务落到哪：额度要人充值 → 待人工处理
        assertTrue(AigErrorClassEnum.QUOTA_EXCEEDED.isNeedsHuman(), "额度耗尽是等人充值，不是偶发失败");
        assertFalse(AigErrorClassEnum.AUTH_FAILED.isNeedsHuman(), "鉴权失败的既定口径保持不变，不在本次改动范围内");
        assertFalse(AigErrorClassEnum.QUOTA_EXCEEDED.isRetryable(), "充值前重试必然同样失败");
        assertTrue(AigErrorClassEnum.QUOTA_EXCEEDED.isCircuitBreak());
        assertTrue(AigErrorClassEnum.QUOTA_EXCEEDED.isWorthFallback(),
            "这一家没额度了，换一家往往立刻可用——正是 fallback 存在的意义");
    }

    @Test
    @DisplayName("换候选（fallback）集合：入参类、策略类与 V2 三类都不换——换谁都一样")
    void worthFallbackSetIsExact() {
        List<AigErrorClassEnum> noFallback = Arrays.stream(AigErrorClassEnum.values())
            .filter(item -> !item.isWorthFallback())
            .toList();
        assertEquals(List.of(AigErrorClassEnum.INVALID_REQUEST, AigErrorClassEnum.POLICY_DENIED,
                AigErrorClassEnum.APPROVAL_REQUIRED, AigErrorClassEnum.ARTIFACT_INVALID,
                AigErrorClassEnum.TOOL_FAILED, AigErrorClassEnum.SECURITY_QUARANTINE), noFallback,
            "入参错误与策略拒绝换候选只是把同一个失败乘以候选数；"
                + "需要调用审批同理——换一家 Provider 一样要审批，顺延只会多烧几次调用；"
                + "V2 追加的三类也不该换：制品校验失败（换模型同样产出坏制品——问题在制品不在模型）、"
                + "工具调用失败（工具不是 Provider，换模型解决不了工具挂）、"
                + "安全隔离（换一家等于把可疑输入再送一处）");
    }

    @Test
    @DisplayName("「重试」与「换候选」是两维：OUTPUT_UNPARSABLE 不重试但换候选，UNKNOWN 也不重试但换候选")
    void retryAndFallbackAreIndependentDimensions() {
        // 同样的输入与提示词，重试只会得到同样的输出 —— 但换个模型可能就合格了
        assertFalse(AigErrorClassEnum.OUTPUT_UNPARSABLE.isRetryable());
        assertTrue(AigErrorClassEnum.OUTPUT_UNPARSABLE.isWorthFallback());

        // 认不出的错误向同一个 Provider 再要一次要保守，但去另一个已过审的 Provider 试一次是 fallback 的意义
        assertFalse(AigErrorClassEnum.UNKNOWN.isRetryable());
        assertTrue(AigErrorClassEnum.UNKNOWN.isWorthFallback(),
            "UNKNOWN 若也不换候选，备选模型就永远不会被用到");

        // 鉴权失败：不重试同一家（只会把账号打到风控），但换一家往往立刻可用
        assertFalse(AigErrorClassEnum.AUTH_FAILED.isRetryable());
        assertTrue(AigErrorClassEnum.AUTH_FAILED.isWorthFallback());
        assertTrue(AigErrorClassEnum.AUTH_FAILED.isCircuitBreak());
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
