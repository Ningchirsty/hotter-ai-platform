package org.dromara.ai.video.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把数据库列名（snake_case）转成接口契约里的 camelCase。
 *
 * <p>为什么需要它：视频模块的任务列表、任务详情、素材列表此前直接把 JdbcTemplate 的行
 * 原样返回，前端收到的是 {@code output_asset_id} / {@code asset_type} 这类<b>列名</b>，
 * 而前端类型声明的是 {@code outputAssetId} / {@code assetType}。后果很具体：
 * 任务卡片上的编号、时长、失败原因全是空的，素材缩略图不显示，
 * 点「预览成片」还会直接判定「该任务没有可预览的成片素材」——成片明明就在库里。</p>
 *
 * <p>对外输出应该是 camelCase（与若依其它 VO 一致），数据库列名不外漏。</p>
 */
public final class CamelCase {

    private CamelCase() {
    }

    /**
     * 转换一行记录（递归处理嵌套的 Map/List，例如任务事件序列）。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> row(Map<String, Object> row) {
        if (row == null) {
            return Map.of();
        }
        return (Map<String, Object>) value(row);
    }

    /**
     * 转换多行记录。
     */
    public static List<Map<String, Object>> rows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(row(row));
        }
        return out;
    }

    /**
     * 递归转换任意值：Map 转换键，List 逐项处理，其它原样返回。
     */
    public static Object value(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(key(String.valueOf(entry.getKey())), value(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(value(item));
            }
            return out;
        }
        return value;
    }

    /**
     * 单个键名转换：{@code asset_type -> assetType}。
     *
     * <p>没有下划线的键原样返回（{@code id}、{@code status}、{@code tier} 等）。</p>
     */
    public static String key(String key) {
        if (key == null || key.indexOf('_') < 0) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key.length());
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }
}
