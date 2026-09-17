package org.dromara.talent.helper;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.talent.config.TalentProperties;
import org.springframework.stereotype.Component;

/**
 * 手机号标准化 / 哈希 / 脱敏工具。
 * <p>
 * 设计口径：
 * <ul>
 *     <li>落库字段只有三个：{@code phone_cipher}（@EncryptField 密文）、{@code phone_hash}（SHA-256，重复预检）、
 *     {@code phone_tail4}（弱匹配，非敏感）。</li>
 *     <li>明文严禁写日志、严禁作为查询条件（{@code @EncryptField} 不支持 Wrapper 条件查询）。</li>
 * </ul>
 *
 * <p>
 * <b>关于 {@code mybatis-encryptor.enable=false} 的降级说明（encryptFallback）：</b>
 * 基线字段加密默认关闭（{@code mybatis-encryptor.enable} 默认 false），
 * 关闭时 {@code @EncryptField} 是"空操作"，{@code phone_cipher} 会以<b>明文</b>落库。
 * 该配置属于部署基线（{@code ruoyi-admin} 的 application.yml），本模块<b>不自行实现加密</b>，
 * 仅在 {@link org.dromara.talent.service.impl.TalentProfileServiceImpl} 读取时保证"是否有明文可用"这一点成立
 * （即 {@code phoneCipher} 字段在开启加密时是解密后的明文、关闭时本身就是明文）。
 * 若生产未启用加密，必须由运维侧开启 {@code mybatis-encryptor.enable=true} 并妥善管理密钥，
 * 否则 {@code phone_cipher} 明文落库，属于配置缺陷而非代码缺陷。
 * </p>
 *
 * @author talent
 */
@Component
@RequiredArgsConstructor
public class TalentPhoneHelper {

    /**
     * 手机号哈希盐，来自 TalentProperties，禁止硬编码。
     */
    private final TalentProperties talentProperties;

    /**
     * 标准化手机号：去除所有非数字字符、去掉 +86 / 86 前缀、校验 11 位且以 1 开头。
     *
     * @param rawPhone 原始手机号（允许含空格、横线、+86 前缀）
     * @return 11 位纯数字手机号
     * @throws ServiceException 入参为空或格式非法
     */
    public String normalize(String rawPhone) {
        if (StringUtils.isBlank(rawPhone)) {
            throw new ServiceException("手机号不能为空");
        }
        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.length() == 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        } else if (digits.length() == 14 && digits.startsWith("086")) {
            digits = digits.substring(3);
        }
        if (digits.length() != 11 || !digits.startsWith("1")) {
            throw new ServiceException("手机号格式不正确");
        }
        return digits;
    }

    /**
     * 计算手机号哈希：{@code SHA-256(salt + normalizedPhone)}，返回 64 位小写十六进制。
     *
     * @param normalizedPhone 已标准化手机号
     * @return 64 位小写十六进制哈希
     */
    public String hash(String normalizedPhone) {
        if (StringUtils.isBlank(normalizedPhone)) {
            throw new ServiceException("手机号不能为空");
        }
        String salt = talentProperties.getPhoneHashSalt();
        if (StringUtils.isBlank(salt)) {
            throw new ServiceException("手机号哈希盐未配置");
        }
        return DigestUtil.sha256Hex(salt + normalizedPhone);
    }

    /**
     * 取手机号后四位；入参不足 4 位时返回原值。
     *
     * @param normalizedPhone 已标准化手机号
     * @return 后四位
     */
    public String tail4(String normalizedPhone) {
        if (StringUtils.isBlank(normalizedPhone)) {
            return normalizedPhone;
        }
        int length = normalizedPhone.length();
        return length < 4 ? normalizedPhone : normalizedPhone.substring(length - 4);
    }

    /**
     * 生成 138****1234 形式的展示值；长度不足时逐段降级，绝不返回比入参更长的内容。
     *
     * @param normalizedPhone 已标准化手机号（也兼容未标准化的明文）
     * @return 脱敏展示值
     */
    public String mask(String normalizedPhone) {
        if (StringUtils.isBlank(normalizedPhone)) {
            return normalizedPhone;
        }
        int length = normalizedPhone.length();
        if (length >= 11) {
            return normalizedPhone.substring(0, 3) + "****" + normalizedPhone.substring(length - 4);
        }
        if (length >= 7) {
            return normalizedPhone.substring(0, 3) + "****" + normalizedPhone.substring(length - 2);
        }
        if (length >= 4) {
            return "***" + normalizedPhone.substring(length - 4);
        }
        return "****";
    }

}
