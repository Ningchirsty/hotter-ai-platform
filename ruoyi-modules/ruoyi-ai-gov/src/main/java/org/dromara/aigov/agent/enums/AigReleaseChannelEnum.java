package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布通道 / 可见范围（设计 §5.3、§10.2 的 {@code release_channel}）。
 *
 * <p><b>取值口径的来源，以及它的一处设计缺口</b>：设计文档两处都只出现了这个<b>列名</b>
 * （§5.3「agent_code、version、release_channel、适用 scenario_code」、§10.2 表清单），
 * <b>没有定义它取什么值</b>。本实现按 §6.3 的语义确定：
 * 「通过后发布 Candidate，<b>仅绑定指定品牌、部门或测试项目</b>」——也就是说通道表达的是
 * <b>这个版本面向谁可见</b>。于是取值为：</p>
 * <ul>
 *     <li>{@link #TESTING}：仅测试项目可见（沙箱/内部验证期）；</li>
 *     <li>{@link #BRAND}：仅指定品牌可见；</li>
 *     <li>{@link #DEPT}：仅指定部门可见；</li>
 *     <li>{@link #GENERAL}：通用（灰度达标后的正式范围）。</li>
 * </ul>
 *
 * <p>前三个是<b>受限通道</b>（{@link #limited()}）：选中它们时必须存在
 * {@code aig_agent_binding} 绑定行，否则这个版本谁都看不到——「发布了但没人能看见」
 * 与「没发布」在界面上长得一样，必须由代码把这种情况挡掉。
 * {@link #GENERAL} 不受限，因为它的可见范围本来就是全体。</p>
 *
 * <p>若这个口径与实际预期不符，改这里与 SQL 注释即可，<b>不需要回填数据</b>
 * （列是 varchar，且当前尚无生产数据）。刻意没有静默发明一套「看起来像标准」的枚举。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigReleaseChannelEnum {

    /**
     * 仅测试项目
     */
    TESTING("TESTING", "仅测试项目可见", true),

    /**
     * 仅指定品牌
     */
    BRAND("BRAND", "仅指定品牌可见", true),

    /**
     * 仅指定部门
     */
    DEPT("DEPT", "仅指定部门可见", true),

    /**
     * 通用
     */
    GENERAL("GENERAL", "通用（全体可见）", false);

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否受限通道（受限者必须有绑定行，否则等于谁都看不到）
     */
    private final boolean limited;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigReleaseChannelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigReleaseChannelEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
