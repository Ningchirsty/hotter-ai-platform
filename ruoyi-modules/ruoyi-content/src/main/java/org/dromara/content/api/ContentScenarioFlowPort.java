package org.dromara.content.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.enums.ContentDeliverableTypeEnum;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内容域的"场景任务"执行端口（增量 15）。
 *
 * <h3>它做什么</h3>
 * <p>把一个来自岗位场景派发的平台任务，转成一条内容任务 {@code cp_task}（走内容域自己的
 * {@code IContentTaskService#create}——不复制内容域的建任务规则）。</p>
 *
 * <h3>快照约定（这就是"场景 → 内容"的契约）</h3>
 * <p>平台任务的输入快照是一个 JSON 对象，识别下列字段：
 * {@code deliverableType}（**必填**，必须是 {@code ContentDeliverableTypeEnum} 的值）、
 * {@code taskName}、{@code productId}、{@code skuCode}、{@code ownerId}、{@code deadline}、
 * {@code dataLevel}。读不懂 / 缺必填 → **业务拒绝**（如实回原因），绝不建一条空内容任务。</p>
 *
 * <h3>幂等</h3>
 * <p>{@code cp_task.platform_task_id} 上有唯一索引：平台任务被人工重新入队、执行器再派发一次时，
 * 先查后插 + 撞唯一键回查，都只会得到**同一条**内容任务。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentScenarioFlowPort implements AigScenarioFlowPort {

    /**
     * 本实现负责的适配器（{@code AigScenarioAdapterEnum.CONTENT_EXISTING_FLOW}）
     */
    private static final String ADAPTER = "CONTENT_EXISTING_FLOW";

    /**
     * 任务名兜底前缀
     */
    private static final String NAME_PREFIX = "内容任务-";

    private final IContentTaskService contentTaskService;
    private final CpTaskMapper taskMapper;
    private final JsonMapper jsonMapper;

    @Override
    public String adapter() {
        return ADAPTER;
    }

    @Override
    public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
        if (request == null || request.getTaskId() == null) {
            return AigScenarioFlowResult.rejected("派发请求缺少平台任务ID");
        }
        // 幂等：同一平台任务只建一条内容任务（人工重新入队会再派发一次）
        CpTask existing = findExisting(request.getTaskId());
        if (existing != null) {
            log.info("平台任务已派发过，复用内容任务：platformTaskId={}, taskId={}",
                request.getTaskId(), existing.getTaskId());
            return AigScenarioFlowResult.accepted(String.valueOf(existing.getTaskId()));
        }

        JsonNode snapshot = readSnapshot(request.getSnapshotJson());
        if (snapshot == null || !snapshot.isObject()) {
            return AigScenarioFlowResult.rejected(
                "输入快照无法解析为内容任务（需要一个 JSON 对象）：platformTaskId=" + request.getTaskId());
        }
        String deliverableType = text(snapshot, "deliverableType");
        if (StringUtils.isBlank(deliverableType)) {
            return AigScenarioFlowResult.rejected("快照缺少 deliverableType（内容交付类型），无法建内容任务");
        }
        if (ContentDeliverableTypeEnum.find(deliverableType.trim()) == null) {
            return AigScenarioFlowResult.rejected("快照里的 deliverableType 非法：" + deliverableType.trim());
        }

        ContentTaskBo bo = new ContentTaskBo();
        bo.setDeliverableType(deliverableType.trim());
        String taskName = text(snapshot, "taskName");
        bo.setTaskName(StringUtils.isBlank(taskName)
            ? StringUtils.substring(NAME_PREFIX + StringUtils.blankToDefault(request.getScenarioCode(), "SCENE")
                + "-" + request.getTaskId(), 0, 255)
            : StringUtils.substring(taskName.trim(), 0, 255));
        bo.setProductId(longOrNull(snapshot, "productId"));
        bo.setSkuCode(text(snapshot, "skuCode"));
        Long ownerId = longOrNull(snapshot, "ownerId");
        bo.setOwnerId(ownerId != null ? ownerId : request.getRequesterId());
        bo.setDataLevel(StringUtils.isNotBlank(request.getDataLevel())
            ? request.getDataLevel() : text(snapshot, "dataLevel"));
        bo.setPlatformTaskId(request.getTaskId());
        bo.setRemark(StringUtils.substring("由岗位场景派发：platformTaskId=" + request.getTaskId()
            + "，scenario=" + request.getScenarioCode() + "@" + request.getScenarioVersion(), 0, 500));

        try {
            Long taskId = contentTaskService.create(bo);
            log.info("场景任务已交给内容链路：platformTaskId={}, contentTaskId={}, adapter={}",
                request.getTaskId(), taskId, ADAPTER);
            return AigScenarioFlowResult.accepted(String.valueOf(taskId));
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已建出同一条：以库里那条为准（唯一键是最终裁判）
            CpTask winner = findExisting(request.getTaskId());
            if (winner == null) {
                throw e;
            }
            return AigScenarioFlowResult.accepted(String.valueOf(winner.getTaskId()));
        } catch (ServiceException e) {
            // 内容域自己的业务拒绝（产品不存在、交付类型非法…）：如实回给任务层，不当系统故障
            return AigScenarioFlowResult.rejected(StringUtils.blankToDefault(e.getMessage(), "内容域拒绝了这次派发"));
        }
    }

    /**
     * 按平台任务ID查已有的内容任务（幂等判断）。
     *
     * @param platformTaskId 平台任务ID
     * @return 内容任务；不存在返回 null
     */
    private CpTask findExisting(Long platformTaskId) {
        return taskMapper.selectOne(Wrappers.<CpTask>lambdaQuery()
            .eq(CpTask::getPlatformTaskId, platformTaskId)
            .last("limit 1"));
    }

    /**
     * 读快照 JSON。
     *
     * @param snapshotJson 快照原文（可空）
     * @return 根节点；读不出返回 null
     */
    private JsonNode readSnapshot(String snapshotJson) {
        if (StringUtils.isBlank(snapshotJson)) {
            return null;
        }
        try {
            return jsonMapper.readTree(snapshotJson);
        } catch (Exception e) {
            log.warn("场景快照不是合法 JSON：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 取文本字段。
     *
     * @param node  JSON 对象
     * @param field 字段名
     * @return 文本；缺失/非文本返回 null
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    /**
     * 取长整型字段（数字或数字字符串）。
     *
     * @param node  JSON 对象
     * @param field 字段名
     * @return 值；缺失/解析不出返回 null
     */
    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.valueOf(value.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
