package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「这次排版用哪个模板」的解析（V0.2 R23，文档 §24 的模块模板字段）。
 *
 * <p>模块规划里每个模块可以钉一个模板（`template_codes`），但**一个详情页只能用一个模板**。
 * 于是规则必须写死在一个地方、并且可单测：</p>
 * <ol>
 *   <li>没有任何屏钉模板 → 用默认模板；</li>
 *   <li>所有钉了的屏都指向同一个 {@code code@version} → 用它（能不能用由发布门决定）；</li>
 *   <li>钉得不一样 → 抛错说明冲突（随便挑一个等于把用户的配置当没看见）；</li>
 *   <li>只写了 code 没写版本 → 抛错要求写全（不猜版本）。</li>
 * </ol>
 *
 * <p>为什么抽成 helper：R23 想真机验"钉了未登记的模板会报错"，但排版接口的**第一道是视觉门**
 * （未过门直接拒），新项目根本走不到模板那一步。逻辑抽出来以后，这几种分支都能被单测钉死，
 * 真机则验"读接口给出的模板标识确实跟着计划变"。</p>
 *
 * @author creative
 */
public final class CreativeTemplatePin {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeTemplatePin() {
    }

    /**
     * 解析结果。
     *
     * @param code     模板码
     * @param version  模板版本
     * @param fromPlan 是不是模块规划钉的（false=默认模板）
     */
    public record Pinned(String code, String version, boolean fromPlan) {
    }

    /**
     * 从各屏的 spec_json 汇总模板选择。
     *
     * @param specJsons     各屏的 spec_json（可含 null / 空 / 脏数据）
     * @param defaultCode   默认模板码
     * @param defaultVersion 默认模板版本
     * @return 模板选择
     * @throws ServiceException 钉得冲突或没写版本时抛出（消息面向用户）
     */
    public static Pinned resolve(List<String> specJsons, String defaultCode, String defaultVersion) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        if (specJsons != null) {
            for (String specJson : specJsons) {
                collect(specJson, votes);
            }
        }
        if (votes.isEmpty()) {
            return new Pinned(defaultCode, defaultVersion, false);
        }
        if (votes.size() > 1) {
            throw new ServiceException("模块规划里给不同模块钉了不同的排版模板（"
                + String.join("、", votes.keySet()) + "）。一个详情页只能用一个模板，请统一后再排版。");
        }
        String pinned = votes.keySet().iterator().next();
        int at = pinned.indexOf('@');
        if (at <= 0 || at == pinned.length() - 1) {
            throw new ServiceException("模块规划里的模板要写成「模板码@版本」（例如 longpage@1.0.2），当前=" + pinned);
        }
        return new Pinned(pinned.substring(0, at), pinned.substring(at + 1), true);
    }

    /**
     * 从单屏 spec 里收集模板码。
     *
     * @param specJson 屏规格 JSON（可空/可脏）
     * @param votes    票数累加器
     */
    private static void collect(String specJson, Map<String, Integer> votes) {
        if (StringUtils.isBlank(specJson)) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(specJson);
            JsonNode codes = node.path("templateCodes");
            if (codes.isArray()) {
                for (JsonNode item : codes) {
                    String value = StringUtils.trimToNull(item.asText());
                    if (value != null) {
                        votes.merge(value, 1, Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            // 老分镜没有这个字段、或 spec_json 被手工改坏：按"没钉"处理（不猜），
            // 但也绝不因此让整次排版失败——坏的是一个屏的元数据，不是业务配置。
        }
    }
}
