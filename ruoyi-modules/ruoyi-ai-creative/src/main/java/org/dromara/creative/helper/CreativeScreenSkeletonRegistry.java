package org.dromara.creative.helper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 屏骨架的加载入口（V0.2 C′）。
 *
 * <p><b>为什么要一个 Spring 组件</b>：骨架要在<b>启动时</b>就校验并打日志（坏契约不能等到生成分镜时才炸），
 * 而使用方 {@link CreativeDraftFactory} 是静态工具类、拿不到 Spring 注入。所以这里做两件事：
 * 启动时加载并校验、把结果放进静态引用供工具类使用。</p>
 *
 * <p><b>外部覆盖</b>：配置 {@code creative.screen-skeleton.path} 指向一个 JSON 文件即可改屏数与构成
 * （不需要重新打包；文件不存在时自动回落内置契约并告警）。</p>
 */
@Slf4j
@Component
public class CreativeScreenSkeletonRegistry {

    private static volatile CreativeScreenSkeleton current;

    /**
     * 外部契约路径（可空；为空用 classpath 内置契约）。
     */
    @Value("${creative.screen-skeleton.path:}")
    private String externalPath;

    /**
     * 启动时加载并校验屏骨架。
     */
    @PostConstruct
    public void init() {
        current = CreativeScreenSkeleton.load(externalPath);
    }

    /**
     * 当前骨架（静态可访问）。
     *
     * <p>Spring 容器没跑时（纯单元测试直接调 {@link CreativeDraftFactory}）走懒加载兜底，
     * 保证工具类在任何上下文里都能拿到与生产一致的默认契约。</p>
     *
     * @return 骨架
     */
    public static CreativeScreenSkeleton skeleton() {
        CreativeScreenSkeleton skeleton = current;
        if (skeleton == null) {
            synchronized (CreativeScreenSkeletonRegistry.class) {
                skeleton = current;
                if (skeleton == null) {
                    skeleton = CreativeScreenSkeleton.load(null);
                    current = skeleton;
                }
            }
        }
        return skeleton;
    }

    /**
     * 覆盖当前骨架（**仅供测试**：用来验证 3 屏/9 屏这类非默认骨架也能走通）。
     *
     * @param skeleton 骨架；传 null 表示恢复默认懒加载
     */
    public static void overrideForTest(CreativeScreenSkeleton skeleton) {
        current = skeleton;
    }
}
