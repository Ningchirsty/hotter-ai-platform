package org.dromara.aigov.workspace.portal.helper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作台收藏清单的编解码（主文档线增量 5）。
 *
 * <h3>为什么容忍坏数据、而不是抛异常</h3>
 * <p>收藏是**偏好**：一串坏掉的 JSON 不该让员工打不开工作台。所以解码遇到非法内容按"没有收藏"处理，
 * 而不是让整个门户报错。反过来说，也正因为它只是偏好——**绝不能拿它参与可见性判定**，
 * 否则"坏数据 → 没有收藏"就会变成"坏数据 → 看不到岗位"。</p>
 *
 * <h3>为什么要有上限</h3>
 * <p>{@code favorites_json} 是 longtext，但"能存"不等于"该存"。没有上限时，一个脚本化调用
 * 可以把整张表撑大，而界面上没人会看第 200 个收藏。上限之外的新增**直接不记录**并保持原样
 * （不静默丢弃已有收藏）。</p>
 *
 * @author ai-gov
 */
public final class AigWorkspaceFavorites {

    /**
     * 序列化（Jackson 3）
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 收藏上限（超过后新增不再记录；已有收藏不动）
     */
    public static final int MAX_FAVORITES = 200;

    private AigWorkspaceFavorites() {
    }

    /**
     * 解码收藏清单。
     *
     * @param json JSON 数组字符串（可空/可坏）
     * @return 收藏清单（保序、去重、去空白项）；解不出时返回空清单
     */
    public static List<String> decode(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isArray()) {
                return result;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode item : node) {
                // 只接受**字符串**：收藏的是岗位编码（字符串）。
                // 若把数字/布尔也 asText 进来，就会存下"1"/"true"这种永远匹配不上岗位的条目——
                // 它们不会报错，只会让收藏清单里出现点了没反应的假条目。
                if (item == null || !item.isTextual()) {
                    continue;
                }
                String code = item.asText();
                if (code == null || code.isBlank()) {
                    continue;
                }
                if (seen.add(code.trim())) {
                    result.add(code.trim());
                }
            }
        } catch (Exception e) {
            // 偏好坏了不该挡住工作台：按"没有收藏"处理
            return new ArrayList<>();
        }
        return result;
    }

    /**
     * 编码收藏清单。
     *
     * @param favorites 收藏清单（可空）
     * @return JSON 数组字符串
     */
    public static String encode(List<String> favorites) {
        List<String> safe = favorites == null ? List.of() : favorites;
        return MAPPER.writeValueAsString(safe);
    }

    /**
     * 收藏/取消收藏。
     *
     * @param favorites 当前收藏
     * @param roleCode  岗位编码（空白时原样返回，不做任何事）
     * @param favorite  true=收藏，false=取消
     * @return 新的收藏清单
     */
    public static List<String> toggle(List<String> favorites, String roleCode, boolean favorite) {
        List<String> current = favorites == null ? new ArrayList<>() : new ArrayList<>(favorites);
        if (roleCode == null || roleCode.isBlank()) {
            return current;
        }
        String code = roleCode.trim();
        if (favorite) {
            if (current.contains(code)) {
                return current;
            }
            if (current.size() >= MAX_FAVORITES) {
                // 到上限就不再记录新的，但**不动**已有收藏（静默丢弃会让用户以为"点了没反应"）
                return current;
            }
            current.add(code);
            return current;
        }
        current.remove(code);
        return current;
    }

}
