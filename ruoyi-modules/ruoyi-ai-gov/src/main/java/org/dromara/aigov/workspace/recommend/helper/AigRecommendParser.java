package org.dromara.aigov.workspace.recommend.helper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 推荐结果的解析（主文档线增量 7）。
 *
 * <h3>为什么解析要写得"能容错、但结论从严"</h3>
 * <p>模型输出天然不老实：可能包一层 markdown 代码块、可能前后带解释、可能给个对象
 * {@code {"actionCodes":[...]}}、也可能给纯数组。这些都**允许**（都是同一份信息的不同写法；
 * 只认一种写法的结果是"明明推荐对了却解析失败"，用户看到"没有推荐"）。
 * 但结论必须从严：</p>
 * <ul>
 *     <li>解析不出结构 → **空列表**（绝不猜卡片）；</li>
 *     <li>条目不是字符串 / 空白 → 跳过；</li>
 *     <li>重复编码 → 去重（保留第一次出现的顺序）；</li>
 *     <li>超过上限 → 截断（多余的丢掉，不报错——推荐多给几条不算错）。</li>
 * </ul>
 *
 * <h3>这里<b>不</b>做的一件事：判断"这张卡片可见吗"</h3>
 * <p>可见性必须由调用方用**服务端的可见卡片清单**做交集过滤，不能由解析器猜——
 * 提示词里只放了可见卡片，但模型完全可能凭常识吐出一个不存在的编码。
 * 所以解析器只负责"把模型说的编码提取出来"，可见性过滤是服务层用集合运算完成的一步。</p>
 *
 * @author ai-gov
 */
public final class AigRecommendParser {

    /**
     * 解析用（只读 JSON，不参与任何输出拼装）
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 允许的最大输出长度（防一次异常巨大的响应把内存吃掉；超出直接按解析失败处理）
     */
    private static final int MAX_RAW_CHARS = 20000;

    private AigRecommendParser() {
    }

    /**
     * 从模型输出里提取推荐编码。
     *
     * @param raw        模型原始输出（可空）
     * @param maxResults 最多返回几个（&lt;=0 时按 1 处理）
     * @return 编码列表（保序、去重、截断）；解析不出结构时返回空列表
     */
    public static List<String> parse(String raw, int maxResults) {
        int limit = maxResults <= 0 ? 1 : maxResults;
        if (raw == null || raw.isBlank() || raw.length() > MAX_RAW_CHARS) {
            return new ArrayList<>();
        }
        JsonNode root = readJson(raw);
        if (root == null) {
            return new ArrayList<>();
        }
        JsonNode array = locateArray(root);
        if (array == null) {
            return new ArrayList<>();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : array) {
            if (item == null || !item.isTextual()) {
                // 非字符串条目跳过：编码是字符串，把数字/对象 asText 进来只会得到匹配不上的假条目
                continue;
            }
            String code = item.asText();
            if (StringUtils.isBlank(code)) {
                continue;
            }
            seen.add(code.trim());
            if (seen.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 尽力从输出里读出一个 JSON（容忍代码块与前后解释）。
     *
     * @param raw 原始输出
     * @return JSON 根节点；读不出返回 null
     */
    private static JsonNode readJson(String raw) {
        String text = raw.trim();
        // 去掉 markdown 代码块围栏（最常见的"不老实"）
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        JsonNode direct = tryRead(text);
        if (direct != null) {
            return direct;
        }
        // 前后带解释时，取第一个 { 到最后一个 }（或 [ 到 ]）之间的片段再试一次
        JsonNode object = tryRead(slice(text, '{', '}'));
        if (object != null) {
            return object;
        }
        return tryRead(slice(text, '[', ']'));
    }

    /**
     * 截取首尾括号之间的片段。
     *
     * @param text  文本
     * @param open  起始字符
     * @param close 结束字符
     * @return 片段；找不到返回 null
     */
    private static String slice(String text, char open, char close) {
        int start = text.indexOf(open);
        int end = text.lastIndexOf(close);
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * 试读 JSON。
     *
     * @param text 文本（可空）
     * @return 根节点；不是合法 JSON 返回 null
     */
    private static JsonNode tryRead(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 定位"编码数组"：对象里取 actionCodes/actions/recommendations，或本身就是数组。
     *
     * @param root 根节点
     * @return 数组节点；没有返回 null
     */
    private static JsonNode locateArray(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        if (!root.isObject()) {
            return null;
        }
        for (String field : List.of("actionCodes", "actions", "recommendations")) {
            JsonNode node = root.get(field);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

}
