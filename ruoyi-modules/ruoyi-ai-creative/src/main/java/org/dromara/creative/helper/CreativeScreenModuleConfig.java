package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.utils.StringUtils;

/**
 * 一屏从模块规划带来的配置（V0.2 R23）。
 *
 * <p>分镜生成时会把模块规划里"这一屏专属"的配置烙进屏的 {@code spec_json}
 * （参考图、视觉表达、模板、模块目标、卖点块）。出图与排版是在别的请求里跑的，
 * 它们只看得到屏——所以这里给一个**统一的读法**，避免每个调用方各写一份解析。</p>
 *
 * <p><b>读不到就当没有</b>：老分镜没有这些字段、或 {@code spec_json} 被手工改坏，
 * 都不该让出图/排版跑不起来；但也绝不"猜一个"——要么按配置来，要么按默认行为，
 * 并在日志里说明（静默用错配置比没有配置更糟）。</p>
 *
 * @param referenceFileId 参考图附件ID（可空＝取最近一张图片附件）
 * @param visualRules     视觉表达（可空）
 * @param qaRules         质检规则 JSON（可空＝这一屏没配规则，体检时如实显示"未配置"，见 {@link CreativeQaRules}）
 * @author creative
 */
public record CreativeScreenModuleConfig(Long referenceFileId, String visualRules, String qaRules) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 什么都没有的配置（读不到时用） */
    public static final CreativeScreenModuleConfig EMPTY = new CreativeScreenModuleConfig(null, null, null);

    /**
     * 从屏的 spec_json 解析模块配置。
     *
     * @param specJson 屏规格 JSON（可空）
     * @return 模块配置；解析不了返回 {@link #EMPTY}
     */
    public static CreativeScreenModuleConfig parse(String specJson) {
        if (StringUtils.isBlank(specJson)) {
            return EMPTY;
        }
        try {
            JsonNode node = MAPPER.readTree(specJson);
            return new CreativeScreenModuleConfig(referenceOf(node), rulesOf(node), qaRulesOf(node));
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /**
     * 取参考图附件ID：{@code referenceFileIds} 里第一个能解析成数字的（模块里可以写多个候选，第一个是选中项）。
     *
     * @param node 规格 JSON
     * @return 附件ID；没有则 null
     */
    private static Long referenceOf(JsonNode node) {
        JsonNode refs = node.path("referenceFileIds");
        if (!refs.isArray()) {
            return null;
        }
        for (JsonNode item : refs) {
            String text = StringUtils.trimToNull(item.asText());
            if (text == null) {
                continue;
            }
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                // 非数字的编码跳过：参考图字段也可能写的是别体系的编码，不是错误
            }
        }
        return null;
    }

    /**
     * 取视觉表达（原样文本；空或纯空白视为没有）。
     *
     * @param node 规格 JSON
     * @return 视觉表达；没有则 null
     */
    private static String rulesOf(JsonNode node) {
        return StringUtils.trimToNull(node.path("visualRules").asText(null));
    }

    /**
     * 取质检规则 JSON（R29：模块库的 qaRules 在分镜生成时被烙进屏）。
     *
     * @param node 规格 JSON
     * @return 规则 JSON 文本；没有则 null
     */
    private static String qaRulesOf(JsonNode node) {
        JsonNode qa = node.path("qaRules");
        if (qa.isMissingNode() || qa.isNull()) {
            return null;
        }
        // 规则既可能是对象（模块库里就是 JSON），也可能是被写成字符串的 JSON：
        // 两者都按原样文本交给 CreativeQaRules 解析，这里不做"聪明"的转换。
        String text = qa.isTextual() ? qa.asText() : qa.toString();
        return StringUtils.trimToNull(text);
    }

    /**
     * 从屏规格里取模块编码（R30 起交付包要按模块编码命名产物，多个调用方都需要）。
     *
     * @param specJson 屏规格 JSON（可空）
     * @return 模块编码；取不到返回空串
     */
    public static String moduleCodeOf(String specJson) {
        if (StringUtils.isBlank(specJson)) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(specJson);
            return StringUtils.blankToDefault(StringUtils.trimToNull(node.path("moduleCode").asText(null)), "");
        } catch (Exception e) {
            return "";
        }
    }
}
