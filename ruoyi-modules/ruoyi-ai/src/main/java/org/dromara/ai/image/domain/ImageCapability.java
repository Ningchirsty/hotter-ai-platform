package org.dromara.ai.image.domain;

/**
 * 图像创作能力编码。
 *
 * <p>与契约 {@code script/image/workflows/image-workflow-contracts.json} 的
 * {@code capabilityCode} 一一对应；每个能力恰好绑定一个 {@code workflowCode}。</p>
 */
public enum ImageCapability {

    /**
     * 文生图：只有提示词，输出尺寸由 size 档位决定。
     */
    T2I("t2i", "文生图", false, true),

    /**
     * 图生图：经典 img2img，输出跟随输入图，重绘幅度由 strength 控制。
     */
    I2I("i2i", "图生图", true, false),

    /**
     * 指令改图：image1 为编辑目标（决定画布），image2/image3 为可选参考图。
     */
    EDIT("edit", "指令改图", true, false),

    /**
     * 抠图去背景：固定提示词，输出带 alpha 的透明 PNG。
     */
    BGREMOVE("bgremove", "抠图去背景", true, false),

    /**
     * 白底图：先用抠图工作流拿到透明蒙版，再由后端把蒙版<b>确定性</b>地合成到纯白底。
     *
     * <p><b>为什么不让模型直接画白底</b>：模型路线（不管是图生图还是指令改图）都会顺手改掉产品本身，
     * 实测「保持产品不变」这类约束并不可靠（贴花、文字会被重绘）；而电商白底图恰恰要求
     * <b>产品像素级不变 + 背景恒为纯白 255</b>。所以这里的分工是「模型负责抠图，程序负责合成」，
     * 合成这一步没有任何随机性，永远不会把产品画坏。</p>
     */
    WHITE_BG("whitebg", "白底图", true, false);

    private final String code;
    private final String label;
    private final boolean requiresImage;
    private final boolean requiresSize;

    ImageCapability(String code, String label, boolean requiresImage, boolean requiresSize) {
        this.code = code;
        this.label = label;
        this.requiresImage = requiresImage;
        this.requiresSize = requiresSize;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 是否必须提供输入图。
     */
    public boolean requiresImage() {
        return requiresImage;
    }

    /**
     * 是否必须提供 size 档位（只有文生图需要）。
     */
    public boolean requiresSize() {
        return requiresSize;
    }

    /**
     * 是否允许前端覆写提示词（抠图与白底图都是固定提示词）。
     */
    public boolean allowsPrompt() {
        return this == T2I || this == I2I || this == EDIT;
    }

    /**
     * 是否由后端把抠图蒙版合成到纯白底（只有白底图）。
     */
    public boolean compositesOnWhite() {
        return this == WHITE_BG;
    }

    /**
     * 解析能力编码，未知返回 {@code null}（调用方据此返回 400，而不是抛 500）。
     */
    public static ImageCapability parse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (ImageCapability capability : values()) {
            if (capability.code.equalsIgnoreCase(trimmed)) {
                return capability;
            }
        }
        return null;
    }
}
