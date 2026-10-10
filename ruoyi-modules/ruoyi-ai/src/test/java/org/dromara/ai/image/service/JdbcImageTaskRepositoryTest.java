package org.dromara.ai.image.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdbcImageTaskRepository} 的列类型映射测试。
 *
 * <p><b>为什么需要它</b>：{@code has_alpha} 是 {@code TINYINT(1)}，MySQL 驱动默认把它映射成
 * {@code Boolean}（{@code tinyInt1isBit=true}）。仓库实现曾经强转 {@code Number}，导致
 * <b>素材下载与缩略图接口全部 500</b>
 * （{@code ClassCastException: class java.lang.Boolean cannot be cast to class java.lang.Number}）——
 * 而上传、任务列表都不读这一列，离线单测又用替身仓储，所以一路漏到隔离联调才暴露。</p>
 */
class JdbcImageTaskRepositoryTest {

    @Test
    @DisplayName("TINYINT(1) 的各种真实返回形态都要能正确解析")
    void tinyIntMapping() {
        assertEquals(Boolean.TRUE, JdbcImageTaskRepository.tinyIntToBoolean(Boolean.TRUE));
        assertEquals(Boolean.FALSE, JdbcImageTaskRepository.tinyIntToBoolean(Boolean.FALSE));
        assertEquals(Boolean.TRUE, JdbcImageTaskRepository.tinyIntToBoolean(1));
        assertEquals(Boolean.FALSE, JdbcImageTaskRepository.tinyIntToBoolean(0));
        // Boolean.parseBoolean("1") 是 false，所以字符串分支必须显式处理 "1"
        assertEquals(Boolean.TRUE, JdbcImageTaskRepository.tinyIntToBoolean("1"));
        assertEquals(Boolean.FALSE, JdbcImageTaskRepository.tinyIntToBoolean("0"));
        assertEquals(null, JdbcImageTaskRepository.tinyIntToBoolean(null));
    }

    @Test
    @DisplayName("行映射遇到 Boolean 形态的 has_alpha 不得抛 ClassCastException")
    void mapsRowWithBooleanFlag() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 2101967178066923522L);
        row.put("tenant_id", "000000");
        row.put("user_id", 1761799999999999001L);
        row.put("task_id", 123L);
        row.put("asset_type", "IMAGE");
        row.put("source_kind", "OUTPUT");
        row.put("original_name", "out.png");
        row.put("storage_key", "000000/1761799999999999001/output/x.png");
        row.put("content_type", "image/png");
        row.put("size_bytes", 1407940L);
        row.put("checksum", null);
        row.put("width", 1024);
        row.put("height", 1024);
        // 关键：驱动真实返回的是 Boolean，而不是 0/1
        row.put("has_alpha", Boolean.TRUE);

        ImageTaskRepository.AssetRow asset = JdbcImageTaskRepository.toAssetRow(row);

        assertEquals(1024, asset.width());
        assertEquals(1024, asset.height());
        assertEquals(Boolean.TRUE, asset.hasAlpha());
        assertTrue(asset.sizeBytes() > 0);
        assertEquals("image/png", asset.contentType());
    }

    @Test
    @DisplayName("行映射遇到数值形态（TINYINT 关闭 bit 映射时）同样正确")
    void mapsRowWithNumericFlag() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("tenant_id", "000000");
        row.put("user_id", 2L);
        row.put("asset_type", "IMAGE");
        row.put("source_kind", "UPLOAD");
        row.put("storage_key", "k");
        row.put("size_bytes", 10L);
        row.put("has_alpha", 0);
        assertEquals(Boolean.FALSE, JdbcImageTaskRepository.toAssetRow(row).hasAlpha());
    }

    @Test
    @DisplayName("任务查询必须带上 submitted_time / started_time（否则页面「耗时」永远是「—」）")
    void taskSelectCarriesTimeColumnsTheCreativeSideReads() {
        String sql = JdbcImageTaskRepository.taskSelect();
        // 视觉工厂用它算耗时：durationMs = finished_time - started_time
        assertTrue(sql.contains("started_time"),
            "缺 started_time → CreativeGenerationServiceImpl.durationOf 恒为 null → 「耗时」列永远是「—」");
        // 内核真正的提交时刻（候选行的 create_time 是派发**之后**写的，不等价）
        assertTrue(sql.contains("submitted_time"),
            "缺 submitted_time → 页面上拿不到内核记录的真实提交时刻");
        assertTrue(sql.contains("finished_time"), "缺 finished_time 则耗时算不出来");
    }
    @Test
    @DisplayName("真实 JDBC 任务查询必须保留模板幂等编号")
    void ownedTaskQueryPreservesTemplateIdempotencyKey() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:h2:mem:template-repository" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        String columns = JdbcImageTaskRepository.taskSelect().split("SELECT", 2)[1].split("FROM", 2)[0];
        var definitions = new java.util.ArrayList<String>();
        for (String column : columns.split(",")) {
            String name = column.strip();
            definitions.add(name + (java.util.List.of("id", "user_id").contains(name) ? " BIGINT" : " VARCHAR(8000)"));
        }
        if (!columns.contains("idempotency_key")) definitions.add("idempotency_key VARCHAR(128)");
        definitions.add("del_flag CHAR(1)");
        jdbc.execute("CREATE TABLE image_task (" + String.join(",", definitions) + ")");
        String key = "tpl-37f0fa97-c5cd-4818-bbcf-dbe4e51e3c69";
        jdbc.update("INSERT INTO image_task (id,tenant_id,user_id,status,idempotency_key,del_flag) VALUES (7,'a',9,'QUEUED',?,'0')", key);
        var repository = new JdbcImageTaskRepository(jdbc);
        assertEquals(key, repository.requireOwnedTask(7L, "a", 9L).get("idempotency_key"));
    }

}
