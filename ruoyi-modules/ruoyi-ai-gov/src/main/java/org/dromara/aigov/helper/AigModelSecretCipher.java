package org.dromara.aigov.helper;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.SM4;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigModelCryptoProperties;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 模型密钥加解密工具（与 snail-ai 的 {@code CryptoHelper} 逐字节对齐）。
 *
 * <p><b>算法口径</b>：{@code SM4 / CBC / PKCS5Padding}，key 与 iv 均为十六进制字符串
 * 解码后的 16 字节。此处刻意与 snail-ai 的实现保持一致，任何偏差都会导致
 * snail-ai 运行时解密失败（表现为模型调用报错，而非启动报错，排查成本极高），
 * 因此本类在配置就绪时会在启动阶段做一次「加密→解密」自检并<b>快速失败</b>。</p>
 *
 * <p><b>「能不能加解密」与「能不能写入」是两件事</b>：</p>
 * <ul>
 *     <li>只要配了 {@code secret-key} / {@code iv}，治理层就具备加解密能力——
 *         连通性测试要用它解出真实凭据；</li>
 *     <li>{@code aigov.model-crypto.enabled=true} 才额外允许治理台<b>写入</b>密钥。</li>
 * </ul>
 * <p>刻意不做成同一个开关：否则「密钥只在 snail-ai 管理端配置」的部署，
 * 为了用连通性测试就得打开治理台的写入能力——为了读而开写，是个说不通的口子。</p>
 *
 * <p><b>密钥一致性对账</b>：{@link #fingerprint()} 输出 key+iv 的 SHA-256 前 12 位，
 * 启动时会打印。治理层与 snail-ai 两侧日志中的该值应当相同；不同即说明配置不一致，
 * 此时写入的密文 snail-ai 解不开。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigModelSecretCipher {

    /**
     * 自检探针明文。仅用于验证加解密闭环，不写入任何存储。
     */
    private static final String SELF_CHECK_PROBE = "aigov-secret-self-check";

    /**
     * SM4 密钥字节长度（128 位）。
     */
    private static final int KEY_BYTES = 16;

    private final AigModelCryptoProperties properties;

    /**
     * 实际的 SM4 实例；未配置 secret-key/iv 时为 null（即不具备加解密能力）。
     * <p>hutool 的 {@code SymmetricCrypto} 内部对 cipher 加锁，实例可安全共享。</p>
     */
    private SM4 sm4;

    /**
     * 启动自检：参数齐全时校验合法性并验证加解密闭环。
     *
     * <p>校验失败直接抛异常终止启动——宁可起不来，也不要让「密钥写进去但 snail-ai 解不开」
     * 这种故障在运行期才暴露。</p>
     */
    @PostConstruct
    public void init() {
        boolean hasKey = StringUtils.isNotBlank(properties.getSecretKey());
        boolean hasIv = StringUtils.isNotBlank(properties.getIv());
        if (!hasKey && !hasIv) {
            if (properties.isEnabled()) {
                // enabled=true 却没给参数：配置矛盾，启动即失败，避免运行期才报「无法写入」
                throw new IllegalStateException(
                    "aigov.model-crypto.enabled=true 但未配置 secret-key/iv；"
                        + "请填入与 snail-ai 的 snail-ai.crypto 一致的十六进制值");
            }
            log.info("模型密钥加解密未配置（aigov.model-crypto 未设置）：密钥只在 snail-ai 管理端配置，"
                + "治理台的密钥录入与连通性测试解密均不可用");
            return;
        }
        byte[] key = decodeHexOrFail(properties.getSecretKey(), "aigov.model-crypto.secret-key");
        byte[] iv = decodeHexOrFail(properties.getIv(), "aigov.model-crypto.iv");
        this.sm4 = new SM4(Mode.CBC, Padding.PKCS5Padding, key, iv);
        // 自检：能加能解，才能保证写出的密文是 snail-ai 读得懂的形态
        String cipher = sm4.encryptBase64(SELF_CHECK_PROBE);
        String plain = sm4.decryptStr(cipher);
        if (!SELF_CHECK_PROBE.equals(plain)) {
            throw new IllegalStateException("模型密钥加解密自检失败：请检查 aigov.model-crypto 配置");
        }
        log.info("模型密钥加解密已就绪（SM4/CBC/PKCS5Padding），写入={}，配置指纹 fingerprint={}；"
                + "请与 snail-ai 侧日志比对，不一致则密钥密文无法被解密",
            properties.isEnabled() ? "已启用" : "未启用（仅可解密）", fingerprint());
    }

    /**
     * 治理层当前是否具备密钥加解密能力（取决于是否配置了 secret-key/iv）。
     *
     * @return 是否可加解密
     */
    public boolean isAvailable() {
        return sm4 != null;
    }

    /**
     * 治理层当前是否允许写入（录入/清除）模型密钥。
     *
     * @return 是否允许写入
     */
    public boolean isWriteEnabled() {
        return properties.isEnabled() && sm4 != null;
    }

    /**
     * 加密明文密钥。
     *
     * @param plainApiKey 明文密钥；空值返回 {@code null}（表示「不写入」）
     * @return Base64 密文，或 null
     */
    public String encrypt(String plainApiKey) {
        if (StringUtils.isBlank(plainApiKey)) {
            return null;
        }
        if (!isWriteEnabled()) {
            throw new ServiceException(
                "模型密钥录入未启用，无法写入密钥（请配置 aigov.model-crypto.enabled=true，"
                    + "并填入与 snail-ai 的 snail-ai.crypto 一致的 secret-key/iv）");
        }
        return sm4.encryptBase64(plainApiKey);
    }

    /**
     * 解密 {@code sai_model_config.api_key} 里的密文，用于「拿真实凭据发起一次探测」。
     *
     * <p><b>失败必须是可读的</b>：密钥解不开的原因只有两类——两端 crypto 参数不一致，
     * 或者该列被手工改成了明文。这两种情况都会让 {@code decryptStr} 抛异常，
     * 而异常原文可能带上密文；因此这里统一换成一句可操作的提示，
     * <b>绝不把密文或底层异常消息带出去</b>。</p>
     *
     * @param cipherText 库中的 Base64 密文；空值返回 {@code null}
     * @return 明文密钥，或 null
     */
    public String decrypt(String cipherText) {
        if (StringUtils.isBlank(cipherText)) {
            return null;
        }
        if (!isAvailable()) {
            throw new ServiceException(
                "模型已配置密钥，但治理层未配置密钥加解密（aigov.model-crypto.secret-key/iv 未设置），"
                    + "无法解密后使用；请填入与 snail-ai 的 snail-ai.crypto 一致的十六进制值");
        }
        try {
            return sm4.decryptStr(cipherText);
        } catch (Exception e) {
            // 只带异常类型，不带 message（hutool 的报错里可能回显输入密文）
            log.warn("模型密钥解密失败（类型 {}）：请核对 aigov.model-crypto 与 snail-ai 的 crypto 参数是否一致，"
                + "以及该列是否被手工改成了明文", e.getClass().getSimpleName());
            throw new ServiceException(
                "模型密钥解密失败：请确认 aigov.model-crypto 的 secret-key/iv 与 snail-ai 的 "
                    + "snail-ai.crypto 完全一致，且该列未被手工改成明文");
        }
    }

    /**
     * 配置指纹：key+iv 的 SHA-256 前 12 位十六进制。
     * <p>只用于两侧日志人工比对，不泄漏密钥本身。</p>
     *
     * @return 指纹字符串；未配置时返回 {@code "-"}
     */
    public String fingerprint() {
        if (StringUtils.isBlank(properties.getSecretKey()) || StringUtils.isBlank(properties.getIv())) {
            return "-";
        }
        return StringUtils.substring(DigestUtil.sha256Hex(properties.getSecretKey() + ":" + properties.getIv()), 0, 12);
    }

    /**
     * 十六进制解码，失败时给出可操作的报错。
     *
     * @param value 十六进制字符串
     * @param name  配置项名（用于报错定位）
     * @return 解码后的字节数组
     */
    private byte[] decodeHexOrFail(String value, String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("模型密钥录入已启用，但缺少配置：" + name);
        }
        byte[] bytes;
        try {
            bytes = HexUtil.decodeHex(value.trim());
        } catch (Exception e) {
            throw new IllegalStateException(name + " 必须是十六进制字符串（32 个 hex 字符 = 16 字节）", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(name + " 解码后应为 " + KEY_BYTES + " 字节，实际 " + bytes.length
                + " 字节；请与 snail-ai 的 snail-ai.crypto 配置保持一致");
        }
        return bytes;
    }

}
