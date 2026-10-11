package org.dromara.aigov.workspace.helper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 场景引用 {@code scenario://<code>@<version>} 的解析（主文档线增量 3 提出来的共用件）。
 *
 * <h3>为什么从校验器里提出来</h3>
 * <p>这份形态最初只出现在岗位包校验器里（那里只需要"能不能解析"）。启动链路也要解析它，
 * 而且需要**解析出的编码与版本**（去查场景版本是否可用）。两份正则意味着两份"什么算合法引用"的定义，
 * 于是会出现"校验通过、启动解析不了"（或反过来）——那类不一致只有用户能发现。
 * 所以这里只有一处实现，校验器与启动链路都调它。</p>
 *
 * @param code    场景编码
 * @param version 版本号（{@code x.y.z}）
 * @author ai-gov
 */
public record AigScenarioRef(String code, String version) {

    /**
     * 引用形态：{@code scenario://<code>@<version>}
     */
    private static final Pattern PATTERN =
        Pattern.compile("^scenario://([A-Za-z0-9_]+)@(\\d+\\.\\d+\\.\\d+)$");

    /**
     * 解析引用。
     *
     * @param raw 原始引用（可空）
     * @return 引用；形态不符返回 null（**不抛异常**：调用方要把"形态不对"作为一条业务问题报出去）
     */
    public static AigScenarioRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new AigScenarioRef(matcher.group(1), matcher.group(2));
    }

    /**
     * 还原成引用字符串。
     *
     * @return 引用字符串
     */
    public String toUri() {
        return "scenario://" + code + "@" + version;
    }

}
