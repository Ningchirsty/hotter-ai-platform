package org.dromara.ai.image.service;

import org.dromara.ai.image.cloud.ImageCloudService;
import org.dromara.common.core.domain.PageResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本人成功云端任务的真实作品池；只读，不触发供应商生成或分享其他账号素材。 */
@Service
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageInspirationService {
    private final JdbcTemplate jdbc;
    public ImageInspirationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 按账号、模型、能力及描述检索真实作品，每次查询包含最新完成任务。 */
    public PageResult<Map<String, Object>> list(String tenant, long user, int page, int size,
                                               String model, String capability, String keyword,
                                               String category, List<String> assetIds) {
        int safeSize = Math.max(1, Math.min(24, size));
        int safePage = Math.max(1, Math.min(100000, page));
        List<Object> args = new ArrayList<>(List.of(tenant, user, ImageCloudService.WORKFLOW));
        StringBuilder from = new StringBuilder("""
             FROM image_asset a JOIN image_task t ON t.id = a.task_id
              AND t.tenant_id = a.tenant_id AND t.user_id = a.user_id
             WHERE a.tenant_id = ? AND a.user_id = ? AND t.workflow_code = ?
               AND t.status = 'SUCCEEDED' AND t.del_flag = '0' AND a.del_flag = '0'
               AND a.source_kind = 'OUTPUT' AND a.asset_type = 'IMAGE'
            """);
        if (model != null && !model.isBlank()) { from.append(" AND t.model_code = ?"); args.add(model); }
        if (capability != null && !capability.isBlank()) { from.append(" AND t.capability_code = ?"); args.add(capability); }
        if (keyword != null && !keyword.isBlank()) {
            from.append(" AND (LOCATE(LOWER(?), LOWER(t.prompt)) > 0 OR LOCATE(LOWER(?), LOWER(t.task_name)) > 0)");
            String term = keyword.strip().substring(0, Math.min(keyword.strip().length(), 100));
            args.add(term); args.add(term);
        }
        if (category != null && !category.isBlank() && !"全部".equals(category)) {
            from.append(" AND (" + categorySql() + ") = ?"); args.add(category);
        }
        if (assetIds != null) {
            List<String> ids = assetIds.stream().limit(200).filter(id -> id.matches("[0-9]{1,19}")).toList();
            if (ids.isEmpty()) from.append(" AND 1 = 0");
            else { from.append(" AND a.id IN (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")"); args.addAll(ids); }
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        long count = total == null ? 0 : total;
        safePage = (int) Math.min(safePage, Math.max(1, (count + safeSize - 1) / safeSize));
        String select = "SELECT a.id AS asset_id, t.id AS task_id, t.task_no, t.task_name, t.model_code, "
            + "t.capability_code, t.prompt, a.width, a.height, a.content_type, t.finished_time";
        args.add(safeSize); args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> works = jdbc.queryForList(select + from + " ORDER BY a.id DESC LIMIT ? OFFSET ?", args.toArray())
            .stream().map(row -> {
                Map<String, Object> item = new LinkedHashMap<>();
                // 字符串 ID 保留雪花 ID 的精度；不下发存储路径、原始供应商 URL 或请求快照。
                item.put("id", String.valueOf(row.get("asset_id")));
                item.put("assetId", String.valueOf(row.get("asset_id")));
                item.put("taskId", String.valueOf(row.get("task_id")));
                item.put("taskNo", row.get("task_no"));
                item.put("title", row.get("task_name"));
                item.put("model", row.get("model_code"));
                item.put("capability", row.get("capability_code"));
                item.put("prompt", row.get("prompt"));
                item.put("width", row.get("width")); item.put("height", row.get("height"));
                item.put("contentType", row.get("content_type"));
                item.put("createdAt", row.get("finished_time"));
                item.put("source", "bluocto");
                return item;
            }).toList();
        return PageResult.build(works, count);
    }

    private static String categorySql() {
        String[][] categories = {
            {"产品设计", "product", "bottle", "teapot", "vase", "产品", "瓶", "茶壶", "花瓶"},
            {"建筑设计", "architecture", "building", "interior", "建筑", "室内", "空间"},
            {"角色设计", "character", "portrait", "person", "角色", "人物", "肖像"},
            {"海报与广告", "poster", "advertis", "海报", "广告"},
            {"插画", "illustration", "cartoon", "插画", "卡通"}
        };
        StringBuilder expression = new StringBuilder("CASE ");
        for (String[] group : categories) {
            List<String> tests = new ArrayList<>();
            for (int i = 1; i < group.length; i++) tests.add("LOCATE('" + group[i] + "', LOWER(t.prompt)) > 0");
            expression.append("WHEN ").append(String.join(" OR ", tests)).append(" THEN '").append(group[0]).append("' ");
        }
        return expression.append("ELSE '风格与构图' END").toString();
    }
}
