package org.dromara.ai.image.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * 测试夹具：把仓库里真实的契约与 API 模板复制到临时目录，并可改写发布状态。
 *
 * <p><b>为什么需要它</b>：契约的发布状态会变。此前"断言 DRAFT 会被拒绝"的测试直接读仓库契约，
 * 契约从 DRAFT 提升为 PUBLISHED 后这些断言立刻失效——真实发生过：发布当天两条测试变红，
 * 其中一条还去真等 ComfyUI 输出，白等 300 秒。把与状态相关的场景建立在自己造的契约上，
 * 测试才和"仓库当前发布到哪一步"解耦。</p>
 */
public final class ImageContractTestFixture {

    /** 仓库根下的受控契约目录（测试工作目录是模块目录）。 */
    public static final Path REPO_SCRIPT = Path.of("..", "..", "script").toAbsolutePath().normalize();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ImageContractTestFixture() {
    }

    /**
     * 落盘一份状态改写为 {@code status} 的契约（模板按原字节复制，checksum 才能通过校验）。
     *
     * @param targetDir 契约根目录（会被创建）
     */
    public static void materialize(Path targetDir, String status) throws Exception {
        JsonNode source = MAPPER.readTree(Files.readString(
            REPO_SCRIPT.resolve(ImageWorkflowContractRegistry.CONTRACT_FILE), StandardCharsets.UTF_8));
        ObjectNode contract = source.deepCopy();
        ((ObjectNode) contract.path("meta")).put("status", status);
        for (JsonNode capability : contract.path("capabilities")) {
            for (JsonNode workflow : capability.path("workflows")) {
                ((ObjectNode) workflow).put("status", status);
            }
        }
        Path target = targetDir.resolve(ImageWorkflowContractRegistry.CONTRACT_FILE);
        Files.createDirectories(target.getParent());
        Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(contract) + "\n",
            StandardCharsets.UTF_8);

        Path apiSource = REPO_SCRIPT.resolve("image/workflows/api");
        Path apiTarget = target.getParent().resolve("api");
        Files.createDirectories(apiTarget);
        try (Stream<Path> files = Files.list(apiSource)) {
            for (Path file : files.toList()) {
                Files.copy(file, apiTarget.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** 落盘并加载，返回可直接使用的注册表。 */
    public static ImageWorkflowContractRegistry registry(Path targetDir, String status) throws Exception {
        materialize(targetDir, status);
        ImageWorkflowContractRegistry registry = new ImageWorkflowContractRegistry(targetDir, MAPPER);
        registry.load();
        return registry;
    }

    /** 读取仓库真实契约里每条工作流的 version/checksum（用于构造"库里审核过的版本"）。 */
    public static java.util.List<org.dromara.ai.image.service.WorkflowReviewState> approvedStates(String status)
        throws Exception {
        JsonNode source = MAPPER.readTree(Files.readString(
            REPO_SCRIPT.resolve(ImageWorkflowContractRegistry.CONTRACT_FILE), StandardCharsets.UTF_8));
        java.util.List<org.dromara.ai.image.service.WorkflowReviewState> states = new java.util.ArrayList<>();
        for (JsonNode capability : source.path("capabilities")) {
            for (JsonNode workflow : capability.path("workflows")) {
                states.add(new org.dromara.ai.image.service.WorkflowReviewState(
                    workflow.path("workflowCode").asText(),
                    workflow.path("version").asText(),
                    workflow.path("checksum").asText(),
                    status));
            }
        }
        return states;
    }
}
