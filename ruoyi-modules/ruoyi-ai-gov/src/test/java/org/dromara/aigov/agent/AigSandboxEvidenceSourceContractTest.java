package org.dromara.aigov.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 发布门槛证据来源的契约守卫（2026-10-11 裁定：**训练台测试证据不参与门槛判定**）。
 *
 * <h3>它防的是什么</h3>
 * <p>{@code SANDBOX_RUN} 是五个发布门槛里<b>唯一</b>能证明"这段（外部的、不可信的）代码
 * 真的在隔离环境里跑起来过"的一环，它的判据来源只有 {@code aig_sandbox_run}
 * （宿主侧 worker 的一次性受限容器：退出码 0、未超时、无网）。</p>
 *
 * <p>训练台的"测试调用"落在 {@code aig_studio_execution_link}，那是一次<b>受治理的模型调用</b>，
 * 不是隔离执行。两者的混淆有一个很难发现的失效形态：把测试证据接进门槛后，
 * 编译过、单测过、页面上还是绿灯——**看起来门槛更严了，实际上是唯一一道隔离证据被换成了
 * "模型说这次没问题"**，于是从未在沙箱验证过的外部代码可以顺利发布。</p>
 *
 * <p>所以这条守卫把口径钉成构建期断言：发布门槛代码里<b>不得</b>出现训练台证据表/实体，
 * 且 {@code SANDBOX_RUN} 的断言必须走 {@code sandboxRunEvidence}。想改口径，就得先改这条用例——
 * 那一刻人必须回答"训练台的模型调用算不算沙箱执行"。</p>
 *
 * <p>扫描的是<b>目录</b>而不是写死的文件清单：以后新增的门槛/证据类会自动被纳入，
 * 免得守卫随着文件增长而悄悄漏检。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxEvidenceSourceContractTest {

    /**
     * 发布门槛相关源码所在目录（相对仓库根）
     */
    private static final String GATE_SOURCE_DIR =
        "ruoyi-modules/ruoyi-ai-gov/src/main/java/org/dromara/aigov/agent";

    /**
     * 训练台测试证据的实体名与表名（出现即等于"门槛在用测试证据"）
     */
    private static final List<String> STUDIO_EVIDENCE_TOKENS = List.of(
        "AigStudioExecutionLink", "aig_studio_execution_link");

    /**
     * 至少应扫到的门槛源码文件数（防止"一个都没扫到"也算通过）
     */
    private static final int MIN_SCANNED_FILES = 20;

    @Test
    @DisplayName("★发布门槛不得引用训练台测试证据：那是一次模型调用，不是隔离执行")
    void gateDoesNotConsumeStudioTestEvidence() throws IOException {
        List<Path> files = gateSourceFiles();
        assertTrue(files.size() >= MIN_SCANNED_FILES,
            "门槛源码只扫到 " + files.size() + " 个文件（<" + MIN_SCANNED_FILES + "）：扫描可能已失效");

        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = stripComments(Files.readString(file));
            for (String token : STUDIO_EVIDENCE_TOKENS) {
                if (source.contains(token)) {
                    problems.add(file.getFileName() + " 引用了训练台测试证据（" + token + "）");
                }
            }
        }
        assertTrue(problems.isEmpty(), "发布门槛的证据来源被改动了：" + problems
            + "。SANDBOX_RUN 只能以 aig_sandbox_run 为判据（测试调用 ≠ 沙箱执行）；"
            + "若要改这条口径，请先改本用例并说明理由。");
    }

    @Test
    @DisplayName("SANDBOX_RUN 的正向口径仍在：发布断言确实去查沙箱账本")
    void sandboxGateStillReadsSandboxLedger() throws IOException {
        Path registry = locateRepoRoot().resolve(GATE_SOURCE_DIR)
            .resolve("service/impl/AigAgentRegistryServiceImpl.java");
        assertTrue(Files.isRegularFile(registry), "找不到发布服务实现：" + registry);
        String source = stripComments(Files.readString(registry));

        assertTrue(source.contains("SANDBOX_RUN"), "发布服务应处理 SANDBOX_RUN 门槛");
        assertTrue(source.contains("sandboxRunEvidence("),
            "SANDBOX_RUN 的断言必须走 sandboxRunEvidence（沙箱运行账本）");
    }

    /**
     * 取门槛相关源码文件清单。
     *
     * @return 文件清单
     * @throws IOException 读取失败
     */
    private static List<Path> gateSourceFiles() throws IOException {
        Path dir = locateRepoRoot().resolve(GATE_SOURCE_DIR);
        assertTrue(Files.isDirectory(dir), "门槛源码目录不存在：" + dir);
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
     * 去掉注释行（注释里提到某张表不该算"用了它"）。
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
