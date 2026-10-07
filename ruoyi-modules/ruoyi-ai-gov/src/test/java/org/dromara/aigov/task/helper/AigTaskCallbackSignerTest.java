package org.dromara.aigov.task.helper;

import org.dromara.aigov.task.config.AigCallbackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回调验签器行为测试。
 *
 * <p><b>为什么这段必须逐条测</b>：验签是回调链路上唯一能把「对方发的」与
 * 「别人发的」区分开的手段——回调端点必然对外可达，只要猜到一个 {@code provider_job_id}
 * 就能伪造「任务已完成」并塞入任意结果。而验签的错误方向有两种，且都静默：</p>
 * <ul>
 *     <li><b>漏拒</b>：把不该放的放进来（未签名、算法被换成 none、未配密钥时「跳过验签」）；</li>
 *     <li><b>误拒</b>：把真回调挡在外面（载荷被重新序列化、签名用 Base64 而非十六进制）。
 *         误拒同样严重——任务永远收不到结果，而对方只会看到「一直失败」。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskCallbackSignerTest {

    private static final String PROVIDER = "bluocto";
    private static final String SECRET = "unit-test-secret";
    private static final String PAYLOAD = "{\"jobId\":\"j-1\",\"status\":\"finished\",\"n\":1}";

    private AigCallbackProperties properties;
    private AigTaskCallbackSigner signer;

    @BeforeEach
    void setUp() {
        properties = new AigCallbackProperties();
        properties.getSecrets().put(PROVIDER, SECRET);
        signer = new AigTaskCallbackSigner(properties);
    }

    /**
     * 按给定密钥算 HMAC-SHA256（测试侧独立实现，避免与被测代码共用同一段逻辑而互相掩盖）。
     *
     * @param secret  密钥
     * @param payload 载荷
     * @return MAC 字节
     */
    private static byte[] hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成十六进制签名。
     *
     * @param secret  密钥
     * @param payload 载荷
     * @return 十六进制签名
     */
    private static String hexSign(String secret, String payload) throws Exception {
        byte[] mac = hmac(secret, payload);
        StringBuilder sb = new StringBuilder(mac.length * 2);
        for (byte b : mac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("正确签名（十六进制）通过")
    void acceptsValidHexSignature() throws Exception {
        AigTaskCallbackSigner.VerifyResult result =
            signer.verify(PROVIDER, PAYLOAD, "HMAC-SHA256", hexSign(SECRET, PAYLOAD));

        assertTrue(result.verified(), "正确签名必须通过；实际=" + result.detail());
    }

    @Test
    @DisplayName("正确签名（Base64）也通过：各家编码习惯不同，不该逼对接方改自己的系统")
    void acceptsValidBase64Signature() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(hmac(SECRET, PAYLOAD));

        AigTaskCallbackSigner.VerifyResult result = signer.verify(PROVIDER, PAYLOAD, "HMAC-SHA256", base64);

        assertTrue(result.verified(), "Base64 签名同样是同一个 MAC 字节，不该被拒；实际=" + result.detail());
    }

    @Test
    @DisplayName("算法大小写与缺省都接受（声明为空视为 HMAC-SHA256）")
    void acceptsAlgorithmCaseAndBlank() throws Exception {
        String signature = hexSign(SECRET, PAYLOAD);

        assertTrue(signer.verify(PROVIDER, PAYLOAD, "hmac-sha256", signature).verified(), "算法名大小写不敏感");
        assertTrue(signer.verify(PROVIDER, PAYLOAD, null, signature).verified(), "未声明算法按 HMAC-SHA256");
    }

    @Test
    @DisplayName("未配置密钥的 Provider 一律拒绝——不是「跳过验签」")
    void rejectsProviderWithoutSecret() {
        AigTaskCallbackSigner.VerifyResult result = signer.verify("unknown-provider", PAYLOAD, null, "whatever");

        assertFalse(result.verified(), "未配密钥必须拒绝");
        assertTrue(result.detail().contains("未配置"), "原因要与「签名不匹配」区分开：前者改配置，后者可能是攻击；"
            + "实际=" + result.detail());
    }

    @Test
    @DisplayName("缺少签名一律拒绝（未签名回调不得推进状态）")
    void rejectsMissingSignature() {
        assertFalse(signer.verify(PROVIDER, PAYLOAD, null, null).verified());
        assertFalse(signer.verify(PROVIDER, PAYLOAD, null, "   ").verified());
    }

    @Test
    @DisplayName("不支持的算法一律拒绝，而不是「换我们支持的算法再试一遍」")
    void rejectsUnsupportedAlgorithm() throws Exception {
        // 算法混淆攻击的经典形态：把算法字段改成 none 或更弱的一种
        AigTaskCallbackSigner.VerifyResult result =
            signer.verify(PROVIDER, PAYLOAD, "none", hexSign(SECRET, PAYLOAD));

        assertFalse(result.verified(), "声明算法不是 HMAC-SHA256 时必须拒绝");
        assertTrue(result.detail().contains("不支持的签名算法"), "实际=" + result.detail());
    }

    @Test
    @DisplayName("签名不匹配一律拒绝")
    void rejectsWrongSignature() throws Exception {
        AigTaskCallbackSigner.VerifyResult result =
            signer.verify(PROVIDER, PAYLOAD, "HMAC-SHA256", hexSign("another-secret", PAYLOAD));

        assertFalse(result.verified(), "用别的密钥算出的签名必须被拒");
        assertTrue(result.detail().contains("不匹配"), "实际=" + result.detail());
    }

    @Test
    @DisplayName("载荷被改一个字节即验签失败——这正是验签存在的意义")
    void rejectsTamperedPayload() throws Exception {
        String signature = hexSign(SECRET, PAYLOAD);
        String tampered = PAYLOAD.replace("\"n\":1", "\"n\":2");

        assertFalse(signer.verify(PROVIDER, tampered, "HMAC-SHA256", signature).verified(),
            "载荷被改动后签名必须失效（否则验签等于没做）");
    }

    @Test
    @DisplayName("载荷为空或 Provider 编码缺失时拒绝，且给出可读原因")
    void rejectsBlankInputs() {
        assertFalse(signer.verify(PROVIDER, "", null, "abc").verified(), "载荷为空无法验签");
        assertFalse(signer.verify(null, PAYLOAD, null, "abc").verified(), "Provider 编码缺失时无法确定密钥");
        assertNotNull(signer.verify("", PAYLOAD, null, "abc").detail(), "拒绝也必须给原因");
    }

    @Test
    @DisplayName("签名格式完全不认识时拒绝，而不是当成「空字节数组」比较")
    void rejectsUnrecognizableSignatureFormat() {
        AigTaskCallbackSigner.VerifyResult result = signer.verify(PROVIDER, PAYLOAD, "HMAC-SHA256", "!!!not-a-signature!!!");

        assertFalse(result.verified(), "无法解析的签名必须拒绝");
        assertTrue(result.detail().contains("格式"), "实际=" + result.detail());
    }

    @Test
    @DisplayName("signHex 供对接方自检：与手工计算的 HMAC 一致")
    void signHexMatchesManualComputation() throws Exception {
        String produced = signer.signHex(PROVIDER, PAYLOAD);

        assertNotNull(produced, "已配置密钥时应能算出签名");
        assertTrue(produced.equalsIgnoreCase(hexSign(SECRET, PAYLOAD)),
            "自检工具算出的值必须与对接方手算一致，否则对接时无法定位问题");
    }

}
