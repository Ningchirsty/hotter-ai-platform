package org.dromara.aigov.task.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 只读镜像行（存量任务在统一任务视图里的投影）。
 *
 * <p><b>两条硬约束，都由结构本身保证而不是靠注释提醒</b>：</p>
 * <ol>
 *     <li><b>只读</b>：{@link #isReadOnly()} 是写死返回 true 的方法，没有对应字段、也没有 setter。
 *         镜像行不接受任何写操作——重试/选定/质检仍在来源自己的接口上，
 *         各带各的权限。若这里给一个可被置 false 的字段，迟早有人会把它置 false。</li>
 *     <li><b>状态原样透传，不做跨来源映射</b>：{@link #status} 与 {@link #statusLabel} 是来源的原值，
 *         {@link #stateMachine} 写明它属于哪套状态机。刻意<b>不</b>把创作域的 8 态映射成
 *         {@code aig_task} 的状态：两套状态机的含义并不相同（例如创作域的 APPROVED 是
 *         「这一屏采用这张图」，与任务级通过不是一回事），映射会让人以为它们按同一套规则推进。
 *         需要判断「是否还会变」时看 {@link #terminal}，它是唯一被跨来源统一过的语义。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Data
public class AigTaskMirrorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 来源编码（如 {@code DP_GENERATION}）
     */
    private String source;

    /**
     * 来源展示名
     */
    private String sourceLabel;

    /**
     * 来源侧主键（字符串：各来源主键类型未必一致）
     */
    private String refId;

    /**
     * 来源侧业务对象ID（对创作域即项目/任务ID）
     */
    private Long projectId;

    /**
     * 来源侧业务对象名（可空）
     */
    private String projectName;

    /**
     * 行标题（可空；用于列表展示）
     */
    private String title;

    /**
     * 来源自己的状态编码（<b>原值</b>）
     */
    private String status;

    /**
     * 来源自己的状态展示名
     */
    private String statusLabel;

    /**
     * 是否终态（唯一被跨来源统一过的语义）
     */
    private boolean terminal;

    /**
     * 该状态所属的状态机（写明来源，避免被误当成 {@code aig_task} 的状态）
     */
    private String stateMachine;

    /**
     * 来源侧候选序号（可空）
     */
    private Integer candidateNo;

    /**
     * 是否已被人工选定为交付物（来源侧口径）
     */
    private boolean selected;

    /**
     * 产出资产ID（可空）
     */
    private Long outputAssetId;

    /**
     * 错误码（可空）
     */
    private String errorCode;

    /**
     * 错误信息（可空）
     */
    private String errorMessage;

    /**
     * 耗时（毫秒，可空）
     */
    private Long durationMs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 恒为 true。
     *
     * <p><b>刻意做成方法而不是字段</b>：字段会被序列化、也会被 setter 改。镜像行在任何情况下
     * 都不接受写操作，这条约束不该依赖「没人去改它」。</p>
     *
     * @return 恒 true
     */
    public boolean isReadOnly() {
        return true;
    }

}
