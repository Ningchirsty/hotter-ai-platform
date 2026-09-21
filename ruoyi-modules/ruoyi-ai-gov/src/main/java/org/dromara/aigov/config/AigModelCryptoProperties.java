package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型密钥加密配置（治理层侧的 SM4 参数）。
 *
 * <p><b>为什么治理层需要这份配置</b>：{@code sai_model_config.api_key} 是 SM4 密文列，
 * 由 snail-ai 在运行时解密使用（{@code com.aizuda.snail.ai.model.crypto.CryptoHelper}）。
 * 治理层既要能按<b>完全相同</b>的算法产出密文（否则 snail-ai 解密失败、模型静默不可用），
 * 也要能解出明文——连通性测试得拿真实凭据才能发得起一次探测请求。</p>
 *
 * <p><b>参数必须与 snail-ai 保持一致</b>（见 snail-ai 的 {@code application.yml}）：</p>
 * <ul>
 *     <li>{@code snail-ai.crypto.secret-key} → 本类 {@link #secretKey}</li>
 *     <li>{@code snail-ai.crypto.iv} → 本类 {@link #iv}</li>
 * </ul>
 * <p>算法固定为 {@code SM4/CBC/PKCS5Padding}，两参数均为<b>十六进制字符串</b>
 * （32 个 hex 字符 = 16 字节），与 snail-ai 的 {@code HexUtil.decodeHex} 对齐。</p>
 *
 * <p><b>{@code enabled} 只控制「能不能写入」</b>：默认 false 时治理台拒绝录入/清除密钥，
 * 保持「密钥只在 snail-ai 管理端配置」的原口径；而只要填了 {@link #secretKey} / {@link #iv}，
 * 治理层就具备加解密能力（连通性测试要用）。两者刻意分开——否则「密钥只在 snail-ai 配置」
 * 的部署为了用连通性测试，就得打开治理台的写入能力，为了读而开写并不合理。</p>
 *
 * <p>启用任一能力前请先确认这两项与 snail-ai 完全一致，可用两侧启动日志中的
 * {@code fingerprint} 比对。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.model-crypto")
public class AigModelCryptoProperties {

    /**
     * 是否允许治理层<b>写入</b>（录入/清除）模型密钥。
     * <p>默认 <b>false</b>：关闭时 {@code PUT /aigov/model/secret} 一律拒绝，
     * 密钥仍需在 snail-ai 管理端配置。</p>
     * <p>注意它<b>不</b>控制解密：只要配了 {@link #secretKey} / {@link #iv}，
     * 连通性测试就能解密使用既有密钥。详见类注释。</p>
     */
    private boolean enabled = false;

    /**
     * SM4 密钥（十六进制，32 字符 = 16 字节）。
     * <p>必须与 snail-ai 的 {@code snail-ai.crypto.secret-key} 一致。</p>
     */
    private String secretKey;

    /**
     * SM4 初始向量 IV（十六进制，32 字符 = 16 字节）。
     * <p>必须与 snail-ai 的 {@code snail-ai.crypto.iv} 一致。</p>
     */
    private String iv;

}
