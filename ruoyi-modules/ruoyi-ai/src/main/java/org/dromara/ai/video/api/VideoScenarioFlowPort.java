package org.dromara.ai.video.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.cloud.VideoCloudTenantResolver;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.VideoTaskSubmissionService;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 视频域的"场景任务"执行端口（增量 17）。
 *
 * <h3>它做什么</h3>
 * <p>把一个来自岗位场景派发的平台任务，转成一条视频任务 {@code video_task}。建任务<b>不复制规则</b>：
 * 走页面提交同一段编排 {@link VideoTaskSubmissionService#submit}（能力/工作流/字段白名单/固定档位/
 * 素材归属全部由它校验）。这里只负责"平台任务 → 视频提交载荷"的翻译与归属确定。</p>
 *
 * <h3>快照约定（"场景 → 视频"的契约）</h3>
 * <p>平台任务的输入快照是一个 JSON 对象，<b>与视频页提交载荷同构</b>，可以是两种形态之一
 * （由 {@code VideoTaskSubmissionService#submit} 里的能力归一化统一收口，本端口只负责转发）：</p>
 * <ul>
 *     <li><b>用途形态（新）</b>：{@code abilityCode}（必填，如 {@code PRODUCT_MOTION}）、
 *         {@code workflowCode}、{@code inputs}（用途字段对象）、{@code assets}（素材ID对象，
 *         键为用途声明的素材键）、{@code output}（{@code size/strength/tier/dur} 里契约允许的那些）。
 *         归一化会按契约拼出提示词并落到标准字段上；<b>不要</b>在这种快照里塞 {@code taskName}——
 *         归一化会拒绝未知键，任务名由用途名派生。</li>
 *     <li><b>经典形态</b>：{@code capabilityCode}（{@code T2V/I2V/FL2V/R2V}）、{@code workflowCode}、
 *         {@code fields}（{@code desc/tier/dur/img/first/last/reference1/reference2} 中该能力允许的那些）、
 *         {@code taskName}（可空；缺省由本端口给一个可读兜底名）。</li>
 * </ul>
 * <p>读不懂 / 缺必填 / 契约不符 → <b>业务拒绝</b>（如实回原因），绝不建一条空视频任务。</p>
 *
 * <h3>幂等</h3>
 * <p>两条一起兜底：①{@code video_task.platform_task_id} 上有唯一索引，先查后插；
 * ②把提交载荷的 {@code idempotencyKey} 覆写为 {@code PLATFORM-<platformTaskId>}，
 * 于是"同一平台任务被人工重新入队、执行器再派发一次"只会得到<b>同一条</b>视频任务。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "video", name = "enabled", havingValue = "true")
public class VideoScenarioFlowPort implements AigScenarioFlowPort {

    /**
     * 本实现负责的适配器（{@code AigScenarioAdapterEnum.VIDEO_EXISTING_FLOW}）
     */
    private static final String ADAPTER = "VIDEO_EXISTING_FLOW";

    /**
     * 平台任务派发用的幂等键前缀
     */
    private static final String IDEMPOTENCY_PREFIX = "PLATFORM-";

    private final VideoTaskSubmissionService submissionService;
    private final VideoTaskRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Override
    public String adapter() {
        return ADAPTER;
    }

    @Override
    public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
        if (request == null || request.getTaskId() == null) {
            return AigScenarioFlowResult.rejected("派发请求缺少平台任务ID");
        }
        // 幂等：同一平台任务只建一条视频任务（人工重新入队会再派发一次）
        Long existing = repository.findByPlatformTaskId(request.getTaskId());
        if (existing != null) {
            log.info("平台任务已派发过，复用视频任务：platformTaskId={}, taskId={}",
                request.getTaskId(), existing);
            return AigScenarioFlowResult.accepted(String.valueOf(existing));
        }
        if (request.getRequesterId() == null) {
            return AigScenarioFlowResult.rejected("派发请求缺少提交人，无法确定视频任务归属");
        }

        Map<String, Object> payload = readSnapshot(request.getSnapshotJson());
        if (payload == null) {
            return AigScenarioFlowResult.rejected(
                "输入快照无法解析为视频任务（需要一个 JSON 对象）：platformTaskId=" + request.getTaskId());
        }
        // 幂等键由平台任务ID派生：与页面提交共享"同一幂等键只有一条任务"的约束
        payload.put("idempotencyKey", IDEMPOTENCY_PREFIX + request.getTaskId());
        // 用途形态（abilityCode）**不加** taskName：归一化会拒绝未知键，且任务名由用途名派生。
        // 只在经典形态（capabilityCode）下补一个可读兜底名。
        if (payload.get("abilityCode") == null
            && payload.get("taskName") == null && payload.get("capabilityCode") != null) {
            payload.put("taskName", payload.get("capabilityCode") + " 场景任务");
        }

        String tenantId;
        try {
            tenantId = VideoCloudTenantResolver.resolve(jdbc, request.getRequesterId());
        } catch (VideoTaskException e) {
            // 归属无法确定属于"这次派发不可受理"，如实回原因，不退化成默认租户
            return AigScenarioFlowResult.rejected(e.getMessage());
        }

        try {
            VideoTaskSubmissionService.SubmissionResult result = submissionService.submit(
                payload, tenantId, request.getRequesterId(), request.getTaskId());
            log.info("场景任务已交给视频链路：platformTaskId={}, videoTaskId={}, adapter={}",
                request.getTaskId(), result.taskId(), ADAPTER);
            return AigScenarioFlowResult.accepted(String.valueOf(result.taskId()));
        } catch (VideoTaskException e) {
            // 视频域自己的业务拒绝（契约不符/字段缺失/档位不符/素材越权…）：如实回给任务层
            return AigScenarioFlowResult.rejected(
                e.getMessage() == null ? "视频域拒绝了这次派发" : e.getMessage());
        }
    }

    /**
     * 读快照 JSON 成一个可变的对象 Map（后续要覆写幂等键）。
     *
     * @param snapshotJson 快照原文（可空）
     * @return 载荷；读不出/不是对象返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        try {
            Object parsed = mapper.readValue(snapshotJson, Map.class);
            return parsed instanceof Map ? new HashMap<>((Map<String, Object>) parsed) : null;
        } catch (Exception e) {
            log.warn("场景快照不是合法 JSON 对象：{}", e.getClass().getSimpleName());
            return null;
        }
    }

}
