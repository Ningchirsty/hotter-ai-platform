package org.dromara.ai.video.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 成片/素材内容接口的 Range 解析测试。
 *
 * <p>为什么值得单独测：视频拖动进度条依赖 206 分片响应，区间算错会导致
 * 播放器卡住或读到越界内容；而这段逻辑是纯函数、不依赖 Spring，正好用单测锁住。
 * 方法是私有的，因此用反射调用——这比重构成包可见只为测试更安全。</p>
 */
class AssetContentRangeTest {

    private static long[] parse(String header, long total) throws Exception {
        Method m = VideoCreationController.class
            .getDeclaredMethod("parseRange", String.class, long.class);
        m.setAccessible(true);
        return (long[]) m.invoke(null, header, total);
    }

    @Test
    @DisplayName("Range：常规区间 bytes=0-99")
    void normalRange() throws Exception {
        assertArrayEquals(new long[] {0, 99}, parse("bytes=0-99", 1000));
        assertArrayEquals(new long[] {500, 999}, parse("bytes=500-", 1000));
    }

    @Test
    @DisplayName("Range：结束位置超出总长度时收敛到末尾（不越界）")
    void endBeyondTotalIsClamped() throws Exception {
        assertArrayEquals(new long[] {900, 999}, parse("bytes=900-99999", 1000));
    }

    @Test
    @DisplayName("Range：后缀区间 bytes=-N 取最后 N 字节（浏览器 seek 常用）")
    void suffixRange() throws Exception {
        assertArrayEquals(new long[] {900, 999}, parse("bytes=-100", 1000));
        // 后缀大于总长度时，取整个文件
        assertArrayEquals(new long[] {0, 999}, parse("bytes=-5000", 1000));
    }

    @Test
    @DisplayName("Range：不可满足的区间返回 null（调用方据此回 416）")
    void unsatisfiableReturnsNull() throws Exception {
        assertNull(parse("bytes=1000-1200", 1000), "起点越界应不可满足");
        assertNull(parse("bytes=500-100", 1000), "起点大于终点应不可满足");
        // 注意 total=0 时任何区间都不可满足，避免出现负数长度
        assertNull(parse("bytes=0-0", 0), "空文件任何区间都不可满足");
    }

    @Test
    @DisplayName("Range：非法或缺失的头返回 null，不抛异常")
    void malformedReturnsNull() throws Exception {
        assertNull(parse("bytes=abc-def", 1000));
        assertNull(parse("bytes=-", 1000));
        assertNull(parse("items=0-10", 1000));
    }

    @Test
    @DisplayName("Range：单字节区间与整文件区间")
    void edgeRanges() throws Exception {
        assertArrayEquals(new long[] {0, 0}, parse("bytes=0-0", 1));
        assertArrayEquals(new long[] {0, 999}, parse("bytes=0-999", 1000));
        assertArrayEquals(new long[] {999, 999}, parse("bytes=999-", 1000));
    }

    @Test
    @DisplayName("Range：区间长度计算不会溢出为负")
    void lengthNeverNegative() throws Exception {
        long[] r = parse("bytes=0-0", 1);
        assertNotNull(r);
        assertEquals(1, r[1] - r[0] + 1);
    }
}
