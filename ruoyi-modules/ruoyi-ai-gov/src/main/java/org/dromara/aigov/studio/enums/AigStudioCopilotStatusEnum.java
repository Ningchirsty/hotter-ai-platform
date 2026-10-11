package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对话式 Copilot 一轮对话的结论（附件 07 增量 6）。
 *
 * <p><b>为什么要区分 REJECTED 与 FAILED</b>：失败是"这次没调通"，拒绝是"模型提出了一件
 * 平台不允许的事"（例如想改 {@code agentCategory}）。后者才是这条通道最该被看见的信号——
 * 它说明提示词与白名单没有约束住模型。两者合成一个 FAILED，这件事就永远沉在日志里。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioCopilotStatusEnum {

    /**
     * 已产出提案（进入人工审阅；草稿未被改动）
     */
    SUCCEEDED("SUCCEEDED", "已产出提案"),

    /**
     * 提案被平台规则拒绝（越权路径 / 值类型不符 / 没有产生变化等）
     */
    REJECTED("REJECTED", "提案被拒绝"),

    /**
     * 调用或解析失败（未调通、没有返回内容、输出不是要求的 JSON）
     */
    FAILED("FAILED", "调用失败"),

    /**
     * 进行中（证据先落：崩在调用中间也留下"可能已经调用过"的痕迹）
     */
    RUNNING("RUNNING", "进行中");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

}
