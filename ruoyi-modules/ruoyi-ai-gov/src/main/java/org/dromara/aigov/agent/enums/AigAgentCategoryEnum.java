package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 类别（设计 §5.2 的四个首期内置 Agent）。
 *
 * <p>用枚举而不是自由字符串：类别是「按类统计质量/成本/采纳率」的分组键，
 * 也是页面上「我要找哪类 Agent」的筛选维度。一个拼错的类别不会报错，
 * 只会让这类 Agent 在统计与筛选里同时消失。</p>
 *
 * <p>四个取值与设计 §5.2 的表格一一对应；{@code assistant} 列写的是该 Agent 在
 * 仓库里对应的现有实现（WP3 只是把它包成受控版本，<b>算法不重写</b>）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigAgentCategoryEnum {

    /**
     * 详情页策划
     */
    PLANNING("PLANNING", "详情页策划", "CreativeDraftFactory / CreativeDraftBrain"),

    /**
     * 视觉 DNA
     */
    VISUAL_DNA("VISUAL_DNA", "视觉 DNA", "CreativeDnaService / VisualBrainAdapter / ReferenceImageAnalyzer"),

    /**
     * 生成任务构建
     */
    GENERATION("GENERATION", "生成任务构建", "CreativeProductionService / ImageTaskSubmissionService"),

    /**
     * 视觉 QA
     */
    QA("QA", "视觉 QA", "CreativeImageRuleChecker / cp_output_check");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 仓库里对应的现有实现（说明「包一层」而不是「重写」）
     */
    private final String implementation;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigAgentCategoryEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigAgentCategoryEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
