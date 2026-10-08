package org.dromara.ai.image.service;

import org.dromara.ai.image.cloud.ImageCloudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ImageInspirationServiceTest {
    private JdbcTemplate jdbc;
    private ImageInspirationService service;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:inspiration" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE image_task (id BIGINT, tenant_id VARCHAR, user_id BIGINT, workflow_code VARCHAR, status VARCHAR, del_flag VARCHAR, model_code VARCHAR, capability_code VARCHAR, prompt VARCHAR, task_no VARCHAR, task_name VARCHAR, finished_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE image_asset (id BIGINT, task_id BIGINT, tenant_id VARCHAR, user_id BIGINT, source_kind VARCHAR, asset_type VARCHAR, del_flag VARCHAR, width INT, height INT, content_type VARCHAR)");
        service = new ImageInspirationService(jdbc);
        insert(1, "000000", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "OUTPUT", "vase product");
        insert(2, "000000", 7, "RUNNING", ImageCloudService.WORKFLOW, "0", "OUTPUT", "building");
        insert(3, "000000", 8, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "OUTPUT", "private other user");
        insert(4, "other", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "OUTPUT", "private tenant");
        insert(5, "000000", 7, "SUCCEEDED", "local-workflow", "0", "OUTPUT", "local image");
        insert(6, "000000", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "1", "OUTPUT", "deleted");
        insert(7, "000000", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "UPLOAD", "input reference");
        insert(8, "000000", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "OUTPUT", "architecture building");
    }
    private void insert(long id, String tenant, long user, String status, String workflow, String deleted, String kind, String prompt) {
        jdbc.update("INSERT INTO image_task VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", id, tenant, user, workflow, status, deleted, "gpt-image-2.5-flare", "T2I", prompt, "IMAGE-"+id, "Work "+id);
        jdbc.update("INSERT INTO image_asset VALUES (?,?,?,?,?,?,?,?,?,?)", id+100, id, tenant, user, kind, "IMAGE", "0", 1024, 1536, "image/png");
    }
    @Test void onlyOwnedSuccessfulCloudOutputsAreVisibleAndSnowflakeIdsRemainStrings() {
        var page = service.list("000000", 7, 1, 6, null, null, null, null, null);
        assertEquals(2, page.getTotal());
        assertEquals(List.of("108", "101"), page.getRows().stream().map(row -> row.get("assetId")).toList());
        assertFalse(page.getRows().iterator().next().containsKey("storageKey"));
    }
    @Test void paginationIsStableAndNewCompletedWorksBecomeVisibleWithoutGenerating() {
        var first = service.list("000000", 7, 1, 1, null, null, null, null, null);
        var second = service.list("000000", 7, 2, 1, null, null, null, null, null);
        assertNotEquals(first.getRows().iterator().next().get("id"), second.getRows().iterator().next().get("id"));
        insert(9, "000000", 7, "SUCCEEDED", ImageCloudService.WORKFLOW, "0", "OUTPUT", "new vase");
        var updated = service.list("000000", 7, 1, 1, null, null, null, null, null);
        assertEquals(3, updated.getTotal()); assertEquals("109", updated.getRows().iterator().next().get("id"));
    }
    @Test void filtersApplyBeforePaginationAndInjectedTextCannotExpandAccountScope() {
        assertEquals(1, service.list("000000", 7, 1, 1, null, null, null, "建筑设计", null).getTotal());
        assertEquals(0, service.list("000000", 7, 1, 1, "' OR 1=1 --", null, null, null, null).getTotal());
        assertEquals(0, service.list("000000", 7, 1, 1, null, "EDIT", null, null, null).getTotal());
        assertEquals(1, service.list("000000", 7, 1, 1, null, null, "vase", null, null).getTotal());
        assertEquals(1, service.list("000000", 7, 1, 6, null, null, null, null, List.of("108", "103")).getTotal());
        assertEquals(0, service.list("000000", 7, 1, 6, null, null, null, null, List.of()).getTotal());
    }
    @Test void mismatchedTaskAndAssetOwnerIsExcluded() {
        jdbc.update("UPDATE image_asset SET user_id = 7 WHERE id = 103");
        assertEquals(2, service.list("000000", 7, 1, 6, null, null, null, null, null).getTotal());
    }
}
