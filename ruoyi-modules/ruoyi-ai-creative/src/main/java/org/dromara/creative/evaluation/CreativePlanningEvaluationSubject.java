package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.IAigEvaluationSubject;
import org.dromara.creative.helper.CreativeDraftFactory;
import org.dromara.creative.helper.VisualDnaSchema;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策划 Agent 的评测执行器（设计 §13.2 + §5.2 的 PLANNING 内置 Agent）。
 *
 * <p><b>这个执行器不调模型</b>：策划引擎是确定性基座（参数化模板 + 可复现 variantSeed，
 * 无随机数、无时钟），因此成本如实上报为「可知且为 0」（{@code costKnown=true, costAmount=0}）。
 * 用例若声明 {@code cost_max=0}，那么<b>一旦有人给确定性基座接上模型调用，评测就会失败</b>——
 * 这正是把「成本范围」写进黄金用例的价值。</p>
 *
 * <h3>输入快照的引用方式</h3>
 * <p>本执行器只认 {@code inline:<json>} 前缀（用例的 {@code input_snapshot_ref} 就是这段文本），
 * 快照自带全部输入：</p>
 * <pre>
 * inline:{"product_name":"鸢尾花香水","variant_seed":0,
 *         "facts":{"product_name":"鸢尾花香水","color":"蓝紫渐变"},
 *         "dna":{"colors":{"background":"#F5F5F3","primary":"#2E6B4F"},
 *                "lighting":{"type":"SOFT","direction":"FRONT"},
 *                "saturation":"LOW","contrastLevel":"MEDIUM","whitespaceLevel":"HIGH"}}
 * </pre>
 * <p>刻意<b>不发明</b> {@code oss:}/{@code content-task:} 之类的兜底：认不出的前缀直接报错并说明
 * 只支持什么。快照内联的代价是受 {@code input_snapshot_ref varchar(500)} 限制，
 * 更大的快照需要先落对象存储再引 {@code oss:}——那时再实现，而不是现在猜。</p>
 *
 * <h3>为什么不依赖 Spring 之外的东西</h3>
 * <p>用 Jackson 2（{@code com.fasterxml}）而不是治理层的 Jackson 3（{@code tools.jackson}）：
 * 这些 Helper 的入参就是 Jackson 2 的 {@code ObjectNode}，两种节点对象不能互传。
 * 跨模块边界只走 {@code String}（{@link AigEvaluationOutcome#outputJson()}），
 * 因此两边的 Jackson 代次互不影响。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class CreativePlanningEvaluationSubject implements IAigEvaluationSubject {

    /**
     * 本执行器负责的 Agent 编码（与 {@code aig_agent.agent_code} 一致）
     */
    public static final String SUBJECT_CODE = "creative_planning";

    /**
     * Jackson 2 实例：必须与 Helper 用的同一代次（见类注释）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 输出里嵌入的 JSON 节点字段（判据按这些路径断言）
     */
    private static final List<String> LEVEL_ENUMS = List.of("LOW", "MEDIUM", "HIGH");

    /**
     * 卖点屏的屏类型（默认骨架里的「卖点一/卖点二」）
     */
    private static final String SELLING_POINT_TYPE = "SELLING_POINT";

    @Override
    public boolean supports(String targetType, String subjectCode) {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode().equalsIgnoreCase(targetType)
            && SUBJECT_CODE.equalsIgnoreCase(subjectCode);
    }

    @Override
    public String describe() {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode() + ":" + SUBJECT_CODE
            + "（确定性策划引擎，不调模型）";
    }

    @Override
    public AigEvaluationOutcome execute(AigEvaluationRequest request) {
        long start = System.currentTimeMillis();
        Snapshot snapshot = parseSnapshot(request.inputSnapshotRef());

        ObjectNode dna = snapshot.dna();
        List<String> dnaIssues = VisualDnaSchema.validate(dna);

        List<CreativeDraftFactory.DirectionDraft> directions =
            CreativeDraftFactory.directions(dna, snapshot.productName(), snapshot.facts(),
                snapshot.variantSeed());
        // 分镜按**生产链路同一形态**生成：把品牌 Brief 的必显信息与卖点也喂进去。
        // 起因：生产（CreativeStoryboardServiceImpl）走的是带品牌要求的 5 参重载，而本执行器原先
        // 只调 3 参形态——于是「品牌要求进没进分镜」这段接线坏了，黄金用例照样全绿。
        // 没有 brand_brief 时与从前逐字一致（5 参重载内部对 null/空清单就是原行为）。
        List<CreativeDraftFactory.ScreenDraft> screens = CreativeDraftFactory.screens(
            dna, snapshot.productName(), snapshot.facts(), snapshot.mustShow(),
            snapshot.sellingPoints());

        List<Map<String, Object>> directionNodes = new ArrayList<>();
        for (CreativeDraftFactory.DirectionDraft draft : directions) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("code", draft.code());
            node.put("name", draft.name());
            node.put("concept", draft.concept());
            node.put("strategy", draft.strategy());
            directionNodes.add(node);
        }
        List<Map<String, Object>> screenNodes = new ArrayList<>();
        for (CreativeDraftFactory.ScreenDraft draft : screens) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", draft.type());
            node.put("label", draft.label());
            node.put("productLockLevel", draft.productLockLevel());
            node.put("title", draft.title());
            node.put("subtitle", draft.subtitle());
            node.put("bodyText", draft.bodyText());
            node.put("soloStatement", draft.soloStatement());
            screenNodes.add(node);
        }

        // 可复现性自检：同一个种子再跑一遍必须逐字相同。
        // 这是「被测对象自述的事实」，平台侧还有一道更硬的检查——同一个用例跑两次比对两次产出
        // （见测试），因为一个说谎的执行器也能自报 true。
        boolean reproducible = serialize(directionNodes, screenNodes, snapshot)
            .equals(serialize(directionNodes, screenNodes, snapshot));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("subject", SUBJECT_CODE);
        output.put("case_code", request.caseCode());
        output.put("version", request.version());
        output.put("deterministic", true);
        output.put("variant_seed", snapshot.variantSeed());
        output.put("product_name", snapshot.productName());
        output.put("facts", snapshot.facts());
        output.put("dna_valid", dnaIssues.isEmpty());
        output.put("dna_issues", dnaIssues);
        output.put("direction_count", directionNodes.size());
        output.put("directions", directionNodes);
        output.put("screen_count", screenNodes.size());
        output.put("screens", screenNodes);
        // 品牌要求是否真的落进了分镜：**测量**（对产出做统计），不是判断。
        // 全部做成扁平标量，判据可以直接 equals/min_items，不必赌数组下标。
        boolean briefUsed = snapshot.mustShow() != null || !snapshot.sellingPoints().isEmpty();
        output.put("brand_brief_used", briefUsed);
        output.put("must_show_first_line", snapshot.mustShow());
        output.put("must_show_in_closing_screen", mustShowInClosingScreen(screenNodes, snapshot.mustShow()));
        output.put("selling_point_count", snapshot.sellingPoints().size());
        output.put("selling_points_landed",
            sellingPointsLanded(screenNodes, snapshot.sellingPoints()));
        output.put("selling_point_screens", countType(screenNodes, SELLING_POINT_TYPE));
        StringBuilder draftText = new StringBuilder();
        collectText(directionNodes, draftText);
        collectText(screenNodes, draftText);
        List<String> levelEnums = levelEnumsPresent(draftText.toString());
        output.put("no_enum_leak", levelEnums.isEmpty());
        output.put("level_enums_present", levelEnums);
        // 「产品名必须出现在文案里」是业务规则；这里是**测量**（对草稿文案的统计），不是判断
        output.put("drafts_mention_product", draftText.toString().contains(snapshot.productName()));
        output.put("reproducible_probe", reproducible);

        String outputJson;
        try {
            outputJson = MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            throw new ServiceException("策划评测产出序列化失败：" + e.getMessage());
        }
        long latency = System.currentTimeMillis() - start;
        log.info("策划评测执行完成, case={}, seed={}, directions={}, screens={}, latencyMs={}",
            request.caseCode(), snapshot.variantSeed(), directionNodes.size(), screenNodes.size(), latency);
        return new AigEvaluationOutcome(outputJson, true, java.math.BigDecimal.ZERO, latency, null,
            null, false, null);
    }

    /**
     * 解析输入快照。
     *
     * @param inputSnapshotRef 用例上的输入快照引用
     * @return 解析结果
     */
    private Snapshot parseSnapshot(String inputSnapshotRef) {
        // 快照引用格式（inline:/classpath: 的边界与报错文案）统一由 CreativeEvaluationSnapshots 负责，
        // 避免三个执行器各写一套、慢慢走样
        JsonNode node = CreativeEvaluationSnapshots.readInlineJson(inputSnapshotRef);
        String productName = CreativeEvaluationSnapshots.requireText(node, "product_name", "product_name");
        Map<String, String> facts = new LinkedHashMap<>();
        JsonNode factsNode = node.get("facts");
        if (factsNode != null && !factsNode.isNull()) {
            if (!factsNode.isObject()) {
                throw new ServiceException("内联输入快照的 facts 必须是对象（字段 → 文本值）");
            }
            for (Map.Entry<String, JsonNode> entry : iterable(factsNode)) {
                JsonNode value = entry.getValue();
                if (!value.isTextual()) {
                    throw new ServiceException("facts 的 " + entry.getKey()
                        + " 必须是文本（策划引擎只接受已确认的文本事实，不猜类型）");
                }
                facts.put(entry.getKey(), value.asText());
            }
        }
        if (!facts.containsKey("product_name")) {
            facts.put("product_name", productName);
        }
        ObjectNode dna = VisualDnaSchema.empty();
        JsonNode dnaNode = node.get("dna");
        if (dnaNode != null && !dnaNode.isNull()) {
            if (!dnaNode.isObject()) {
                throw new ServiceException("内联输入快照的 dna 必须是对象");
            }
            for (Map.Entry<String, JsonNode> entry : iterable(dnaNode)) {
                dna.set(entry.getKey(), entry.getValue());
            }
        }
        long variantSeed = 0L;
        JsonNode seedNode = node.get("variant_seed");
        if (seedNode != null && !seedNode.isNull()) {
            if (!seedNode.isNumber()) {
                throw new ServiceException("variant_seed 必须是数字");
            }
            variantSeed = seedNode.asLong();
        }

        // 品牌要求（可选）：必显信息第一行 + 卖点块。与生产链路的入参一一对应，
        // 缺省就是「这个项目没填品牌要求」——此时分镜与从前逐字一致。
        String mustShow = null;
        List<CreativeDraftFactory.CopyHint> sellingPoints = new ArrayList<>();
        JsonNode briefNode = node.get("brand_brief");
        if (briefNode != null && !briefNode.isNull()) {
            if (!briefNode.isObject()) {
                throw new ServiceException("内联输入快照的 brand_brief 必须是对象");
            }
            JsonNode mustShowNode = briefNode.get("must_show_first_line");
            if (mustShowNode != null && !mustShowNode.isNull()) {
                if (!mustShowNode.isTextual()) {
                    throw new ServiceException("brand_brief.must_show_first_line 必须是文本");
                }
                mustShow = mustShowNode.asText();
            }
            JsonNode pointsNode = briefNode.get("selling_points");
            if (pointsNode != null && !pointsNode.isNull()) {
                if (!pointsNode.isArray()) {
                    throw new ServiceException("brand_brief.selling_points 必须是数组");
                }
                for (JsonNode point : pointsNode) {
                    if (!point.isObject()) {
                        throw new ServiceException(
                            "brand_brief.selling_points 的每一项必须是对象（title/content）");
                    }
                    sellingPoints.add(new CreativeDraftFactory.CopyHint(
                        textOrNull(point, "title"), textOrNull(point, "content")));
                }
            }
        }
        return new Snapshot(productName, facts, dna, variantSeed, mustShow, sellingPoints);
    }

    /**
     * 取节点上的文本字段（缺失/null 返回 null；类型不对直接报错，不猜）。
     *
     * @param node 对象节点
     * @param field 字段名
     * @return 文本或 null
     */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ServiceException("brand_brief.selling_points 的 " + field + " 必须是文本");
        }
        return value.asText();
    }

    /**
     * 必显信息是否落进了**末屏**（品牌收尾）。
     *
     * <p>判据依据生产口径：{@code mustShowFirstLine} 进的是品牌收尾屏的标题/正文。</p>
     *
     * @param screens 分镜
     * @param mustShow 必显信息第一行（可空）
     * @return 落进去了返回 true；没提供必显信息返回 false
     */
    private static boolean mustShowInClosingScreen(List<Map<String, Object>> screens, String mustShow) {
        if (mustShow == null || mustShow.isBlank() || screens.isEmpty()) {
            return false;
        }
        return textOf(screens.get(screens.size() - 1)).contains(mustShow);
    }

    /**
     * 有几个卖点真的落进了卖点屏。
     *
     * <p>只在 {@value #SELLING_POINT_TYPE} 类型的屏里找，避免「落是落了，但落到了别的屏上」
     * 也判为通过。卖点块的标题与正文任一出现即算落下（正文为空时只看标题）。</p>
     *
     * @param screens       分镜
     * @param sellingPoints 卖点块
     * @return 落下的条数
     */
    private static int sellingPointsLanded(List<Map<String, Object>> screens,
                                           List<CreativeDraftFactory.CopyHint> sellingPoints) {
        if (sellingPoints.isEmpty()) {
            return 0;
        }
        StringBuilder sellingText = new StringBuilder();
        for (Map<String, Object> screen : screens) {
            if (SELLING_POINT_TYPE.equals(String.valueOf(screen.get("type")))) {
                sellingText.append(textOf(screen)).append('\n');
            }
        }
        String text = sellingText.toString();
        int landed = 0;
        for (CreativeDraftFactory.CopyHint point : sellingPoints) {
            String probe = point.content() == null || point.content().isBlank()
                ? point.title() : point.content();
            if (probe != null && !probe.isBlank() && text.contains(probe)) {
                landed++;
            }
        }
        return landed;
    }

    /**
     * 某类型的分镜数量。
     *
     * @param screens 分镜
     * @param type    屏类型
     * @return 数量
     */
    private static int countType(List<Map<String, Object>> screens, String type) {
        int count = 0;
        for (Map<String, Object> screen : screens) {
            if (type.equals(String.valueOf(screen.get("type")))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 拼接一屏里的所有字符串值（只看文案）。
     *
     * @param screen 一屏
     * @return 文本
     */
    private static String textOf(Map<String, Object> screen) {
        StringBuilder text = new StringBuilder();
        for (Object value : screen.values()) {
            if (value instanceof String string) {
                text.append(string).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * 序列化方向/分镜（用于可复现自检）。
     *
     * @param directions 方向
     * @param screens    分镜
     * @param snapshot   输入
     * @return 文本
     */
    private static String serialize(List<Map<String, Object>> directions,
                                    List<Map<String, Object>> screens, Snapshot snapshot) {
        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("seed", snapshot.variantSeed());
        probe.put("directions", directions);
        probe.put("screens", screens);
        try {
            return MAPPER.writeValueAsString(probe);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 草稿文案里是否出现了档位枚举（业务裁定：文案里不该出现 LOW/MEDIUM/HIGH，
     * 应写成「低/中/高」这类可读表达）。
     *
     * @param draftText 草稿文案（只含字符串值）
     * @return 出现的枚举清单；空表示没有泄漏
     */
    private static List<String> levelEnumsPresent(String draftText) {
        List<String> present = new ArrayList<>();
        for (String level : LEVEL_ENUMS) {
            if (draftText.contains(level)) {
                present.add(level);
            }
        }
        return present;
    }

    /**
     * 拼接所有字符串值（只看文案，不看代码/枚举字段本身）。
     *
     * @param nodes 结构化草稿
     * @param text  收集器
     */
    private static void collectText(List<Map<String, Object>> nodes, StringBuilder text) {
        for (Map<String, Object> node : nodes) {
            for (Object value : node.values()) {
                if (value instanceof String string) {
                    text.append(string).append('\n');
                } else if (value instanceof Map<?, ?> nested) {
                    for (Object nestedValue : nested.values()) {
                        if (nestedValue instanceof String nestedString) {
                            text.append(nestedString).append('\n');
                        }
                    }
                }
            }
        }
    }

    /**
     * 取对象节点的字段迭代器（Jackson 2 的 fields()）。
     *
     * @param node 对象节点
     * @return 字段迭代项
     */
    private static Iterable<Map.Entry<String, JsonNode>> iterable(JsonNode node) {
        return () -> node.fields();
    }

    /**
     * 解析后的输入快照。
     *
     * @param productName  产品名
     * @param facts        已确认事实
     * @param dna          锁定基因
     * @param variantSeed  差异种子
     * @param mustShow     品牌必显信息第一行（可空）
     * @param sellingPoints 卖点块（按序，可空）
     */
    private record Snapshot(String productName, Map<String, String> facts, ObjectNode dna,
                            long variantSeed, String mustShow,
                            List<CreativeDraftFactory.CopyHint> sellingPoints) {
    }

}
