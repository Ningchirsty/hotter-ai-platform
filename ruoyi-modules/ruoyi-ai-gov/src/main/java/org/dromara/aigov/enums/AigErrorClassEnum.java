package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Provider 调用错误分类。
 *
 * <p><b>为什么需要它</b>：在这之前，调用失败只有一个「失败」——{@code errorSummary} 是一段
 * 给人看的文本，机器读不懂。于是三种本该完全不同的处置被混成一种：</p>
 * <ul>
 *     <li><b>参数/Schema 错</b>：重试一百次也是同样的错，必须转人工补正确的输入；</li>
 *     <li><b>限流/超时</b>：等一会儿重试就能过，不该打扰人；</li>
 *     <li><b>鉴权失败</b>：密钥错了，继续调用只会把账号打到风控，必须<b>立刻熔断</b>并告警。</li>
 * </ul>
 * <p>把这三类分开，是「默认最多自动重试 3 次」这条规则能落地的前提——
 * 否则只能无差别重试，既浪费预算又把可恢复的错误和不可恢复的错误一起堆到人面前。</p>
 *
 * <p><b>判定顺序</b>（{@link #classify}）：调用器上报的结构化 {@code errorCode} →
 * HTTP 状态码 → 错误文本兜底。前两者可靠，第三者是给拿不到状态码的链路
 * （如经 snail-ai 转发的调用）兜底的，因此刻意保守：**认不出就算 UNKNOWN，
 * 不猜「大概是限流吧」**——猜错会让不可重试的错误被反复重试。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigErrorClassEnum {

    /**
     * 入参/输出 Schema 错误：不重试，转人工补正确输入（设计 §13.3）
     */
    INVALID_REQUEST("INVALID_REQUEST", "参数或输出Schema错误", false, true, false),

    /**
     * 权限、数据等级或外发策略拒绝：不重试，转人工走审批或改用本地 Provider（设计 §13.3）
     */
    POLICY_DENIED("POLICY_DENIED", "权限或外发策略拒绝", false, true, false),

    /**
     * 限流：指数退避后可重试
     */
    RATE_LIMITED("RATE_LIMITED", "被限流", true, false, false),

    /**
     * 超时：退避后可重试
     */
    TIMEOUT("TIMEOUT", "调用超时", true, false, false),

    /**
     * 鉴权失败：不重试，**立即熔断**该 Provider 并告警（设计 §13.3）
     */
    AUTH_FAILED("AUTH_FAILED", "鉴权失败", false, false, true),

    /**
     * 服务不可用/网络不通：可重试，达到上限后转人工
     */
    UNAVAILABLE("UNAVAILABLE", "服务不可用", true, false, false),

    /**
     * 结果不可解析：不重试，只保存摘要并标记失败，**不得写入正式业务字段**（设计 §13.3）
     */
    OUTPUT_UNPARSABLE("OUTPUT_UNPARSABLE", "结果不可解析", false, true, false),

    /**
     * 未能归类：保守处理——不重试、不自动转人工（保持既有「失败即失败」的行为）
     */
    UNKNOWN("UNKNOWN", "未归类的错误", false, false, false);

    /**
     * 编码（入库与日志口径）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 是否允许自动重试
     */
    private final boolean retryable;
    /**
     * 是否应转人工（NEED_HUMAN）
     */
    private final boolean needsHuman;
    /**
     * 是否应立即熔断该 Provider
     */
    private final boolean circuitBreak;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigErrorClassEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigErrorClassEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 归类一次调用失败。
     *
     * <p>顺序：结构化 errorCode → HTTP 状态码 → 文本兜底。认不出返回 {@link #UNKNOWN}。</p>
     *
     * @param errorCode 调用器上报的错误码（可为 null）
     * @param httpStatus HTTP 状态码（可为 null）
     * @param message   错误文本（可为 null）
     * @return 错误分类，恒不为 null
     */
    public static AigErrorClassEnum classify(String errorCode, Integer httpStatus, String message) {
        // 1. 调用器直接上报本枚举的编码（最可靠：调用器最清楚自己遇到了什么）
        AigErrorClassEnum byCode = find(errorCode);
        if (byCode != null) {
            return byCode;
        }
        // 2. HTTP 状态码（直连类调用器拿得到）
        if (httpStatus != null) {
            if (httpStatus == 401 || httpStatus == 403) {
                return AUTH_FAILED;
            }
            if (httpStatus == 429) {
                return RATE_LIMITED;
            }
            if (httpStatus == 408 || httpStatus == 504) {
                return TIMEOUT;
            }
            if (httpStatus >= 500) {
                return UNAVAILABLE;
            }
            if (httpStatus == 400 || httpStatus == 422) {
                return INVALID_REQUEST;
            }
        }
        // 3. 文本兜底（拿不到状态码的链路）
        if (message == null || message.isBlank()) {
            return UNKNOWN;
        }
        String text = message.toLowerCase();
        // 鉴权/密钥类：先判，因为「api key 无效」里也可能出现 400
        if (containsAny(text, "unauthorized", "forbidden", "invalid api key", "api key", "鉴权", "密钥", "未授权")) {
            return AUTH_FAILED;
        }
        if (containsAny(text, "rate limit", "too many requests", "限流", "quota")) {
            return RATE_LIMITED;
        }
        if (containsAny(text, "timeout", "timed out", "超时")) {
            return TIMEOUT;
        }
        if (containsAny(text, "不符合schema", "不符合 schema", "schema", "json 解析", "解析失败", "不可解析")) {
            return INVALID_REQUEST;
        }
        if (containsAny(text, "unreachable", "connection refused", "connect timed", "不可达", "服务不可用", "无法连接")) {
            return UNAVAILABLE;
        }
        return UNKNOWN;
    }

    /**
     * 文本是否包含任一关键字。
     *
     * @param text      已转小写的文本
     * @param keywords  关键字
     * @return 命中任一返回 true
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
