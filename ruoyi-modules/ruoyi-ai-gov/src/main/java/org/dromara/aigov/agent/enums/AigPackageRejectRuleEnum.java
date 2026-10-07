package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Package 拒绝规则（设计 §6.2 五类）。
 *
 * <p><b>为什么必须做成枚举，而不是在扫描器里 if 出一段文字</b>：{@code aig_package_version.scan_result}
 * 只存 PASS/REJECT，真正可审计的信息在 {@code scan_detail} 里。如果拒绝只见一段自由文案，
 * 那么「这次拒绝到底命中了哪一条规则」就不可检索、不可统计，评审时只能靠读句子猜。
 * 枚举化之后：每一条拒绝都带 code（可检索）+ 设计依据（可逐条对齐），
 * 且 {@code scan_detail} 里写明命中的规则 code——这正是建表注释里「不允许空泛文案」的落地方式。</p>
 *
 * <p>五条规则的描述刻意与 §6.2 的原文一一对应，{@link #basis} 记录条款号。新增一条拒绝规则
 * 必须同时说明它对应设计哪一句——否则就是平台自己发明门槛，那和「按规则放行」是两回事。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigPackageRejectRuleEnum {

    /**
     * 要求可执行或越权能力。
     */
    EXECUTABLE_OR_PRIVILEGED_ACCESS("EXECUTABLE_OR_PRIVILEGED_ACCESS",
        "要求数据库直连、Shell/SSH、Docker Socket、生产密钥或管理员权限",
        "§6.2-1"),

    /**
     * 未声明必填声明。
     */
    UNDECLARED_MANDATORY_FIELD("UNDECLARED_MANDATORY_FIELD",
        "未声明外网访问、数据等级、许可证、依赖或输出 Schema 等 §6.1 最小字段",
        "§6.2-2"),

    /**
     * 要求无法限制的任意代码执行。
     */
    UNBOUNDED_CODE_EXECUTION("UNBOUNDED_CODE_EXECUTION",
        "要求平台无法限制的任意代码执行（未声明的字段/工具一律按不可界定处理）",
        "§6.2-3"),

    /**
     * 来源、校验和或版权信息不明确。
     */
    UNCLEAR_PROVENANCE("UNCLEAR_PROVENANCE",
        "来源、校验和或版权信息不明确（或与包记录不一致）",
        "§6.2-4"),

    /**
     * 试图绕过平台链路。
     */
    BYPASSES_PLATFORM_CHAIN("BYPASSES_PLATFORM_CHAIN",
        "试图绕过任务、审核、资产或审计链路（无黄金用例/输出 Schema/回滚策略即无法进入平台链路）",
        "§6.2-5");

    /**
     * 规则编码（写入 scan_detail，可检索）
     */
    private final String code;

    /**
     * 规则描述（与 §6.2 原文对应）
     */
    private final String desc;

    /**
     * 设计依据（条款号）
     */
    private final String basis;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigPackageRejectRuleEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigPackageRejectRuleEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
