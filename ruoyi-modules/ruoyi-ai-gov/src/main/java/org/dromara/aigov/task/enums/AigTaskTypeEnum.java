package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 统一任务类型（设计 §9.1）。
 *
 * <p>用枚举而不是自由字符串的原因与状态相同：任务类型是跨域统计（「这个月生成了多少图」）
 * 与权限、计费口径的分组键。一个拼错的类型不会报错，只会让该任务在统计里消失。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskTypeEnum {

    /**
     * 生成 Page Spec、Brief、Prompt Plan
     */
    PLAN_GENERATION("PLAN_GENERATION", "生成规划（Page Spec/Brief/Prompt Plan）"),

    /**
     * 分析参考图与品牌视觉规则
     */
    VISUAL_DNA_ANALYSIS("VISUAL_DNA_ANALYSIS", "视觉基因分析"),

    /**
     * 文案、摘要、结构化内容
     */
    TEXT_GENERATION("TEXT_GENERATION", "文本生成"),

    /**
     * 生图
     */
    IMAGE_GENERATION("IMAGE_GENERATION", "图像生成"),

    /**
     * 局部编辑、换背景、扩图
     */
    IMAGE_EDIT("IMAGE_EDIT", "图像编辑"),

    /**
     * 视频生成（后续）
     */
    VIDEO_GENERATION("VIDEO_GENERATION", "视频生成"),

    /**
     * 创建企业设计平台会话/跳转
     */
    DESIGN_SESSION_CREATE("DESIGN_SESSION_CREATE", "创建设计会话"),

    /**
     * 视觉质量辅助检查
     */
    VISUAL_QA("VISUAL_QA", "视觉质量检查"),

    /**
     * Agent/Skill Golden Case 评测
     */
    AGENT_EVALUATION("AGENT_EVALUATION", "Agent 评测");

    /**
     * 编码
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
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigTaskTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
