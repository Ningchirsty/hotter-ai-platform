package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 统一任务的<b>执行方</b>（{@code aig_task.execution_mode}）。
 *
 * <p><b>为什么必须有这一列</b>：任务表原先隐含假设「谁建任务，平台就负责执行它」——
 * 调度器扫 {@code RETRY_WAIT} 会把它<b>重新入队</b>（{@link #PLATFORM} 语义下正确），
 * 扫 {@code DISPATCHED/RUNNING} 超时会把它判<b>失败</b>。但业务域也可以自己执行任务：
 * 创作域的图像生成把编排留在<b>图像内核</b>（{@code ImageTaskSubmissionService}），
 * 只把这次工作<b>登记</b>成一条 {@code aig_task} 以便在统一任务视图里可见。这类任务若被
 * 平台当作自己的任务扫到，会出现两种都是真事故的结果：</p>
 * <ul>
 *     <li>{@code RETRY_WAIT} 被重新入队 → 平台<b>再执行一遍</b>（两份产出、两次计费）；</li>
 *     <li>在途被超时判失败 → 内核还在出图，账上已经 FAILED（甚至转人工重排，又一次计费）。</li>
 * </ul>
 * <p>所以「谁执行」必须是数据里的一个显式事实，而不是靠任务类型去猜——
 * 猜错的表现是"平台把别人正在跑的任务又跑了一遍"，从日志上看不出来。</p>
 *
 * <p><b>默认 {@link #PLATFORM}</b>：存量行在迁移时一律取默认值，因此本列上线
 * <b>不改变任何既有任务的调度行为</b>。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskExecutionModeEnum {

    /**
     * 平台执行：任务由统一调用入口执行（路由 → 调用器），调度器负责重试与超时判定
     */
    PLATFORM("PLATFORM", "平台执行（走统一调用入口）"),

    /**
     * 业务域执行：编排留在业务域（如创作域的图像内核），平台只登记与展示，不执行、不扫描
     */
    EXTERNAL("EXTERNAL", "业务域执行（平台不执行也不扫描）");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否平台执行（"平台能不能碰这条任务"的判据）。
     *
     * @return 平台执行返回 true
     */
    public boolean isPlatform() {
        return this == PLATFORM;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举；未命中返回 null
     */
    public static AigTaskExecutionModeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskExecutionModeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
