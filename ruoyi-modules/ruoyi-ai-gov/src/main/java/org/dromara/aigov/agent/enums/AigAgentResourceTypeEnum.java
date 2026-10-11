package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.StringJoiner;

/**
 * 工具/知识绑定可绑定的资源类型（附件 07 §5 差距表「工具/知识绑定：只有声明字段，无绑定表」）。
 *
 * <p><b>为什么不给工具与知识各开一张表</b>：两者的字段完全一样（版本 + 编码 + 范围 + 启用），
 * 行为也完全一样（都是"声明的收窄"，都不是可执行引擎）。分成两张表的结果是每个查询、
 * 每个校验都要写两遍，而两遍之间迟早会不一致。用一个 {@code resource_type} 区分，
 * 需要单查某一类时加一个条件即可。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigAgentResourceTypeEnum {

    /**
     * 工具（对应 {@code aig_agent_version.allowed_tools}）
     */
    TOOL("TOOL", "工具"),

    /**
     * 知识范围（对应 {@code aig_agent_version.knowledge_scope_json}）
     */
    KNOWLEDGE("KNOWLEDGE", "知识范围");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigAgentResourceTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigAgentResourceTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 供报错文案列出可选值（不列出来，调用方只能靠猜）。
     *
     * @return 形如 {@code TOOL（工具） / KNOWLEDGE（知识范围）}
     */
    public static String describe() {
        StringJoiner joiner = new StringJoiner(" / ");
        for (AigAgentResourceTypeEnum item : values()) {
            joiner.add(item.code + "（" + item.desc + "）");
        }
        return joiner.toString();
    }

}
