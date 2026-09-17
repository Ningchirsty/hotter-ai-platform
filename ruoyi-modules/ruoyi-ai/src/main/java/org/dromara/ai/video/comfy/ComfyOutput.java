package org.dromara.ai.video.comfy;

/**
 * 一次 ComfyUI 执行产出的成片信息（实测值）。
 *
 * @param fileName      ComfyUI 输出目录中的文件名
 * @param subfolder     输出子目录（可为空字符串）
 * @param type          ComfyUI 的 type 字段，通常为 output
 * @param width         实测宽度
 * @param height        实测高度
 * @param fps           实测帧率
 * @param durationMillis 实测时长（毫秒）
 * @param sizeBytes     实测字节数
 */
public record ComfyOutput(
    String fileName,
    String subfolder,
    String type,
    Integer width,
    Integer height,
    Double fps,
    Long durationMillis,
    Long sizeBytes
) {

    /**
     * 校验成片是否符合产品约束：必须为 1080P 且不超过时长上限。
     *
     * @param maxDurationMillis 允许的最大时长（毫秒）
     * @return 是否需要截断
     */
    public boolean needsTruncation(long maxDurationMillis) {
        return durationMillis != null && durationMillis > maxDurationMillis;
    }

    /**
     * 是否满足 1920x1080。
     */
    public boolean is1080p() {
        return width != null && height != null && width == 1920 && height == 1080;
    }
}
