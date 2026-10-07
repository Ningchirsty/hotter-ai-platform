package org.dromara.aigov.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 权限常量与菜单种子的覆盖率检查（设计 §2.2 四层分权、§9.1 菜单设计）。
 *
 * <p><b>这条检查防的是什么</b>：{@code @SaCheckPermission} 里的权限串只有在
 * {@code sys_menu.perms} 里存在、且被授予某个角色时才有意义。少种一个的后果是
 * <b>该接口对所有人 403</b>——包括管理员。而这个错误：
 * <ul>
 *     <li>编译期发现不了（权限串只是个字符串）；</li>
 *     <li>单元测试发现不了（Controller 测试通常不挂真实鉴权）；</li>
 *     <li>运行期只会表现成"点了没反应"，排查要从 Sa-Token 的权限集合倒着查。</li>
 * </ul>
 * 所以把它做成构建期就能红的检查：<b>每个 PERM_* 常量都必须在某个菜单/权限脚本里出现</b>。</p>
 *
 * <p>检查的是「有种子」而不是「已授权给某角色」：授权范围是运维决定（谁该有发布权是业务问题），
 * 而"这个权限串平台认不认识"是代码问题。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPermissionSeedCoverageTest {

    /**
     * 权限串的形态（与 {@code @SaCheckPermission} 里用的一致）
     */
    private static final Pattern PERMISSION = Pattern.compile("'(aig:[a-z:_-]+)'");

    @Test
    @DisplayName("每个权限常量都必须在菜单脚本里种子，否则接口对所有人 403")
    void everyPermissionConstantIsSeeded() throws Exception {
        Set<String> declared = declaredPermissions();
        assertFalse(declared.isEmpty(), "没有读到任何 PERM_* 常量：检查读取逻辑");

        Set<String> seeded = seededPermissions(locateRepoRoot());
        List<String> missing = new ArrayList<>();
        for (String permission : declared) {
            if (!seeded.contains(permission)) {
                missing.add(permission);
            }
        }
        assertTrue(missing.isEmpty(),
            "以下权限在 AigConstants 里声明了、但没有任何菜单/权限脚本种它：" + missing
                + "。后果是该接口对所有人都 403（Sa-Token 按 perms 判定），"
                + "而这个错只会在运行期表现成「点了没反应」。请把它加进 script/sql 下的菜单/权限脚本，"
                + "并决定授予哪些角色。");
    }

    @Test
    @DisplayName("权限常量不重复：两个常量指同一个串会让授权范围变成猜谜")
    void permissionConstantsAreUnique() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicated = new ArrayList<>();
        for (Field field : AigConstants.class.getDeclaredFields()) {
            if (!isPermissionConstant(field)) {
                continue;
            }
            String value = (String) field.get(null);
            if (!seen.add(value)) {
                duplicated.add(field.getName() + "=" + value);
            }
        }
        assertTrue(duplicated.isEmpty(), "权限常量重复定义：" + duplicated);
    }

    @Test
    @DisplayName("仓库根能定位到（定位逻辑本身也要有断言，否则检查会静默变成空转）")
    void repoRootIsLocatable() {
        Path root = locateRepoRoot();
        assertNotNull(root);
        assertTrue(Files.isDirectory(root.resolve("script/sql")), "script/sql 应存在：" + root);
    }

    /**
     * 读出 {@code AigConstants} 里全部权限常量。
     *
     * @return 权限串集合
     * @throws IllegalAccessException 反射读取失败
     */
    private static Set<String> declaredPermissions() throws IllegalAccessException {
        Set<String> permissions = new LinkedHashSet<>();
        for (Field field : AigConstants.class.getDeclaredFields()) {
            if (isPermissionConstant(field)) {
                permissions.add((String) field.get(null));
            }
        }
        return permissions;
    }

    /**
     * 是否为权限常量（{@code PERM_} 前缀的静态 String）。
     *
     * @param field 字段
     * @return 是则 true
     */
    private static boolean isPermissionConstant(Field field) {
        return field.getName().startsWith("PERM_") && field.getType() == String.class
            && Modifier.isStatic(field.getModifiers());
    }

    /**
     * 扫描菜单/权限脚本，收集已种子的权限串。
     *
     * @param repoRoot 仓库根
     * @return 权限串集合
     * @throws IOException 读取失败
     */
    private static Set<String> seededPermissions(Path repoRoot) throws IOException {
        Set<String> permissions = new LinkedHashSet<>();
        Path sqlDir = repoRoot.resolve("script/sql");
        try (Stream<Path> files = Files.list(sqlDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".sql") || (!name.contains("menu") && !name.contains("perm"))) {
                    continue;
                }
                Matcher matcher = PERMISSION.matcher(Files.readString(file));
                while (matcher.find()) {
                    permissions.add(matcher.group(1));
                }
            }
        }
        return permissions;
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
