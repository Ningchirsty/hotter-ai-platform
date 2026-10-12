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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * <p><b>还防一类更隐蔽的错</b>：权限串"出现在脚本里"不等于"行真的插进去了"。
 * 每个 insert 都有 {@code where not exists (menu_id = …)} 守卫，所以一旦两条 insert 复用同一个
 * {@code menu_id}，后来的那条会被<b>静默跳过</b>——脚本 exit=0、日志一片正常，而权限点从未创建。
 * 生产实测踩过：{@code aig:agent:resource-binding} 的 menu_id 与同文件里较早的
 * 「Package上传」撞了，于是「工具/知识绑定」的按钮一直是 403。故另加 menu_id 唯一性检查。</p>
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

    /**
     * {@code insert into sys_menu (…)} 的开头（用来把文件切成一条条 insert）
     */
    private static final Pattern SYS_MENU_INSERT =
        Pattern.compile("insert\\s+into\\s+sys_menu\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * insert 里第一个 {@code select <主键>,}
     */
    private static final Pattern FIRST_SELECTED_ID = Pattern.compile("select\\s+(?:ignore\\s+)?(\\d{15,20})\\s*,");

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

    @Test
    @DisplayName("菜单种子的 menu_id 不能撞：撞了会被 where not exists 静默跳过，权限点永远建不出来")
    void menuSeedIdsAreUnique() throws IOException {
        Path sqlDir = locateRepoRoot().resolve("script/sql");
        List<String> problems = new ArrayList<>();
        // id -> permsKey -> 文件清单（跨文件撞 ID 但权限串不同 = 危险；相同则是刻意的重复声明）
        Map<String, Map<String, List<String>>> byId = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(sqlDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".sql")) {
                    continue;
                }
                String sql = Files.readString(file);
                problems.addAll(duplicateMenuIdsInFile(name, sql));
                for (MenuSeed seed : menuSeeds(name, sql)) {
                    byId.computeIfAbsent(seed.id(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(seed.permsKey(), k -> new ArrayList<>())
                        .add(seed.file());
                }
            }
        }
        for (Map.Entry<String, Map<String, List<String>>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add("menu_id " + entry.getKey() + " 在不同文件里被声明成不同的权限行："
                    + entry.getValue() + "（先执行的那份生效，后者被静默跳过）");
            }
        }

        assertTrue(problems.isEmpty(),
            "菜单种子里 menu_id 撞了：" + problems
                + "。每个 insert 都有 `where not exists (menu_id = …)` 守卫，"
                + "所以撞 ID 的那条会被**静默跳过**——脚本 exit=0、看不出任何异常，"
                + "而权限点从未创建 ⇒ 对应接口对所有人 403（生产实测过："
                + "「工具/知识绑定」的 aig:agent:resource-binding 就是这么丢的）。"
                + "换一个该表里没用过的 ID 即可（授权语句是按 perms 反查的，会自己跟上）。");
    }

    @Test
    @DisplayName("撞 ID 的检测器本身要有效（否则这条守卫会静默变成空转）")
    void collisionDetectorItselfWorks() {
        String duplicated = """
            insert into sys_menu (menu_id, menu_name, perms) select 1768000000000000024, 'A', 'aig:a'
              where not exists (select 1 from sys_menu t where t.menu_id = 1768000000000000024);
            insert into sys_menu (menu_id, menu_name, perms) select 1768000000000000024, 'B', 'aig:b'
              where not exists (select 1 from sys_menu t where t.menu_id = 1768000000000000024);
            """;
        List<String> found = duplicateMenuIdsInFile("sample.sql", duplicated);
        assertEquals(1, found.size(), "检测器没抓到样例里的重复 menu_id：" + found);

        String clean = """
            insert into sys_menu (menu_id, menu_name, perms) select 1768000000000000024, 'A', 'aig:a'
              where not exists (select 1 from sys_menu t where t.menu_id = 1768000000000000024);
            insert into sys_menu (menu_id, menu_name, perms) select 1768000000000000027, 'B', 'aig:b'
              where not exists (select 1 from sys_menu t where t.menu_id = 1768000000000000027);
            """;
        assertTrue(duplicateMenuIdsInFile("sample.sql", clean).isEmpty(), "干净的样例被误报");
    }

    /**
     * 找出同一个文件里被两条 {@code insert into sys_menu} 复用的 menu_id。
     *
     * @param fileName 文件名（只用于报错）
     * @param sql      文件内容
     * @return 问题描述；无问题返回空列表
     */
    private static List<String> duplicateMenuIdsInFile(String fileName, String sql) {
        Map<String, Integer> firstSeen = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (MenuSeed seed : menuSeeds(fileName, sql)) {
            Integer previous = firstSeen.putIfAbsent(seed.id(), seed.line());
            if (previous != null) {
                problems.add(fileName + " 里 menu_id " + seed.id()
                    + " 被两条 insert 用了（第 " + previous + " 行与第 " + seed.line() + " 行）");
            }
        }
        return problems;
    }

    /**
     * 拆出文件里每条 {@code insert into sys_menu} 的主键、权限串与行号。
     *
     * @param fileName 文件名
     * @param sql      文件内容
     * @return 种子清单
     */
    private static List<MenuSeed> menuSeeds(String fileName, String sql) {
        List<MenuSeed> seeds = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher insert = SYS_MENU_INSERT.matcher(sql);
        while (insert.find()) {
            starts.add(insert.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : sql.length();
            String block = sql.substring(from, to);
            Matcher id = FIRST_SELECTED_ID.matcher(block);
            if (!id.find()) {
                continue;
            }
            // 权限串取该语句里出现的 aig:*（权限行是 perms 列；页面行是列表权限，同样可比）。
            // 必须去重：同一语句里同一个串可能出现多次（守卫、授权语句），带重复会把
            // "内容完全相同的两份声明"误判成"声明成了不同的权限行"。
            Set<String> perms = new LinkedHashSet<>();
            Matcher perm = PERMISSION.matcher(block);
            while (perm.find()) {
                perms.add(perm.group(1));
            }
            int line = sql.substring(0, from).split("\n", -1).length;
            seeds.add(new MenuSeed(id.group(1), String.join("|", perms), line, fileName));
        }
        return seeds;
    }

    /**
     * 一条菜单种子（用于撞 ID 检测）。
     *
     * @param id       menu_id
     * @param permsKey 该语句里出现的权限串拼接（用于判断"同一个 ID 是不是被声明成了不同的权限行"）
     * @param line     行号
     * @param file     文件名
     */
    private record MenuSeed(String id, String permsKey, int line, String file) {
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
