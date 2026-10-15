package org.dromara.aigov.execution;

import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.common.core.utils.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 有效权限与有效数据等级（设计 §7.2）。
 *
 * <p>设计原文：「有效权限采用<b>逐层相交</b>：登录身份范围 ∩ 岗位卡片范围 ∩ Agent 版本范围 ∩
 * 知识/工具资源范围。有效数据等级取模板、输入附件、检索片段和业务对象等级的<b>最高值</b>。
 * 任意禁止条件优先，无法分类的内部资料不得按公开处理。」</p>
 *
 * <p>三条判据都是纯函数，可逐条单测——它们的共同特征是「算错了不会报错」：
 * 交集算成并集会让越权资源进入运行；等级取错（取了最低值）会让受限资料被当成公开；
 * 外发许可的"与"算成"或"会让任意一方允许就外发。</p>
 *
 * @author ai-gov
 */
public final class AigEffectivePolicy {

    private AigEffectivePolicy() {
    }

    /**
     * 范围相交：声明范围 ∩ 收窄范围。
     *
     * <p><b>收窄为空表示"不额外收窄"</b>，此时返回声明范围本身——这与既有绑定语义一致
     * （空值=不限，而不是"都不匹配"）。写成"空即全部拒绝"会让没有绑定行的版本一律跑不起来。</p>
     *
     * @param declared 声明范围（如版本 {@code knowledgeRefs}）
     * @param narrowed 收窄范围（如启用的 KNOWLEDGE 绑定编码）
     * @return 交集（保持声明顺序，去重）
     */
    public static List<String> intersect(Collection<String> declared, Collection<String> narrowed) {
        Set<String> base = clean(declared);
        Set<String> narrow = clean(narrowed);
        if (!narrow.isEmpty()) {
            base.retainAll(narrow);
        }
        return List.copyOf(base);
    }

    /**
     * 有效数据等级 = 各来源中的<b>最高</b>等级（严格优先）。
     *
     * <p>无法识别的等级一律忽略——但注意：忽略不代表放宽，调用方若完全取不到等级，
     * 应显式传入 {@code STRICT}，而不是依赖本方法的默认。</p>
     *
     * @param codes 各来源的等级编码（可空、可含空白）
     * @return 最高等级编码；全部不可识别时返回 null
     */
    public static String strictestDataLevel(String... codes) {
        AigDataLevelEnum strictest = null;
        if (codes == null) {
            return null;
        }
        for (String code : codes) {
            AigDataLevelEnum level = AigDataLevelEnum.find(code);
            if (level == null) {
                continue;
            }
            if (strictest == null || level.getRank() > strictest.getRank()) {
                strictest = level;
            }
        }
        return strictest == null ? null : strictest.getCode();
    }

    /**
     * 有效外发许可 = 所有参与方都不禁止（设计 §7.2「任意禁止条件优先」）。
     *
     * <p>{@code null} 表示该方不额外限制；只要有一方为 {@code FALSE} 即为否。</p>
     *
     * @param flags 各方的外发许可（企业策略 / Agent 配置 / 资源策略 / 模型路由）
     * @return 允许返回 true
     */
    public static boolean externalAllowed(Boolean... flags) {
        if (flags == null) {
            return false;
        }
        for (Boolean flag : flags) {
            if (Boolean.FALSE.equals(flag)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> clean(Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

}
