package org.dromara.ai.video.cloud;
import org.dromara.ai.video.config.VideoModuleConfiguration;
import org.dromara.ai.video.controller.VideoCreationController;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
/** Checks default-off cloud wiring alongside the original local video beans. */
class CloudVideoWiringTest {
 private final ApplicationContextRunner runner=new ApplicationContextRunner()
  .withUserConfiguration(VideoModuleConfiguration.class,VideoCreationController.class,org.dromara.ai.video.service.VideoTaskSubmissionService.class,VideoCloudConfiguration.class,VideoCloudController.class)
  .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
  .withBean(VideoTaskRepository.class,()->mock(VideoTaskRepository.class))
  .withPropertyValues("video.contract-root="+Path.of("..","..","script").toAbsolutePath(),"video.comfy-base-url=http://192.0.2.1:8188","video.sync-contract-to-db=false","video.fail-stale-running-on-startup=false");
 @Test void disabledVideoLeavesNoCloudBeans(){runner.withPropertyValues("video.enabled=false").run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(VideoCloudService.class);});}
 @Test void enabledLocalKeepsCloudGenerationDisabled(){runner.withPropertyValues("video.enabled=true").run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(VideoCreationController.class);assertThat(c).hasSingleBean(VideoCloudController.class);assertThat(c.getBean(VideoCloudService.class).models().get("configured")).isEqualTo(false);assertThat(c.getBean(VideoCloudService.class).models().get("verifiedVariants")).isEqualTo(java.util.Set.of());});}
}
