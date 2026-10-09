package org.dromara.ai.image.template;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Feed 关闭时不创建任何同步任务；凭据缺失不会阻止平台其他模块启动。 */
@Configuration
@EnableConfigurationProperties(TemplateFeedProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix="image",name={"enabled","template-feed.enabled"},havingValue="true")
public class TemplateFeedConfiguration {
    @Bean public TemplateFeedCache templateFeedCache(TemplateFeedProperties properties) { return new TemplateFeedCache(properties); }
    @Bean public Refresh templateFeedRefresh(TemplateFeedCache cache) { return new Refresh(cache); }
    public record Refresh(TemplateFeedCache cache) {
        @Scheduled(initialDelay=1000,fixedDelay=600000) public void run() { cache.refresh(); }
    }
}
