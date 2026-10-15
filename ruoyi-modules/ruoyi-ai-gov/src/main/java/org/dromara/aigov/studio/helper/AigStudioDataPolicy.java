package org.dromara.aigov.studio.helper;

/**
 * 草稿的数据处理口径（设计 §4.1、§7.2）。
 *
 * <p><b>为什么要有这一处</b>：训练台测试调用与培训助手对话都是"拿草稿内容去发起模型调用"，
 * 而草稿上写着 {@code allowExternal}（Y/N）。过去这两个入口只把 {@code dataLevel} 交给网关，
 * 草稿的外发许可根本到不了路由——于是「这个 Agent 禁止外发」在实际调用上等于没写。</p>
 *
 * <p><b>为什么返回 {@code Boolean} 而不是 {@code boolean}</b>：网关用 {@code null} 表示
 * 「调用方不额外限制」，而这里<b>永远</b>给出明确结论（Y → TRUE，其余 → FALSE）。
 * 空白一律按「不允许」处理：把"没填"当成"允许外发"是把配置缺失变成一次数据外发，
 * 而"没填"最常见的原因正是责任人还没想清楚。</p>
 *
 * @author ai-gov
 */
public final class AigStudioDataPolicy {

    private AigStudioDataPolicy() {
    }

    /**
     * 解析草稿声明的外发许可。
     *
     * @param allowExternal 草稿 {@code allowExternal}（Y/N，可空）
     * @return {@code TRUE} 表示业务侧不禁止外发；其余（含空白、null）返回 {@code FALSE}
     */
    public static Boolean externalAllowed(String allowExternal) {
        return "Y".equalsIgnoreCase(allowExternal == null ? null : allowExternal.trim());
    }

}
