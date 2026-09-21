package org.dromara.ai.image.service;

import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdbcImageWorkflowVersionRepository} 的 SQL/参数一致性测试（离线，不连库）。
 *
 * <p><b>为什么需要它</b>：这个类曾经因为「SQL 里 14 个占位符、参数传了 15 个」在运行期抛
 * {@code Parameter index out of range (15 > number of parameters, which is 14)}，
 * 导致契约→镜像表同步整条静默失败（同步异常只告警不抛出，所以启动照常）。
 * 单元测试当时用的是替身仓储、CI 也不连真实数据库，因此一路漏到隔离联调才暴露。</p>
 *
 * <p>这里把 SQL 与参数拆分出来后，就能用最便宜的方式把这类错误钉死：数量必须一致，
 * 且每个 {@code VALUES(?)} 都要有对应的列。</p>
 */
class JdbcImageWorkflowVersionRepositoryTest {

    private static final String CHECKSUM =
        "d8a773d86c93240a6efe348db9c1ad2d96d4fcd92c75253d8aafc7109cdc7b15";

    private static ImageWorkflowVersion sampleVersion() {
        return new ImageWorkflowVersion(
            "wf-t2i-qwen21", "T2I", "QWEN21", "v0.1.0-draft", "DRAFT",
            "image/workflows/api/wf-t2i-qwen21-v0.1.0.json", CHECKSUM,
            List.of(),
            List.of(), "8", "images", "image/png", "image/png",
            Map.of("1:1 方图 · 1MP（1024×1024）", new int[]{1024, 1024}),
            "1:1 方图 · 1MP（1024×1024）", Set.of(), null, false,
            4 * 1024 * 1024, 32, 300);
    }

    @Test
    @DisplayName("SQL 占位符个数与参数个数必须一致（曾是真实缺陷）")
    void placeholderCountMatchesArgs() {
        long placeholders = JdbcImageWorkflowVersionRepository.UPSERT_SQL.chars().filter(c -> c == '?').count();
        List<Object> args = JdbcImageWorkflowVersionRepository.upsertArgs(
            123L, sampleVersion(), CHECKSUM, true, "[]", "{}", "{}", "{}", "[]");
        assertEquals(placeholders, args.size(),
            "SQL 占位符数与参数个数不一致：运行期会抛 Parameter index out of range");
        assertEquals(123L, args.get(0), "第一个参数必须是主键");
    }

    @Test
    @DisplayName("supported_outputs_json 必须真的落库（不允许像视频侧那样静默丢弃参数）")
    void supportedOutputsIsPersisted() {
        assertTrue(JdbcImageWorkflowVersionRepository.UPSERT_SQL.contains("supported_outputs_json"),
            "SQL 必须包含 supported_outputs_json 列");
        List<Object> args = JdbcImageWorkflowVersionRepository.upsertArgs(
            1L, sampleVersion(), CHECKSUM, true, "[]", "{}", "{}", "{}", "[{\"size\":\"1:1\"}]");
        assertTrue(args.contains("[{\"size\":\"1:1\"}]"), "supportedOutputs 必须出现在参数里");
    }

    @Test
    @DisplayName("模板未通过校验时 checksum 写 NULL，不伪装成已校验")
    void checksumNullWhenTemplateNotLoaded() {
        List<Object> args = JdbcImageWorkflowVersionRepository.upsertArgs(
            1L, sampleVersion(), CHECKSUM, false, "[]", "{}", "{}", "{}", "[]");
        assertEquals(11, args.indexOf(null), "第 11 个参数（checksum）应为 NULL");
    }

    @Test
    @DisplayName("UPDATE 子句不得包含 status / published_by / published_time（同步不能冲掉审核结果）")
    void updateClauseKeepsAuditFields() {
        String updatePart = JdbcImageWorkflowVersionRepository.UPSERT_SQL
            .substring(JdbcImageWorkflowVersionRepository.UPSERT_SQL.indexOf("ON DUPLICATE KEY UPDATE"));
        assertTrue(!updatePart.contains("status ="), "UPDATE 不得写入 status");
        assertTrue(!updatePart.contains("published_by"), "UPDATE 不得写入 published_by");
        assertTrue(!updatePart.contains("published_time"), "UPDATE 不得写入 published_time");
    }
}
