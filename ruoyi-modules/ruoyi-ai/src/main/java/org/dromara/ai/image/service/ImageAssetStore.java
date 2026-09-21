package org.dromara.ai.image.service;

import org.dromara.ai.video.service.AssetStorage;

import java.nio.file.Path;

/**
 * 图像模块的素材存储门面。
 *
 * <p><b>为什么需要这一层</b>：视频模块用 {@code @ConditionalOnMissingBean(AssetStorage.class)} 装配
 * 存储 Bean。如果图像模块也注册一个 {@code AssetStorage} 类型的 Bean，装配顺序一旦不利，
 * 视频模块会跳过自己的 Bean 并拿到图像模块的实例（存储根目录被串味），而视频链路是已实测通过的。
 * 因此图像模块把 {@link AssetStorage} 作为<b>内部实现细节</b>包在这个门面里，
 * 对外只暴露 {@code ImageAssetStore} 这一种类型，两个模块的 Bean 类型互不重叠。</p>
 */
public class ImageAssetStore {

    private final AssetStorage storage;
    private final ImageThumbnailService thumbnails;

    public ImageAssetStore(AssetStorage storage) {
        this.storage = storage;
        this.thumbnails = new ImageThumbnailService(storage);
    }

    /**
     * 保存上传素材，返回存储键。
     */
    public String storeUpload(String tenantId, long userId, String originalName, byte[] content, String contentType) {
        return storage.storeUpload(tenantId, userId, originalName, content, contentType);
    }

    /**
     * 保存任务产出，返回存储键。
     */
    public String storeOutput(String tenantId, long userId, long taskId, String fileName,
                              byte[] content, String contentType) {
        return storage.storeOutput(tenantId, userId, taskId, fileName, content, contentType);
    }

    /**
     * 读取素材内容。
     */
    public byte[] read(String storageKey) {
        return storage.read(storageKey);
    }

    /**
     * 素材在本机的路径（用于 ImageIO 实测）；不支持本地访问时返回 null。
     */
    public Path localPath(String storageKey) {
        return storage.localPath(storageKey);
    }

    /**
     * 生成或读取缓存缩略图；无法生成时返回 null（调用方退回原图）。
     */
    public byte[] thumbnail(String storageKey, String contentType) {
        return thumbnails.thumbnail(storageKey, contentType);
    }
}
