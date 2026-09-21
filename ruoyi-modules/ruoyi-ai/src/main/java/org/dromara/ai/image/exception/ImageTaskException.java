package org.dromara.ai.image.exception;

/**
 * 图像创作模块业务异常。
 *
 * <p>带 {@code errorCode} 便于前端与日志区分「契约拒绝」与「基础设施故障」，
 * 控制器内用 {@code @ExceptionHandler} 统一转成 400 + 可读原因。</p>
 */
public class ImageTaskException extends RuntimeException {

    private final String errorCode;

    public ImageTaskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ImageTaskException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static ImageTaskException invalidContract(String message) {
        return new ImageTaskException("INVALID_CONTRACT", message);
    }

    public static ImageTaskException assetNotFound(String message) {
        return new ImageTaskException("ASSET_NOT_FOUND", message);
    }

    public static ImageTaskException comfyFailure(String message) {
        return new ImageTaskException("COMFY_FAILURE", message);
    }

    public static ImageTaskException outputInvalid(String message) {
        return new ImageTaskException("OUTPUT_INVALID", message);
    }
}
