package org.dromara.aigov.task.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.execution.AigFlowNodeTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件判断的结论来源（设计 §6.5：「流程先提供…条件判断…」）。
 *
 * <h3>它补的是哪条断链</h3>
 * <p>{@link AigFlowRuntime} 遇到 {@code CONDITION} 节点时必须有结论才能继续；没有结论就
 * {@code WAIT_CONDITION}（刻意不默认真或假）。在补上本类之前，调用方永远传空表，
 * 于是<b>任何声明了条件判断的流程都会停在"待人工处理"</b>——判据本身是对的，
 * 但没有"结论从哪来"这一环，等于条件判断不可用。</p>
 *
 * <h3>首期口径：结论取自<b>冻结输入快照</b>的同名布尔字段</h3>
 * <p>节点 {@code ref} 就是快照 JSON 顶层的字段名，值必须是真的布尔
 * （{@code true}/{@code false}）。选择"读快照而不是造一套表达式语言"的理由：</p>
 * <ul>
 *   <li><b>可归因</b>：快照是冻结的、带哈希的，所以"当时为什么走了这条路"能被复现——
 *       表达式引擎读的是"当前状态"，事后无法重放；</li>
 *   <li><b>不引入第二套语法</b>：设计明确不叠加另一套 DAG/DSL，条件先做成"外部算好、快照里给出结论"
 *       的形式，业务域的复杂判断留在业务域；</li>
 *   <li><b>fail-closed</b>：字段缺失、不是布尔（含字符串 {@code "true"}）、快照不是对象、
 *       JSON 解析失败 —— 一律<b>不给结论</b>（不是给 false），流程停在待人工处理并留痕。
 *       把 {@code "true"} 当 {@code true}、把缺失当 {@code false} 都是在猜，
 *       而猜错的方向恰好是"跳过了一个本该跑的节点"。</li>
 * </ul>
 *
 * <p><b>只读顶层</b>：不做 {@code a.b.c} 路径（那已经是表达式语言的雏形，属独立变更）。</p>
 *
 * @author ai-gov
 */
@Slf4j
public final class AigFlowConditions {

    private AigFlowConditions() {
    }

    /**
     * 从冻结输入快照里取出各条件节点的结论。
     *
     * @param snapshotJson 冻结输入快照 JSON（可空/可非对象）
     * @param nodes        执行计划里的流程节点（可空）
     * @param jsonMapper   JSON 解析器
     * @return 结论表：节点 ref → 布尔；取不到结论的节点<b>不出现</b>在表里
     */
    public static Map<String, Boolean> fromSnapshotJson(String snapshotJson, List<AigFlowNode> nodes,
                                                        JsonMapper jsonMapper) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        if (nodes == null || nodes.isEmpty() || jsonMapper == null) {
            return results;
        }
        List<AigFlowNode> conditions = new ArrayList<>();
        for (AigFlowNode node : nodes) {
            if (node != null && AigFlowNodeTypeEnum.CONDITION == AigFlowNodeTypeEnum.find(node.type())) {
                conditions.add(node);
            }
        }
        if (conditions.isEmpty()) {
            // 没有条件节点就不去解析快照：解析失败会刷一条告警，而这次执行根本用不到它
            return results;
        }
        if (StringUtils.isBlank(snapshotJson)) {
            log.warn("流程有条件判断节点，但任务没有输入快照，条件结论一律取不到（流程会停在待人工处理）");
            return results;
        }
        Object parsed;
        try {
            parsed = jsonMapper.readValue(snapshotJson, Map.class);
        } catch (Exception e) {
            log.warn("输入快照不是可解析的 JSON，条件结论一律取不到（流程会停在待人工处理）：{}",
                StringUtils.substring(e.getMessage(), 0, 200));
            return results;
        }
        if (!(parsed instanceof Map<?, ?> snapshot)) {
            log.warn("输入快照不是 JSON 对象，条件结论一律取不到（流程会停在待人工处理）");
            return results;
        }
        for (AigFlowNode node : conditions) {
            String key = node.ref();
            if (StringUtils.isBlank(key)) {
                // 条件节点没有引用键：无法判断它看的是哪个字段。不猜，等人工处理
                log.warn("条件判断节点没有声明引用键（ref），该节点无法取到结论");
                continue;
            }
            Object value = snapshot.get(key.trim());
            if (value instanceof Boolean flag) {
                results.put(key.trim(), flag);
            } else {
                log.warn("条件判断的字段不是布尔值，按「没有结论」处理（不猜真假）：key={}, 实际类型={}",
                    key.trim(), value == null ? "缺失" : value.getClass().getSimpleName());
            }
        }
        return results;
    }

}
