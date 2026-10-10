package org.dromara.ai.image.service;

import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.video.service.JdbcVideoTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Executes real repository SQL against an isolated database; never accesses production. */
class TaskRecoverySqlTest {
    private JdbcTemplate jdbc;
    private JdbcImageTaskRepository images;
    private JdbcVideoTaskRepository videos;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(source);
        String common = "id BIGINT PRIMARY KEY, tenant_id VARCHAR(32), user_id BIGINT, task_no VARCHAR(128), "
            + "task_name VARCHAR(128), status VARCHAR(32), del_flag VARCHAR(1) DEFAULT '0', "
            + "capability_code VARCHAR(32), workflow_code VARCHAR(64), workflow_version VARCHAR(32), model_code VARCHAR(64), "
            + "size_label VARCHAR(64), strength_label VARCHAR(64), prompt VARCHAR(1024), negative_prompt VARCHAR(1024), "
            + "input_json VARCHAR(1024), idempotency_key VARCHAR(128), comfy_prompt_id VARCHAR(64), comfy_worker VARCHAR(64), "
            + "output_asset_id BIGINT, cover_asset_id BIGINT, progress INT, error_code VARCHAR(64), error_message VARCHAR(1024), "
            + "attempt_count INT, output_width INT, output_height INT, output_has_alpha INT NOT NULL DEFAULT 0, output_size_bytes BIGINT, "
            + "submitted_time TIMESTAMP, started_time TIMESTAMP, create_time TIMESTAMP, finished_time TIMESTAMP, "
            + "update_time TIMESTAMP, tier VARCHAR(64), duration_seconds INT";
        jdbc.execute("CREATE TABLE image_task (" + common + ")");
        jdbc.execute("CREATE TABLE video_task (" + common + ")");
        images = new JdbcImageTaskRepository(jdbc);
        videos = new JdbcVideoTaskRepository(jdbc);
    }

    @Test
    void retryClearsStaleExecutionAndGuardsOwnershipAndConcurrentClicks() {
        jdbc.update("INSERT INTO image_task (id,tenant_id,user_id,status,error_code,error_message,finished_time,"
            + "comfy_prompt_id,output_asset_id,progress,prompt) VALUES (1,'a',42,'TIMEOUT','TIMEOUT','old',NOW(),'old',99,10,'keep prompt')");
        assertEquals(0, images.reopen(1, "b", 42, ImageTaskStatus.TIMEOUT));
        assertEquals(0, images.reopen(1, "a", 43, ImageTaskStatus.TIMEOUT));
        assertEquals(1, images.reopen(1, "a", 42, ImageTaskStatus.TIMEOUT));
        assertEquals(0, images.reopen(1, "a", 42, ImageTaskStatus.TIMEOUT));
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM image_task WHERE id=1");
        assertEquals("QUEUED", row.get("status"));
        for (String key : List.of("error_code", "error_message", "finished_time", "comfy_prompt_id", "output_asset_id"))
            assertNull(row.get(key), key);
        assertEquals("keep prompt", row.get("prompt"));
        assertEquals(0, ((Number) row.get("output_has_alpha")).intValue());
        assertEquals(0, images.reopen(1, "a", 42, ImageTaskStatus.SUCCEEDED));
    }

    @Test
    void searchAndPaginationCoverOldRecordsAndKeepTenantsIsolated() {
        for (String table : List.of("image_task", "video_task")) {
            for (int id=1; id<=75; id++) jdbc.update("INSERT INTO " + table
                + " (id,tenant_id,user_id,status,task_no,task_name) VALUES (?,'a',42,'FAILED',?,?)",
                id, "TASK-" + id, id==1 ? "old 100%_target" : "other");
            jdbc.update("INSERT INTO " + table
                + " (id,tenant_id,user_id,status,task_no,task_name) VALUES (90,'b',42,'FAILED','FOREIGN','old 100%_target')");
        }
        assertEquals(75, images.countOwnedTasks("a",42,"FAILED",null));
        assertEquals(75, videos.countOwnedTasks("a",42,"FAILED",null));
        assertEquals(20, images.listOwnedTasks("a",42,"FAILED",null,20,20).size());
        assertEquals(20, videos.listOwnedTasks("a",42,"FAILED",null,20,20).size());
        assertEquals(1, images.countOwnedTasks("a",42,"FAILED","100%_TARGET"));
        assertEquals(1, videos.countOwnedTasks("a",42,"FAILED","100%_TARGET"));
        assertEquals(1L, ((Number) images.listOwnedTasks("a",42,"FAILED","100%_TARGET",0,20).get(0).get("id")).longValue());
        assertEquals(1L, ((Number) videos.listOwnedTasks("a",42,"FAILED","100%_TARGET",0,20).get(0).get("id")).longValue());
        assertEquals(0, images.countOwnedTasks("a",99,null,null));
        assertEquals(0, videos.countOwnedTasks("a",99,null,null));
    }
}
