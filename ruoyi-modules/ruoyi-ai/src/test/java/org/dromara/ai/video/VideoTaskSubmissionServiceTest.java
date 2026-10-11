package org.dromara.ai.video;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.VideoTaskSubmissionService;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/** Guards the shared submission path after merging the scenario and creative-ability implementations. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VideoTaskSubmissionServiceTest.IdConfiguration.class)
class VideoTaskSubmissionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private WorkflowContractRegistry registry;
    private VideoTaskRepository repository;
    private VideoTaskSubmissionService service;

    @BeforeEach
    void setUp() {
        Path root = Path.of("").toAbsolutePath().getParent().getParent().resolve("script");
        registry = spy(new WorkflowContractRegistry(root, mapper));
        registry.load();
        repository = mock(VideoTaskRepository.class);
        // Mockito returns 0L for an unstubbed boxed Long; absence is null in the real repository.
        when(repository.findByIdempotencyKey(anyString(), anyLong(), nullable(String.class))).thenReturn(null);
        service = new VideoTaskSubmissionService(registry, new H3TemplatePreparer(mapper), repository, mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_MOTION", "REFERENCE_STORY"})
    void businessSubmissionPreservesAssetsExactDurationAndPlatformLink(String code) throws Exception {
        JsonNode ability = definition(code);
        String workflow = ability.path("workflows").path(0).path("workflowCode").asText();
        WorkflowVersion version = registry.peek(workflow);
        assertNotNull(version);
        // The production catalog remains DRAFT; this test exercises submission after release approval.
        doReturn(version).when(registry).require(workflow, false);
        Map<String, Object> request = abilityRequest(ability, version);

        var result = service.submit(request, "tenant-a", 9L, 88L);
        ArgumentCaptor<VideoTaskRepository.TaskRow> captor = ArgumentCaptor.forClass(VideoTaskRepository.TaskRow.class);
        verify(repository).insertTask(captor.capture());
        var row = captor.getValue();
        assertEquals(result.taskId(), row.id());
        assertEquals(88L, row.platformTaskId());
        assertEquals("tenant-a", row.tenantId());
        assertEquals(9L, row.userId());
        assertEquals(ability.path("capabilityCode").asText(), row.capabilityCode());
        assertEquals(workflow, row.workflowCode());
        JsonNode stored = mapper.readTree(row.inputJson());
        assertEquals(version.fixedFieldValidation().dur(), stored.path("durationLabel").asText());
        assertEquals(3, row.durationSeconds(), "2.x seconds must never become 204 or 233 seconds");
        assertFalse(row.prompt().isBlank());
        verify(repository).requireOwnedAsset(10L, "tenant-a", 9L);
        if (code.equals("REFERENCE_STORY")) {
            verify(repository).requireOwnedAsset(11L, "tenant-a", 9L);
            assertEquals(10L, stored.path("first").asLong());
            assertEquals(11L, stored.path("last").asLong());
        } else {
            assertEquals(10L, stored.path("img").asLong());
        }
    }

    @Test
    void legacyPageSubmissionRetainsPublishedWorkflowAndFiveSecondDuration() throws Exception {
        var result = service.submit(Map.of("capabilityCode", "T2V", "workflowCode", "wf-t2v-h3",
            "fields", Map.of("desc", "一只猫走过花园", "tier", "高清 · 1080P", "dur", "5 秒")),
            "tenant-a", 9L, null);
        ArgumentCaptor<VideoTaskRepository.TaskRow> captor = ArgumentCaptor.forClass(VideoTaskRepository.TaskRow.class);
        verify(repository).insertTask(captor.capture());
        var row = captor.getValue();
        assertEquals(result.taskId(), row.id());
        assertNull(row.platformTaskId());
        assertEquals(5, row.durationSeconds());
        assertEquals("5 秒", mapper.readTree(row.inputJson()).path("durationLabel").asText());
    }

    @Test
    void existingIdempotencyKeyReturnsOriginalTaskWithoutWritingAnother() {
        when(repository.findByIdempotencyKey("tenant-a", 9L, "existing-key")).thenReturn(42L);
        var result = service.submit(Map.of("capabilityCode", "T2V", "workflowCode", "wf-t2v-h3",
            "fields", Map.of("desc", "一只猫走过花园", "tier", "高清 · 1080P", "dur", "5 秒"),
            "idempotencyKey", "existing-key"), "tenant-a", 9L, 88L);
        assertTrue(result.idempotent());
        assertEquals(42L, result.taskId());
        verify(repository, never()).insertTask(any());
    }

    @Test
    void imageAbilityCannotCreateVideoTask() {
        assertThrows(VideoTaskException.class, () -> service.submit(Map.of("abilityCode", "POSTER",
            "workflowCode", "wf-ability-image-poster-qwen2512"), "tenant-a", 9L, 88L));
        verifyNoInteractions(repository);
    }

    @Test
    void foreignReferenceAssetIsRejectedBeforeAnyTaskIsWritten() {
        JsonNode ability = definition("REFERENCE_STORY");
        String workflow = ability.path("workflows").path(0).path("workflowCode").asText();
        WorkflowVersion version = registry.peek(workflow);
        doReturn(version).when(registry).require(workflow, false);
        when(repository.requireOwnedAsset(11L, "tenant-a", 9L))
            .thenThrow(VideoTaskException.assetNotFound("素材不属于当前用户"));
        assertThrows(VideoTaskException.class, () -> service.submit(abilityRequest(ability, version),
            "tenant-a", 9L, 88L));
        verify(repository, never()).insertTask(any());
    }

    private JsonNode definition(String code) {
        for (JsonNode ability : registry.abilities()) {
            if (code.equals(ability.path("code").asText())) return ability;
        }
        throw new AssertionError("Missing real ability: " + code);
    }

    private Map<String, Object> abilityRequest(JsonNode ability, WorkflowVersion version) {
        Map<String, Object> inputs = new HashMap<>();
        for (JsonNode field : ability.path("inputs")) {
            inputs.put(field.path("key").asText(), field.path("options").isArray()
                ? field.path("options").path(0).asText() : "主体稳定，镜头缓慢移动");
        }
        Map<String, Object> assets = new HashMap<>();
        long id = 10L;
        for (JsonNode field : ability.path("assets")) assets.put(field.path("key").asText(), id++);
        return Map.of("abilityCode", ability.path("code").asText(), "workflowCode", version.workflowCode(),
            "inputs", inputs, "assets", assets, "output", Map.of("tier", version.fixedFieldValidation().tier(),
            "dur", version.fixedFieldValidation().dur()), "idempotencyKey", "PLATFORM-88");
    }

    @Configuration(proxyBeanMethods = false)
    static class IdConfiguration {
        @Bean IdentifierGenerator identifierGenerator() { return new DefaultIdentifierGenerator(); }
        @Bean static SpringUtil springUtil() { return new SpringUtil(); }
    }
}
