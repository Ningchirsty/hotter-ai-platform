package org.dromara.creative.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 视觉工厂阶段（{@code cp_task.visual_stage}）。
 *
 * <p><b>为什么单独一列而不是复用 cp_task.status</b>：{@code cp_task.status} 是内容协同的
 * 交付生命周期（DRAFT→…→DELIVERED，14 态），把视觉生产的 19 个阶段塞进去会污染它的语义，
 * 也会让内容协同的列表/统计口径一起变形。因此视觉阶段单独成列，只在
 * {@code deliverable_type='ECOM_DETAIL'} 的项目上使用。</p>
 *
 * <p><b>双状态防漂移（硬约束）</b>：本列只允许 {@code CreativeProjectService} 的
 * {@code moveStage} 一处写入，且必须走 {@link #canMoveTo} 的合法性校验；
 * 每次变更同时写一条 {@code dp_stage_event}，因此任何时候都能回答
 * 「谁在什么时候把它推到这一步」。</p>
 *
 * @author creative
 */
@Getter
@AllArgsConstructor
public enum DpVisualStageEnum {

    /**
     * 资料与参考图齐备（项目刚进入视觉工厂）
     */
    MATERIAL_READY("MATERIAL_READY", "资料就绪"),

    /**
     * 正在生成视觉基因
     */
    DNA_GENERATING("DNA_GENERATING", "基因生成中"),
    /**
     * 视觉基因待人工确认
     */
    DNA_REVIEW("DNA_REVIEW", "基因待确认"),
    /**
     * 视觉基因已锁定（后续出图必须引用它）
     */
    DNA_LOCKED("DNA_LOCKED", "基因已锁定"),

    /**
     * 正在生成 A/B/C 视觉方向
     */
    DIRECTION_GENERATING("DIRECTION_GENERATING", "方向生成中"),
    /**
     * 视觉方向待选定
     */
    DIRECTION_REVIEW("DIRECTION_REVIEW", "方向待选定"),
    /**
     * 视觉方向已选定
     */
    DIRECTION_LOCKED("DIRECTION_LOCKED", "方向已选定"),

    /**
     * 分镜生成中
     */
    STORYBOARD_GENERATING("STORYBOARD_GENERATING", "分镜生成中"),
    /**
     * 分镜待人工确认
     */
    STORYBOARD_REVIEW("STORYBOARD_REVIEW", "分镜待确认"),
    /**
     * 分镜已锁定
     */
    STORYBOARD_LOCKED("STORYBOARD_LOCKED", "分镜已锁定"),

    /**
     * 视觉门（人工审核中，闸门结论 + 人工动作）
     */
    VISUAL_GATE("VISUAL_GATE", "视觉门审核中"),
    /**
     * 视觉门通过，可开始生产
     */
    VISUAL_LOCKED("VISUAL_LOCKED", "视觉已锁定"),

    /**
     * 批量出图中
     */
    PRODUCING("PRODUCING", "出图中"),
    /**
     * 自动质检中（只筛除，不放行）
     */
    QA_PROCESSING("QA_PROCESSING", "质检中"),

    /**
     * 排版渲染中
     */
    LAYOUT_PROCESSING("LAYOUT_PROCESSING", "排版中"),
    /**
     * 机排版 V0.8 已出
     */
    V08_READY("V08_READY", "机排版完成"),
    /**
     * 设计师精修中
     */
    DESIGN_REFINING("DESIGN_REFINING", "人工精修中"),

    /**
     * 终审中
     */
    FINAL_REVIEW("FINAL_REVIEW", "终审中"),
    /**
     * 完成（V1.0 交付）
     */
    COMPLETED("COMPLETED", "已完成");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static DpVisualStageEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DpVisualStageEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 描述（未命中返回原始编码，便于排障展示）。
     *
     * @param code 编码
     * @return 可读描述
     */
    public static String descOf(String code) {
        DpVisualStageEnum item = find(code);
        return item == null ? code : item.desc;
    }

    /**
     * 阶段推进合法性（防漂移的唯一判据）。
     *
     * <p>规则：</p>
     * <ul>
     *     <li>停留原地：允许（幂等重算）；</li>
     *     <li><b>向前推进</b>（含跳步）：允许——人的真实动作就是跳步的，
     *     例如已有可用基因时直接出图（R0 即 MATERIAL_READY → PRODUCING），
     *     强行要求逐步经过每个状态只会逼出「假过渡」；</li>
     *     <li><b>回退</b>：只允许退到「待确认 / 返工」态（见 {@link #isReworkTarget()}），
     *     不允许退回某个「生成中」态——那会让页面出现永不结束的中间态；</li>
     *     <li>终态 {@link #COMPLETED} 不可再动：返工走新版本，而不是改阶段。</li>
     * </ul>
     *
     * @param target 目标阶段
     * @return 是否允许
     */
    public boolean canMoveTo(DpVisualStageEnum target) {
        if (target == null || target == this) {
            return true;
        }
        if (this == COMPLETED) {
            return false;
        }
        if (target.ordinal() > this.ordinal()) {
            return true;
        }
        return target.isReworkTarget();
    }

    /**
     * 是否「待确认 / 返工」态：允许作为回退目标。
     *
     * @return 是否可作回退目标
     */
    public boolean isReworkTarget() {
        return this == MATERIAL_READY || this == DNA_REVIEW || this == DIRECTION_REVIEW
            || this == STORYBOARD_REVIEW || this == VISUAL_GATE || this == DESIGN_REFINING
            || this == FINAL_REVIEW;
    }

    /**
     * 是否为「进行中」态（页面应显示进度而不是让人等一个静止的页面）。
     *
     * @return 是否进行中
     */
    public boolean isRunning() {
        return this == DNA_GENERATING || this == DIRECTION_GENERATING
            || this == STORYBOARD_GENERATING || this == PRODUCING
            || this == QA_PROCESSING || this == LAYOUT_PROCESSING;
    }

}
