package org.dromara.creative.service.impl;

import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.service.ICreativeProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件缩略图落对象存储（V0.2 R34）。
 *
 * <p>要钉的是"这笔钱每个文件只付一次"：第一个请求读原图 + 缩放 + 写伴生对象，
 * 之后（哪怕换了进程、进程内缓存是空的）直接读那个几十 KB 的小对象，**不再读原图**。</p>
 *
 * <p>用假的 OSS 助手（Map 当对象存储）而不是断言具体的调用序列：这样钉的是
 * "第一次之后原图没被再读过"这个**行为**，而不是"某个方法被调了几次"的实现细节。</p>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeFileThumbnailCacheTest {

    private static final Long TASK = 777L;
    private static final Long FILE = 55L;
    private static final String THUMB_KEY = "content-private/" + TASK + "/" + FILE + "/thumb.jpg";

    @Mock
    private CpTaskFileMapper fileMapper;
    @Mock
    private ContentOssHelper ossHelper;
    @Mock
    private IContentTaskService contentTaskService;

    @InjectMocks
    private CreativeProjectServiceImpl service;

    /** 假对象存储：key → 字节 */
    private final Map<String, byte[]> store = new HashMap<>();
    /** 原图被读了几次（用来证明第二次不再读原图） */
    private int originalReads;

    @BeforeEach
    void setUp() {
        store.clear();
        originalReads = 0;
        // 进程内缓存是 static 的：同一个 JVM 里前面的测试方法会把它填上，
        // 不清的话这里会读到上一轮的缩略图（第一版就是这么"第一次没读原图"的）。
        clearMemoryCache();
        store.put("k/original.png", png(1200, 900));

        CpTaskFile file = new CpTaskFile();
        file.setFileId(FILE);
        file.setTaskId(TASK);
        file.setFileName("original.png");
        file.setFileRef("k/original.png");
        file.setFileExt("png");
        file.setFileSize(1024L);
        when(fileMapper.selectById(FILE)).thenReturn(file);

        // readFileContent 走内容域的附件清单（与页面读图同一条路径），测试也必须走它
        CpTaskFileVo vo = new CpTaskFileVo();
        vo.setFileId(FILE);
        vo.setTaskId(TASK);
        vo.setFileName("original.png");
        vo.setFileRef("k/original.png");
        vo.setFileExt("png");
        when(contentTaskService.listFiles(TASK)).thenReturn(List.of(vo));

        when(ossHelper.getBytes(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if ("k/original.png".equals(key)) {
                originalReads++;
            }
            byte[] hit = store.get(key);
            if (hit == null) {
                throw new org.dromara.common.core.exception.ServiceException("对象不存在");
            }
            return hit;
        });
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ossHelper).put(anyString(), any());
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(40, 90, 60));
        g.fillRect(0, 0, width, height);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("第一次：读原图 + 缩放 + 写伴生对象（缩略图明显更小）")
    void firstCallComputesAndPersists() {
        ICreativeProjectService.FileContent thumb = service.readFileThumbnail(TASK, FILE);

        assertEquals("image/jpeg", thumb.contentType());
        assertTrue(thumb.bytes().length > 0);
        assertTrue(thumb.bytes().length < store.get("k/original.png").length,
            "缩略图应比原图小：thumb=" + thumb.bytes().length + " original=" + store.get("k/original.png").length);
        assertEquals(1, originalReads, "第一次必须读原图");
        assertTrue(store.containsKey(THUMB_KEY), "伴生对象应写进对象存储：" + store.keySet());
        verify(ossHelper, times(1)).put(anyString(), any());
    }

    @Test
    @DisplayName("第二次（进程内缓存清空后）：只读伴生对象，不再读原图")
    void secondCallUsesPersistedThumbnail() {
        service.readFileThumbnail(TASK, FILE);
        int readsAfterFirst = originalReads;

        clearMemoryCache();

        ICreativeProjectService.FileContent again = service.readFileThumbnail(TASK, FILE);

        assertEquals("image/jpeg", again.contentType());
        assertEquals(readsAfterFirst, originalReads, "第二次不该再读原图（伴生对象已存在）");
        assertNotNull(store.get(THUMB_KEY));
    }

    @Test
    @DisplayName("原图解不开时如实回落原图字节，且不写伴生对象（不制造假缩略图）")
    void unreadableImageFallsBack() {
        store.put("k/original.png", "not-an-image".getBytes(StandardCharsets.UTF_8));

        ICreativeProjectService.FileContent fallback = service.readFileThumbnail(TASK, FILE);

        assertEquals("not-an-image", new String(fallback.bytes(), StandardCharsets.UTF_8));
        assertFalse(store.containsKey(THUMB_KEY), "解不开的图不该写伴生对象");
    }

    @Test
    @DisplayName("伴生对象写失败不影响返回缩略图（快一点的功能不该变成看不到图）")
    void persistFailureDoesNotBreakResponse() {
        doThrow(new org.dromara.common.core.exception.ServiceException("存储满"))
            .when(ossHelper).put(anyString(), any());

        ICreativeProjectService.FileContent thumb = service.readFileThumbnail(TASK, FILE);

        assertEquals("image/jpeg", thumb.contentType());
        assertTrue(thumb.bytes().length > 0);
        verify(ossHelper, never()).delete(anyString());
    }

    /**
     * 清掉进程内缓存里这个附件的条目（缓存是静态的，测试之间要隔离）。
     *
     * <p>刻意用反射而不是在生产类上开一个"测试专用清缓存方法"：缓存是 private static 的实现细节，
     * 为测试开口子（哪怕是 package-private）更容易被误用。</p>
     */
    @SuppressWarnings("unchecked")
    private static void clearMemoryCache() {
        try {
            java.lang.reflect.Field field = CreativeProjectServiceImpl.class.getDeclaredField("THUMB_CACHE");
            field.setAccessible(true);
            Map<String, byte[]> cache = (Map<String, byte[]>) field.get(null);
            cache.keySet().removeIf(key -> key.startsWith(FILE + ":"));
        } catch (Exception e) {
            throw new IllegalStateException("清缓存失败（测试自身问题）：" + e.getMessage(), e);
        }
    }
}
