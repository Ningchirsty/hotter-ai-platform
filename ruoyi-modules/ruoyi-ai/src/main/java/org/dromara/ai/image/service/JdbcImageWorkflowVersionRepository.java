package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code image_workflow_version} 的 JDBC 实现（MySQL 方言，与视频模块一致）。
 */
@Slf4j
@Repository
public class JdbcImageWorkflowVersionRepository implements ImageWorkflowVersionRepository {

    private static final long SYSTEM_USER = 1L;

    private final JdbcTemplate jdbc;

    public JdbcImageWorkflowVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean upsert(ImageWorkflowVersion version, String checksum, boolean templateLoaded, String mappingJson,
                          String outputRuleJson, String billingJson, String perfJson, String supportedOutputsJson) {
        int affected = jdbc.update("""
            INSERT INTO image_workflow_version (id, capability_code, workflow_code, model_code, version,
                                                workflow_path, mapping_json, output_rule_json, billing_json,
                                                perf_json, checksum, status, create_by, create_time,
                                                update_by, update_time)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),?,NOW())
            ON DUPLICATE KEY UPDATE
                capability_code = VALUES(capability_code),
                model_code = VALUES(model_code),
                workflow_path = VALUES(workflow_path),
                mapping_json = VALUES(mapping_json),
                output_rule_json = VALUES(output_rule_json),
                billing_json = VALUES(billing_json),
                perf_json = VALUES(perf_json),
                checksum = VALUES(checksum),
                update_by = VALUES(update_by),
                update_time = NOW()
            """,
            org.dromara.common.mybatis.utils.IdGeneratorUtil.nextLongId(),
            version.capabilityCode(), version.workflowCode(), version.modelCode(), version.version(),
            version.apiJsonFile(), mappingJson, outputRuleJson, billingJson, perfJson,
            templateLoaded ? checksum : null, version.status(), SYSTEM_USER, SYSTEM_USER,
            supportedOutputsJson);
        // MySQL：1=插入，2=更新
        return affected == 1;
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM image_workflow_version", Long.class);
        return count == null ? 0 : count;
    }
}
