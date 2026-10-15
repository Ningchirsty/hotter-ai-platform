package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析引擎选择器测试（附件「按试点评测选型」的落地开关）。
 *
 * <p>钉住：按来源类型选默认引擎；配置指定引擎时若不存在/不支持必须报错，不许静默退回默认。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeParseRegistryTest {

    private static AigKnowledgePipelineProperties props(String engineId) {
        AigKnowledgePipelineProperties properties = new AigKnowledgePipelineProperties();
        properties.setParserEngineId(engineId);
        return properties;
    }

    private static AigKnowledgeParseRegistry registry(String configuredEngineId) {
        return new AigKnowledgeParseRegistry(
            List.of(new AigPlainTextParseEngine(), new AigDocxParseEngine()), props(configuredEngineId));
    }

    @Test
    @DisplayName("未配置时按来源类型选内置默认：TXT/Markdown → 文本引擎，DOCX → DOCX 引擎")
    void selectsBySourceType() {
        AigKnowledgeParseRegistry registry = registry("");

        assertEquals("builtin-text-v1", registry.select(AigKnowledgeSourceTypeEnum.TXT).engineId());
        assertEquals("builtin-text-v1", registry.select(AigKnowledgeSourceTypeEnum.MARKDOWN).engineId());
        assertEquals("builtin-docx-v1", registry.select(AigKnowledgeSourceTypeEnum.DOCX).engineId());
        assertEquals(List.of("builtin-text-v1", "builtin-docx-v1"), registry.availableEngineIds());
    }

    @Test
    @DisplayName("没有可用引擎：报错并列出已装载的引擎（不猜、不错配）")
    void missingEngineIsExplicit() {
        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> registry("").select(AigKnowledgeSourceTypeEnum.PDF));

        assertEquals(AigKnowledgePipelineException.PARSE_NO_ENGINE, error.getErrorCode());
        assertTrue(error.getMessage().contains("builtin-text-v1"), error.getMessage());
        assertTrue(error.getMessage().contains("首期支持"), error.getMessage());
    }

    @Test
    @DisplayName("★ 配置指定引擎：生效；指定了不存在/不支持的类型都报错（切了没生效最难查）")
    void configuredEngineSwitchesOrFails() {
        assertEquals("builtin-text-v1",
            registry("builtin-text-v1").select(AigKnowledgeSourceTypeEnum.TXT).engineId());

        AigKnowledgePipelineException unknown = assertThrows(AigKnowledgePipelineException.class,
            () -> registry("pdfbox-3").select(AigKnowledgeSourceTypeEnum.TXT));
        assertEquals(AigKnowledgePipelineException.PARSE_NO_ENGINE, unknown.getErrorCode());
        assertTrue(unknown.getMessage().contains("不存在"), unknown.getMessage());

        AigKnowledgePipelineException unsupported = assertThrows(AigKnowledgePipelineException.class,
            () -> registry("builtin-docx-v1").select(AigKnowledgeSourceTypeEnum.TXT));
        assertTrue(unsupported.getMessage().contains("不支持来源类型"), unsupported.getMessage());
    }

    @Test
    @DisplayName("解析走引擎并把引擎 ID/编码带进结果")
    void parseDelegatesToEngine() {
        KnowledgeParsePort.ParseResult result = registry("").parse(new KnowledgeParsePort.ParseRequest(
            "第一行\n第二行\n".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT, "a.txt"));

        assertEquals("builtin-text-v1", result.engineId());
        assertEquals(2, result.blocks().size());
    }

}
