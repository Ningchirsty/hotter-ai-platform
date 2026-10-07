package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 黄金用例类型（设计 §13.2 的三类首期用例）。
 *
 * <p>三类对应平台上真正会被评测的对象：详情页策划（PLAN）、参考图视觉分析（VISUAL_DNA）、
 * 图像生成与 QA（IMAGE_QA）。刻意不设「通用/其它」这种兜底类型：类型是给「这条用例该跑在
 * 什么对象上」用的，兜底类型的实际含义是「没人知道该跑在哪」。</p>
 *
 * <p><b>类型与评测执行器不是一一对应</b>：执行器按「评测对象」（某个 Agent/Skill 版本）注册，
 * 一个执行器可以承载多条不同类型的用例（例如视觉 DNA 的用例既有 VISUAL_DNA 判据、
 * 也可能带 IMAGE_QA 的产物校验）。类型影响的是用例的可判定判据写法，不是派发。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigEvaluationCaseTypeEnum {

    /**
     * 详情页策划
     */
    PLAN("PLAN", "详情页策划"),

    /**
     * 参考图视觉分析
     */
    VISUAL_DNA("VISUAL_DNA", "参考图视觉分析"),

    /**
     * 图像生成与 QA
     */
    IMAGE_QA("IMAGE_QA", "图像生成与 QA");

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
    public static AigEvaluationCaseTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigEvaluationCaseTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 可选值清单（报错时列出，避免调用方去翻代码）。
     *
     * @return 逗号分隔的编码清单
     */
    public static String codes() {
        StringBuilder sb = new StringBuilder();
        for (AigEvaluationCaseTypeEnum item : values()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(item.code);
        }
        return sb.toString();
    }

}
