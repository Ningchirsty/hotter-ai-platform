package org.dromara.aigov.workspace.portal;

import org.dromara.aigov.constant.AigConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 门户契约守卫（主文档线增量 5 收尾）。
 *
 * <h3>它防的是什么</h3>
 * <p>门户（员工工作台）是**刻意不带权限点**的：它只返回"当前用户自己"能看到的东西，
 * 范围由数据决定（文档 §6.1 与各 controller 的注释都写了理由）。这个决定有一个很难发现的失效方式：
 * 某天有人"顺手"给门户端点加上 {@code @SaCheckPermission("aig:portal:view")}——
 * 编译通过、单测通过（控制器测试通常不挂真实鉴权），运行期表现是
 * <b>所有员工点开工作台都是空白</b>，而日志里只有一行 403。
 *
 * <p>所以这里把"门户只用 {@code @SaCheckLogin}"钉成构建期断言：要加权限点，就必须先改这条用例——
 * 而改它的那一刻，人就不得不回答"这个权限点授给谁"。</p>
 *
 * <p>扫描的是**目录**而不是写死的文件清单：新加的门户 controller 会自动被纳入，
 * 免得守卫随着文件增长而悄悄漏检（{@code check-script-setup.mjs} 就是被这条教训改过的）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalContractTest {

    /**
     * 面向员工的门户 controller 所在目录（相对仓库根）
     */
    private static final List<String> PORTAL_CONTROLLER_DIRS = List.of(
        "ruoyi-modules/ruoyi-ai-gov/src/main/java/org/dromara/aigov/workspace/portal/controller",
        "ruoyi-modules/ruoyi-ai-gov/src/main/java/org/dromara/aigov/workspace/launch/controller");

    /**
     * 端点的映射注解（用于判断"这里有几个端点"）
     */
    private static final List<String> MAPPINGS = List.of(
        "@GetMapping(", "@PostMapping(", "@PutMapping(", "@DeleteMapping(");

    @Test
    @DisplayName("★门户端点只用 @SaCheckLogin：加权限点会让所有员工 403，且编译期/单测都发现不了")
    void portalEndpointsAreLoginOnly() throws IOException {
        List<Path> files = portalControllerFiles();
        assertFalse(files.isEmpty(), "没扫到任何门户 controller：检查扫描逻辑（守卫不能空转）");

        int endpoints = 0;
        int logins = 0;
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = stripComments(Files.readString(file));
            int here = countAny(source, MAPPINGS);
            int loginHere = count(source, "@SaCheckLogin");
            endpoints += here;
            logins += loginHere;
            String name = file.getFileName().toString();
            if (here == 0) {
                problems.add(name + " 没有任何端点（是不是放错了目录？）");
            }
            if (loginHere != here) {
                problems.add(name + " 端点数 " + here + " 与 @SaCheckLogin 数 " + loginHere + " 不一致");
            }
            // 查的是注解名本身（不带 @ 前缀）：写成全限定名 @cn.dev33...SaCheckPermission 也拦得住——
            // 只查 "@SaCheckPermission(" 会让"换个写法"悄悄绕过守卫
            if (source.contains("SaCheckPermission")) {
                problems.add(name + " 出现了 SaCheckPermission：门户是自范围接口，加权限点会让所有员工 403");
            }
        }
        assertTrue(problems.isEmpty(), "门户鉴权契约被破坏：" + problems);
        assertTrue(endpoints >= 9, "门户端点数应不少于 9（实际 " + endpoints + "）：扫描可能已失效");
        assertEquals(endpoints, logins, "每个门户端点都应带 @SaCheckLogin");
    }

    @Test
    @DisplayName("门户不声明任何 aig:portal:* 权限常量（常量一旦存在，迟早会被种进菜单并掐死入口）")
    void noPortalPermissionConstants() throws IllegalAccessException {
        List<String> found = new ArrayList<>();
        for (Field field : AigConstants.class.getDeclaredFields()) {
            if (!field.getName().startsWith("PERM_") || field.getType() != String.class
                || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String value = (String) field.get(null);
            if (value != null && value.startsWith("aig:portal:")) {
                found.add(field.getName() + "=" + value);
            }
        }
        assertTrue(found.isEmpty(), "门户不应有权限点常量：" + found
            + "。若确实要加，请先改这条用例并回答「这个权限点授给谁」——"
            + "默认不授权的结果是所有员工打不开工作台。");
    }

    @Test
    @DisplayName("仓库根可定位（否则上面的扫描会静默变成空转）")
    void repoRootIsLocatable() {
        Path root = locateRepoRoot();
        assertTrue(Files.isDirectory(root.resolve("script/sql")), "script/sql 应存在：" + root);
        assertTrue(Files.isDirectory(root.resolve("ruoyi-modules/ruoyi-ai-gov")), "模块目录应存在：" + root);
    }

    /**
     * 取门户 controller 文件清单。
     *
     * @return 文件清单
     * @throws IOException 读取失败
     */
    private static List<Path> portalControllerFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String relative : PORTAL_CONTROLLER_DIRS) {
            Path dir = locateRepoRoot().resolve(relative);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    if (file.getFileName().toString().endsWith("Controller.java")) {
                        files.add(file);
                    }
                }
            }
        }
        return files;
    }

    /**
     * 去掉注释行（注释里提到某个注解不该算"用了它"）。
     *
     * @param source 源码
     * @return 去掉注释行后的源码
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * 统计一组子串出现的总次数。
     *
     * @param source 源码
     * @param tokens 子串
     * @return 次数
     */
    private static int countAny(String source, List<String> tokens) {
        int total = 0;
        for (String token : tokens) {
            total += count(source, token);
        }
        return total;
    }

    /**
     * 统计子串出现次数。
     *
     * @param source 源码
     * @param token  子串
     * @return 次数
     */
    private static int count(String source, String token) {
        int total = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            total++;
            index = source.indexOf(token, index + token.length());
        }
        return total;
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
