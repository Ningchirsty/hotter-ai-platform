package org.dromara.ai.video.cloud;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 蓝章鱼视频独立配置；密钥不进入任务、前端或日志。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "video.cloud")
public class VideoCloudProperties {
    private boolean enabled;
    private String apiKeyFile = "";
    private String publicAssetBaseUrl = "";
    private int timeoutSeconds = 1800;
    private int concurrency = 2;
    private int queueCapacity = 8;
    private List<String> verifiedVariants = List.of();
    private List<String> validationVariants = List.of();
    private List<Long> validationUserIds = List.of();
    private int validationLimit;
    private String verificationFile = "";

    public String readKey() {
        if (!enabled || apiKeyFile == null || apiKeyFile.isBlank()) return "";
        try {
            Path p = Path.of(apiKeyFile);
            if (Files.size(p) > 4096) return "";
            String key = Files.readString(p).replaceFirst("^\\uFEFF", "").strip();
            return key.isEmpty() || key.chars().anyMatch(c -> c <= 32 || c >= 127) ? "" : key;
        } catch (Exception e) { return ""; }
    }
    public boolean configured() { return !readKey().isBlank(); }
}
