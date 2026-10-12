package org.dromara.aigov.deploy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
 * 生产发布工作流「存量库补齐脚本」清单的覆盖守卫。
 *
 * <p><b>这条检查防的是什么</b>：{@code deploy-poc.yml} 在切换后端镜像<b>之前</b>会对生产库
 * 重放一批"只新增"脚本（{@code Prepare additive runtime schema}）。新镜像里的 Mapper 一定比
 * 线上库新——少一列的后果是：<b>部署本身完全成功</b>（脚本 exit=0、容器健康检查过、版本号也对），
 * 但相关接口在运行期 500。而这个 500 极难定位，因为 RuoYi 把 SQL 异常统一掩成
 * 「发生未知异常，请联系管理员」——响应体和日志里都看不到缺的是哪一列。</p>
 *
 * <p>更糟的是它<b>不会在开发环境暴露</b>：开发库是照 {@code script/sql} 建的全新库，列一直齐；
 * 只有"从旧版本升上来、且没跑过补列脚本"的库才会中招，而那正是生产库。</p>
 *
 * <p><b>为什么补列脚本当初被漏掉、又为什么可以安全补回去</b>：建表脚本用
 * {@code CREATE TABLE IF NOT EXISTS}，重复执行无害，所以最早很自然地被收进了清单；
 * 而补列脚本写的是 {@code ALTER TABLE … ADD COLUMN}，MySQL 8.0 没有
 * {@code ADD COLUMN IF NOT EXISTS}，看起来"不能重放"。实际上这些脚本一律用
 * {@code information_schema} 判断 + {@code PREPARE} 动态执行 DDL 的方式实现幂等，
 * 是可以重复执行的（{@code aig_sandbox_run_attestation.sql} 等 11 份都是这个写法）。
 * 于是它们被遗漏的真实原因只是"没被想起来"，而遗漏的代价恰好最大。</p>
 *
 * <p>所以这里把两件事都钉住：</p>
 * <ol>
 *     <li>{@code script/sql} 下每个含真实 {@code ALTER TABLE} 的 {@code aig_*} 脚本
 *         <b>都必须在重放清单里</b>；</li>
 *     <li>清单里的每个脚本<b>必须自己幂等</b>——否则第二次部署会以
 *         {@code Duplicate column name} 直接失败（在切换镜像之前失败，好在是 fail-fast）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigDeploySchemaReplayCoverageTest {

    /**
     * 发布工作流（它自己会对生产库重放一批"只新增"脚本）
     */
    private static final String WORKFLOW = ".github/workflows/deploy-poc.yml";

    /**
     * 重放清单：{@code for migration in \ <文件...> ; do}
     */
    private static final Pattern REPLAY_BLOCK =
        Pattern.compile("for\\s+migration\\s+in\\s+(.*?);\\s*do", Pattern.DOTALL);

    /**
     * 清单里的一个脚本名。
     */
    private static final Pattern MIGRATION_FILE = Pattern.compile("[A-Za-z0-9_]+\\.sql");

    /**
     * 真实的 {@code ALTER TABLE}。注意：这些脚本把 DDL 写成<b>字符串</b>再交给
     * {@code PREPARE}（幂等所需），所以这里匹配的关键字出现在字符串里，属预期。
     */
    private static final Pattern ALTER_TABLE = Pattern.compile("ALTER\\s+TABLE", Pattern.CASE_INSENSITIVE);

    /**
     * 建表脚本的幂等形态。
     */
    private static final Pattern IF_NOT_EXISTS = Pattern.compile("IF\\s+NOT\\s+EXISTS", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("★ 每个含 ALTER TABLE 的 aig_* 迁移都必须在发布工作流的重放清单里"
        + "（漏了 = 部署成功但接口 500，且异常被 RuoYi 掩成「发生未知异常」）")
    void everyAdditiveAlterMigrationIsReplayed() throws IOException {
        Path root = locateRepoRoot();
        Set<String> replayed = replayedMigrations(readWorkflow(root));
        assertFalse(replayed.isEmpty(),
            "没读到重放清单（" + WORKFLOW + "）：工作流的写法变了，这个守卫不能静默通过");

        List<String> missing = new ArrayList<>();
        for (String file : alterMigrations(root)) {
            if (!replayed.contains(file)) {
                missing.add(file);
            }
        }
        assertTrue(missing.isEmpty(),
            "以下补列迁移不在 " + WORKFLOW + " 的 `for migration in …` 清单里：" + missing
                + "。工作流在切镜像前只会重放清单里的脚本，所以线上库会缺这些列——"
                + "部署照样成功，运行期接口 500，而 RuoYi 把 SQL 异常掩成「发生未知异常，请联系管理员」，"
                + "响应体和日志都看不出缺哪一列。这些脚本都用 information_schema + PREPARE 做了幂等，"
                + "可安全重放，请把它们加进清单。");
    }

    @Test
    @DisplayName("★ 被重放的脚本必须自己幂等（补列要 information_schema+PREPARE，建表要 IF NOT EXISTS）")
    void replayedMigrationsAreIdempotent() throws IOException {
        Path root = locateRepoRoot();
        List<String> notIdempotent = new ArrayList<>();

        for (String file : replayedMigrations(readWorkflow(root))) {
            Path path = root.resolve("script/sql").resolve(file);
            if (!Files.isRegularFile(path)) {
                // 存在性由工作流里的 `test -s` 保证（部署时它会先报错），这里不重复判
                continue;
            }
            String sql = Files.readString(path);
            boolean alter = ALTER_TABLE.matcher(sql).find();
            boolean guarded = sql.contains("information_schema")
                && Pattern.compile("PREPARE", Pattern.CASE_INSENSITIVE).matcher(sql).find();
            boolean ifNotExists = IF_NOT_EXISTS.matcher(sql).find();
            if (alter ? !guarded : !ifNotExists) {
                notIdempotent.add(file + (alter ? "（含 ALTER TABLE，但缺 information_schema/PREPARE 守卫）"
                    : "（建表但没有 IF NOT EXISTS）"));
            }
        }

        assertTrue(notIdempotent.isEmpty(),
            "以下被重放的脚本不是幂等的：" + notIdempotent
                + "。工作流<b>每次</b>部署都会重放这批脚本，不幂等意味着第二次部署会在切镜像之前"
                + "以 Duplicate column name / Table already exists 失败。"
                + "补列请照 aig_sandbox_run_attestation.sql 的 information_schema + PREPARE 写法，"
                + "建表请用 CREATE TABLE IF NOT EXISTS。");
    }

    @Test
    @DisplayName("仓库根能定位到（定位逻辑本身也要有断言，否则守卫会静默变成空转）")
    void repoRootIsLocatable() {
        Path root = locateRepoRoot();
        assertNotNull(root);
        assertTrue(Files.isDirectory(root.resolve("script/sql")), "script/sql 应存在：" + root);
        assertTrue(Files.isRegularFile(root.resolve(WORKFLOW)), WORKFLOW + " 应存在：" + root);
    }

    @Test
    @DisplayName("清单解析器本身要有效（否则这条守卫会静默变成空转）")
    void replayListParserItselfWorks() {
        String sample = """
            - name: Prepare additive runtime schema before switching backend
              run: |
                for migration in \\
                  aig_one.sql aig_two.sql \\
                  cp_three.sql ry_four.sql; do
                  test -s "script/sql/$migration"
                done
            """;
        Set<String> parsed = replayedMigrations(sample);
        assertTrue(parsed.containsAll(List.of("aig_one.sql", "aig_two.sql", "cp_three.sql", "ry_four.sql")),
            "解析器没读全样例里的脚本名：" + parsed);
        assertFalse(parsed.contains("script/sql"), "解析器把路径当成了脚本名：" + parsed);
        assertTrue(replayedMigrations("no list here").isEmpty(), "干净样例被误报");
    }

    /**
     * 读出工作流里被重放的脚本清单。
     *
     * @param workflowFile 工作流全文
     * @return 脚本文件名集合（不含路径）
     */
    private static Set<String> replayedMigrations(String workflowFile) {
        Set<String> files = new LinkedHashSet<>();
        Matcher block = REPLAY_BLOCK.matcher(workflowFile);
        while (block.find()) {
            Matcher file = MIGRATION_FILE.matcher(block.group(1));
            while (file.find()) {
                files.add(file.group());
            }
        }
        return files;
    }

    /**
     * 找出 {@code script/sql} 下所有含真实 {@code ALTER TABLE} 的 {@code aig_*} 脚本。
     *
     * @param root 仓库根
     * @return 文件名集合
     * @throws IOException 读取失败
     */
    private static Set<String> alterMigrations(Path root) throws IOException {
        Set<String> files = new LinkedHashSet<>();
        try (Stream<Path> entries = Files.list(root.resolve("script/sql"))) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (!name.startsWith("aig_") || !name.endsWith(".sql")) {
                    continue;
                }
                if (ALTER_TABLE.matcher(Files.readString(entry)).find()) {
                    files.add(name);
                }
            }
        }
        return files;
    }

    /**
     * 读工作流文件（读不到直接 fail：这个守卫不能跳过）。
     *
     * @param root 仓库根
     * @return 全文
     */
    private static String readWorkflow(Path root) {
        try {
            return Files.readString(root.resolve(WORKFLOW));
        } catch (Exception e) {
            return fail("读不到 " + WORKFLOW + "：" + e);
        }
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
