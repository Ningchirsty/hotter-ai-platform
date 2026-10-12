package org.dromara.ai.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.controller.ImageCreationController;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.controller.VideoCreationController;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the HTTP mapper used by Spring Boot 4, across the Jackson 2/3 boundary. */
class AbilityHttpSerializationTest {
    private final ObjectMapper contracts = new ObjectMapper();
    private final JsonMapper http = new JsonMapper();

    @Test
    void imageResponsePreservesAllEightPurposesAndThirteenBindingsAsAnArray() throws Exception {
        var registry = mock(ImageWorkflowContractRegistry.class);
        when(registry.abilities()).thenReturn(definitions("image"));
        var controller = new ImageCreationController(registry, null, null, null, null, null, null, null);
        verifyResponse(controller.abilities(), "image", 8, 13);
    }

    @Test
    void videoResponsePreservesAllSixPurposesAndFifteenBindingsAsAnArray() throws Exception {
        var registry = mock(WorkflowContractRegistry.class);
        when(registry.abilities()).thenReturn(definitions("video"));
        var controller = new VideoCreationController(registry, null, null, null, null, null,
            null, null, null, contracts, null, null);
        verifyResponse(controller.abilities(), "video", 6, 15);
    }

    private com.fasterxml.jackson.databind.JsonNode definitions(String media) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("script/image/workflows"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Repository workflow contracts not found");
        return contracts.readTree(Files.readString(root.resolve("script/" + media + "/workflows/abilities.json")));
    }

    private void verifyResponse(Object response, String media, int purposeCount, int bindingCount) throws Exception {
        var payload = contracts.readTree(http.writeValueAsString(response));
        assertThat(payload.path("code").asInt()).isEqualTo(200);
        var data = payload.path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(purposeCount);
        assertThat(data).isEqualTo(definitions(media));
        int bindings = 0;
        for (var ability : data) {
            assertThat(ability.path("media").asText()).isEqualTo(media);
            for (var binding : ability.path("workflows")) {
                assertThat(binding.path("media").asText()).isEqualTo(media);
                assertThat(binding.path("publicationEvidence").path("testedCommit").asText()).hasSize(40);
                bindings++;
            }
        }
        assertThat(bindings).isEqualTo(bindingCount);
    }
}
