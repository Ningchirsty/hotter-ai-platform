package org.dromara.ai.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

class NativeImagePublicationTest {
    @Test void everyPublishedNativeImageMatchesCompletePlatformEvidence() throws Exception {
        var root=Path.of("..","..","script").toAbsolutePath();
        var mapper=new ObjectMapper();
        var registry=new ImageWorkflowContractRegistry(root,mapper);registry.load();
        var report=mapper.readTree(Files.readString(root.resolve("native-workflow-publication.json")));
        assertEquals("PASSED",report.path("technicalAcceptance").asText());
        var expected=new TreeSet<String>();
        for(var evidence:report.path("workflows")) {
            if(!"image".equals(evidence.path("media").asText())) continue;
            String code=evidence.path("workflowCode").asText();
            assertTrue(code.startsWith("wf-local-"));assertTrue(expected.add(code));
            assertEquals("SUCCEEDED",evidence.path("status").asText());
            assertFalse(evidence.path("taskId").asText().isBlank());
            assertFalse(evidence.path("comfyPromptId").asText().isBlank());
            assertTrue(evidence.path("sha256").asText().matches("[0-9a-f]{64}"));
            var binding=registry.require(code,true);
            assertEquals(binding.checksum(),evidence.path("checksum").asText());
            assertEquals(binding.version(),evidence.path("version").asText());
            assertNotNull(registry.templateOf(code));
        }
        assertEquals(11,expected.size());
        var actual=new TreeSet<String>();
        registry.registeredVersions().stream().filter(v->v.workflowCode().startsWith("wf-local-")&&v.isPublished()).forEach(v->actual.add(v.workflowCode()));
        assertEquals(expected,actual,"本地模型发布集合必须与真实平台证据完全一致");
    }
}
