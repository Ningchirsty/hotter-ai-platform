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

    /**
     * 入参不满足工作流的输出预算（派发前就拒绝，不烧 GPU）。
     *
     * <p>与 {@link #outputInvalid} 分开是因为两者含义完全不同：那个是「出完了但产出不合格」，
     * 这个根本还没出图——把入参问题报成 OUTPUT_INVALID 会让排查方向跑到模型侧去。</p>
     *
     * @param message 可执行的原因
     * @return 异常
     */
    public static ImageTaskException inputTooLarge(String message) {
        return new ImageTaskException("INPUT_TOO_LARGE", message);
    }
}
