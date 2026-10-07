package org.dromara.aigov.task.helper;

import cn.hutool.core.util.HexUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.config.AigCallbackProperties;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Provider 回调验签器（HMAC-SHA256）。
 *
 * <p><b>为什么验签是回调链路上唯一不能省的环节</b>：回调会推进任务状态、
 * 在「成功」路径上还会触发下游交付。回调端点必然对外可达，攻击者只要能猜到一个
 * {@code provider_job_id}，就能伪造「任务已完成」并塞入任意结果。
 * 签名是唯一能把「对方发的」与「别人发的」区分开的手段——IP 白名单会变、
 * 回调地址可能被转发，都不足以长期依靠。</p>
 *
 * <p><b>四条拒绝规则，每条都有明确理由</b>：</p>
 * <ol>
 *     <li><b>未配置该 Provider 的密钥 → 拒绝</b>。不是「跳过验签」：把「不配也能用」
 *         当默认，等于这个机制在现实中不存在（没人会主动配它）。
 *         拒绝时的原因与「签名不对」分开记录，前者要改配置、后者可能是攻击，
 *         排障方向完全不同。</li>
 *     <li><b>签名为空 → 拒绝</b>。</li>
 *     <li><b>声明的算法不是 HMAC-SHA256 → 拒绝</b>，而不是「按我们支持的算法试一遍」。
 *         否则攻击者把算法字段改成 {@code none} 或换一种更弱的算法就可能绕过——
 *         这是算法混淆攻击的经典形态。</li>
 *     <li><b>比较必须恒定时间</b>（{@link MessageDigest#isEqual}）。用 {@code equals}
 *         逐字节比较可被计时侧信道逐步猜出正确签名，签名校验尤其不能用短路比较。</li>
 * </ol>
 *
 * <p><b>签名接受十六进制或 Base64 两种写法</b>：各家 Provider 的编码习惯不同，
 * 让接入方去改对方系统不现实。两种编码解码出来的<b>MAC 字节</b>仍需完全一致，
 * 因此这不削弱安全性，只是省掉无谓的对接摩擦。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigTaskCallbackSigner {

    /**
     * 唯一支持的签名算法。
     */
    public static final String ALGORITHM = "HMAC-SHA256";

    /**
     * JCE 的算法名（HTTP 头的写法与本常量不同，故分开定义）。
     */
    private static final String JCE_ALGORITHM = "HmacSHA256";

    /**
     * 验签配置。
     */
    private final AigCallbackProperties properties;

    /**
     * 验签结果。
     *
     * @param verified 是否通过
     * @param detail   可读原因（无论通过与否都要能回答「为什么」）
     */
    public record VerifyResult(boolean verified, String detail) {

        /**
         * 通过。
         *
         * @return 结果
         */
        public static VerifyResult pass() {
            return new VerifyResult(true, "验签通过");
        }

        /**
         * 未通过。
         *
         * @param detail 原因
         * @return 结果
         */
        public static VerifyResult fail(String detail) {
            return new VerifyResult(false, detail);
        }
    }

    /**
     * 校验回调签名。
     *
     * @param providerCode Provider 编码
     * @param rawPayload   收到的原始载荷（不得重新序列化）
     * @param algorithm    回调声明的算法（可空，视为声明 HMAC-SHA256）
     * @param signature    回调携带的签名（十六进制或 Base64）
     * @return 验签结果（含可读原因）
     */
    public VerifyResult verify(String providerCode, String rawPayload, String algorithm, String signature) {
        if (StringUtils.isBlank(providerCode)) {
            return VerifyResult.fail("缺少 Provider 编码，无法确定验签密钥");
        }
        if (StringUtils.isBlank(rawPayload)) {
            return VerifyResult.fail("载荷为空，无法验签");
        }
        if (StringUtils.isNotBlank(algorithm) && !ALGORITHM.equalsIgnoreCase(algorithm.trim())) {
            // 算法混淆：不接受「换个算法再试」，否则攻击者把算法写成 none 就可能绕过
            return VerifyResult.fail("不支持的签名算法：" + algorithm + "（仅支持 " + ALGORITHM + "）");
        }
        if (StringUtils.isBlank(signature)) {
            return VerifyResult.fail("缺少签名：未签名的回调一律拒绝，不会推进任务状态");
        }
        String secret = properties.secretOf(providerCode);
        if (StringUtils.isBlank(secret)) {
            return VerifyResult.fail("未配置该 Provider 的回调验签密钥（aigov.callback.secrets."
                + providerCode + "）：按「不配就拒绝」处理，不跳过验签");
        }
        byte[] expected = hmac(secret, rawPayload);
        if (expected == null) {
            return VerifyResult.fail("验签计算失败（密钥或算法不可用）");
        }
        byte[] actual = decodeSignature(signature.trim());
        if (actual == null) {
            return VerifyResult.fail("签名格式无法识别（既不是十六进制也不是 Base64）");
        }
        // 恒定时间比较：短路比较会被计时侧信道逐步猜出正确签名
        if (!MessageDigest.isEqual(expected, actual)) {
            return VerifyResult.fail("签名不匹配");
        }
        return VerifyResult.pass();
    }

    /**
     * 计算载荷的十六进制 HMAC-SHA256（供对接方自检与日志比对使用）。
     *
     * @param providerCode Provider 编码
     * @param rawPayload   原始载荷
     * @return 十六进制签名；未配置密钥时返回 null
     */
    public String signHex(String providerCode, String rawPayload) {
        byte[] mac = hmac(properties.secretOf(providerCode), rawPayload);
        return mac == null ? null : HexUtil.encodeHexStr(mac);
    }

    /**
     * 按密钥与载荷算 HMAC-SHA256。
     *
     * @param secret     密钥（可空）
     * @param rawPayload 原始载荷
     * @return MAC 字节；参数不合法或算法不可用时返回 null
     */
    private byte[] hmac(String secret, String rawPayload) {
        if (StringUtils.isBlank(secret) || rawPayload == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(JCE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), JCE_ALGORITHM));
            return mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 不把签名或密钥写进日志
            log.error("回调验签计算失败（算法 {}）：{}", JCE_ALGORITHM, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析签名文本：先按十六进制，再按 Base64。
     *
     * @param signature 签名文本
     * @return 原始 MAC 字节；都不识别返回 null
     */
    private byte[] decodeSignature(String signature) {
        try {
            return HexUtil.decodeHex(signature);
        } catch (Exception ignored) {
            // 不是十六进制，继续尝试 Base64
        }
        try {
            return Base64.getDecoder().decode(signature);
        } catch (Exception ignored) {
            return null;
        }
    }

}
