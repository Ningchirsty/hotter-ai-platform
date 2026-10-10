package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigActionLaunchModeEnum;
import org.dromara.aigov.workspace.enums.AigLaunchTargetTypeEnum;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.aigov.workspace.enums.AigScenarioAdapterEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * routeKey 白名单的契约守卫（增量 0「契约冻结」）。
 *
 * <h3>这条测试防的是什么</h3>
 * <p>岗位包的 Action 会带一个 {@code routeKey}，门户据此跳转。这里有三类"不会报错"的错法：</p>
 * <ol>
 *     <li><b>前后端名单不一致</b>：后端发得出这个键、前端不认识 → 点了没反应；反过来前端有、后端没有 →
 *         那个入口永远配不出来。两边都"各自能跑"。</li>
 *     <li><b>目标路径不存在</b>：键在白名单里，但前端没有那个页面 → 点进去空白。</li>
 *     <li><b>启动方式/目标类型被扩成自由字符串</b>：岗位包里能声明平台没实现的类型。</li>
 * </ol>
 * <p>三者都只能在运行期被用户发现，所以钉到构建期。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteKeyRegistryContractTest {

    /**
     * 前端注册表里 `KEY: {` 形态的条目
     */
    private static final Pattern FRONTEND_ENTRY = Pattern.compile("(?m)^\\s*([A-Z][A-Z0-9_]*)\\s*:\\s*\\{");

    @Test
    @DisplayName("仓库根可定位（定位逻辑本身也要有断言，否则检查会静默空转）")
    void repoRootIsLocatable() {
        Path root = locateRepoRoot();
        assertTrue(Files.isDirectory(root.resolve("script/sql")), "script/sql 应存在：" + root);
        assertTrue(Files.isRegularFile(root.resolve("frontend/src/config/aiWorkspaceRouteRegistry.ts")),
            "前端注册表应存在：" + root);
    }

    @Test
    @DisplayName("★ 前后端 routeKey 集合必须完全一致（不一致的表现是「点了没反应」或「配不出来」）")
    void frontendAndBackendKeySetsMatch() throws IOException {
        Set<String> backend = new LinkedHashSet<>(AigRouteKeyRegistry.keys());
        Set<String> frontend = frontendKeys();
        assertFalse(backend.isEmpty(), "后端白名单为空：检查登记逻辑");
        assertFalse(frontend.isEmpty(), "前端白名单读不到：检查解析方式（文件结构可能变了）");
        assertEquals(backend, frontend,
            "routeKey 集合不一致。只在前端：" + difference(frontend, backend)
                + "；只在后端：" + difference(backend, frontend));
    }

    @Test
    @DisplayName("★ 每个 routeKey 的目标路径都必须能在菜单脚本里找到对应组件（否则「点进去空白」）")
    void everyTargetResolvesToARealMenuComponent() throws IOException {
        Set<String> sqlText = menuSqlText();
        assertFalse(sqlText.isEmpty(), "没读到任何菜单脚本：检查扫描方式");
        for (AigRouteKeyRegistry.RouteTarget target : AigRouteKeyRegistry.all()) {
            String path = target.frontendPath();
            assertNotNull(path, target.routeKey() + " 缺路径");
            assertTrue(path.startsWith("/"), target.routeKey() + " 的路径应以 / 开头，实际=" + path);
            // 前端页面的组件路径约定：菜单里的 component = 路径去掉前导斜杠 + /index
            String component = path.substring(1) + "/index";
            assertTrue(sqlText.stream().anyMatch(sql -> sql.contains("'" + component + "'")),
                target.routeKey() + " 的目标组件 '" + component + "' 在任何菜单脚本里都找不到："
                    + "岗位工作台会给出一个点进去空白的入口");
            assertTrue(org.dromara.common.core.utils.StringUtils.isNotBlank(target.frontendRouteName()),
                target.routeKey() + " 缺前端路由名");
        }
    }

    @Test
    @DisplayName("不在白名单里的键必须解析不到（调用方据此拒绝，不许猜路径）")
    void unknownKeysAreRejected() {
        assertFalse(AigRouteKeyRegistry.contains("NOT_A_REAL_KEY"));
        assertNull(AigRouteKeyRegistry.find("NOT_A_REAL_KEY"));
        assertFalse(AigRouteKeyRegistry.contains(null));
        assertNull(AigRouteKeyRegistry.find(null));
        assertTrue(AigRouteKeyRegistry.contains("CREATIVE_PROJECT"));
        assertNotNull(AigRouteKeyRegistry.find("CREATIVE_PROJECT"));
    }

    @Test
    @DisplayName("启动方式与目标类型是封闭集合（扩值必须同时改这里，不能悄悄加）")
    void launchEnumsAreClosedSets() {
        assertEquals(Set.of("QUICK", "FORM", "STUDIO", "NAVIGATION"), codesOf(AigActionLaunchModeEnum.values()));
        assertEquals(Set.of("SCENARIO", "QUICK_CAPABILITY", "NAVIGATION"),
            codesOf(AigLaunchTargetTypeEnum.values()));
        assertEquals(Set.of("DRAFT", "TESTING", "PUBLISHED", "DISABLED"),
            codesOf(AigRoleReleaseStatusEnum.values()));
        assertEquals(Set.of("CREATIVE_EXISTING_FLOW", "VIDEO_EXISTING_FLOW",
                "CONTENT_EXISTING_FLOW", "NONE"),
            codesOf(AigScenarioAdapterEnum.values()));
    }

    /**
     * 取枚举的 code 集合。
     *
     * @param values 枚举值
     * @return code 集合
     */
    private static Set<String> codesOf(AigScenarioAdapterEnum[] values) {
        Set<String> codes = new LinkedHashSet<>();
        for (AigScenarioAdapterEnum item : values) {
            codes.add(item.getCode());
        }
        return codes;
    }

    /**
     * 取枚举的 code 集合。
     *
     * @param values 枚举值
     * @return code 集合
     */
    private static Set<String> codesOf(AigActionLaunchModeEnum[] values) {
        Set<String> codes = new LinkedHashSet<>();
        for (AigActionLaunchModeEnum item : values) {
            codes.add(item.getCode());
        }
        return codes;
    }

    /**
     * 取枚举的 code 集合。
     *
     * @param values 枚举值
     * @return code 集合
     */
    private static Set<String> codesOf(AigLaunchTargetTypeEnum[] values) {
        Set<String> codes = new LinkedHashSet<>();
        for (AigLaunchTargetTypeEnum item : values) {
            codes.add(item.getCode());
        }
        return codes;
    }

    /**
     * 取枚举的 code 集合。
     *
     * @param values 枚举值
     * @return code 集合
     */
    private static Set<String> codesOf(AigRoleReleaseStatusEnum[] values) {
        Set<String> codes = new LinkedHashSet<>();
        for (AigRoleReleaseStatusEnum item : values) {
            codes.add(item.getCode());
        }
        return codes;
    }

    /**
     * 读前端注册表里的键。
     *
     * @return 键集合
     * @throws IOException 读取失败
     */
    private static Set<String> frontendKeys() throws IOException {
        Path file = locateRepoRoot().resolve("frontend/src/config/aiWorkspaceRouteRegistry.ts");
        String text = Files.readString(file);
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = FRONTEND_ENTRY.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * 把所有菜单脚本读成一份文本（用于找组件路径）。
     *
     * @return 全部 SQL 文本
     * @throws IOException 读取失败
     */
    private static Set<String> menuSqlText() throws IOException {
        Set<String> texts = new LinkedHashSet<>();
        Path sqlDir = locateRepoRoot().resolve("script/sql");
        try (Stream<Path> files = Files.list(sqlDir)) {
            for (Path file : files.toList()) {
                if (file.getFileName().toString().endsWith(".sql")) {
                    texts.add(Files.readString(file));
                }
            }
        }
        return texts;
    }

    /**
     * 差集（A 有 B 没有）。
     *
     * @param a 集合 A
     * @param b 集合 B
     * @return A-B
     */
    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> diff = new LinkedHashSet<>(a);
        diff.removeAll(b);
        return diff;
    }

    /**
     * 从运行目录往上找含 {@code script/sql} 的仓库根。
     *
     * @return 仓库根
     */
    private static Path locateRepoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("script/sql"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        fail("找不到含 script/sql 的仓库根，user.dir=" + System.getProperty("user.dir"));
        return null;
    }

}
