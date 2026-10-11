package org.dromara.aigov.workspace.launch;

import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.dromara.aigov.workspace.launch.helper.AigLaunchChecklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 启动链与「运行时健康」的边界守卫（2026-10-11 裁定：**启动链不做健康拦截**）。
 *
 * <h3>它防的是什么</h3>
 * <p>{@code RUNTIME_UNHEALTHY} 的判定今天只有一处：路由引擎。口径是
 * <b>只在明确「坏」（{@code DOWN}/{@code UNHEALTHY}）时排除候选，{@code UNKNOWN}/null/{@code DEGRADED}
 * 一律放行</b>——理由写在 {@code AigRouteServiceImpl}：{@code health_status} 由人工点一下连通性测试
 * 才写入，绝大多数模型从未测过，要求 UP 等于把"没测过"判成不可用，会让所有既有路由失效。
 * 该口径由 {@code AigRouteServiceImplHealthTest} 逐条钉住。</p>
 *
 * <p>风险在于**再长出一份判定**：如果启动链也去读健康（或跑一次路由解析）来拦，
 * 两份口径迟早分叉（"预检说不行、执行说行"），而这正是本项目反复踩过的那类不一致。
 * 更糟的是启动链若把"健康未知"当成不可用，会让**所有**从未测过的模型对应的卡片都点不动。</p>
 *
 * <p>所以启动链只保留一个<b>纯透传</b>的布尔位（{@link AigLaunchChecklist.Input#runtimeUnhealthy()}）：
 * 谁能把它置为 true 是一个独立决定，今天的答案是谁都不置（恒 false），健康判定留给下游执行点。
 * 本用例把这条边界钉成构建期断言：启动链源码不得出现健康数据源/路由解析入口。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchHealthBoundaryTest {

    /**
     * 启动链源码目录（相对仓库根）
     */
    private static final String LAUNCH_SOURCE_DIR =
        "ruoyi-modules/ruoyi-ai-gov/src/main/java/org/dromara/aigov/workspace/launch";

    /**
     * 出现在启动链里就说明"它开始自己判健康/自己做路由解析了"
     */
    private static final List<String> HEALTH_SOURCE_TOKENS = List.of(
        "aig_model_governance", "AigModelGovernance", "healthStatus", "HealthStatus", "HEALTH_BAD",
        // dryRun = 启动链自己跑一次路由解析；那是 B 方案（启动前预检），必须先改本用例
        "dryRun");

    /**
     * 至少应扫到的启动链源码文件数（防止"一个都没扫到"也算通过）
     */
    private static final int MIN_SCANNED_FILES = 15;

    @Test
    @DisplayName("★启动链不得自判健康：健康判定只留在路由引擎一处（未知≠不可用）")
    void launchChainDoesNotJudgeHealthItself() throws IOException {
        List<Path> files = launchSourceFiles();
        assertTrue(files.size() >= MIN_SCANNED_FILES,
            "启动链源码只扫到 " + files.size() + " 个文件（<" + MIN_SCANNED_FILES + "）：扫描可能已失效");

        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = stripComments(Files.readString(file));
            for (String token : HEALTH_SOURCE_TOKENS) {
                if (source.contains(token)) {
                    problems.add(file.getFileName() + " 引用了健康数据源/路由解析（" + token + "）");
                }
            }
        }
        assertTrue(problems.isEmpty(), "启动链开始自己判健康了：" + problems
            + "。判定只保留在路由引擎（AigRouteServiceImpl + AigRouteServiceImplHealthTest），"
            + "口径是「只在 DOWN/UNHEALTHY 时排除，未知一律放行」；"
            + "启动链只允许透传 AigLaunchChecklist 的那个布尔位。若要改成启动前预检，请先改本用例。");
    }

    @Test
    @DisplayName("透传位仍然可用：布尔位为 true 时只报 RUNTIME_UNHEALTHY，为 false 时放行")
    void runtimeUnhealthyFlagIsAPassThrough() {
        assertTrue(AigLaunchChecklist.check(input(false)).isEmpty(),
            "健康标志为 false 时不应因此拦截（未知/正常都不算不可用）");
        assertEquals(List.of(AigLaunchErrorEnum.RUNTIME_UNHEALTHY),
            AigLaunchChecklist.check(input(true)),
            "布尔位为 true 时应报 RUNTIME_UNHEALTHY——机制保留，只是今天没有地方把它置 true");
    }

    /**
     * 造一份"除健康标志外都合规"的输入。
     *
     * @param runtimeUnhealthy 运行时不可用标志
     * @return 输入
     */
    private static AigLaunchChecklist.Input input(boolean runtimeUnhealthy) {
        return new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY", "cap/x", null, List.of(),
            Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{}", true, false, runtimeUnhealthy);
    }

    /**
     * 取启动链源码文件清单。
     *
     * @return 文件清单
     * @throws IOException 读取失败
     */
    private static List<Path> launchSourceFiles() throws IOException {
        Path dir = locateRepoRoot().resolve(LAUNCH_SOURCE_DIR);
        assertTrue(Files.isDirectory(dir), "启动链源码目录不存在：" + dir);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path file : stream.toList()) {
                if (file.getFileName().toString().endsWith(".java")) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    /**
     * 去掉注释行（注释里提到某个数据源不该算"用了它"）。
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
