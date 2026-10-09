package org.dromara.ai.image.template;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Feed 与计费令牌分离，只读取服务端密钥文件。 */
@Getter @Setter
@ConfigurationProperties(prefix = "image.template-feed")
public class TemplateFeedProperties {
    private boolean enabled;
    private boolean generationEnabled;
    private String keyFile = "";
    private String cacheDirectory = "./temp/image-template-feed";
    private String publicBaseUrl = "https://pm.hottter.cn/prod-api/image/templates";
    private List<String> blockedTerms = List.of();
    public String readKey() {
        try {
            if (keyFile.isBlank() || Files.size(Path.of(keyFile)) > 4096) return "";
            String key = Files.readString(Path.of(keyFile)).replace("\uFEFF", "").strip();
            return key.matches("[A-Za-z0-9_-]{16,128}") ? key : "";
        } catch (Exception ignored) { return ""; }
    }
}
