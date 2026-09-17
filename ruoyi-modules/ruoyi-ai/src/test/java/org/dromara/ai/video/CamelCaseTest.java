package org.dromara.ai.video;

import org.dromara.ai.video.support.CamelCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口字段名必须是 camelCase。
 *
 * <p>锁住一次真实事故：任务列表/详情/素材列表直接把数据库行原样返回，
 * 前端收到的是 {@code output_asset_id} / {@code asset_type} 这类列名，
 * 而前端类型声明的是 camelCase。结果任务卡片上的编号、时长、失败原因全空，
 * 缩略图不显示，点「预览成片」直接判定「该任务没有可预览的成片素材」。</p>
 */
class CamelCaseTest {

    @Test
    @DisplayName("单键：snake_case 转 camelCase，无下划线的键原样保留")
    void convertsKeys() {
        assertEquals("assetType", CamelCase.key("asset_type"));
        assertEquals("outputAssetId", CamelCase.key("output_asset_id"));
        assertEquals("durationMs", CamelCase.key("duration_ms"));
        assertEquals("originalName", CamelCase.key("original_name"));
        assertEquals("id", CamelCase.key("id"));
        assertEquals("status", CamelCase.key("status"));
        assertEquals("tier", CamelCase.key("tier"));
        // 边界：首尾下划线、连续下划线都不应产生异常结果
        assertEquals("AssetType", CamelCase.key("_asset_type"));
        assertEquals("assetType", CamelCase.key("asset__type"));
        assertEquals("assetTypeX", CamelCase.key("asset_type_x"));
    }

    @Test
    @DisplayName("任务行：前端声明的字段一个都不能少")
    void convertsTaskRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("task_no", "VIDEO-1");
        row.put("task_name", "图生视频");
        row.put("capability_code", "I2V");
        row.put("model_code", "h3");
        row.put("status", "SUCCEEDED");
        row.put("tier", "流畅 · 720P");
        row.put("duration_seconds", 5);
        row.put("output_asset_id", 2100528711380414467L);
        row.put("error_message", null);
        row.put("create_time", "2026-09-17 10:00:00");

        Map<String, Object> out = CamelCase.row(row);
        for (String key : List.of("id", "taskNo", "taskName", "capabilityCode", "modelCode",
            "status", "tier", "durationSeconds", "outputAssetId", "errorMessage", "createTime")) {
            assertTrue(out.containsKey(key), "缺少前端要用的字段：" + key + "，实际=" + out.keySet());
        }
        // 原始列名不应再出现
        assertTrue(out.keySet().stream().noneMatch(k -> k.contains("_")),
            "不应残留数据库列名：" + out.keySet());
        assertEquals("VIDEO-1", out.get("taskNo"));
        assertEquals(2100528711380414467L, out.get("outputAssetId"));
    }

    @Test
    @DisplayName("素材行：assetType/contentType/sizeBytes/width 等都要转过来")
    void convertsAssetRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 2L);
        row.put("asset_type", "IMAGE");
        row.put("source_kind", "UPLOAD");
        row.put("original_name", "yuyi.png");
        row.put("content_type", "image/png");
        row.put("size_bytes", 3242395L);
        row.put("width", 1024);
        row.put("height", 1024);
        row.put("duration_ms", null);
        row.put("task_id", null);

        Map<String, Object> out = CamelCase.row(row);
        assertEquals("IMAGE", out.get("assetType"));
        assertEquals("UPLOAD", out.get("sourceKind"));
        assertEquals("yuyi.png", out.get("originalName"));
        assertEquals("image/png", out.get("contentType"));
        assertEquals(3242395L, out.get("sizeBytes"));
        assertEquals(1024, out.get("width"));
        assertTrue(out.containsKey("durationMs"));
        assertTrue(out.containsKey("taskId"));
    }

    @Test
    @DisplayName("任务详情里的 events 列表也要递归转换（eventType / createTime）")
    void convertsNestedEvents() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sequence", 1);
        event.put("event_type", "SUBMITTED");
        event.put("detail", "已提交 ComfyUI");
        event.put("create_time", "2026-09-17 10:00:01");

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("task_no", "VIDEO-1");
        task.put("events", List.of(event));

        Map<String, Object> out = CamelCase.row(task);
        assertEquals("VIDEO-1", out.get("taskNo"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) out.get("events");
        assertEquals(1, events.size());
        assertEquals("SUBMITTED", events.get(0).get("eventType"));
        assertTrue(events.get(0).containsKey("createTime"), "事件时间也要转换：" + events.get(0).keySet());
    }

    @Test
    @DisplayName("null / 空输入不抛异常")
    void handlesNulls() {
        assertTrue(CamelCase.row(null).isEmpty());
        assertTrue(CamelCase.rows(null).isEmpty());
        assertEquals("assetType", CamelCase.key("asset_type"));
    }
}
