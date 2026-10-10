package org.dromara.aigov.workspace.launch.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 启动失败的原因码（附件 §12.5 的九个码，落成平台的封闭集合）。
 *
 * <h3>为什么要用码，而不是只给一句人话</h3>
 * <p>员工看到的应该是"这个岗位还没对你开放"这种话；而排障、统计与前端分支需要的是**稳定标识**。
 * 两者混在一起的结果是：为了改文案而动了逻辑判断，或者为了判断而把内部原因直接显示给人看。</p>
 *
 * <h3>文案纪律</h3>
 * <p>{@link #getMessage()} 是**可以直接给员工看**的话：不出现私钥、端口、SQL、栈、内部路径、供应商名。
 * 有专门用例守着这一点（不许出现这些词）。真正的原因细节只进日志。</p>
 *
 * <h3>刻意是封闭集合</h3>
 * <p>扩一个码意味着前端要能处理它、文案要评审过。悄悄加一个码的后果是前端把它当成"未知错误"，
 * 于是员工看到一句无用的提示——所以扩值必须同时改契约测试。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigLaunchErrorEnum {

    /**
     * 岗位未授予当前用户（不可见）
     */
    ROLE_NOT_GRANTED("ROLE_NOT_GRANTED", "这个岗位还没有对你开放"),

    /**
     * 卡片不可用（停用/已被移除/分类已不在清单里）
     */
    ACTION_NOT_AVAILABLE("ACTION_NOT_AVAILABLE", "这张卡片当前不可用，请稍后再试或联系岗位负责人"),

    /**
     * 场景版本被阻断（不存在、未发布或已停用）
     */
    SCENE_VERSION_BLOCKED("SCENE_VERSION_BLOCKED", "这张卡片依赖的场景版本当前不可用"),

    /**
     * 运行时不可用（健康探测报告异常）
     */
    RUNTIME_UNHEALTHY("RUNTIME_UNHEALTHY", "相关能力暂时不可用，请稍后再试"),

    /**
     * 缺少必填输入
     */
    REQUIRED_INPUT_MISSING("REQUIRED_INPUT_MISSING", "还有必填内容没有填写"),

    /**
     * 项目访问被拒（跨组织/无项目权）
     */
    PROJECT_ACCESS_DENIED("PROJECT_ACCESS_DENIED", "你没有这个项目的访问权限"),

    /**
     * 额度耗尽（人均配额）
     */
    RESOURCE_EXHAUSTED("RESOURCE_EXHAUSTED", "你的用量配额已用尽，请等待周期重置或联系管理员"),

    /**
     * 启动票据过期（或不存在/已被使用）
     */
    LAUNCH_TICKET_EXPIRED("LAUNCH_TICKET_EXPIRED", "这次启动已过期，请重新发起"),

    /**
     * 幂等冲突（同一个幂等键换了请求内容）
     */
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "这次启动与之前的请求不一致，请刷新页面后重新发起");

    /**
     * 码（对外稳定标识）
     */
    private final String code;

    /**
     * 可直接展示给员工的说明
     */
    private final String message;

    /**
     * 按码查找，找不到返回 null。
     *
     * @param code 码
     * @return 枚举；未命中返回 null
     */
    public static AigLaunchErrorEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigLaunchErrorEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
