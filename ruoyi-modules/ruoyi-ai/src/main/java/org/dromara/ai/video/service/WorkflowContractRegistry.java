package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.WorkflowMapping;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从后端受控目录加载工作流契约并校验模板文件。
 *
 * <p>契约 JSON（{@code script/video/workflows/video-workflow-contracts.json}）是唯一权威；
 * 模板文件必须存在于后端受控目录，且 SHA-256 与契约一致，否则拒绝加载。
 * 节点 ID、模型路径不会下发前端。</p>
 */
@Slf4j
public class WorkflowContractRegistry {

    /**
     * 契约文件在受控目录中的相对路径。
     */
    public static final String CONTRACT_FILE = "video/workflows/video-workflow-contracts.json";

    private final Path contractRoot;
    private final ObjectMapper mapper;
    private final H3TemplatePreparer preparer;

    /**
     * workflowCode -> 版本定义。
     */
    private final Map<String, WorkflowVersion> byCode = new HashMap<>();

    /**
     * workflowCode -> 模板原文（启动时读入并校验，避免运行期重复读盘）。
     */
    private final Map<String, String> templates = new HashMap<>();

    public WorkflowContractRegistry(Path contractRoot, ObjectMapper mapper) {
        if (contractRoot == null) {
            throw new IllegalStateException("workflow.contract-root 未配置");
        }
        this.contractRoot = contractRoot;
        this.mapper = mapper;
        this.preparer = new H3TemplatePreparer(mapper);
    }

    /**
     * 加载契约与全部模板。校验失败的条目只记录告警并跳过，
     * 不允许把未通过校验的模板放进可提交集合。
     */
    public void load() {
        Path contractPath = contractRoot.resolve(CONTRACT_FILE);
        if (!Files.isRegularFile(contractPath)) {
            throw new IllegalStateException("契约文件不存在：" + CONTRACT_FILE);
        }
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(contractPath, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("契约文件不是合法 JSON", e);
        }
        for (JsonNode capability : root.path("capabilities")) {
            String capabilityCode = capability.path("capabilityCode").asText("");
            List<String> allowedFields = new ArrayList<>();
            for (JsonNode field : capability.path("fields")) {
                allowedFields.add(field.path("field").asText(""));
            }
            for (JsonNode binding : capability.path("workflows")) {
                WorkflowVersion version = toVersion(capabilityCode, binding, allowedFields);
                register(version);
            }
        }
        log.info("工作流契约加载完成：可提交版本 {} 个", templates.size());
    }

    private WorkflowVersion toVersion(String capabilityCode, JsonNode binding, List<String> allowedFields) {
        List<WorkflowMapping> mappings = new ArrayList<>();
        for (JsonNode item : binding.path("mapping")) {
            mappings.add(new WorkflowMapping(
                item.path("field").asText(""),
                item.path("nodeId").asText(""),
                item.path("inputKey").asText(""),
                item.path("note").asText("")));
        }
        JsonNode fixedNode = binding.path("fixedFieldValidation");
        WorkflowVersion.FixedFieldValidation fixed = fixedNode.isObject()
            ? new WorkflowVersion.FixedFieldValidation(
                fixedNode.path("tier").asText(null), fixedNode.path("dur").asText(null),
                readSupportedTiers(fixedNode))
            : null;
        JsonNode outputRule = binding.path("outputRule");
        Integer maxDuration = outputRule.hasNonNull("maxDurationSeconds")
            ? outputRule.get("maxDurationSeconds").asInt() : null;
        return new WorkflowVersion(
            binding.path("workflowCode").asText(""),
            capabilityCode,
            binding.path("modelCode").asText(null),
            binding.path("version").asText(""),
            binding.path("status").asText("DRAFT"),
            binding.path("apiJsonFile").asText(""),
            binding.path("checksum").asText(""),
            mappings,
            fixed,
            maxDuration,
            outputRule.path("nodeId").asText(null),
            outputRule.path("outputField").asText(null));
    }

    /**
     * 读取契约里的 {@code fixedFieldValidation.supportedTiers}。
     *
     * <p>多档位（如 1080P/720P/480P）由它以数组形式声明，避免用单一 {@code tier}
     * 字符串塞多个值再在代码里拆分。缺失或非数组时返回空集合，调用方按旧的
     * 单一 {@code tier} 语义处理。</p>
     */
    private java.util.Set<String> readSupportedTiers(JsonNode fixedNode) {
        JsonNode node = fixedNode.path("supportedTiers");
        if (!node.isArray()) {
            return java.util.Set.of();
        }
        java.util.LinkedHashSet<String> tiers = new java.util.LinkedHashSet<>();
        for (JsonNode item : node) {
            String text = item.asText("").trim();
            if (!text.isEmpty()) {
                tiers.add(text);
            }
        }
        return java.util.Collections.unmodifiableSet(tiers);
    }

    private void register(WorkflowVersion version) {
        if (version.workflowCode().isBlank()) {
            return;
        }
        byCode.put(version.workflowCode(), version);
        // 模板加载与"可否提交"是两件事：DRAFT 模板也要完成校验并加载，
        // 但 require() 仍然会拒绝把它作为可提交任务。校验失败的模板一律不加载。
        String file = version.apiJsonFile();
        if (file == null || file.isBlank()) {
            log.warn("工作流 {} 缺少模板路径，不予加载", version.workflowCode());
            return;
        }
        Path templatePath = contractRoot.resolve(file);
        if (!Files.isRegularFile(templatePath)) {
            log.warn("工作流 {} 模板文件缺失，不予加载", version.workflowCode());
            return;
        }
        String content;
        try {
            content = Files.readString(templatePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("工作流 {} 模板读取失败，不予加载", version.workflowCode());
            return;
        }
        try {
            preparer.verifyChecksum(content, version);
        } catch (VideoTaskException e) {
            log.warn("工作流 {} 校验未通过，不予加载：{}", version.workflowCode(), e.getMessage());
            return;
        }
        templates.put(version.workflowCode(), content);
    }

    /**
     * 查询可提交的工作流版本。
     *
     * @param workflowCode 工作流编码
     * @param requirePublished 正式环境必须为 PUBLISHED
     */
    public WorkflowVersion require(String workflowCode, boolean requirePublished) {
        WorkflowVersion version = byCode.get(workflowCode);
        if (version == null) {
            throw VideoTaskException.invalidContract("不支持的工作流：" + workflowCode);
        }
        if (!version.isTestable()) {
            throw VideoTaskException.invalidContract("工作流尚未通过实机验收，暂不可提交：" + workflowCode);
        }
        if (requirePublished && !version.isPublished()) {
            throw VideoTaskException.invalidContract("工作流未发布，正式环境不可提交：" + workflowCode);
        }
        if (!templates.containsKey(workflowCode)) {
            throw VideoTaskException.invalidContract("工作流模板未加载：" + workflowCode);
        }
        return version;
    }

    /**
     * 读取已注册版本但不做可提交性校验（用于向前端展示状态）。
     *
     * @return 未注册时返回 null
     */
    public WorkflowVersion peek(String workflowCode) {
        return byCode.get(workflowCode);
    }

    /**
     * 取模板原文（仅服务端可用）。
     */
    public String templateOf(String workflowCode) {
        String content = templates.get(workflowCode);
        if (content == null) {
            throw VideoTaskException.invalidContract("工作流模板未加载：" + workflowCode);
        }
        return content;
    }

    /**
     * 已成功加载模板的工作流数量。
     */
    public int loadedCount() {
        return templates.size();
    }

    /**
     * 已注册的工作流版本数量（含 DRAFT）。
     */
    public int registeredCount() {
        return byCode.size();
    }

    /**
     * 全部已注册版本（含 DRAFT 与仅有模板路径的占位条目）。
     *
     * <p>仅供数据库同步/审计使用：运行时判定「可否提交」仍以 {@link #require} 为准。</p>
     */
    public java.util.Collection<WorkflowVersion> registeredVersions() {
        return java.util.List.copyOf(byCode.values());
    }

    /**
     * 某版本的模板是否已加载并通过校验和校验。
     */
    public boolean isTemplateLoaded(String workflowCode) {
        return templates.containsKey(workflowCode);
    }

    /**
     * 判断某能力是否存在可提交的已发布 H3 版本。
     */
    public boolean hasPublished(VideoCapability capability) {
        return byCode.values().stream()
            .anyMatch(v -> v.isPublished() && capability.name().equalsIgnoreCase(v.capabilityCode()));
    }

    /**
     * 把指定工作流标记为 TESTING。
     *
     * <p>仅用于<b>隔离联调环境</b>：交接文档要求在联调环境把已完成单侧验收的工作流设为
     * TESTING 才能从页面提交验证，而仓库中的契约必须保持 DRAFT，不能为了让测试通过去改契约文件。
     * 生产环境不得调用本方法；工作流转为 PUBLISHED 必须走审核流程。</p>
     *
     * @param workflowCode 工作流编码
     * @return 是否成功标记
     */
    public boolean markTesting(String workflowCode) {
        WorkflowVersion current = byCode.get(workflowCode);
        if (current == null || !templates.containsKey(workflowCode)) {
            return false;
        }
        byCode.put(workflowCode, new WorkflowVersion(
            current.workflowCode(), current.capabilityCode(), current.modelCode(),
            current.version(), "TESTING", current.apiJsonFile(), current.checksum(),
            current.mapping(), current.fixedFieldValidation(), current.maxDurationSeconds(),
            current.outputNodeId(), current.outputField()));
        log.warn("工作流 {} 已标记为 TESTING（仅限隔离联调环境）", workflowCode);
        return true;
    }
}
