package org.dromara.aigov.knowledge.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析引擎选择器（附件「知识处理引擎：优先内部可部署，按试点评测选型」的落地开关）。
 *
 * <h3>两种选择方式，顺序固定</h3>
 * <ol>
 *     <li>配置指定（{@code aigov.knowledge.pipeline.parser-engine-id}）：试点评测结论出来后用它切换引擎；</li>
 *     <li>否则用内置默认：按来源类型挑第一个支持它的引擎。</li>
 * </ol>
 *
 * <p><b>配置指定但找不到/不支持时必须报错，不许静默退回默认</b>：否则"我明明切了引擎、结果还是老效果"
 * 会变成一次只能靠翻日志的排障。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigKnowledgeParseRegistry {

    private final List<KnowledgeParsePort> engines;

    private final AigKnowledgePipelineProperties properties;

    /**
     * 按来源类型解析（含配置覆盖）。
     *
     * @param request 解析请求
     * @return 解析结果
     */
    public KnowledgeParsePort.ParseResult parse(KnowledgeParsePort.ParseRequest request) {
        KnowledgeParsePort engine = select(request.sourceType());
        log.info("知识解析开始, engine={}, type={}, file={}, bytes={}", engine.engineId(),
            request.sourceType().getCode(), request.fileName(), request.content().length);
        KnowledgeParsePort.ParseResult result = engine.parse(request);
        log.info("知识解析完成, engine={}, encoding={}, blocks={}", result.engineId(), result.encoding(),
            result.blocks().size());
        return result;
    }

    /**
     * 选择引擎。
     *
     * @param sourceType 来源类型
     * @return 引擎（非空）
     */
    KnowledgeParsePort select(AigKnowledgeSourceTypeEnum sourceType) {
        String configured = properties.getParserEngineId();
        if (StringUtils.isNotBlank(configured)) {
            for (KnowledgeParsePort engine : engines) {
                if (!engine.engineId().equals(configured.trim())) {
                    continue;
                }
                if (!engine.supports(sourceType)) {
                    throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_NO_ENGINE,
                        "配置指定的解析引擎 " + engine.engineId() + " 不支持来源类型 "
                            + sourceType.getCode() + "（支持的类型见 aigov.knowledge.pipeline.parser-engine-id 说明）");
                }
                return engine;
            }
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_NO_ENGINE,
                "配置指定的解析引擎不存在：" + configured.trim() + "（已装载：" + String.join(", ", availableEngineIds())
                    + "）。显式报错而不退回默认，是为了避免「切了引擎却没生效」这种只能靠日志发现的故障。");
        }
        for (KnowledgeParsePort engine : engines) {
            if (engine.supports(sourceType)) {
                return engine;
            }
        }
        throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_NO_ENGINE,
            "没有可用于来源类型 " + sourceType.getCode() + " 的解析引擎（已装载：" + String.join(", ", availableEngineIds())
                + "）。首期支持 PDF/DOCX/TXT/Markdown。");
    }

    /**
     * 已装载引擎 ID（诊断与错误文案用）。
     *
     * @return 引擎 ID 列表（按装载顺序）
     */
    public List<String> availableEngineIds() {
        List<String> ids = new ArrayList<>();
        for (KnowledgeParsePort engine : engines) {
            ids.add(engine.engineId());
        }
        return ids;
    }

}
