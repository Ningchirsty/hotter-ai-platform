package org.dromara.creative.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpTask;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创作域的"场景任务"执行端口（增量 18）。
 *
 * <h3>它做什么</h3>
 * <p>把一个来自岗位场景派发的平台任务，转成一条**创意项目**（创作项目本体就是内容域的
 * {@code cp_task}，见 {@code CreativeProjectServiceImpl#createProject}）。建项目的规则
 * <b>不复制</b>：走创作域自己的 {@link ICreativeProjectService#createProject}——交付类型是否
 * 已登记/已发布场景档案、SKU 回落产品、写主数据事实都由它校验。</p>
 *
 * <h3>与候选台账（{@code CreativeTaskLedger}）的关系</h3>
 * <p>用户 2026-10-11 拍板：<b>平台任务当"项目级父任务"</b>，创作项目本体走 {@code cp_task}。
 * 场景派发时只建项目、<b>不</b>建候选，因此派发这一步不会产生第二条 {@code aig_task}；
 * 候选是后续设计部在项目里出图时由 {@code CreativeTaskLedger} 各登记一条
 * （{@code project_type=CREATIVE}、{@code project_id=项目ID}、{@code execution_mode=EXTERNAL}），
 * 与平台任务是"同一项目下的父子多任务"关系，靠 {@code cp_task.platform_task_id} 上的
 * {@code projectId} 追溯。</p>
 *
 * <h3>快照约定（"场景 → 创作"的契约）</h3>
 * <p>平台任务的输入快照是一个 JSON 对象，识别下列字段：
 * {@code taskName}、{@code deliverableType}（缺省 {@code ECOM_DETAIL}，必须是已登记且已发布
 * 场景档案的交付类型）、{@code productId}、{@code skuCode}、{@code ownerId}（缺省=提交人）、
 * {@code deadline}（ISO-8601 字符串，缺省不设）。读不懂 → <b>业务拒绝</b>，
 * 绝不建一条空项目。</p>
 *
 * <h3>完成口径（用户 2026-10-11 拍板）</h3>
 * <p><b>交接完成即完成</b>：项目建好就把平台任务收尾为 SUCCEEDED——"交给既有链路"这件事到此
 * 完成；项目里的出图/评审/交付是后续人工长跑，不在平台任务里等。实现上返回
 * {@link AigScenarioFlowResult#acceptedWithHandoffComplete}：由任务层在记录派发事实后立刻收尾，
 * 域在这里<b>不</b>直接调回执服务（那会撞任务层的乐观锁版本）。</p>
 *
 * <h3>幂等</h3>
 * <p>创作项目本体是 {@code cp_task}，所以直接复用内容域那条
 * {@code cp_task.platform_task_id} 唯一索引：先查后插 + 撞唯一键回查，
 * 同一平台任务被人工重新入队、执行器再派发一次时只会得到<b>同一条</b>创意项目。</p>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeScenarioFlowPort implements AigScenarioFlowPort {

    /**
     * 本实现负责的适配器（{@code AigScenarioAdapterEnum.CREATIVE_EXISTING_FLOW}）
     */
    private static final String ADAPTER = "CREATIVE_EXISTING_FLOW";

    /**
     * 交付类型缺省值（与创作域老行为一致：视觉工厂默认详情页）
     */
    private static final String DEFAULT_DELIVERABLE = "ECOM_DETAIL";

    /**
     * 项目名兜底前缀
     */
    private static final String NAME_PREFIX = "创意项目-";

    /**
     * 本模块的 JSON 处理统一走 Jackson 2（与模块内其他 helper 同口径）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ICreativeProjectService projectService;
    private final CpTaskMapper taskMapper;

    @Override
    public String adapter() {
        return ADAPTER;
    }

    @Override
    public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
        if (request == null || request.getTaskId() == null) {
            return AigScenarioFlowResult.rejected("派发请求缺少平台任务ID");
        }
        // 幂等：同一平台任务只建一条创意项目（项目本体是 cp_task，复用其 platform_task_id 唯一索引）
        CpTask existing = findExisting(request.getTaskId());
        if (existing != null) {
            log.info("平台任务已派发过，复用创意项目：platformTaskId={}, projectId={}",
                request.getTaskId(), existing.getTaskId());
            return AigScenarioFlowResult.acceptedWithHandoffComplete(String.valueOf(existing.getTaskId()));
        }

        Map<String, Object> snapshot = readSnapshot(request.getSnapshotJson());
        if (snapshot == null) {
            return AigScenarioFlowResult.rejected(
                "输入快照无法解析为创意项目（需要一个 JSON 对象）：platformTaskId=" + request.getTaskId());
        }

        CreativeProjectBo bo = new CreativeProjectBo();
        String taskName = text(snapshot, "taskName");
        bo.setTaskName(StringUtils.isBlank(taskName)
            ? NAME_PREFIX + StringUtils.blankToDefault(request.getScenarioCode(), "SCENE")
                + "-" + request.getTaskId()
            : taskName.trim());
        bo.setDeliverableType(StringUtils.blankToDefault(
            StringUtils.trimToNull(text(snapshot, "deliverableType")), DEFAULT_DELIVERABLE));
        bo.setProductId(longOrNull(snapshot, "productId"));
        bo.setSkuCode(text(snapshot, "skuCode"));
        Long ownerId = longOrNull(snapshot, "ownerId");
        bo.setOwnerId(ownerId != null ? ownerId : request.getRequesterId());
        bo.setDeadline(parseDeadline(text(snapshot, "deadline")));
        bo.setPlatformTaskId(request.getTaskId());
        bo.setRemark(StringUtils.substring("由岗位场景派发：platformTaskId=" + request.getTaskId()
            + "，scenario=" + request.getScenarioCode() + "@" + request.getScenarioVersion(), 0, 500));

        try {
            Long projectId = projectService.createProject(bo);
            log.info("场景任务已交给创作链路：platformTaskId={}, projectId={}, adapter={}",
                request.getTaskId(), projectId, ADAPTER);
            // 交接完成即完成：项目建好即视为交给设计部，平台任务可立即收尾
            return AigScenarioFlowResult.acceptedWithHandoffComplete(String.valueOf(projectId));
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已建出同一条：以库里那条为准（唯一键是最终裁判）
            CpTask winner = findExisting(request.getTaskId());
            if (winner == null) {
                throw e;
            }
            return AigScenarioFlowResult.acceptedWithHandoffComplete(String.valueOf(winner.getTaskId()));
        } catch (ServiceException e) {
            // 创作域自己的业务拒绝（交付类型未登记/无场景档案、产品不存在…）：如实回给任务层
            return AigScenarioFlowResult.rejected(
                StringUtils.blankToDefault(e.getMessage(), "创作域拒绝了这次派发"));
        }
    }

    /**
     * 按平台任务ID查已有的创意项目（幂等判断）。
     *
     * @param platformTaskId 平台任务ID
     * @return 项目（cp_task）；不存在返回 null
     */
    private CpTask findExisting(Long platformTaskId) {
        return taskMapper.selectOne(Wrappers.<CpTask>lambdaQuery()
            .eq(CpTask::getPlatformTaskId, platformTaskId)
            .last("limit 1"));
    }

    /**
     * 读快照 JSON 成对象。
     *
     * @param snapshotJson 快照原文（可空）
     * @return 对象；读不出/不是对象返回 null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readSnapshot(String snapshotJson) {
        if (StringUtils.isBlank(snapshotJson)) {
            return null;
        }
        try {
            Object parsed = MAPPER.readValue(snapshotJson, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            log.warn("场景快照不是合法 JSON 对象：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 取文本字段。
     *
     * @param snapshot JSON 对象
     * @param field    字段名
     * @return 文本；缺失/非文本返回 null
     */
    private static String text(Map<String, Object> snapshot, String field) {
        Object value = snapshot.get(field);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 取长整型字段（数字或数字字符串）。
     *
     * @param snapshot JSON 对象
     * @param field    字段名
     * @return 值；缺失/解析不出返回 null
     */
    private static Long longOrNull(Map<String, Object> snapshot, String field) {
        Object value = snapshot.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 ISO-8601 截止时间；缺失或格式不对时按"不设截止时间"处理（不是拒绝派发的理由）。
     *
     * @param text 快照里的 deadline 文本
     * @return 截止时间；无/不可解析返回 null
     */
    private static LocalDateTime parseDeadline(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (Exception e) {
            log.warn("快照里的 deadline 不是 ISO-8601 时间，按不设截止时间处理：{}", text);
            return null;
        }
    }

}
