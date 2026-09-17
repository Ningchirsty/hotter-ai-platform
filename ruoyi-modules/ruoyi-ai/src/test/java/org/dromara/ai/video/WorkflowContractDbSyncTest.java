package org.dromara.ai.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.service.VideoWorkflowVersionRepository;
import org.dromara.ai.video.service.WorkflowContractDbSync;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约 → 数据库 同步镜像（方案 A）的离线测试。
 *
 * <p>关键约束：同步只是旁路镜像，绝不能影响运行时「可否提交」的判定。</p>
 */
class WorkflowContractDbSyncTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Path contractRoot;
    private WorkflowContractRegistry registry;
    private StubVersionRepo repo;
    private WorkflowContractDbSync sync;

    @BeforeEach
    void setUp() {
        contractRoot = Path.of("").toAbsolutePath().getParent().getParent().resolve("script");
        registry = new WorkflowContractRegistry(contractRoot, MAPPER);
        registry.load();
        repo = new StubVersionRepo();
        sync = new WorkflowContractDbSync(registry, repo, MAPPER, contractRoot);
    }

    @Test
    @DisplayName("同步：已加载模板的版本写入 checksum，占位条目不写")
    void syncsOnlyVerifiedChecksums() {
        int n = sync.sync();
        assertTrue(n > 0, "应同步出条目");

        // 三个 H3 模板已通过校验和校验 → 必须有 checksum
        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            WorkflowVersion v = repo.rows.get(code);
            assertNotNull(v, code + " 应被同步");
            assertNotNull(v.checksum(), code + " 的 checksum 不应为空");
            assertEquals(64, v.checksum().length(), "应为 SHA-256 十六进制");
            assertFalse(v.checksum().equalsIgnoreCase("TBD"), "不得把 TBD 写进表");
        }

        // 占位条目（模板没有 apiJsonFile 或文件不存在）不应带 checksum
        WorkflowVersion placeholder = repo.rows.get("wf-t2v-h3p");
        assertNotNull(placeholder, "占位条目也应登记（便于运维看到未交付项）");
        assertNull(placeholder.checksum(), "占位条目不得写校验和，否则看起来像已校验");
    }

    @Test
    @DisplayName("同步：状态取契约原值，且不改变可提交判定（PUBLISHED 仍是 PUBLISHED，DRAFT 仍不可提交）")
    void syncDoesNotChangeSubmittability() {
        // 同步前先取契约原值，避免把「同步的结果」当成「契约的原值」来自证。
        String publishedBefore = registry.peek("wf-t2v-h3").status();
        String draftBefore = registry.peek("wf-t2v-wan").status();

        sync.sync();

        assertEquals(publishedBefore, repo.rows.get("wf-t2v-h3").status(),
            "表里的状态应等于契约原值，不得被同步改写");
        assertEquals(draftBefore, repo.rows.get("wf-t2v-wan").status(),
            "DRAFT 条目在表里也应是 DRAFT");

        // 关键：同步不改运行时的可提交判定 —— 已发布的仍可提交，DRAFT 的仍不可提交。
        assertDoesNotThrow(() -> registry.require("wf-t2v-h3", true),
            "已 PUBLISHED 的工作流在同步后仍应可提交");
        assertThrows(org.dromara.ai.video.exception.VideoTaskException.class,
            () -> registry.require("wf-t2v-wan", false),
            "同步不得让 DRAFT 变成可提交");
        assertTrue(registry.hasPublished(VideoCapability.T2V), "T2V 的已发布版本应仍是 wf-t2v-h3");
    }

    @Test
    @DisplayName("同步：缺失的展示字段（billing/perf/outputRule）也要带上")
    void passesThroughContractExtras() {
        sync.sync();
        String code = "wf-t2v-h3";
        assertNotNull(repo.extras.get(code + "|billing"), "billing 应同步");
        assertNotNull(repo.extras.get(code + "|perf"), "perf 应同步");
        assertNotNull(repo.extras.get(code + "|outputRule"), "outputRule 应同步");
        assertTrue(repo.extras.get(code + "|outputRule").contains("maxDurationSeconds"),
            "outputRule 应含时长上限");
    }

    @Test
    @DisplayName("同步：重复执行是幂等的（第二次为更新而非新增）")
    void syncIsIdempotent() {
        int first = sync.sync();
        int second = sync.sync();
        assertEquals(first, second, "两次同步处理的条目数应一致");
        assertEquals(repo.rows.size(), second, "不应产生重复行（按 workflowCode 去重后应等于条目数）");
        assertTrue(repo.updateCount > 0, "第二次应为更新");
    }

    @Test
    @DisplayName("同步：数据库异常不得向上抛（否则会影响服务启动）")
    void syncFailureIsSwallowed() {
        repo.throwOnUpsert = true;
        int n = sync.sync();
        assertEquals(-1, n, "失败应返回 -1");
    }

    /** 替身仓储：按 workflowCode 记录，模拟 upsert 语义。 */
    private static final class StubVersionRepo implements VideoWorkflowVersionRepository {
        final Map<String, WorkflowVersion> rows = new HashMap<>();
        final Map<String, String> extras = new HashMap<>();
        int updateCount = 0;
        boolean throwOnUpsert = false;

        @Override
        public boolean upsert(WorkflowVersion v, String checksum, boolean templateLoaded,
                              String mappingJson, String outputRuleJson, String billingJson,
                              String perfJson, String supportedOutputsJson) {
            if (throwOnUpsert) {
                throw new IllegalStateException("simulated db failure");
            }
            // 模拟真实实现：不覆盖已有行的 status（这里只按 code 记录，够用）
            boolean isInsert = !rows.containsKey(v.workflowCode());
            if (isInsert) {
                rows.put(v.workflowCode(), new WorkflowVersion(v.workflowCode(), v.capabilityCode(),
                    v.modelCode(), v.version(), v.status(), v.apiJsonFile(), checksum,
                    v.mapping(), v.fixedFieldValidation(), v.maxDurationSeconds(),
                    v.outputNodeId(), v.outputField()));
            } else {
                updateCount++;
            }
            extras.put(v.workflowCode() + "|outputRule", outputRuleJson);
            extras.put(v.workflowCode() + "|billing", billingJson);
            extras.put(v.workflowCode() + "|perf", perfJson);
            extras.put(v.workflowCode() + "|supportedOutputs", supportedOutputsJson);
            return isInsert;
        }

        @Override
        public long count() {
            return rows.size();
        }
    }
}
