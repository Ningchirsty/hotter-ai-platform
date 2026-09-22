package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageWorkflowMapping;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图像工作流契约注册表：从后端受控目录加载契约并逐份校验模板。
 *
 * <p>与视频模块的 {@code WorkflowContractRegistry} 同构：运行时的权威是<b>契约文件</b>，
 * 数据库里的 {@code image_workflow_version} 只是同步镜像。模板 SHA-256 与契约不符时
 * 该条工作流<b>不予加载</b>（警告而非启动失败），但提交时会因「模板未加载」被拒绝。</p>
 */
@Slf4j
public class ImageWorkflowContractRegistry {

    /**
     * 契约文件相对 {@code image.contract-root} 的路径。
     */
    public static final String CONTRACT_FILE = "image/workflows/image-workflow-contracts.json";

    private final Path root;
    private final ObjectMapper mapper;
    private final ImageTemplatePreparer preparer;

    /** 已注册的绑定（含未加载模板的 DRAFT 占位）。 */
    private final Map<String, ImageWorkflowVersion> byCode = new LinkedHashMap<>();

    /** 模板校验通过并缓存的绑定（key=workflowCode，value=模板原文）。 */
    private final Map<String, String> templates = new LinkedHashMap<>();

    public ImageWorkflowContractRegistry(Path root, ObjectMapper mapper) {
        if (root == null) {
            throw new IllegalStateException("image.contract-root 未配置");
        }
        this.root = root;
        this.mapper = mapper;
        this.preparer = new ImageTemplatePreparer(mapper);
    }

    /**
     * 加载契约。契约文件缺失或非法 JSON 属于配置错误，直接失败（fail fast）。
     */
    public void load() {
        Path contractPath = root.resolve(CONTRACT_FILE);
        if (!Files.isRegularFile(contractPath)) {
            throw new IllegalStateException("契约文件不存在：" + CONTRACT_FILE);
        }
        JsonNode contract;
        try {
            contract = mapper.readTree(Files.readString(contractPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("契约文件读取失败：" + CONTRACT_FILE, e);
        } catch (Exception e) {
            throw new IllegalStateException("契约文件不是合法 JSON：" + CONTRACT_FILE, e);
        }
        for (JsonNode capability : contract.path("capabilities")) {
            String capabilityCode = capability.path("capabilityCode").asText("");
            List<String> capabilityFields = new ArrayList<>();
            for (JsonNode field : capability.path("fields")) {
                String name = field.path("field").asText("");
                if (!name.isBlank()) {
                    capabilityFields.add(name);
                }
            }
            for (JsonNode workflow : capability.path("workflows")) {
                ImageWorkflowVersion version = toVersion(workflow, capabilityCode, List.copyOf(capabilityFields));
                register(version);
            }
        }
        log.info("图像工作流契约加载完成：已注册 {} 条，模板可用 {} 条", byCode.size(), templates.size());
        if (templates.isEmpty()) {
            log.warn("图像创作模块已启用，但没有任何模板通过校验并加载；提交接口将全部拒绝");
        }
    }

    private ImageWorkflowVersion toVersion(JsonNode workflow, String capabilityCode, List<String> capabilityFields) {
        String code = workflow.path("workflowCode").asText("");
        String status = workflow.path("status").asText("DRAFT");
        JsonNode outputRule = workflow.path("outputRule");
        JsonNode fixed = workflow.path("fixedFieldValidation");

        List<ImageWorkflowMapping> mapping = new ArrayList<>();
        for (JsonNode item : workflow.path("mapping")) {
            mapping.add(new ImageWorkflowMapping(
                item.path("field").asText(""),
                item.path("nodeId").asText(""),
                item.path("inputKey").asText(""),
                item.path("note").asText(null)));
        }

        Map<String, int[]> sizePresets = new LinkedHashMap<>();
        for (JsonNode item : workflow.path("supportedOutputs")) {
            int width = item.path("width").asInt(0);
            int height = item.path("height").asInt(0);
            String label = item.path("size").asText("");
            if (width > 0 && height > 0 && !label.isBlank()) {
                sizePresets.put(label, new int[]{width, height});
            }
        }
        for (JsonNode label : fixed.path("supportedSizes")) {
            // 只作为允许值的来源；没有尺寸的档位（如「跟随输入图」）不会进入 sizePresets
            sizePresets.computeIfAbsent(label.asText(""), key -> new int[]{0, 0});
        }
        sizePresets.entrySet().removeIf(entry -> entry.getValue()[0] <= 0);

        Set<String> strengths = new LinkedHashSet<>();
        for (JsonNode label : fixed.path("supportedStrengths")) {
            strengths.add(label.asText(""));
        }

        int maxSizeMb = outputRule.path("maxSizeMB").isNumber() ? outputRule.path("maxSizeMB").asInt(32) : 32;
        int maxPixels = outputRule.path("maxPixels").asInt(4 * 1024 * 1024);
        int timeoutSeconds = workflow.path("perf").path("timeoutSeconds").asInt(300);

        return new ImageWorkflowVersion(
            code,
            capabilityCode,
            workflow.path("modelCode").asText(null),
            workflow.path("version").asText("v0.1.0-draft"),
            status,
            workflow.path("apiJsonFile").asText(""),
            workflow.path("checksum").asText(""),
            capabilityFields,
            List.copyOf(mapping),
            outputRule.path("nodeId").asText(""),
            outputRule.path("outputField").asText("images"),
            outputRule.path("format").asText("png"),
            outputRule.path("mime").asText("image/png"),
            Map.copyOf(sizePresets),
            fixed.path("size").asText(null),
            Set.copyOf(strengths),
            fixed.path("strength").asText(null),
            "required".equalsIgnoreCase(outputRule.path("alpha").asText("")),
            maxPixels,
            maxSizeMb,
            timeoutSeconds);
    }

    private void register(ImageWorkflowVersion version) {
        byCode.put(version.workflowCode(), version);
        String apiJsonFile = version.apiJsonFile();
        if (apiJsonFile == null || apiJsonFile.isBlank()) {
            log.warn("工作流 {} 缺少模板路径，不予加载", version.workflowCode());
            return;
        }
        Path templatePath = root.resolve(apiJsonFile);
        if (!Files.isRegularFile(templatePath)) {
            log.warn("工作流 {} 模板文件缺失，不予加载：{}", version.workflowCode(), apiJsonFile);
            return;
        }
        String content;
        try {
            content = Files.readString(templatePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("工作流 {} 模板读取失败，不予加载：{}", version.workflowCode(), e.getMessage());
            return;
        }
        try {
            preparer.verifyChecksum(content, version);
        } catch (ImageTaskException e) {
            log.warn("工作流 {} 校验未通过，不予加载：{}", version.workflowCode(), e.getMessage());
            return;
        }
        templates.put(version.workflowCode(), content);
    }

    /**
     * 取出可提交的绑定，并做三级门禁：已注册 → 可联调（TESTING/PUBLISHED）→ 必要时必须已发布 → 模板已加载。
     */
    public ImageWorkflowVersion require(String workflowCode, boolean requirePublished) {
        ImageWorkflowVersion version = byCode.get(workflowCode);
        if (version == null) {
            throw ImageTaskException.invalidContract("不支持的工作流：" + workflowCode);
        }
        if (!version.isTestable()) {
            throw ImageTaskException.invalidContract("工作流尚未通过实机验收，暂不可提交：" + workflowCode);
        }
        if (requirePublished && !version.isPublished()) {
            throw ImageTaskException.invalidContract("工作流未发布，正式环境不可提交：" + workflowCode);
        }
        if (!templates.containsKey(workflowCode)) {
            throw ImageTaskException.invalidContract("工作流模板未加载：" + workflowCode);
        }
        return version;
    }

    /**
     * 只查询绑定，不做门禁（供 capabilities 列表展示状态）。
     */
    public ImageWorkflowVersion peek(String workflowCode) {
        return byCode.get(workflowCode);
    }

    public String templateOf(String workflowCode) {
        String content = templates.get(workflowCode);
        if (content == null) {
            throw ImageTaskException.invalidContract("工作流模板未加载：" + workflowCode);
        }
        return content;
    }

    public boolean isTemplateLoaded(String workflowCode) {
        return templates.containsKey(workflowCode);
    }

    public int loadedCount() {
        return templates.size();
    }

    public int registeredCount() {
        return byCode.size();
    }

    public Collection<ImageWorkflowVersion> registeredVersions() {
        return List.copyOf(byCode.values());
    }

    /**
     * 隔离联调专用：把某条工作流临时标记为 TESTING。返回是否生效。
     */
    public boolean markTesting(String workflowCode) {
        ImageWorkflowVersion current = byCode.get(workflowCode);
        if (current == null || !templates.containsKey(workflowCode)) {
            return false;
        }
        byCode.put(workflowCode, withStatus(current, "TESTING"));
        log.warn("工作流 {} 已标记为 TESTING（仅限隔离联调环境）", workflowCode);
        return true;
    }

    /**
     * 按<b>库里的审核结果</b>提升内存绑定的发布状态。
     *
     * <p>为什么需要它（真实事故）：契约文件被打进后端镜像，而"补丁式重建镜像"
     * （{@code FROM 旧镜像 + COPY 新 jar}）会把旧基座里的契约一起带回来。
     * 一次这样的重建把已验收发布的图像工作流静默退回 DRAFT，页面立刻变成"暂不可提交"。
     * 把发布状态的权威放进 {@code image_workflow_version} 后，换任何镜像都不会改变审核结论。</p>
     *
     * <p>四条守住边界的规则，避免"提权"变成后门：</p>
     * <ol>
     *   <li><b>只提不降</b>：库里是 PUBLISHED 才提升；契约里已 RETIRED 的不覆盖（撤回优先）；</li>
     *   <li><b>版本必须一致</b>：新版本不继承旧版本的审核结论，必须重新验收；</li>
     *   <li><b>checksum 必须一致</b>：模板被原地改动过（违反"固定参数变化必须递增 version"）不继承，并告警；</li>
     *   <li><b>模板必须已加载</b>：模板没通过校验的绑定提升了也没用（提交时仍会被拒）。</li>
     * </ol>
     *
     * @param states 库中的审核状态（通常来自 {@code image_workflow_version} 全表）
     * @return 实际提升的条数
     */
    public int applyReviewStates(Collection<WorkflowReviewState> states) {
        if (states == null || states.isEmpty()) {
            return 0;
        }
        int promoted = 0;
        for (WorkflowReviewState state : states) {
            if (state == null || state.workflowCode() == null || !"PUBLISHED".equalsIgnoreCase(state.status())) {
                continue;
            }
            ImageWorkflowVersion current = byCode.get(state.workflowCode());
            if (current == null || current.isPublished()) {
                continue;
            }
            if ("RETIRED".equalsIgnoreCase(current.status())) {
                log.warn("工作流 {} 契约中已是 RETIRED，忽略库中的 PUBLISHED（撤回优先）", state.workflowCode());
                continue;
            }
            if (!current.version().equals(state.version())) {
                log.info("工作流 {} 库中审核的是 {}，当前契约版本 {} —— 新版本需重新验收，不继承发布状态",
                    state.workflowCode(), state.version(), current.version());
                continue;
            }
            if (state.checksum() == null || !state.checksum().equalsIgnoreCase(current.checksum())) {
                log.warn("工作流 {} 模板字节与已审核版本不一致（checksum 变化），不继承发布状态 —— "
                    + "固定后台参数变化必须递增 version，禁止原地改 PUBLISHED 版本", state.workflowCode());
                continue;
            }
            if (!templates.containsKey(state.workflowCode())) {
                log.warn("工作流 {} 模板未加载，跳过发布状态提升", state.workflowCode());
                continue;
            }
            byCode.put(state.workflowCode(), withStatus(current, "PUBLISHED"));
            promoted++;
            log.info("工作流 {} 按库中审核结果提升为 PUBLISHED（version={}）", state.workflowCode(), current.version());
        }
        if (promoted > 0) {
            log.info("图像工作流按库中审核结果提升 {} 条为 PUBLISHED（发布状态权威在库，不在镜像）", promoted);
        }
        return promoted;
    }

    /** record 不可变：复制一份并替换状态。 */
    private static ImageWorkflowVersion withStatus(ImageWorkflowVersion current, String status) {
        return new ImageWorkflowVersion(
            current.workflowCode(), current.capabilityCode(), current.modelCode(), current.version(),
            status, current.apiJsonFile(), current.checksum(), current.capabilityFields(), current.mapping(),
            current.outputNodeId(),
            current.outputField(), current.outputFormat(), current.outputMime(), current.sizePresets(),
            current.defaultSize(), current.supportedStrengths(), current.defaultStrength(), current.requireAlpha(),
            current.maxPixels(), current.maxSizeMb(), current.timeoutSeconds());
    }
}
