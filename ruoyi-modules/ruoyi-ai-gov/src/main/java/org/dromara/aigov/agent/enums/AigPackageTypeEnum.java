package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Package 类型（设计 §6.1）。
 *
 * <p><b>首期只有声明式</b>（Q9 与设计 §1.3）：Manifest 承载 Prompt / Workflow / 输出 Schema /
 * Skill 绑定 / 权限声明，<b>不含可执行代码</b>。因此本枚举的语义不是「能不能执行代码」，
 * 而是<b>这个包装了什么</b>——它决定安装时该把内容物放进 Agent 还是 Skill 注册表。</p>
 *
 * <p>{@link #MIXED} 同时带入 Agent 与 Skill：一个 Package 里既有可复用的原子能力，
 * 又有编排它们的工作流单元，这是很常见的形态，不该强迫作者拆成两个包
 * （拆成两个包会让「依赖关系」与「一起回滚」这两件事都变复杂）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigPackageTypeEnum {

    /**
     * 只含 Agent 定义
     */
    AGENT("AGENT", "只含 Agent 定义"),

    /**
     * 只含 Skill 定义
     */
    SKILL("SKILL", "只含 Skill 定义"),

    /**
     * 同时含 Agent 与 Skill
     */
    MIXED("MIXED", "含 Agent 与 Skill");

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
    public static AigPackageTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigPackageTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
