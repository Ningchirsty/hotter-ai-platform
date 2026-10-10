package org.dromara.aigov.workspace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务能力卡片的启动方式（附件 §6.2 的四种 Action 类型）。
 *
 * <p><b>为什么必须是封闭枚举、不能是自由字符串</b>：启动方式决定前端走哪套交互
 * （抽屉/表单/专业工作台/纯跳转）。允许自由字符串，岗位包里就能声明一个平台没实现的启动方式，
 * 而失效时的表现是"点了没反应"——排查要从岗位包配置倒着查。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigActionLaunchModeEnum {

    /**
     * 轻量任务：抽屉/对话里完成，结果可复制或归档（只允许经发布的小能力）
     */
    QUICK("QUICK", "轻量任务（抽屉/对话）"),

    /**
     * 表单任务：填表/传文件 → 任务 → 结果
     */
    FORM("FORM", "表单任务"),

    /**
     * 专业工作台：进入既有专业页面完成交付（详情页/视频）
     */
    STUDIO("STUDIO", "专业工作台"),

    /**
     * 纯导航：打开既有业务模块，不启动 AI
     */
    NAVIGATION("NAVIGATION", "纯导航");

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
    public static AigActionLaunchModeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigActionLaunchModeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
