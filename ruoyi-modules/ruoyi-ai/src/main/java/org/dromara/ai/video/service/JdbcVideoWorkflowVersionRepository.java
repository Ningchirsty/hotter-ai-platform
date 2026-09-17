package org.dromara.ai.video.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link VideoWorkflowVersionRepository} 的 JDBC 实现。
 *
 * <p>用一条 {@code INSERT ... ON DUPLICATE KEY UPDATE} 完成幂等写入，依赖唯一索引
 * {@code uk_workflow_version(workflow_code, version)}；并发同步也不会产生重复行。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcVideoWorkflowVersionRepository implements VideoWorkflowVersionRepository {

    private final JdbcTemplate jdbc;

    @Override
    public boolean upsert(WorkflowVersion version, String checksum, boolean templateLoaded,
                          String mappingJson, String outputRuleJson, String billingJson,
                          String perfJson, String supportedOutputsJson) {
        // 注意：UPDATE 子句刻意不含 status / published_by / published_time，
        // 避免一次同步把审核结果（例如已 PUBLISHED）覆盖回 DRAFT。
        int affected = jdbc.update("""
            INSERT INTO video_workflow_version
              (id, capability_code, workflow_code, model_code, version, workflow_path,
               mapping_json, output_rule_json, billing_json, perf_json, checksum, status,
               create_by, create_time, update_by, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW())
            ON DUPLICATE KEY UPDATE
              capability_code   = VALUES(capability_code),
              model_code        = VALUES(model_code),
              workflow_path     = VALUES(workflow_path),
              mapping_json      = VALUES(mapping_json),
              output_rule_json  = VALUES(output_rule_json),
              billing_json      = VALUES(billing_json),
              perf_json         = VALUES(perf_json),
              checksum          = VALUES(checksum),
              update_by         = VALUES(update_by),
              update_time       = NOW()
            """,
            IdGeneratorUtil.nextLongId(),
            version.capabilityCode(),
            version.workflowCode(),
            version.modelCode(),
            version.version(),
            version.apiJsonFile(),
            mappingJson,
            outputRuleJson,
            billingJson,
            perfJson,
            checksum,
            version.status(),
            1L,   // create_by：系统同步
            1L);  // update_by
        // MySQL 的 ON DUPLICATE KEY UPDATE：1=插入，2=更新，0=字段值未变化
        return affected == 1;
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM video_workflow_version", Long.class);
        return n == null ? 0L : n;
    }
}
