package org.dromara.ai.video.exception;

/**
 * 视频创作模块业务异常。
 *
 * <p>面向用户的错误信息必须脱敏：不得包含节点图内容、模型绝对路径、
 * ComfyUI 内部地址或凭据。</p>
 */
public class VideoTaskException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 失败分类，落库到 video_task.error_code。
     */
    private final String errorCode;

    public VideoTaskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VideoTaskException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 契约校验失败（非法 workflowCode / 字段缺失 / 固定档位不符）。
     */
    public static VideoTaskException invalidContract(String message) {
        return new VideoTaskException("INVALID_CONTRACT", message);
    }

    /**
     * 素材不存在或不属于当前用户/租户。
     */
    public static VideoTaskException assetNotFound(String message) {
        return new VideoTaskException("ASSET_NOT_FOUND", message);
    }

    /**
     * ComfyUI 网络或协议失败。
     */
    public static VideoTaskException comfyFailure(String message, Throwable cause) {
        return new VideoTaskException("COMFY_FAILURE", message, cause);
    }

    /**
     * 输出不合规（分辨率/时长/文件缺失）。
     */
    public static VideoTaskException outputInvalid(String message) {
        return new VideoTaskException("OUTPUT_INVALID", message);
    }
}
