package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.WorkflowMapping;
import org.dromara.ai.video.domain.WorkflowVersion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把契约文件同步进 {@code video_workflow_version} 表（方案 A：文件为权威，表为镜像）。
 *
 * <p>刻意<b>不</b>改运行时读取路径：`WorkflowContractRegistry` 仍然只读契约文件并校验 SHA-256，
 * 本类只把同一份契约投影到数据库，便于查询、审计与运维查看。</p>
 *
 * <p>因此同步失败不应该影响服务启动——只告警，不抛异常；
 * 实现 {@link org.springframework.boot.ApplicationRunner} 让它在容器就绪后自动执行一次。</p>
 */
@Slf4j
public class WorkflowContractDbSync implements org.springframework.boot.ApplicationRunner {

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        sync();
    }

    private final WorkflowContractRegistry registry;
    private final VideoWorkflowVersionRepository repository;
    private final ObjectMapper mapper;
    private final Path contractRoot;

    public WorkflowContractDbSync(WorkflowContractRegistry registry,
                                  VideoWorkflowVersionRepository repository,
                                  ObjectMapper mapper,
                                  Path contractRoot) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.contractRoot = contractRoot;
    }

    /**
     * 执行同步。
     *
     * @return 同步的版本条目数；失败返回 -1
     */
    public int sync() {
        try {
            // 契约里除 WorkflowVersion 之外的展示字段（billing/supportedOutputs/perf/fixedParams）
            // 直接从 JSON 取，避免为同步去扩张领域记录。
            Map<String, JsonNode> extras = readContractExtras();

            int inserted = 0;
            int updated = 0;
            for (WorkflowVersion v : registry.registeredVersions()) {
                if (v.workflowCode() == null || v.workflowCode().isBlank()) {
                    continue;
                }
                JsonNode binding = extras.get(v.workflowCode());
                boolean loaded = registry.isTemplateLoaded(v.workflowCode());

                String mappingJson = toJson(v.mapping());
                String outputRuleJson = binding == null ? null
                    : textOf(binding.get("outputRule"));
                String billingJson = binding == null ? null
                    : textOf(binding.get("billing"));
                String perfJson = binding == null ? null
                    : textOf(binding.get("perf"));
                String supportedOutputsJson = binding == null ? null
                    : textOf(binding.get("supportedOutputs"));

                // 只有模板真实加载并通过校验和校验的条目才写 checksum，
                // 占位条目的 TBD 不写进表，避免看起来像已校验。
                String checksum = loaded ? v.checksum() : null;

                boolean isInsert = repository.upsert(v, checksum, loaded,
                    mappingJson, outputRuleJson, billingJson, perfJson, supportedOutputsJson);
                if (isInsert) {
                    inserted++;
                } else {
                    updated++;
                }
            }
            log.info("工作流版本表同步完成：新增 {} 条，更新 {} 条，表内共 {} 条",
                inserted, updated, repository.count());
            return inserted + updated;
        } catch (Exception e) {
            // 同步是旁路能力，绝不能因为它让服务起不来。
            log.warn("工作流版本表同步失败（不影响运行时，运行时仍以契约文件为准）：{}",
                e.getClass().getSimpleName() + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * 读取契约 JSON，建立 workflowCode -> binding 节点 的索引。
     */
    private Map<String, JsonNode> readContractExtras() throws Exception {
        Map<String, JsonNode> map = new HashMap<>();
        Path contractPath = contractRoot.resolve(WorkflowContractRegistry.CONTRACT_FILE);
        if (!Files.isRegularFile(contractPath)) {
            return map;
        }
        JsonNode root = mapper.readTree(Files.readString(contractPath, StandardCharsets.UTF_8));
        for (JsonNode capability : root.path("capabilities")) {
            for (JsonNode binding : capability.path("workflows")) {
                String code = binding.path("workflowCode").asText("");
                if (!code.isBlank()) {
                    map.put(code, binding);
                }
            }
        }
        return map;
    }

    private String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private String toJson(List<WorkflowMapping> mappings) {
        try {
            return mapper.writeValueAsString(mappings);
        } catch (Exception e) {
            return null;
        }
    }
}
