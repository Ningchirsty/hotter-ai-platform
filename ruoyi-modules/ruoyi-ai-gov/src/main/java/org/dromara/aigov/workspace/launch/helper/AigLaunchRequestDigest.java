package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 启动请求的内容摘要（主文档线增量 3）。
 *
 * <h3>它防的是"幂等键被复用到了另一次启动上"</h3>
 * <p>幂等键由客户端生成并在重试时复用——这是它存在的意义。但如果客户端**改了内容却复用同一个键**
 * （最典型的是"用户改了输入又点了一次"，而前端忘了换键），只按幂等键判重复就会
 * <b>把第二次的输入静默丢掉</b>，用户看到的是"提交成功"但内容还是第一次的。
 * 所以键相同还要比摘要：摘要不同 = 冲突（{@code IDEMPOTENCY_CONFLICT}），必须报错。</p>
 *
 * <h3>为什么摘要必须对格式不敏感</h3>
 * <p>与岗位包清单哈希同理：重试时 JSON 的键顺序、空白、{@code 1} 与 {@code 1.0} 都可能变，
 * 而这些都不是"内容变了"。所以复用训练台那份**唯一的规范化实现**
 * （{@link AigStudioContentHasher}），把 {@code snapshotJson} 解析成 JSON 值再参与规范化，
 * 而不是当成一串文本——文本比较会把"多一个空格"判成冲突，让重试永远失败。</p>
 *
 * @author ai-gov
 */
public final class AigLaunchRequestDigest {

    /**
     * 解析用（输出交由 AigStudioContentHasher 规范化，避免两份序列化口径）
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AigLaunchRequestDigest() {
    }

    /**
     * 计算启动请求摘要。
     *
     * @param roleCode      岗位编码
     * @param roleVersionId 岗位版本
     * @param actionCode    卡片编码
     * @param targetRef     目标引用
     * @param taskType      任务类型（可空）
     * @param projectType   业务域（可空）
     * @param projectId     业务项目ID（可空）
     * @param dataLevel     数据等级（可空）
     * @param snapshotJson  输入快照（可空；合法 JSON 会被解析成值参与规范化）
     * @param context       上下文键值（可空）
     * @return 十六进制小写 sha256
     */
    public static String of(String roleCode, Long roleVersionId, String actionCode, String targetRef,
                            String taskType, String projectType, Long projectId, String dataLevel,
                            String snapshotJson, Map<String, String> context) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("roleCode", roleCode);
        root.put("roleVersionId", roleVersionId);
        root.put("actionCode", actionCode);
        root.put("targetRef", targetRef);
        root.put("taskType", taskType);
        root.put("projectType", projectType);
        root.put("projectId", projectId);
        root.put("dataLevel", dataLevel);
        root.put("snapshot", snapshot(snapshotJson));
        // 上下文按键排序后再放进去：规范化会排序，这里排序只是让"人工看这份摘要由什么构成"更直观
        root.put("context", context == null ? Map.of() : new TreeMap<>(context));
        return AigStudioContentHasher.hash(MAPPER.writeValueAsString(root));
    }

    /**
     * 把输入快照解析成 JSON 值（合法 JSON 时），否则按字符串处理。
     *
     * @param snapshotJson 输入快照
     * @return 参与规范化的值
     */
    private static Object snapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(snapshotJson);
            return node == null || node.isMissingNode() ? snapshotJson : node;
        } catch (Exception e) {
            // 不是合法 JSON 也允许（有些卡片收集的是纯文本），按字符串参与摘要
            return snapshotJson;
        }
    }

}
