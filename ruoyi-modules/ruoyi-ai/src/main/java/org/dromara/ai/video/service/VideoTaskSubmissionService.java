package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频任务提交（把"建任务"的编排从 controller 抽出来；增量 17，行为保持重构）。
 *
 * <p><b>为什么抽</b>：场景派发（{@code VideoScenarioFlowPort}）与用户在视频页点提交必须走
 * <b>同一条建任务规则</b>。规则本体仍由 {@code WorkflowContractRegistry} / {@code H3TemplatePreparer} /
 * {@code VideoTaskRepository.requireOwnedAsset} 拥有，这里只是那一段编排的<b>唯一实现</b>。</p>
 *
 * <p>编排顺序与原来的 controller 完全一致：能力/工作流校验 → 字段白名单 → 固定档位与必需素材校验
 * → 素材归属 → 幂等键预查 → 建任务 → 并发撞唯一键回查。改动只有一处实质差异：
 * 任务行多了 {@code platformTaskId}（场景派发时非空，其余为 null）。</p>
 *
 * <p><b>为什么挂 {@code @ConditionalOnProperty}</b>：本类的依赖（契约注册表、模板填充器、
 * {@code videoObjectMapper}）都来自 {@code video.enabled=true} 才装配的配置类。无条件注册会让
 * <b>默认关闭（不配 video.enabled）的应用起不来</b>——容器找不到这些构造参数即启动失败，
 * 而不是安静地不启用视频功能。该约束由 {@code VideoCreationControllerGatingTest} 守护同一模式。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "video", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class VideoTaskSubmissionService {

    /**
     * 契约内的字段白名单（试图覆写 sampler/nodeId 一律拒绝）
     */
    private static final List<String> ALLOWED_FIELDS = List.of("desc", "tier", "dur", "img", "first", "last");

    /**
     * 任务号日期段
     */
    private static final DateTimeFormatter TASK_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final WorkflowContractRegistry registry;
    private final H3TemplatePreparer preparer;
    private final VideoTaskRepository repository;
    private final ObjectMapper mapper;

    /**
     * 提交结果。
     *
     * @param taskId     视频任务ID
     * @param taskNo     任务号（幂等命中时可能为空：调用方本来就有那条）
     * @param idempotent 是否命中幂等（已存在）
     */
    public record SubmissionResult(long taskId, String taskNo, boolean idempotent) {
    }

    /**
     * 校验并建任务（不执行；执行由调用方决定是否排后台）。
     *
     * @param payload        与视频页提交同构的载荷：capabilityCode/workflowCode/fields/taskName/idempotencyKey
     * @param tenantId       租户
     * @param userId         归属用户（素材归属也按它校验）
     * @param platformTaskId 来源平台任务ID（非场景派发时传 null）
     * @return 提交结果
     */
    public SubmissionResult submit(Map<String, Object> payload, String tenantId, long userId,
                                   Long platformTaskId) {
        String capabilityCode = stringOf(payload.get("capabilityCode"));
        String workflowCode = stringOf(payload.get("workflowCode"));
        VideoCapability capability = VideoCapability.parse(capabilityCode);
        if (capability == null) {
            throw VideoTaskException.invalidContract("不支持的能力编码");
        }
        WorkflowVersion version = registry.require(workflowCode, false);
        if (!capability.name().equalsIgnoreCase(version.capabilityCode())) {
            throw VideoTaskException.invalidContract("能力与工作流不匹配");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = payload.get("fields") instanceof Map
            ? (Map<String, Object>) payload.get("fields") : Map.of();

        preparer.validateFieldWhitelist(ALLOWED_FIELDS, fields);

        String prompt = stringOf(fields.get("desc"));
        String tier = stringOf(fields.get("tier"));
        String durationLabel = stringOf(fields.get("dur"));
        Long imageAssetId = longOf(fields.get("img"));
        Long firstAssetId = longOf(fields.get("first"));
        Long lastAssetId = longOf(fields.get("last"));

        preparer.validateFields(capability, version, new H3TemplatePreparer.H3Fields(
            prompt,
            imageAssetId == null ? null : "provided",
            firstAssetId == null ? null : "provided",
            lastAssetId == null ? null : "provided",
            tier, durationLabel));

        if (imageAssetId != null) {
            repository.requireOwnedAsset(imageAssetId, tenantId, userId);
        }
        if (firstAssetId != null) {
            repository.requireOwnedAsset(firstAssetId, tenantId, userId);
        }
        if (lastAssetId != null) {
            repository.requireOwnedAsset(lastAssetId, tenantId, userId);
        }

        String idempotencyKey = stringOf(payload.get("idempotencyKey"));
        Long existing = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
        if (existing != null) {
            return new SubmissionResult(existing, null, true);
        }

        int durationSeconds = parseDurationSeconds(durationLabel);
        long taskId = IdGeneratorUtil.nextLongId();
        String taskNo = "VIDEO-" + LocalDate.now().format(TASK_NO_DATE)
            + "-" + String.format("%06d", Math.floorMod(taskId, 1_000_000L));
        Map<String, Object> inputJson = new HashMap<>();
        if (imageAssetId != null) {
            inputJson.put("img", imageAssetId);
        }
        if (firstAssetId != null) {
            inputJson.put("first", firstAssetId);
        }
        if (lastAssetId != null) {
            inputJson.put("last", lastAssetId);
        }
        String inputJsonText;
        try {
            inputJsonText = mapper.writeValueAsString(inputJson);
        } catch (Exception e) {
            throw VideoTaskException.invalidContract("输入素材序列化失败");
        }

        try {
            repository.insertTask(new VideoTaskRepository.TaskRow(
                taskId, tenantId, userId, taskNo,
                stringOf(payload.getOrDefault("taskName", capabilityCode + " 任务")),
                capability.name(), version.workflowCode(), version.version(), version.modelCode(),
                VideoTaskStatus.QUEUED.name(), tier, durationSeconds, prompt, inputJsonText,
                idempotencyKey, null, platformTaskId));
        } catch (DuplicateKeyException e) {
            // 幂等键（或平台任务ID）上的唯一索引兜底：并发下可能同时通过上面的预检查。
            // 这是「重复提交」而不是错误——返回已存在的那条，否则用户会看到失败却又确实建了任务。
            Long existingId = repository.findByIdempotencyKey(tenantId, userId, idempotencyKey);
            if (existingId != null) {
                log.info("视频任务并发重复提交，返回已存在任务 {}", existingId);
                return new SubmissionResult(existingId, null, true);
            }
            throw e;
        }
        return new SubmissionResult(taskId, taskNo, false);
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw VideoTaskException.invalidContract("素材 ID 必须是数字");
        }
    }

    private static int parseDurationSeconds(String label) {
        if (label == null) {
            return 5;
        }
        String digits = label.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 5;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

}
