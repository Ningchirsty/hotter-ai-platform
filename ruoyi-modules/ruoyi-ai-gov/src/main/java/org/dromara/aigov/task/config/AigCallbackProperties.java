package org.dromara.aigov.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 回调验签配置。
 *
 * <p><b>为什么按 Provider 各配一把密钥</b>：验签的意义是「确认这条回调确实来自对方」。
 * 全网一把共用密钥时，任何一家泄漏都等于所有家可被伪造。密钥存在这里（而非数据库）
 * 是刻意的：数据库里已有大量可写数据，把回调密钥也放进去意味着「拿到库写权限即可伪造回调」——
 * 而回调会推进任务状态、可能触发下游交付。</p>
 *
 * <p><b>没配密钥的 Provider 一律拒绝回调</b>（见 {@code AigTaskCallbackSigner}），
 * 不是「没配就跳过验签」。后者会让这个机制在现实中形同不存在：
 * 没人会主动去配一个「不配也能用」的东西。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.callback")
public class AigCallbackProperties {

    /**
     * Provider 编码 → 验签密钥（HMAC-SHA256）
     */
    private Map<String, String> secrets = new LinkedHashMap<>();

    /**
     * 取某 Provider 的验签密钥。
     *
     * @param providerCode Provider 编码
     * @return 密钥；未配置返回 null
     */
    public String secretOf(String providerCode) {
        if (providerCode == null || secrets == null) {
            return null;
        }
        return secrets.get(providerCode.trim());
    }

}
