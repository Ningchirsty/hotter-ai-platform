package org.dromara.aigov.workspace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 场景包指向的流程适配器（附件 §3.2「P0 不必须依赖完全通用 Workflow DSL」）。
 *
 * <p><b>为什么用封闭枚举而不是自由字符串</b>：场景包是"业务交付的定义"，
 * 而它的执行仍然留在各业务链路里（创作域阶段机 / 视频链路 / 内容链路）。
 * 若允许自由字符串，就能声明一个平台没实现的适配器——失效时的表现是
 * "卡片能点、任务起不来"，排查要从场景配置倒着查。所以只允许这四个值。</p>
 *
 * <p><b>它不是流程引擎</b>：本值只是"这次交给谁去跑"，不定义节点/连线。
 * 通用 Workflow DSL 属于后续独立变更。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigScenarioAdapterEnum {

    /**
     * 复用创作域既有阶段机（详情页视觉工厂）
     */
    CREATIVE_EXISTING_FLOW("CREATIVE_EXISTING_FLOW", "复用创作域既有阶段机"),

    /**
     * 复用视频创作链路（ComfyUI/FFmpeg 等）
     */
    VIDEO_EXISTING_FLOW("VIDEO_EXISTING_FLOW", "复用视频创作链路"),

    /**
     * 复用内容生产链路（产品事实/文案/审核）
     */
    CONTENT_EXISTING_FLOW("CONTENT_EXISTING_FLOW", "复用内容生产链路"),

    /**
     * 不挂流程：纯登记（例如只做资料收集与人工审核的小场景）
     */
    NONE("NONE", "不挂流程（纯登记）");

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
    public static AigScenarioAdapterEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigScenarioAdapterEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
