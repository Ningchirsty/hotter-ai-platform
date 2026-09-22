package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.support.ImageContractTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发布状态的权威在库（{@code image_workflow_version}），不在镜像里的契约文件。
 *
 * <p>这条测试守的是一次真实事故：图像 4 条工作流已实机验收并发布（契约 PUBLISHED），
 * 随后一次"补丁式重建后端镜像"（FROM 旧镜像 + COPY 新 jar）把旧基座里的 DRAFT 契约
 * 一起带回来，页面立刻退回"暂不可提交"。现在发布状态入库，换镜像不再影响它。</p>
 *
 * <p>四条边界同样要守住，否则"按库提权"就成了后门：只提不降 / 版本一致 / checksum 一致 / 模板已加载。</p>
 */
class ImageContractReviewStateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> CODES = List.of(
        "wf-t2i-qwen21", "wf-i2i-qwen21", "wf-edit-qwen21", "wf-bgremove-qwen21");

    @TempDir
    Path contractRoot;

    /** 真实契约里每条工作流的 version/checksum，作为"库里审核过的版本"。 */
    private List<WorkflowReviewState> approved;

    @BeforeEach
    void prepare() throws Exception {
        // 契约落盘为 DRAFT：模拟"镜像里的契约被旧基座带回 DRAFT"这一事故场景。
        ImageContractTestFixture.materialize(contractRoot, "DRAFT");
        approved = ImageContractTestFixture.approvedStates("PUBLISHED");
    }

    private ImageWorkflowContractRegistry loadRegistry() {
        ImageWorkflowContractRegistry registry = new ImageWorkflowContractRegistry(contractRoot, MAPPER);
        registry.load();
        return registry;
    }

    @Test
    @DisplayName("契约是 DRAFT 时：库里的 PUBLISHED 把它提升为已发布，门禁随之放行")
    void reviewStatePromotesDraftContract() {
        ImageWorkflowContractRegistry registry = loadRegistry();
        for (String code : CODES) {
            assertThat(registry.peek(code).isPublished()).isFalse();
        }
        // 提权前：正式环境不可提交
        assertThatThrownBy(() -> registry.require("wf-t2i-qwen21", true))
            .isInstanceOf(ImageTaskException.class)
            .hasMessageContaining("尚未通过实机验收");

        int promoted = registry.applyReviewStates(approved);

        assertThat(promoted).isEqualTo(4);
        for (String code : CODES) {
            assertThat(registry.peek(code).isPublished()).isTrue();
            // 提权后：正式环境可提交（这条正是页面上「提交生成」按钮能不能点的依据）
            assertThat(registry.require(code, true).workflowCode()).isEqualTo(code);
        }
    }

    @Test
    @DisplayName("只提不降：库里是 DRAFT 时不会改变契约里的状态")
    void draftReviewStateChangesNothing() throws Exception {
        ImageWorkflowContractRegistry registry = loadRegistry();
        List<WorkflowReviewState> drafts = ImageContractTestFixture.approvedStates("DRAFT");

        assertThat(registry.applyReviewStates(drafts)).isZero();
        assertThat(registry.peek("wf-t2i-qwen21").isPublished()).isFalse();
    }

    @Test
    @DisplayName("撤回优先：契约里已 RETIRED 的工作流，库里的 PUBLISHED 不得把它拉回来")
    void retiredContractWinsOverPublishedReview() throws Exception {
        ImageContractTestFixture.materialize(contractRoot, "RETIRED");
        ImageWorkflowContractRegistry registry = loadRegistry();

        assertThat(registry.applyReviewStates(approved)).isZero();
        assertThat(registry.peek("wf-t2i-qwen21").isPublished()).isFalse();
    }

    @Test
    @DisplayName("版本必须一致：新版本不继承旧版本的审核结论")
    void newVersionDoesNotInheritApproval() {
        ImageWorkflowContractRegistry registry = loadRegistry();
        List<WorkflowReviewState> stale = approved.stream()
            .map(s -> new WorkflowReviewState(s.workflowCode(), "v0.9.9-draft", s.checksum(), "PUBLISHED"))
            .toList();

        assertThat(registry.applyReviewStates(stale)).isZero();
        assertThat(registry.peek("wf-i2i-qwen21").isPublished()).isFalse();
    }

    @Test
    @DisplayName("checksum 不一致（模板被原地改动）不继承审核结论")
    void checksumMismatchDoesNotInheritApproval() {
        ImageWorkflowContractRegistry registry = loadRegistry();
        List<WorkflowReviewState> tampered = approved.stream()
            .map(s -> new WorkflowReviewState(s.workflowCode(), s.version(), "0".repeat(64), "PUBLISHED"))
            .toList();

        assertThat(registry.applyReviewStates(tampered)).isZero();
        assertThat(registry.peek("wf-edit-qwen21").isPublished()).isFalse();
    }

    @Test
    @DisplayName("空 checksum / 未知工作流 / null 入参都不提权，也不抛异常")
    void degenerateInputsAreIgnored() {
        ImageWorkflowContractRegistry registry = loadRegistry();
        List<WorkflowReviewState> weird = new ArrayList<>();
        weird.add(new WorkflowReviewState("wf-t2i-qwen21", "v0.1.0-draft", null, "PUBLISHED"));
        weird.add(new WorkflowReviewState("wf-not-exist", "v0.1.0-draft", "0".repeat(64), "PUBLISHED"));
        weird.add(null);

        assertThat(registry.applyReviewStates(weird)).isZero();
        assertThat(registry.applyReviewStates(null)).isZero();
        assertThat(registry.applyReviewStates(List.of())).isZero();
        assertThat(registry.peek("wf-t2i-qwen21").isPublished()).isFalse();
    }
}
