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
 * <h3>首期口径：结论取自<b>冻结输入快照</b>的布尔字段（支持 {@code a.b.c} 路径）</h3>
 * <p>节点 {@code ref} 就是快照里的字段名：顶层字段，或 {@code a.b.c} 形式的路径
 * （对象逐层、数组用数字下标，如 {@code items.0.ready}）。选择"读快照而不是造一套表达式语言"的理由：</p>
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
 * <p><b>路径规则刻意收得很紧</b>：只做对象逐层 + 数组数字下标；任何一段缺失、中间层不是对象/数组、
 * 出现空段（{@code a..b}）都按"没有结论"处理（不是 false），流程停在待人工处理。
 * 不做表达式、通配、函数——那已经是另一套语言，属独立变更。</p>
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
            String key = node.ref() == null ? null : node.ref().trim();
            if (StringUtils.isBlank(key)) {
                // 条件节点没有引用键：无法判断它看的是哪个字段。不猜，等人工处理
                log.warn("条件判断节点没有声明引用键（ref），该节点无法取到结论");
                continue;
            }
            Object value = readPath(snapshot, key);
            if (value instanceof Boolean flag) {
                results.put(key, flag);
            } else {
                log.warn("条件判断的字段不是布尔值，按「没有结论」处理（不猜真假）：key={}, 实际类型={}",
                    key, value == null ? "缺失" : value.getClass().getSimpleName());
            }
        }
        return results;
    }

    /**
     * 按引用键取快照里的值：支持 {@code a.b.c} 形式的路径（用户 2026-10-14 要求）。
     *
     * <p>规则刻意收得很紧，因为"取不到结论"与"取到 false"在这条链路上是两件完全不同的事：</p>
     * <ul>
     *   <li>不含点 = 顶层字段（与加路径之前完全一致，存量配置不受影响）；</li>
     *   <li>点分段逐层走<b>对象</b>；纯数字段走进<b>数组</b>（{@code items.0.ready}）；</li>
     *   <li>任何一段缺失、中间层不是对象/数组、或出现空段（{@code a..b}）→ <b>返回 null</b>
     *       （=没有结论），绝不"尽力而为"地回落到某一层；</li>
     *   <li>不做表达式、不做通配、不做函数——那已经是另一套语言（见类注释的取舍）。</li>
     * </ul>
     *
     * @param snapshot 快照根（JSON 对象）
     * @param path     引用键（顶层字段或点分路径）
     * @return 取到的值；取不到返回 null
     */
    private static Object readPath(Map<?, ?> snapshot, String path) {
        if (path.indexOf('.') < 0) {
            return snapshot.get(path);
        }
        Object current = snapshot;
        for (String segment : path.split("\\.", -1)) {
            if (segment.isEmpty()) {
                // "a..b" 这类写法：宁可说"没有结论"（排队等人工），也不要猜用户想指哪一层
                log.warn("条件引用键里有空段，按「没有结论」处理：key={}", path);
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof List<?> list) {
                Integer index = arrayIndex(segment, list.size());
                if (index == null) {
                    log.warn("条件引用键要按数组下标取值，但段不是合法下标，按「没有结论」处理："
                        + "key={}, segment={}, size={}", path, segment, list.size());
                    return null;
                }
                current = list.get(index);
            } else {
                // 中途穿过了一个标量（或 null）：路径不成立，不继续猜
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 把一段解析成数组下标。
     *
     * @param segment 段
     * @param size    数组长度
     * @return 合法下标；不是下标或越界返回 null
     */
    private static Integer arrayIndex(String segment, int size) {
        if (!segment.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int index = Integer.parseInt(segment);
            return index >= 0 && index < size ? index : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
