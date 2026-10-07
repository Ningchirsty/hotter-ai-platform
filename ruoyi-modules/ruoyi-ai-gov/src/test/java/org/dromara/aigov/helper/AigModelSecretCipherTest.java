package org.dromara.aigov.helper;

import org.dromara.aigov.config.AigModelCryptoProperties;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型密钥加解密（SM4/CBC/PKCS5Padding）的真实用例。
 *
 * <p><b>为什么必须补这一类测试</b>：此前 {@link AigModelSecretCipher} 在全部单测里
 * 都只被 {@code mock(...)} 使用，真实的 SM4 路径一次都没跑过。后果是一个真实缺陷长期隐形——
 * <b>hutool-crypto 自己不实现 SM4</b>，它把 {@code SM4/CBC/PKCS5Padding} 交给 JCE，
 * 而 JDK 21 的 SunJCE 没有 SM4，需要 BouncyCastle 提供 provider；而本模块的 pom 里
 * 只有 {@code hutool-crypto}，BC 是被 ruoyi-system → ruoyi-common-encrypt 顺带带上来的。
 * 于是「本模块自己缺 provider」在本模块的构建里完全看不见，直到一次真实调用
 * （.tools/tmp/ImageLiveProbe.java）才以
 * {@code NoSuchAlgorithmException: Cannot find any provider supporting SM4/CBC/PKCS5Padding}
 * 暴露出来。若部署时裁掉 ruoyi-system，症状会更重：{@code @PostConstruct} 自检直接终止启动。</p>
 *
 * <p>因此第一个用例（{@link #initPerformsSelfCheckAndEnablesRoundTrip()}）同时是
 * **provider 是否还在 classpath 上的回归闸门**：它真的走 SM4，缺 provider 必红。</p>
 *
 * <p>纯单元测试：不加载 Spring 上下文，也不碰数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelSecretCipherTest {

    /**
     * 与 snail-ai 的 {@code snail-ai.crypto} 同形态的十六进制参数（32 hex = 16 字节）。
     * <p>测试值，非任何环境的真实密钥。</p>
     */
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String IV = "fedcba9876543210fedcba9876543210";

    /**
     * 造一份配置。
     *
     * @param enabled   是否允许写入
     * @param secretKey SM4 key（十六进制）
     * @param iv        SM4 iv（十六进制）
     * @return 配置对象
     */
    private static AigModelCryptoProperties properties(boolean enabled, String secretKey, String iv) {
        AigModelCryptoProperties properties = new AigModelCryptoProperties();
        properties.setEnabled(enabled);
        properties.setSecretKey(secretKey);
        properties.setIv(iv);
        return properties;
    }

    /**
     * 造一个已就绪的实例（已执行自检）。
     *
     * @return 加解密器
     */
    private static AigModelSecretCipher ready() {
        AigModelSecretCipher cipher = new AigModelSecretCipher(properties(true, KEY, IV));
        cipher.init();
        return cipher;
    }

    @Test
    @DisplayName("自检 + 加解密闭环：真跑 SM4（缺 JCE provider 时本用例必红），密文可解回原文且不含明文")
    void initPerformsSelfCheckAndEnablesRoundTrip() {
        AigModelSecretCipher cipher = ready();

        assertTrue(cipher.isAvailable(), "配了 key/iv 就应具备加解密能力（连通性测试要用）");
        assertTrue(cipher.isWriteEnabled(), "enabled=true 且已就绪时允许写入");

        String plain = "sk-abcdefghijklmnopqrstuvwxyz0123456789";
        String encrypted = cipher.encrypt(plain);

        assertNotNull(encrypted);
        assertTrue(isBase64(encrypted), "密文须是 base64（与 snail-ai 的 CryptoHelper 同形态）：" + encrypted);
        assertFalse(encrypted.contains(plain), "密文里绝不能出现明文");
        assertFalse(encrypted.contains("sk-"), "密文里连明文前缀都不该出现");
        assertEquals(plain, cipher.decrypt(encrypted), "解回来必须与原文完全一致");
    }

    @Test
    @DisplayName("固定 IV 下加密是确定性的——这正是「批量配密钥只加密一次再复用」的依据")
    void encryptionIsDeterministicWithFixedIv() {
        AigModelSecretCipher cipher = ready();

        String first = cipher.encrypt("sk-same-key");
        String second = cipher.encrypt("sk-same-key");

        assertEquals(first, second,
            "SM4/CBC 固定 IV 下同一明文必得同一密文；批量录入依赖这条等价性"
                + "（加密一次·复用到该供应商全部模型 == 逐行各加密一次）。若此断言变红，"
                + "批量写入的等价性论证即失效，必须重新论证或改为逐行加密");
    }

    @Test
    @DisplayName("enabled=true 却没给 key/iv：启动即失败（宁可起不来，也不要运行期才发现写不进）")
    void rejectsContradictoryConfigAtStartup() {
        AigModelSecretCipher cipher = new AigModelSecretCipher(properties(true, null, null));

        IllegalStateException error = assertThrows(IllegalStateException.class, cipher::init);
        assertTrue(error.getMessage().contains("secret-key"),
            "报错要指到具体配置项，否则运维只能靠猜：" + error.getMessage());
    }

    @Test
    @DisplayName("未配置 key/iv 且 enabled=false：不报错、不可写入，encrypt 拒绝而 decrypt 空值返回 null")
    void unConfiguredIsUsableForReadButNotForWrite() {
        AigModelSecretCipher cipher = new AigModelSecretCipher(properties(false, null, null));
        cipher.init();

        assertFalse(cipher.isAvailable(), "没配 key/iv 就没有加解密能力");
        assertFalse(cipher.isWriteEnabled());
        assertEquals("-", cipher.fingerprint(), "未配置时指纹为占位符，避免日志里出现 null");
        assertNull(cipher.encrypt(null), "空值表示「不写入」，不该抛异常");
        assertNull(cipher.encrypt("   "));
        assertNull(cipher.decrypt(null), "空密文直接返回 null");

        ServiceException error = assertThrows(ServiceException.class, () -> cipher.encrypt("sk-x"));
        assertTrue(error.getMessage().contains("未启用"),
            "写不进去时必须明说「未启用」，而不是抛底层 NPE：" + error.getMessage());
        ServiceException noCipher = assertThrows(ServiceException.class, () -> cipher.decrypt("anything"));
        assertTrue(noCipher.getMessage().contains("解密"),
            "有密文却无法解密时，提示要指向 crypto 配置：" + noCipher.getMessage());
    }

    @Test
    @DisplayName("key/iv 非法（非十六进制、长度不对）时报错必须说清期望形态")
    void rejectsMalformedKeyOrIv() {
        AigModelSecretCipher notHex = new AigModelSecretCipher(properties(true, "zzzz", IV));
        assertTrue(assertThrows(IllegalStateException.class, notHex::init).getMessage().contains("十六进制"));

        AigModelSecretCipher wrongLength =
            new AigModelSecretCipher(properties(true, "0123456789abcdef", IV));
        String lengthMessage = assertThrows(IllegalStateException.class, wrongLength::init).getMessage();
        assertTrue(lengthMessage.contains("字节"), "长度不对要说清应是多少字节：" + lengthMessage);
    }

    @Test
    @DisplayName("解密失败时绝不把密文或底层异常原文带出去（密文可能是真实凭据的一部分）")
    void decryptFailureNeverLeaksCipherText() {
        AigModelSecretCipher cipher = ready();
        String garbage = Base64.getEncoder().encodeToString("not-a-real-cipher-text".getBytes());

        ServiceException error = assertThrows(ServiceException.class, () -> cipher.decrypt(garbage));

        assertFalse(error.getMessage().contains(garbage), "提示里不能出现密文：" + error.getMessage());
        assertTrue(error.getMessage().contains("snail-ai"),
            "提示要指向两侧参数一致性这个可操作的动作：" + error.getMessage());
    }

    @Test
    @DisplayName("指纹随 key/iv 变化，且相同配置稳定——两侧日志靠它比对是否一致")
    void fingerprintIdentifiesConfiguration() {
        String first = ready().fingerprint();

        assertEquals(first, ready().fingerprint(), "同一配置的指纹必须稳定");
        assertEquals(12, first.length(), "指纹取 SHA-256 前 12 位");
        assertFalse(first.contains(KEY), "指纹不得泄漏密钥");
        assertNotEquals(first, new AigModelSecretCipher(properties(true, KEY, KEY)).fingerprint(),
            "iv 不同即配置不同，指纹必须能区分");
    }

    /**
     * 判断字符串是否为合法 Base64。
     *
     * @param value 待判断字符串
     * @return 合法则 true
     */
    private static boolean isBase64(String value) {
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
