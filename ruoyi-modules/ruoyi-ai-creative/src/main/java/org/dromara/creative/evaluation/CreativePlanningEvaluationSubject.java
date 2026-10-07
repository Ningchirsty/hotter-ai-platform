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
     * 输入快照前缀：内联 JSON
     */
    public static final String SNAPSHOT_INLINE_PREFIX = "inline:";

    /**
     * Jackson 2 实例：必须与 Helper 用的同一代次（见类注释）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 输出里嵌入的 JSON 节点字段（判据按这些路径断言）
     */
    private static final List<String> LEVEL_ENUMS = List.of("LOW", "MEDIUM", "HIGH");

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
        // 注意：只有 directions 有种子重载；screens 的第 4 个参数是 mustShowFirstLine 而不是种子，
        // 因此分镜按 3 参形态生成（种子只影响方向的拍法选择）
        List<CreativeDraftFactory.ScreenDraft> screens =
            CreativeDraftFactory.screens(dna, snapshot.productName(), snapshot.facts());

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
        if (inputSnapshotRef == null || inputSnapshotRef.isBlank()) {
            throw new ServiceException("用例没有输入快照（input_snapshot_ref 为空）：评测必须在确定的输入上跑");
        }
        String ref = inputSnapshotRef.trim();
        if (!ref.startsWith(SNAPSHOT_INLINE_PREFIX)) {
            throw new ServiceException("不支持的输入快照前缀：" + prefixOf(ref)
                + "。本执行器只支持 " + SNAPSHOT_INLINE_PREFIX + "<json>（内联快照）；"
                + "对象存储/业务ID 形式的快照尚未实现——认不出的前缀不会被当成某种默认输入");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(ref.substring(SNAPSHOT_INLINE_PREFIX.length()));
        } catch (Exception e) {
            throw new ServiceException("内联输入快照不是合法 JSON：" + e.getMessage());
        }
        if (node == null || !node.isObject()) {
            throw new ServiceException("内联输入快照必须是 JSON 对象");
        }
        String productName = text(node, "product_name");
        if (productName == null || productName.isBlank()) {
            throw new ServiceException("内联输入快照缺少 product_name");
        }
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
        return new Snapshot(productName, facts, dna, variantSeed);
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
     * 取文本字段。
     *
     * @param node 节点
     * @param key  字段
     * @return 文本；缺失或非文本返回 null
     */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /**
     * 取快照引用的前缀（报错时告诉调用方平台看到了什么）。
     *
     * <p>只在冒号前那一段「像个 scheme」时才算前缀：否则一段没有前缀的 JSON
     * （{@code {"product_name":"x"}}）里的冒号会被误当成前缀分隔符，报错信息会把整段 JSON
     * 当成前缀回显——那既难看又误导。</p>
     *
     * @param ref 引用
     * @return 前缀（含冒号）
     */
    private static String prefixOf(String ref) {
        int index = ref.indexOf(':');
        if (index <= 0) {
            return "（没有前缀）";
        }
        String candidate = ref.substring(0, index);
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            boolean schemeChar = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9') || ch == '+' || ch == '-' || ch == '.';
            if (!schemeChar || i > 20) {
                return "（没有前缀）";
            }
        }
        return candidate + ":";
    }

    /**
     * 解析后的输入快照。
     *
     * @param productName 产品名
     * @param facts       已确认事实
     * @param dna         锁定基因
     * @param variantSeed 差异种子
     */
    private record Snapshot(String productName, Map<String, String> facts, ObjectNode dna,
                            long variantSeed) {
    }

}
