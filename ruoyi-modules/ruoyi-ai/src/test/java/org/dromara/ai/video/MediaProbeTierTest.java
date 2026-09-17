package org.dromara.ai.video;

import org.dromara.ai.video.config.VideoTierResolutions;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.MediaProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 成片分辨率断言必须跟随所选档位。
 *
 * <p>这里锁住一次真实事故：开放 720P/480P 之后，断言仍写死 1920×1080，
 * 把已经生成、已经落盘的 720P 成片判为不合规——GPU 时间白烧，
 * 而且失败落库只认 QUEUED→FAILED，任务永远停在 RUNNING。</p>
 */
class MediaProbeTierTest {

    private static MediaProbe probe() {
        return new MediaProbe("ffprobe-not-used", "ffmpeg-not-used", 5);
    }

    private static MediaProbe.Probe measured(int w, int h) {
        return new MediaProbe.Probe(w, h, 24.0, 5000L, true);
    }

    @Test
    @DisplayName("720P 任务的 1280×720 成片必须通过")
    void accepts720p() {
        assertDoesNotThrow(() -> probe().assertAcceptable(measured(1280, 720), 1280, 720));
    }

    @Test
    @DisplayName("480P 任务的 864×480 成片必须通过")
    void accepts480p() {
        assertDoesNotThrow(() -> probe().assertAcceptable(measured(864, 480), 864, 480));
    }

    @Test
    @DisplayName("1080P 任务的 1920×1080 成片必须通过")
    void accepts1080p() {
        assertDoesNotThrow(() -> probe().assertAcceptable(measured(1920, 1080), 1920, 1080));
    }

    @Test
    @DisplayName("选了 1080P 却拿到 720P 成片：必须拒绝")
    void rejectsMismatch() {
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> probe().assertAcceptable(measured(1280, 720), 1920, 1080));
        assertEquals("OUTPUT_INVALID", error.getErrorCode());
    }

    @Test
    @DisplayName("选了 720P 却拿到 480P 成片：必须拒绝")
    void rejectsWrongLowerTier() {
        assertThrows(VideoTaskException.class,
            () -> probe().assertAcceptable(measured(864, 480), 1280, 720));
    }

    @Test
    @DisplayName("未实测时不做分辨率断言（环境缺 ffprobe 不得误杀）")
    void skipsWhenUnmeasured() {
        assertDoesNotThrow(() -> probe().assertAcceptable(MediaProbe.Probe.unmeasured(), 1280, 720));
    }

    @Test
    @DisplayName("宽高缺失时不做断言，交由其它环节处理")
    void skipsWhenDimensionsMissing() {
        assertDoesNotThrow(() ->
            probe().assertAcceptable(new MediaProbe.Probe(null, null, 24.0, 5000L, true), 1280, 720));
    }

    @Test
    @DisplayName("档位表能给出各档位的成片目标尺寸，且未知档位返回 null")
    void tierTargetSizes() {
        H3TemplatePreparer preparer = new H3TemplatePreparer(new com.fasterxml.jackson.databind.ObjectMapper(),
            new VideoTierResolutions());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new int[] {1920, 1080}, preparer.expectedOutputSize(H3TemplatePreparer.TIER_1080P));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new int[] {1280, 720}, preparer.expectedOutputSize(H3TemplatePreparer.TIER_720P));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new int[] {864, 480}, preparer.expectedOutputSize(H3TemplatePreparer.TIER_480P));
        assertNull(preparer.expectedOutputSize("不存在的档位"));
        assertNull(preparer.expectedOutputSize(null));
    }

    @Test
    @DisplayName("未装配档位表时返回 null（老构造器行为不能被破坏）")
    void noTierTableReturnsNull() {
        H3TemplatePreparer preparer =
            new H3TemplatePreparer(new com.fasterxml.jackson.databind.ObjectMapper());
        assertNull(preparer.expectedOutputSize(H3TemplatePreparer.TIER_1080P));
    }
}
