package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code image_workflow_version} 的 JDBC 实现（MySQL 方言）。
 *
 * <p>用一条 {@code INSERT ... ON DUPLICATE KEY UPDATE} 完成幂等写入，依赖唯一索引
 * {@code uk_image_workflow_version(workflow_code, version)}。
 * UPDATE 子句刻意不含 {@code status / published_by / published_time} ——
 * 一次同步不得把审核结果（例如已 PUBLISHED）覆盖回 DRAFT。</p>
 *
 * <p><b>与视频模块实现的一处有意差异</b>：视频侧的 {@code upsert} 也接收
 * {@code supportedOutputsJson}，但**没有把它写进任何列**（表里没有对应列），参数被静默丢弃。
 * 图像侧改为真正落库到 {@code supported_outputs_json}：静默丢参数属于隐患——
 * 调用方以为写进去了，实际上没有。</p>
 *
 * <p><b>SQL 与参数拆开暴露给测试</b>：本类曾经因为「SQL 里 14 个占位符、却传了 15 个参数」
 * 导致同步在运行期抛 {@code Parameter index out of range}，而单测用的是替身仓储、CI 也不连库，
 * 所以一路漏到隔离联调才暴露。因此把 {@link #UPSERT_SQL} 与 {@link #upsertArgs} 拆出来，
 * 由 {@code JdbcImageWorkflowVersionRepositoryTest} 断言「占位符数 == 参数个数」。</p>
 */
@Slf4j
@Repository
public class JdbcImageWorkflowVersionRepository implements ImageWorkflowVersionRepository {

    private static final long SYSTEM_USER = 1L;

    /**
     * 幂等 upsert 语句（包级可见，供占位符/参数一致性测试使用）。
     */
    static final String UPSERT_SQL = """
        INSERT INTO image_workflow_version (id, capability_code, workflow_code, model_code, version,
                                            workflow_path, mapping_json, output_rule_json, billing_json,
                                            perf_json, checksum, status, supported_outputs_json,
                                            create_by, create_time, update_by, update_time)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),?,NOW())
        ON DUPLICATE KEY UPDATE
            capability_code = VALUES(capability_code),
            model_code = VALUES(model_code),
            workflow_path = VALUES(workflow_path),
            mapping_json = VALUES(mapping_json),
            output_rule_json = VALUES(output_rule_json),
            billing_json = VALUES(billing_json),
            perf_json = VALUES(perf_json),
            checksum = VALUES(checksum),
            supported_outputs_json = VALUES(supported_outputs_json),
            update_by = VALUES(update_by),
            update_time = NOW()
        """;

    /**
     * 审核状态查询语句（包级可见，便于测试断言列与语义一致）。
     */
    static final String REVIEW_STATE_SQL = """
        SELECT workflow_code, version, checksum, status
          FROM image_workflow_version
        """;

    private final JdbcTemplate jdbc;

    public JdbcImageWorkflowVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean upsert(ImageWorkflowVersion version, String checksum, boolean templateLoaded, String mappingJson,
                          String outputRuleJson, String billingJson, String perfJson, String supportedOutputsJson) {
        int affected = jdbc.update(UPSERT_SQL, upsertArgs(IdGeneratorUtil.nextLongId(), version, checksum,
            templateLoaded, mappingJson, outputRuleJson, billingJson, perfJson, supportedOutputsJson).toArray());
        // MySQL：1=插入，2=更新
        return affected == 1;
    }

    /**
     * 组装 upsert 的参数。主键由调用方传入，避免离线单测触发
     * {@code IdGeneratorUtil} 的静态初始化（它需要 Spring 容器，纯 JUnit 下会
     * {@code ExceptionInInitializerError} —— 视频模块踩过这个坑）。
     */
    static List<Object> upsertArgs(long id, ImageWorkflowVersion version, String checksum, boolean templateLoaded,
                                   String mappingJson, String outputRuleJson, String billingJson,
                                   String perfJson, String supportedOutputsJson) {
        List<Object> args = new ArrayList<>(15);
        args.add(id);
        args.add(version.capabilityCode());
        args.add(version.workflowCode());
        args.add(version.modelCode());
        args.add(version.version());
        args.add(version.apiJsonFile());
        args.add(mappingJson);
        args.add(outputRuleJson);
        args.add(billingJson);
        args.add(perfJson);
        // 模板未通过校验时写 NULL，不伪装成「已校验」
        args.add(templateLoaded ? checksum : null);
        args.add(version.status());
        args.add(supportedOutputsJson);
        args.add(SYSTEM_USER);
        args.add(SYSTEM_USER);
        return args;
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM image_workflow_version", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<WorkflowReviewState> findReviewStates() {
        return jdbc.query(REVIEW_STATE_SQL, (rs, rowNum) -> new WorkflowReviewState(
            rs.getString("workflow_code"),
            rs.getString("version"),
            rs.getString("checksum"),
            rs.getString("status")));
    }
}
