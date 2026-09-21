package org.dromara.hrtalent.support;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 人才联系方式规范化、哈希与脱敏工具（设计文档 §9.4 字段规则、§9.5 索引设计、§21.15 重复预检）。
 *
 * <p><b>职责</b>：</p>
 * <ul>
 *     <li>把电话/邮箱规范化为稳定形式（{@link #normalizePhone(String)} / {@link #normalizeEmail(String)}）；</li>
 *     <li>对规范化结果计算 SHA-256 小写十六进制哈希（{@link #hash(String)}），用于
 *     重复预检；哈希<b>不可逆</b>，且不对哈希建立唯一约束；</li>
 *     <li>列表默认脱敏（{@link #maskPhone(String)} / {@link #maskEmail(String)}）。</li>
 * </ul>
 *
 * <p><b>安全约束</b>：本类只处理「已解密到内存中的明文」，不负责落库；明文一律不得写入日志。
 * 日志禁止打印本类入参。</p>
 *
 * @author hr-talent
 */
public final class TalentContactCodec {

    /**
     * 电话哈希前缀：中国大陆默认区号。
     */
    private static final String DEFAULT_COUNTRY_CODE = "+86";

    /**
     * 国际拨号前缀（{@code 00} 等价于 {@code +}）。
     */
    private static final String IDD_PREFIX = "00";

    /**
     * 中国大陆手机号长度。
     */
    private static final int CN_MOBILE_LENGTH = 11;

    /**
     * 无有效内容的脱敏占位。
     */
    private static final String MASK_EMPTY = "";

    private TalentContactCodec() {
    }

    /**
     * 规范化电话：去空格/横线/括号，统一 {@code +} 前缀，中国大陆号码补 {@code +86}。
     *
     * @param rawPhone 原始电话，可为空
     * @return 规范化电话；无有效内容时返回 null
     */
    public static String normalizePhone(String rawPhone) {
        if (StringUtils.isBlank(rawPhone)) {
            return null;
        }
        String value = rawPhone.replaceAll("[\\s\\-()（）]", "");
        if (StrUtil.isEmpty(value)) {
            return null;
        }
        if (value.startsWith(IDD_PREFIX)) {
            return "+" + value.substring(IDD_PREFIX.length());
        }
        if (value.startsWith("+")) {
            return value;
        }
        // 纯数字：中国大陆 11 位手机号补 +86，其余保持原样（不臆造区号）
        if (value.chars().allMatch(Character::isDigit) && value.length() == CN_MOBILE_LENGTH) {
            return DEFAULT_COUNTRY_CODE + value;
        }
        return value;
    }

    /**
     * 规范化邮箱：去空格并转为小写（设计文档 §9.4：邮箱标准化为小写后计算哈希）。
     *
     * @param rawEmail 原始邮箱，可为空
     * @return 规范化邮箱；无有效内容时返回 null
     */
    public static String normalizeEmail(String rawEmail) {
        if (StringUtils.isBlank(rawEmail)) {
            return null;
        }
        String value = rawEmail.replaceAll("\\s", "").toLowerCase();
        return StrUtil.isEmpty(value) ? null : value;
    }

    /**
     * 计算规范化文本的 SHA-256 小写十六进制哈希（char(64) 口径）。
     *
     * @param normalizedValue 已规范化的文本，可为空
     * @return 64 位小写十六进制哈希；入参为空时返回 null
     */
    public static String hash(String normalizedValue) {
        if (StringUtils.isBlank(normalizedValue)) {
            return null;
        }
        return DigestUtil.sha256Hex(normalizedValue);
    }

    /**
     * 电话明文 → 哈希（先规范化再哈希）。
     *
     * @param rawPhone 原始电话，可为空
     * @return 64 位小写十六进制哈希；无有效内容时返回 null
     */
    public static String phoneHash(String rawPhone) {
        return hash(normalizePhone(rawPhone));
    }

    /**
     * 邮箱明文 → 哈希（先规范化再哈希）。
     *
     * @param rawEmail 原始邮箱，可为空
     * @return 64 位小写十六进制哈希；无有效内容时返回 null
     */
    public static String emailHash(String rawEmail) {
        return hash(normalizeEmail(rawEmail));
    }

    /**
     * 电话脱敏（列表默认口径）。
     *
     * @param rawPhone 电话明文，可为空
     * @return 脱敏电话；无有效内容时返回空串
     */
    public static String maskPhone(String rawPhone) {
        if (StringUtils.isBlank(rawPhone)) {
            return MASK_EMPTY;
        }
        return DesensitizedUtil.mobilePhone(rawPhone);
    }

    /**
     * 邮箱脱敏（列表默认口径）。
     *
     * @param rawEmail 邮箱明文，可为空
     * @return 脱敏邮箱；无有效内容时返回空串
     */
    public static String maskEmail(String rawEmail) {
        if (StringUtils.isBlank(rawEmail)) {
            return MASK_EMPTY;
        }
        return DesensitizedUtil.email(rawEmail);
    }

    /**
     * 把英文逗号分隔的多值用户ID串拆成 ID 数组（VO 多值字段翻译源的逆操作）。
     *
     * @param joinedIds 逗号分隔串，可为空
     * @return ID 数组；无有效元素时返回空数组（不为 null）
     */
    public static Long[] splitIds(String joinedIds) {
        if (StringUtils.isBlank(joinedIds)) {
            return new Long[0];
        }
        List<Long> ids = new ArrayList<>();
        for (String part : StringUtils.splitList(joinedIds)) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，避免一条坏数据导致整个列表接口失败
            }
        }
        return ids.toArray(new Long[0]);
    }

}
