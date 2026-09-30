package org.dromara.creative.helper;

import org.dromara.creative.domain.DpOutputSpec;

/**
 * 把 {@code dp_output_spec} 的**固定尺寸**换算成"出图要用的像素"（V0.2 R27）。
 *
 * <p><b>为什么需要它</b>：主图（MAIN_IMAGE）的规格是 800×800 / 1200×1200（FIXED、1:1），
 * 但在此之前 `dp_output_spec` 只被**长图排版**用来定页宽，出图尺寸完全由工作流决定——
 * 于是"主图出 800×800"这条配置一直是"存着没用"。这一层把规格翻译成出图参数。</p>
 *
 * <p><b>为什么"跟随输入图"的工作流要缩放参考图</b>：`wf-i2i-qwen21` / `wf-whitebg-qwen21`
 * 这类契约没有固定尺寸档位（`supportedOutputs` 是"跟随输入图"），产出尺寸就等于送进去那张图的尺寸。
 * 所以要出 800×800，就得把送模型的参考图适配成 800×800（项目里的附件本身不动）。</p>
 *
 * @author creative
 */
public final class CreativeOutputSpecResolver {

    private CreativeOutputSpecResolver() {
    }

    /**
     * 目标尺寸。
     *
     * @param width  宽（px）
     * @param height 高（px）
     * @param code   规格编码（用于留痕）
     * @param reason 为什么用这个尺寸（用于留痕）
     */
    public record TargetSize(int width, int height, String code, String reason) {

        /**
         * @return 是否可用（宽高都为正）
         */
        public boolean usable() {
            return width > 0 && height > 0;
        }
    }

    /**
     * 从输出规格里取固定尺寸。
     *
     * <p>只认 {@code height_mode=FIXED} 且宽高都有效的规格：
     * 长图那种 {@code AUTO} 高度（高度由内容决定）不能当出图尺寸用，否则会把长图当方图出。</p>
     *
     * @param spec 输出规格（可空）
     * @return 目标尺寸；不适用时返回 null（调用方照旧按工作流默认出图）
     */
    public static TargetSize fixedSizeOf(DpOutputSpec spec) {
        if (spec == null || spec.getWidth() == null || spec.getHeight() == null) {
            return null;
        }
        if (!"FIXED".equalsIgnoreCase(spec.getHeightMode())) {
            return null;
        }
        if (spec.getWidth() <= 0 || spec.getHeight() <= 0) {
            return null;
        }
        return new TargetSize(spec.getWidth(), spec.getHeight(), spec.getSpecCode(),
            "按交付类型的默认输出规格 " + spec.getSpecCode() + "（"
                + spec.getWidth() + "×" + spec.getHeight()
                + (spec.getRatio() == null ? "" : " " + spec.getRatio()) + "，FIXED）出图");
    }
}
