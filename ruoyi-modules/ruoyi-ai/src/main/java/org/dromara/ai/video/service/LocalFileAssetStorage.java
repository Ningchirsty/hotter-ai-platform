package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.exception.VideoTaskException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 后端受控目录实现的素材存储。
 *
 * <p>适用于联调环境与尚未配置私有对象存储的部署。要点：</p>
 * <ul>
 *   <li>存储键按 {@code tenantId/userId} 分目录，便于隔离与清理；</li>
 *   <li>文件名由服务端生成，不使用浏览器提供的名字，避免路径穿越；</li>
 *   <li>{@link #read(String)} 只接受本实现生成的相对键，拒绝绝对路径与上跳。</li>
 * </ul>
 *
 * <p>正式环境应改用私有对象存储实现（桶关闭公共读），本类不应作为生产存储。</p>
 */
@Slf4j
public class LocalFileAssetStorage implements AssetStorage {

    private final Path root;

    public LocalFileAssetStorage(Path root) {
        if (root == null) {
            throw new IllegalStateException("video.storage.local-root 未配置");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建素材存储目录：" + this.root, e);
        }
        log.info("视频素材本地存储根目录：{}", this.root);
    }

    @Override
    public String storeUpload(String tenantId, long userId, String originalName,
                              byte[] content, String contentType) {
        String ext = extensionOf(originalName, contentType);
        String key = scopedKey(tenantId, userId, "upload", ext);
        write(key, content);
        return key;
    }

    @Override
    public String storeOutput(String tenantId, long userId, long taskId, String fileName,
                              byte[] content, String contentType) {
        String ext = extensionOf(fileName, contentType);
        String key = scopedKey(tenantId, userId, "output", ext);
        write(key, content);
        return key;
    }

    @Override
    public byte[] read(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw VideoTaskException.assetNotFound("素材文件不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw VideoTaskException.assetNotFound("素材文件读取失败");
        }
    }

    @Override
    public Path localPath(String storageKey) {
        Path target = resolveSafe(storageKey);
        return Files.isRegularFile(target) ? target : null;
    }

    @Override
    public String keyOf(Path file) {
        if (file == null) {
            return null;
        }
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            return null;
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private String scopedKey(String tenantId, long userId, String category, String ext) {
        String safeTenant = StringUtils.hasText(tenantId) ? tenantId.replaceAll("[^A-Za-z0-9_-]", "_") : "unknown";
        return safeTenant + "/" + userId + "/" + category + "/"
            + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private void write(String key, byte[] content) {
        if (content == null || content.length == 0) {
            throw VideoTaskException.outputInvalid("素材内容为空");
        }
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("素材写入失败", e);
        }
    }

    /**
     * 解析存储键并确认其落在根目录内，防止路径穿越。
     */
    private Path resolveSafe(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw VideoTaskException.assetNotFound("存储键为空");
        }
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root)) {
            throw VideoTaskException.assetNotFound("非法存储键");
        }
        return candidate;
    }

    private static String extensionOf(String name, String contentType) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                String ext = name.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{1,5}")) {
                    return ext;
                }
            }
        }
        if (contentType != null) {
            return switch (contentType.toLowerCase()) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/webp" -> ".webp";
                case "image/png" -> ".png";
                case "video/mp4" -> ".mp4";
                case "audio/mpeg" -> ".mp3";
                case "audio/wav", "audio/x-wav" -> ".wav";
                default -> ".bin";
            };
        }
        return ".bin";
    }
}
