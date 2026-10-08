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
 * <p><b>三个处置维度</b>（每条都各有 {@code boolean} 开关，不靠调用方 if-else 猜语义）：</p>
 * <ul>
 *     <li>{@link #isRetryable()}：向<b>同一个</b> Provider 再要一次（可能重复计费）；</li>
 *     <li>{@link #isWorthFallback()}：换到有序候选里的<b>下一个</b> Provider 试一次；</li>
 *     <li>{@link #isNeedsHuman()}：转人工；{@link #isCircuitBreak()}：熔断。</li>
 * </ul>
 * <p>「重试」与「换候选」是两件事，取值刻意不同：前者要保守（重复计费且大概率同样失败），
 * 后者正是 fallback 存在的意义——若认不出的错误也不换候选，备选模型永远不会被用到。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigErrorClassEnum {

    /**
     * 入参/输出 Schema 错误：不重试，转人工补正确输入（设计 §13.3）
     */
    INVALID_REQUEST("INVALID_REQUEST", "参数或输出Schema错误", false, true, false, false),

    /**
     * 权限、数据等级或外发策略拒绝：不重试，转人工走审批或改用本地 Provider（设计 §13.3）
     */
    POLICY_DENIED("POLICY_DENIED", "权限或外发策略拒绝", false, true, false, false),

    /**
     * 需要调用审批但没有有效授权（C3）。
     *
     * <p><b>为什么单独一类，而不是并进 {@link #POLICY_DENIED}</b>：两者要人做的事不同。
     * 策略拒绝是"这条路不通，改方案或改策略"；而"缺审批"是"去提一张申请单，
     * 批准后在有效期内免再审"——是流程没走，不是方案不行。</p>
     *
     * <p>处置与 {@link #POLICY_DENIED} 一致的部分：都不重试、都转人工、都不值得换候选
     * （换一家 Provider 同样要审批）。<b>不熔断</b>：这与 Provider 的健康无关，
     * 熔断会把一个流程问题记成供应商故障。</p>
     *
     * <p><b>对灰度判据的影响</b>：本类<b>不在</b>
     * {@code AigCanaryEvidence.SEVERE_CLASS_CODES} 里——"授权流程没走"不说明版本质量差。
     * 但它仍然计入失败率（调用确实失败了），因此不会把问题藏起来。</p>
     */
    APPROVAL_REQUIRED("APPROVAL_REQUIRED", "需要调用审批（无有效授权）", false, true, false, false),

    /**
     * 限流：指数退避后可重试；重试耗尽仍失败则值得换下一个候选
     */
    RATE_LIMITED("RATE_LIMITED", "被限流", true, false, false, true),

    /**
     * 超时：退避后可重试；重试耗尽仍失败则值得换下一个候选
     */
    TIMEOUT("TIMEOUT", "调用超时", true, false, false, true),

    /**
     * 鉴权失败：不重试，**立即熔断**该 Provider 并告警（设计 §13.3）。
     * 值得换候选——密钥错的是这一家，换一家往往立刻可用。
     */
    AUTH_FAILED("AUTH_FAILED", "鉴权失败", false, false, true, true),

    /**
     * 额度/余额不足：不重试、**转人工**、立即熔断该 Provider、值得换候选。
     *
     * <p><b>为什么不能并入 {@link #AUTH_FAILED}</b>：这是实测撞出来的。bluocto 账号余额为 0 时，
     * 上游回的是 <b>403</b> + 「用户额度不足, 剩余额度: ＄0.000000」——与鉴权失败走同一个状态码。
     * 两者确实都该熔断、都不该重试，但<b>要人做的事情完全不同</b>：
     * 鉴权失败要换/修密钥，额度耗尽要充值或申请预算。
     * 判成「鉴权失败」会把运维引向「是不是密钥不对」这条错路，
     * 而真正的动作在财务侧——诊断方向错了，比没有诊断更费时间。</p>
     *
     * <p>与 {@link #AUTH_FAILED} 的唯一处置差别是 {@code needsHuman=true}：
     * 额度是能被外部动作<b>恢复</b>的资源，恢复后任务重排即可继续，
     * 因此让它落到「待人工处理」这个有人盯着的池子更合适。</p>
     *
     * <p>为什么仍然熔断：额度没恢复前，对同一家继续调用必然同样失败，
     * 熔断只是把「已经确定的结果」提前，省掉无意义的往返与告警噪音。</p>
     */
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "额度或余额不足", false, true, true, true),

    /**
     * 服务不可用/网络不通：可重试，达到上限后换下一个候选
     */
    UNAVAILABLE("UNAVAILABLE", "服务不可用", true, false, false, true),

    /**
     * 结果不可解析：不重试同一模型（同样的输入与提示词，重试只会得到同样的输出），
     * 但值得换一个模型——换个模型可能就按格式回了。仍需转人工确认（设计 §13.3）。
     */
    OUTPUT_UNPARSABLE("OUTPUT_UNPARSABLE", "结果不可解析", false, true, false, true),

    /**
     * 未能归类：不重试、不自动转人工；**值得换候选**。
     *
     * <p>这里的取舍与 {@link #retryable} 刻意不同：重试是向<b>同一个</b> Provider 再要一次
     * （可能重复计费、且大概率同样失败），而换候选是去另一个<b>已通过策略审核</b>的
     * Provider 试一次。前者要保守，后者正是 fallback 存在的意义——
     * 认不出的错误若也不换候选，备选模型就永远不会被用到。</p>
     */
    UNKNOWN("UNKNOWN", "未归类的错误", false, false, false, true);

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
     * 是否值得换下一个候选（有序 fallback）
     */
    private final boolean worthFallback;

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
            // 402 Payment Required 语义唯一，不必再看文案
            if (httpStatus == 402) {
                return QUOTA_EXCEEDED;
            }
            // 401/403/429 都可能是「额度/余额」，也可能是「密钥」或「限流」：
            // 先看文案再按状态兜底。顺序很关键——把计费问题判成鉴权问题会让人去换密钥
            // （真正的动作在财务侧），判成限流问题会让系统对着一件不可能成功的事退避重试。
            if ((httpStatus == 401 || httpStatus == 403 || httpStatus == 429) && looksLikeQuota(message)) {
                return QUOTA_EXCEEDED;
            }
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
        // 额度/计费要先判：它常与 403/429 一起出现，而且「quota」这个词本身也常出现在限流文案里。
        // 判错的代价不对称——把额度判成限流会无意义地重试（烧时间与告警噪音），
        // 把限流判成额度只是少试几次并让人来看一眼。
        if (isQuotaText(text)) {
            return QUOTA_EXCEEDED;
        }
        // 鉴权/密钥类
        if (containsAny(text, "unauthorized", "forbidden", "invalid api key", "api key", "鉴权", "密钥", "未授权")) {
            return AUTH_FAILED;
        }
        // 注意这里刻意不含 "quota"：它属于额度/计费，已在上面判掉
        if (containsAny(text, "rate limit", "too many requests", "限流")) {
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
     * 文本是否含「额度/计费」特征（大小写不敏感）。
     *
     * <p>关键词取自真实上游文案与主流网关的固定错误串：实测 bluocto 返回
     * 「用户额度不足, 剩余额度: ＄0.000000」；OpenAI 系为
     * {@code insufficient_quota} / 「You exceeded your current quota」；
     * 另有 {@code payment required}、{@code credit balance} 等形态。</p>
     *
     * <p>刻意把 {@code quota} 也收进来：它在限流文案里偶有出现，但把额度误判成限流的代价
     * （对着一件不可能成功的事退避重试）大于反过来（少试几次、让人来看一眼）。</p>
     *
     * @param message 错误文本（可为 null）
     * @return 命中返回 true
     */
    private static boolean looksLikeQuota(String message) {
        return message != null && !message.isBlank() && isQuotaText(message.toLowerCase());
    }

    /**
     * 文本（已转小写）是否含额度/计费特征。
     *
     * @param text 已转小写的文本
     * @return 命中返回 true
     */
    private static boolean isQuotaText(String text) {
        return containsAny(text, "额度", "余额", "欠费", "配额", "quota", "insufficient", "balance",
            "billing", "payment required", "no credit", "out of credit");
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
