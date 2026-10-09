package org.dromara.aigov.task.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.helper.AigTaskCallbackSigner;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RateLimiter;
import org.dromara.common.redis.enums.LimitType;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provider 回调入口（{@code POST /aigov/task/callback}）。
 *
 * <h3>它补的是什么</h3>
 * <p>回调链路的每一环（验签、幂等账本、定位任务、状态机推进、错误归类）此前都已实现并有测试，
 * 但<b>没有 HTTP 入口</b>：{@code handleCallback} 只有服务与接口两处调用者。
 * 也就是说，任何走 HTTP 的外部执行面（ComfyUI 异步作业、渲染服务、外部 Agent）
 * 都不可能把结果送回来——那个能力在代码里存在，在现实里不存在。</p>
 *
 * <h3>为什么签名与 Provider 编码走请求头</h3>
 * <p>验签是对<b>原始请求体字节</b>算 HMAC（任何重新序列化都会让签名对不上），
 * 所以请求体必须原封不动地交给服务层。把签名放进请求体就意味着「先解析、再取签名、
 * 再拿原始串去验」，多一步就多一次被改写的机会。因此三个头：
 * {@code X-Hotter-Provider} / {@code X-Hotter-Signature} / {@code X-Hotter-Sign-Algorithm}，
 * 请求体只放业务载荷（契约见 {@code contract/provider-callback.schema.json}）。</p>
 *
 * <h3>为什么返回真实 HTTP 状态码，而不是平台惯用的「200 + body 里的 code」</h3>
 * <p>调用方是<b>机器</b>，它按状态码决定要不要重推：签名不对（401）、任务不存在（404）、
 * 顺序过期（409）都是「再推一百次也没用」，而 200 会让对方的监控显示一切正常。
 * 平台内部面向页面的接口用 200+code 是因为消费方是人写的页面；这里消费方是有重试逻辑的执行面，
 * 所以两种约定在这里刻意不同。</p>
 *
 * <h3>三个必须一起成立的部署前提（有守卫测试盯着，见 {@code AigTaskCallbackTransportGuardTest}）</h3>
 * <ol>
 *     <li>{@code security.excludes} 里要有本路径——否则 Sa-Token 拦截器先要求登录，
 *         回调会在进入本方法前就以「未登录」失败；</li>
 *     <li>{@code xss.excludeUrls} 里要有本路径——XSS 过滤器注册在 {@code /*} 上，
 *         它会重写请求体，导致<b>所有签名都对不上</b>（这类故障排查起来极费时间：
 *         签名算法、密钥、工具都没问题，只是字节被改过）；</li>
 *     <li>调用方密钥要配（{@code aigov.callback.secrets.<provider>}）——没配就拒绝，
 *         这是验签器的既定口径（「不配也能用」等于这个机制不存在）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/task/callback")
public class AigTaskCallbackController extends BaseController {

    /**
     * Provider 编码请求头。
     */
    public static final String HEADER_PROVIDER = "X-Hotter-Provider";

    /**
     * 签名请求头（十六进制或 Base64 的 HMAC-SHA256）。
     */
    public static final String HEADER_SIGNATURE = "X-Hotter-Signature";

    /**
     * 签名算法请求头（可空，默认按 HMAC-SHA256 处理）。
     */
    public static final String HEADER_ALGORITHM = "X-Hotter-Sign-Algorithm";

    /**
     * 回调频率上限（每分钟每 IP）。
     *
     * <p><b>为什么必须限流</b>：这是唯一一个不需要登录态就能到达的写路径，而
     * <b>未验签的回调也会写一行账本</b>（那是刻意的：对方乱推、密钥配错、有人伪造，
     * 三种情况的处置完全不同，不记就无从判断）。没有限流时，任何人用它刷
     * {@code aig_callback} 表就能把磁盘写满——2026-10-08 那次「磁盘写满 → 容器起不来 →
     * 回滚也失败」的事故说明这条线的代价有多高。120/分钟足够真实执行面（进度回执
     * 按秒推也够），而对刷表的人是硬上限。</p>
     */
    private static final int CALLBACK_PER_MINUTE = 120;

    private final IAigTaskService taskService;
    private final JsonMapper jsonMapper;

    /**
     * 接收一次 Provider 回调。
     *
     * @param rawBody      原始请求体（<b>原样交给验签</b>，不解析后再序列化）
     * @param providerCode Provider 编码（请求头，必填）
     * @param signature    签名（请求头，必填）
     * @param algorithm    签名算法（请求头，可空）
     * @return 处理结果（HTTP 状态码表达「要不要重推」）
     */
    @RateLimiter(time = 60, count = CALLBACK_PER_MINUTE, limitType = LimitType.IP)
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<R<AigCallbackVo>> receive(
        @RequestBody(required = false) String rawBody,
        @RequestHeader(value = HEADER_PROVIDER, required = false) String providerCode,
        @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature,
        @RequestHeader(value = HEADER_ALGORITHM, required = false) String algorithm) {

        if (providerCode == null || providerCode.isBlank()) {
            // 没有 Provider 编码就无法确定验签密钥，也无法记账（账本的 provider_code 是 NOT NULL）。
            // 这种请求连「一次回调尝试」都算不上，因此不写账本——否则任何人都能用裸 POST 刷表
            return ResponseEntity.badRequest().body(R.fail("缺少请求头 " + HEADER_PROVIDER
                + "：无法确定验签密钥，也无法登记这条回调"));
        }

        AigTaskCallbackBo bo = new AigTaskCallbackBo();
        bo.setProviderCode(providerCode.trim());
        bo.setSignature(signature);
        bo.setSignAlgorithm(algorithm);
        bo.setRawPayload(rawBody);

        String parseError = fillBusinessFields(bo, rawBody);
        if (parseError != null) {
            // 载荷不合法时**不进服务层**（同上：不写账本，避免裸 POST 刷表），但日志留痕，
            // 便于区分「对接方发错了」与「有人在乱打」
            log.warn("回调载荷无法解析: provider={}, 原因={}", providerCode, parseError);
            return ResponseEntity.badRequest().body(R.fail("回调载荷不是合法的平台回调契约（"
                + parseError + "）：请对照 docs/platform-v2/contract/provider-callback.schema.json"));
        }

        AigCallbackVo vo = taskService.handleCallback(bo);
        return ResponseEntity.status(httpStatusOf(vo)).body(R.ok(vo));
    }

    /**
     * 从请求体里取出业务字段（<b>不改变 rawPayload</b>）。
     *
     * <p>解析的是同一串字节的副本：验签用的是 {@code rawPayload} 原文，
     * 所以「解析一次」不会影响验签结论。字段缺失不在这里报错——它们由服务层按
     * 「未知目标状态 → 顺序过期」处理并记账，那才是能留下证据的路径。</p>
     *
     * @param bo      待填充的回调入参
     * @param rawBody 原始请求体（可空）
     * @return 解析失败原因；成功返回 null
     */
    private String fillBusinessFields(AigTaskCallbackBo bo, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "请求体为空";
        }
        JsonNode node;
        try {
            node = jsonMapper.readTree(rawBody);
        } catch (Exception e) {
            return "不是合法 JSON（" + e.getClass().getSimpleName() + "）";
        }
        if (node == null || !node.isObject()) {
            return "顶层必须是 JSON 对象";
        }
        bo.setEventId(text(node, "eventId"));
        bo.setProviderJobId(text(node, "providerJobId"));
        bo.setToStatus(text(node, "toStatus"));
        bo.setErrorCode(text(node, "errorCode"));
        bo.setDetail(text(node, "detail"));
        JsonNode progress = node.get("progress");
        if (progress != null && progress.isNumber()) {
            bo.setProgress(progress.asInt());
        } else if (progress != null && !progress.isNull()) {
            return "progress 必须是 0-100 的整数";
        }
        return null;
    }

    /**
     * 取字符串字段（null/缺失返回 null）。
     *
     * @param node 对象节点
     * @param name 字段名
     * @return 字段值文本；非字符串或为空时返回 null
     */
    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * 把处理结论映射成 HTTP 状态码（执行面据此决定要不要重推）。
     *
     * @param vo 处理结果
     * @return HTTP 状态
     */
    private HttpStatus httpStatusOf(AigCallbackVo vo) {
        String result = vo == null ? null : vo.getProcessResult();
        if (result == null) {
            return HttpStatus.OK;
        }
        return switch (result) {
            // 已记账且已推进 / 已处理过的重复投递：都算「收到了」，不该重推
            case "ACCEPTED", "DUPLICATE" -> HttpStatus.OK;
            // 验签相关的一切拒绝统一 401：对方要改的是密钥或签名算法，重推无用
            case "REJECTED_UNSIGNED" -> HttpStatus.UNAUTHORIZED;
            // 定位不到任务：多半是回调先于登记到达或作业号写错，重推可能有用（登记完成后）
            case "TASK_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            // 顺序过期：迟到的完成回调打在已变更的任务上，重推永远无用
            case "ORDER_STALE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.OK;
        };
    }

}
