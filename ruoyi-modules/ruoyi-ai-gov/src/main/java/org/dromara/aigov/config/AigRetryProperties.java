package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 调用失败自动重试配置。
 *
 * <p><b>只对可重试的错误分类生效</b>（限流/超时/服务不可用），
 * 判定见 {@code AigErrorClassEnum.isRetryable()}。参数错误、权限拒绝、结果不可解析、
 * 鉴权失败一律不重试：前几类重试多少次都是同样的结果，鉴权失败继续调用只会把账号打到风控。</p>
 *
 * <p><b>为什么默认开启</b>：限流与瞬时 5xx 是外部模型接入里最常见的失败，
 * 不重试就要人手工再点一次，而这类错误恰恰是「等一下就好」的。
 * 需要临时关停外呼排查时把它设为 false 即可。</p>
 *
 * <p><b>尝试次数口径</b>：设计文档写的是「最大自动重试次数为 3」。
 * 本实现采用 <b>总尝试次数 = 3（含首次调用）</b>，即最多重试 2 次。
 * 这样与创作域既有的 {@code MAX_ATTEMPTS_PER_SCREEN=3} 同名同义，
 * 避免同一平台里两处「3」表示不同次数——那种歧义一旦产生，
 * 事后看审计日志会把「配了 3 次」理解成 3 次重试还是 3 次调用全靠猜。</p>
 *
 * <p><b>幂等提醒</b>：重试只对读取型/推理型调用（对话补全类）安全。
 * 若将来把图像、视频这类「每次调用都真实消耗 GPU/计费」的能力接到这条同步链路上，
 * 必须先补上 {@code task_id + attempt_no} 幂等键，否则重试会造成重复计费。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.retry")
public class AigRetryProperties {

    /**
     * 是否启用自动重试。
     */
    private boolean enabled = true;

    /**
     * 总尝试次数（含首次调用）。最小 1（等于不重试）。
     */
    private int maxAttempts = 3;

    /**
     * 首次退避时长（毫秒）。
     */
    private long baseBackoffMs = 200L;

    /**
     * 退避时长上限（毫秒）。
     */
    private long maxBackoffMs = 2000L;

}
