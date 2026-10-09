package org.dromara.aigov.task.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 回调入口的<b>部署前提</b>守卫。
 *
 * <p><b>为什么这种测试必须存在</b>：回调端点是唯一不需要登录态就能到达的写路径，
 * 而它能不能工作取决于两处<b>配置</b>，两处都不会因为代码写错而报错——只会在生产上表现为
 * 「回调永远失败」，而且失败信号极具误导性：</p>
 * <ul>
 *     <li>漏了 {@code security.excludes} → Sa-Token 拦截器先要求登录，回调在进入业务代码前
 *         就被判「未登录」；</li>
 *     <li>漏了 {@code xss.excludeUrls} → XSS 过滤器重写请求体，<b>所有签名都对不上</b>，
 *         而排查方向会先落到「密钥配错了/算法不对」上（本轮就是先把这两条想清楚才敢加端点）。</li>
 * </ul>
 * <p>这两种失效在单测里完全不可见（单测直接调控制器方法，不经过任何过滤器），
 * 所以只能靠读配置文件来守。断言也包括「文件确实被读到、块确实存在」——
 * 否则文件改名或键被挪走时，这个守卫会变成静默空转。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskCallbackTransportGuardTest {

    /**
     * 回调路径（与 {@code AigTaskCallbackController} 的映射一致）。
     */
    private static final String CALLBACK_PATH = "/aigov/task/callback";

    @Test
    @DisplayName("★ 回调路径必须同时进 security.excludes 与 xss.excludeUrls（否则回调永远失败且故障信号误导）")
    void callbackPathIsExcludedFromSessionAndXss() {
        Path yml = resourcesDir().resolve("application.yml");
        List<String> lines = readLines(yml);

        List<String> securityBlock = block(lines, "security:", "mybatis-plus:");
        List<String> xssBlock = block(lines, "xss:", "lock4j:");

        assertTrue(securityBlock.stream().anyMatch(l -> l.trim().startsWith("- " + CALLBACK_PATH)),
            "security.excludes 里缺 " + CALLBACK_PATH + "：Sa-Token 会先要求登录，回调进不到业务代码。"
                + "当前块=" + securityBlock);
        assertTrue(xssBlock.stream().anyMatch(l -> l.trim().startsWith("- " + CALLBACK_PATH)),
            "xss.excludeUrls 里缺 " + CALLBACK_PATH + "：XSS 过滤器会重写请求体，"
                + "所有签名都对不上（看起来像密钥配错）。当前块=" + xssBlock);
    }

    @Test
    @DisplayName("★ prod 若重定义了这两个列表，也必须带上回调路径（否则生产上单独失效）")
    void prodProfileDoesNotDropTheExclusion() {
        Path prod = resourcesDir().resolve("application-prod.yml");
        List<String> lines = readLines(prod);
        boolean redefinesSecurity = lines.stream().anyMatch(l -> l.startsWith("security:"));
        boolean redefinesXss = lines.stream().anyMatch(l -> l.startsWith("xss:"));

        if (redefinesSecurity) {
            List<String> block = block(lines, "security:", "mybatis-plus:");
            assertTrue(block.stream().anyMatch(l -> l.trim().startsWith("- " + CALLBACK_PATH)),
                "application-prod.yml 重定义了 security.excludes，但没带 " + CALLBACK_PATH
                    + "：基础配置的排除会被整体覆盖掉。当前块=" + block);
        }
        if (redefinesXss) {
            List<String> block = block(lines, "xss:", "lock4j:");
            assertTrue(block.stream().anyMatch(l -> l.trim().startsWith("- " + CALLBACK_PATH)),
                "application-prod.yml 重定义了 xss.excludeUrls，但没带 " + CALLBACK_PATH
                    + "。当前块=" + block);
        }
        assertFalse(redefinesSecurity && redefinesXss && lines.isEmpty(),
            "配置文件读空了：这个守卫不该在什么都没读到的情况下通过");
    }

    /**
     * 定位 {@code ruoyi-admin/src/main/resources}。
     *
     * @return 资源目录
     */
    private static Path resourcesDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("ruoyi-admin/src/main/resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return fail("找不到 ruoyi-admin/src/main/resources，user.dir=" + System.getProperty("user.dir"));
    }

    /**
     * 读文件（失败直接 fail：文件读不到时守卫必须响，而不是跳过）。
     *
     * @param path 文件
     * @return 行列表
     */
    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (Exception e) {
            return fail("读不到 " + path + "：" + e);
        }
    }

    /**
     * 取一个顶层键下的行（到下一个给定顶层键为止）。
     *
     * @param lines 全文件行
     * @param start 起始键（形如 {@code security:}）
     * @param end   结束键（下一个顶层键）
     * @return 该块的行
     */
    private static List<String> block(List<String> lines, String start, String end) {
        List<String> collected = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith(start)) {
                inside = true;
                continue;
            }
            if (inside && line.startsWith(end)) {
                return collected;
            }
            if (inside) {
                collected.add(line);
            }
        }
        if (collected.isEmpty()) {
            fail("没找到配置块 " + start + "（到 " + end + " 为止）——键被改名或文件结构变了，"
                + "这个守卫不能静默通过");
        }
        return collected;
    }

}
