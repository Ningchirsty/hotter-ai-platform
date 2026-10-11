package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageContractDbSyncTest {
    @Test void syncIncludesBusinessMappingsAndOutputSpecifications() {
        Path root = Path.of("..", "..", "script");
        var mapper = new ObjectMapper();
        var registry = new ImageWorkflowContractRegistry(root, mapper);
        registry.load();
        var repository = mock(ImageWorkflowVersionRepository.class);
        var sync = new ImageContractDbSync(registry, repository, mapper, root);
        assertTrue(sync.sync() > 0);
        var mapping = ArgumentCaptor.forClass(String.class);
        var output = ArgumentCaptor.forClass(String.class);
        var supported = ArgumentCaptor.forClass(String.class);
        verify(repository).upsert(argThat((ImageWorkflowVersion v) -> v.workflowCode().equals("wf-ability-image-poster-qwen2512")),
            anyString(), eq(true), mapping.capture(), output.capture(), nullable(String.class), anyString(), supported.capture());
        assertTrue(mapping.getValue().contains("prompt"));
        assertTrue(output.getValue().contains("image/png"));
        assertTrue(supported.getValue().contains("1328"));
    }
}
