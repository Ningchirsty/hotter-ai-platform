package org.dromara.ai.video.cloud;
import org.dromara.ai.video.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
/** 云端服务启用不影响本地执行器与工作流注册表。 */
@Configuration
@EnableConfigurationProperties(VideoCloudProperties.class)
@ConditionalOnProperty(prefix="video",name="enabled",havingValue="true")
public class VideoCloudConfiguration {
    @Bean(destroyMethod="shutdown")
    public VideoCloudService videoCloudService(VideoCloudProperties p,VideoTaskRepository r,AssetStorage s,MediaProbe probe){return new VideoCloudService(p,r,s,probe);}
}
