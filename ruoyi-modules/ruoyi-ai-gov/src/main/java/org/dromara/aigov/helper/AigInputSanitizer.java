package org.dromara.aigov.helper;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 审计输入脱敏工具。
 * <p><b>硬约束</b>：{@code input_summary} 绝不写入人才个人资料
 * （姓名/手机/证件/简历正文等）。因此本工具<b>只写字段名、长度与计数</b>，
 * 不写任何 payload 取值与提示词正文——这是「宁可少写，不可泄露」的取舍。</p>
 *
 * @author ai-gov
 */
public final class AigInputSanitizer {

    /**
     * 手机号（大陆）兜底打码规则。
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /**
     * 身份证号兜底打码规则。
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 邮箱兜底打码规则。
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    /**
     * 受限字段名（命中即整字段跳过，不参与任何摘要）。
     */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
        "name", "realname", "real_name", "candidatename", "candidate_name", "username",
        "phone", "mobile", "tel", "telephone",
        "idcard", "id_card", "idno", "id_no", "certno", "cert_no", "passport",
        "resume", "resume_raw", "resumeraw", "resumecontent", "resume_content",
        "email", "mail", "address", "birthday", "birthdate"
    );

    private AigInputSanitizer() {
    }

    /**
     * 计算输入指纹：SHA-256 十六进制（64 位），用于 {@code input_hash}。
     * <p>指纹只覆盖提示词与结构化载荷，<b>不可逆</b>，用于离线比对而非还原。</p>
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      数据等级
     * @param prompt         提示词原文
     * @param payload        结构化载荷
     * @return 64 位十六进制摘要
     */
    public static String hashInput(String capabilityCode, String dataLevel, String prompt, Map<String, Object> payload) {
        StringBuilder raw = new StringBuilder();
        raw.append(StringUtils.blankToDefault(capabilityCode, "")).append('|')
            .append(StringUtils.blankToDefault(dataLevel, "")).append('|')
            .append(StringUtils.blankToDefault(prompt, "")).append('|');
        if (payload != null && !payload.isEmpty()) {
            // 排序保证同输入同哈希
            for (String key : new TreeSet<>(payload.keySet())) {
                raw.append(key).append('=').append(safeValue(payload.get(key))).append(';');
            }
        }
        return DigestUtil.sha256Hex(raw.toString());
    }

    /**
     * 构造可安全入库的输入摘要。
     * <p>只包含：允许出现的字段名列表、提示词长度、载荷字段数；并对手机号/证件号/邮箱做兜底打码。</p>
     *
     * @param prompt  提示词原文（不写正文，只取长度）
     * @param payload 结构化载荷（只取字段名）
     * @return 摘要，入参全空时返回 null
     */
    public static String buildSummary(String prompt, Map<String, Object> payload) {
        List<String> parts = new ArrayList<>();
        if (payload != null && !payload.isEmpty()) {
            Set<String> safeKeys = new TreeSet<>();
            for (String key : payload.keySet()) {
                if (StringUtils.isBlank(key) || isForbiddenKey(key)) {
                    continue;
                }
                safeKeys.add(key);
            }
            parts.add("payloadFields=" + safeKeys);
            parts.add("payloadFieldCount=" + payload.size());
            parts.add("redactedFieldCount=" + (payload.size() - safeKeys.size()));
        }
        if (StringUtils.isNotBlank(prompt)) {
            parts.add("promptLength=" + prompt.length());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return truncate(mask(String.join("; ", parts)), AigConstants.AUDIT_SUMMARY_MAX);
    }

    /**
     * 兜底脱敏：手机号 / 身份证号 / 邮箱打码。
     *
     * @param raw 原始文本
     * @return 脱敏文本，入参为空返回 null
     */
    public static String mask(String raw) {
        if (StringUtils.isBlank(raw)) {
            return raw;
        }
        String safe = PHONE_PATTERN.matcher(raw).replaceAll("1**********");
        safe = ID_CARD_PATTERN.matcher(safe).replaceAll("******************");
        safe = EMAIL_PATTERN.matcher(safe).replaceAll("***@***");
        return safe;
    }

    /**
     * 截断到指定长度。
     *
     * @param raw   原始文本
     * @param limit 长度上限
     * @return 截断后的文本
     */
    public static String truncate(String raw, int limit) {
        if (raw == null) {
            return null;
        }
        return raw.length() > limit ? raw.substring(0, limit) : raw;
    }

    /**
     * 是否属于受限字段名（大小写、下划线不敏感）。
     *
     * @param key 字段名
     * @return 是否受限
     */
    public static boolean isForbiddenKey(String key) {
        if (StringUtils.isBlank(key)) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (String forbidden : FORBIDDEN_KEYS) {
            if (forbidden.replace("_", "").equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取载荷值的类型化占位（绝不写原文）。
     *
     * @param value 载荷值
     * @return 类型占位串
     */
    private static String safeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof Iterable<?>) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

}
