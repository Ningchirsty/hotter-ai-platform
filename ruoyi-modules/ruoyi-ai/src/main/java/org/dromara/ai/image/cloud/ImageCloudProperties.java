package org.dromara.ai.image.cloud;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 云端图像服务配置；密钥只从服务端文件读取，不进入 DTO、日志或数据库。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "image.cloud")
public class ImageCloudProperties {
    private boolean enabled;
    private String apiKeyFile = "";
    private int timeoutSeconds = 600;
    private int concurrency = 2;
    private int queueCapacity = 16;
    private List<String> outputHosts = List.of("bluocto.com", "rolldek.com", "dashscope-463f.oss-accelerate.aliyuncs.com", "dashscope-7c2c.oss-accelerate.aliyuncs.com");

    public String readKey() {
        if (!enabled || apiKeyFile == null || apiKeyFile.isBlank()) return "";
        try {
            Path path = Path.of(apiKeyFile);
            if (Files.size(path) > 4096) return "";
            String key = Files.readString(path);
            if (key.startsWith("\uFEFF")) key = key.substring(1);
            key = key.strip();
            // 兼容 Windows UTF-8 BOM 和末尾换行，拒绝内嵌空白或非 ASCII 请求头字符。
            return key.isEmpty() || key.chars().anyMatch(c -> c <= 32 || c >= 127) ? "" : key;
        } catch (Exception ignored) {
            return "";
        }
    }

    public boolean configured() {
        return !readKey().isEmpty();
    }
}
