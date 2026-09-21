package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约 → {@code image_workflow_version} 同步镜像。
 *
 * <p>与视频模块同款定位：表只是镜像，运行时权威仍是契约文件。任何异常只告警、绝不抛出，
 * 避免一个旁路能力把启动搞挂。</p>
 */
@Slf4j
public class ImageContractDbSync implements ApplicationRunner {

    private final ImageWorkflowContractRegistry registry;
    private final ImageWorkflowVersionRepository repository;
    private final ObjectMapper mapper;
    private final Path contractRoot;

    public ImageContractDbSync(ImageWorkflowContractRegistry registry,
                               ImageWorkflowVersionRepository repository,
                               ObjectMapper mapper,
                               Path contractRoot) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.contractRoot = contractRoot;
    }

    @Override
    public void run(ApplicationArguments args) {
        sync();
    }

    /**
     * 执行一次同步。
     *
     * @return 处理的条目数；失败返回 -1
     */
    public int sync() {
        try {
            Map<String, JsonNode> bindings = readContractExtras();
            int inserted = 0;
            int updated = 0;
            for (ImageWorkflowVersion version : registry.registeredVersions()) {
                JsonNode binding = bindings.get(version.workflowCode());
                boolean isNew = repository.upsert(
                    version,
                    version.checksum(),
                    registry.isTemplateLoaded(version.workflowCode()),
                    json(binding, "mapping"),
                    json(binding, "outputRule"),
                    json(binding, "billing"),
                    json(binding, "perf"),
                    json(binding, "supportedOutputs"));
                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }
            log.info("图像工作流版本表同步完成：新增 {} 条，更新 {} 条，表内共 {} 条",
                inserted, updated, repository.count());
            return inserted + updated;
        } catch (Exception e) {
            log.warn("图像工作流版本表同步失败（不影响运行时，运行时仍以契约文件为准）：{}", e.toString());
            return -1;
        }
    }

    private Map<String, JsonNode> readContractExtras() throws Exception {
        Path contractPath = contractRoot.resolve(ImageWorkflowContractRegistry.CONTRACT_FILE);
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(contractPath)) {
            return result;
        }
        JsonNode contract = mapper.readTree(Files.readString(contractPath, StandardCharsets.UTF_8));
        for (JsonNode capability : contract.path("capabilities")) {
            for (JsonNode workflow : capability.path("workflows")) {
                result.put(workflow.path("workflowCode").asText(""), workflow);
            }
        }
        return result;
    }

    private String json(JsonNode binding, String field) throws Exception {
        if (binding == null) {
            return null;
        }
        JsonNode node = binding.get(field);
        return node == null || node.isNull() ? null : mapper.writeValueAsString(node);
    }
}
