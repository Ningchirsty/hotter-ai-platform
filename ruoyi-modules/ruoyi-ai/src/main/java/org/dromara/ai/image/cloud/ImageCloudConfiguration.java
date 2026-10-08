package org.dromara.ai.image.cloud;

import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImageCloudProperties.class)
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageCloudConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ImageCloudService imageCloudService(ImageCloudProperties properties,
                                               ImageTaskRepository repository, ImageAssetStore assets) {
        return new ImageCloudService(properties, repository, assets);
    }
}
