package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型连通性测试配置。
 *
 * <p>为什么单独拿一个前缀：连通性测试是<b>运维动作</b>，它的超时与重试预算
 * 和业务调用（{@code aigov.snail-ai.timeout-ms}）不是一回事。混在一起会互相牵制——
 * 把业务超时调大只为了让「测试连接」好看，或者为了测试把业务超时改小，都不合理。</p>
 *
 * <p><b>为什么默认值从 8 秒提到 15 秒并有重试</b>：探测目标是跨境/网关型端点时，
 * 单次 TCP+TLS 握手偶发被丢弃很常见（实测同一台机器上同一地址既有 350ms 成功、
 * 也有 9.9s 才超时的记录）。原先固定 8 秒且不重试，会把一次网络抖动直接呈现成
 * 「地址不可达或超时」这种无法操作的结论——运维据此排查配置，方向就错了。
 * 重试只针对<b>网络层</b>失败，收到任何 HTTP 状态码都算明确结论，不再重试。</p>
 *
 * <p>可通过环境变量覆盖，例如 {@code AIGOV_MODEL_TEST_TIMEOUT_MS=30000}。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.model-test")
public class AigModelTestProperties {

    /**
     * 单次探测超时（毫秒）。
     * <p>同时作用于连接与读取。默认 15000。</p>
     */
    private int timeoutMs = 15000;

    /**
     * 网络层失败后的额外重试次数（不含首次尝试）。默认 2。
     */
    private int retries = 2;

    /**
     * 重试间隔（毫秒）。默认 500。
     */
    private long retryBackoffMs = 500L;

}
