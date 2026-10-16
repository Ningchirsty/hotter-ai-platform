package org.dromara.aigov.helper;

import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.common.core.utils.StringUtils;

/**
 * 「能力声明的最低数据等级」与调用方传入等级的合并口径（Q2C，纯函数）。
 *
 * <p><b>它补的是哪个洞</b>：数据等级此前<b>完全由调用方传入</b>。业务方（或一个图省事的集成）
 * 把客户名单按 {@code INTERNAL} 发过来，路由就按 {@code INTERNAL} 找策略——可能落在一个
 * 允许外发的模型上。等级标低拦不住，这是原口径里最大的一个洞。</p>
 *
 * <h3>口径</h3>
 * <ul>
 *     <li><b>只抬不降</b>：生效等级 = max(声明, 调用方传入)。声明只用于把等级抬上去，
 *         绝不允许用它把调用方标高的等级降下来（否则"能力声明 STRICT、调用方标 STRICT"
 *         会被降成 PUBLIC 之类，比不声明更糟）；</li>
 *     <li><b>不声明（空白）</b>= 调用方说了算，与存量行为完全一致；</li>
 *     <li><b>声明值无法识别</b>：保留调用方等级，但要能被看见（{@link #explain} 会给出提示），
 *         而不是当成"没有声明"悄悄过去。前端表单只提供四个合法选项，正常配置不到这一步。</li>
 * </ul>
 *
 * @author ai-gov
 */
public final class AigDataLevelEscalation {

    private AigDataLevelEscalation() {
    }

    /**
     * 计算生效数据等级。
     *
     * @param declaredMinLevel 能力声明的最低数据等级（可空）
     * @param requested        调用方传入的数据等级（非空，路由在此之前已判空）
     * @return 生效等级（只可能 ≥ requested）
     */
    public static AigDataLevelEnum effective(String declaredMinLevel, AigDataLevelEnum requested) {
        AigDataLevelEnum declared = parse(declaredMinLevel);
        if (declared == null || requested == null) {
            return requested;
        }
        return declared.getRank() > requested.getRank() ? declared : requested;
    }

    /**
     * 合并说明（路由命中明细用）。声明没起作用时不产生噪音。
     *
     * @param declaredMinLevel 能力声明的最低数据等级（可空）
     * @param requested        调用方传入的数据等级（可空）
     * @return 说明；无需说明时返回 null
     */
    public static String explain(String declaredMinLevel, AigDataLevelEnum requested) {
        if (StringUtils.isBlank(declaredMinLevel)) {
            return null;
        }
        AigDataLevelEnum declared = parse(declaredMinLevel);
        if (declared == null) {
            return "能力声明的最低数据等级无法识别（值=" + declaredMinLevel.trim()
                + "，只接受 PUBLIC/INTERNAL/RESTRICTED/STRICT）：本次仍按调用方传入的等级="
                + code(requested) + " 处理";
        }
        if (requested != null && declared.getRank() > requested.getRank()) {
            return "能力声明最低数据等级=" + declared.getCode() + "，高于本次传入的 " + requested.getCode()
                + "：本次按 " + declared.getCode() + " 生效（声明只抬不降）";
        }
        return null;
    }

    /**
     * 声明值是否合法（给表单/服务端校验用）。
     *
     * @param declaredMinLevel 声明值（可空 = 合法，表示不声明）
     * @return 合法返回 true
     */
    public static boolean legalDeclaration(String declaredMinLevel) {
        return StringUtils.isBlank(declaredMinLevel) || parse(declaredMinLevel) != null;
    }

    private static AigDataLevelEnum parse(String declaredMinLevel) {
        if (StringUtils.isBlank(declaredMinLevel)) {
            return null;
        }
        return AigDataLevelEnum.find(declaredMinLevel.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static String code(AigDataLevelEnum level) {
        return level == null ? "未指定" : level.getCode();
    }
}
