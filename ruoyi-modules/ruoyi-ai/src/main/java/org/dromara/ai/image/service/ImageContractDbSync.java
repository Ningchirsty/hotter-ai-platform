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
 * 契约 → {@code image_workflow_version} 同步镜像，并在启动时把库中的<b>审核结果</b>读回内存。
 *
 * <p>与视频模块同款定位：表只是契约的镜像，任何异常只告警、绝不抛出，
 * 避免一个旁路能力把启动搞挂。</p>
 *
 * <p><b>唯一的例外是审核状态</b>：{@code status/published_by/published_time/test_report_json}
 * 由人工落定，是运行时发布状态的权威。同步刻意不覆盖它们，反过来由
 * {@link #applyReviewStates()} 读回去提权。原因见 {@link WorkflowReviewState} 的说明：
 * 契约被打进镜像，"补丁式重建镜像"（FROM 旧镜像 + COPY 新 jar）会把旧基座的契约一起带回，
 * 曾因此把已发布的图像工作流静默退回 DRAFT。</p>
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
        // 顺序有意义：先确保表里有本版本的行，再按审核结果提权。
        applyReviewStates();
    }

    /**
     * 把库中的审核结果叠加到内存绑定上。
     *
     * <p>与 {@link #sync()} 相互独立：即使镜像表写入失败，只要审核状态可读，
     * 发布状态也应该被提上来（否则一次同步故障就会让"谁能提交"发生变化）。</p>
     *
     * @return 提升的条数；失败返回 -1
     */
    public int applyReviewStates() {
        try {
            int promoted = registry.applyReviewStates(repository.findReviewStates());
            if (promoted > 0) {
                log.info("已按库中审核结果提升 {} 条工作流为 PUBLISHED", promoted);
            }
            return promoted;
        } catch (Exception e) {
            log.warn("读取图像工作流审核状态失败（发布状态将退化为契约文件里的值）：{}", e.toString());
            return -1;
        }
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
