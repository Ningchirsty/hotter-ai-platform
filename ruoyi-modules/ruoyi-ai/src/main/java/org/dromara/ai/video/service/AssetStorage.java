package org.dromara.ai.video.service;

/**
 * 素材存储抽象。
 *
 * <p>用于把上传素材与任务成片落到私有对象存储或后端受控目录。
 * 实现必须保证：淡出公共读、按租户/用户前缀隔离、删除走软删。</p>
 */
public interface AssetStorage {

    /**
     * 保存用户上传的素材。
     *
     * @param tenantId     租户
     * @param userId       用户
     * @param originalName 浏览器提供的原始文件名（仅用于推断扩展名，不作为存储键）
     * @param content      文件内容
     * @param contentType  MIME 类型
     * @return 存储键
     */
    String storeUpload(String tenantId, long userId, String originalName, byte[] content, String contentType);

    /**
     * 保存任务产出的成片。
     *
     * @return 存储键
     */
    String storeOutput(String tenantId, long userId, long taskId, String fileName,
                       byte[] content, String contentType);

    /**
     * 读取素材内容。
     *
     * @param storageKey 存储键
     */
    byte[] read(String storageKey);

    /**
     * 返回素材在<b>本机文件系统</b>上的路径，供 ffprobe/ffmpeg 实测与截断使用。
     *
     * <p>纯对象存储实现无法提供本地路径，返回 {@code null} 即可；
     * 此时编排器会退化为「未实测」，不得声称输出已验证。</p>
     *
     * @param storageKey 存储键
     * @return 本地路径，或 null 表示不支持本地访问
     */
    default java.nio.file.Path localPath(String storageKey) {
        return null;
    }

    /**
     * 由本机文件路径还原存储键。
     *
     * <p>截断会生成新文件并替换原文件，存储键必须随之更新，
     * 否则数据库里的键会指向已被删除的文件（这是实际踩过的缺陷）。
     * 不支持本地访问的实现返回 {@code null} 即可。</p>
     *
     * @param file 本机文件路径
     * @return 存储键，或 null 表示无法还原
     */
    default String keyOf(java.nio.file.Path file) {
        return null;
    }

    /**
     * 缩略图在本机文件系统上的目标路径。
     *
     * <p>缩略图必须与素材本体分开存放：本体的目录会被运维清理脚本按「数据库里没有引用」
     * 判定为无主文件，缩略图本来就不入库，混在里面会被误删。</p>
     *
     * <p>返回 null 表示该实现不支持缩略图（纯对象存储），调用方应退回使用原图。</p>
     *
     * @param storageKey 素材的存储键
     * @return 缩略图路径（不保证已存在），或 null
     */
    default java.nio.file.Path thumbnailPath(String storageKey) {
        return null;
    }
}
