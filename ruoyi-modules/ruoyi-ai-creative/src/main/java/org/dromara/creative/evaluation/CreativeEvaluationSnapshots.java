package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;

import java.io.InputStream;

/**
 * 评测输入快照的解析（设计 §13.2「记录输入快照」）。
 *
 * <p><b>两种引用形态，各有明确的边界</b>：</p>
 * <ul>
 *     <li>{@code inline:<json>} —— 文本快照本身直接写在用例行里（策划 Agent 的基因/事实、
 *         视觉 DNA 的参考图引用、视觉 QA 的规则集都放这里）。用例自带全部输入，
 *         <b>评审读用例行就能看到这次评测跑的到底是什么</b>，也天然可重放。</li>
 *     <li>{@code classpath:<相对路径>} —— 二进制快照（图片）只能引用<b>随平台发布</b>的资源，
 *         且限定在 {@link #ALLOWED_IMAGE_DIR} 目录下。两个理由：一是用例行是 varchar 存不下图；
 *         二是如果允许任意 classpath 路径，用例就能读到配置/密钥之类的资源。</li>
 * </ul>
 *
 * <p>认不出的前缀一律报错并说明支持什么——<b>不会猜一个默认输入</b>。
 * 输入不确定的评测，结论没有意义。</p>
 *
 * <p>用 Jackson 2（{@code com.fasterxml}）与业务 Helper 保持同一代次；跨模块只走 String。</p>
 *
 * @author creative
 */
public final class CreativeEvaluationSnapshots {

    /**
     * 文本快照前缀
     */
    public static final String PREFIX_INLINE = "inline:";

    /**
     * 资源快照前缀
     */
    public static final String PREFIX_CLASSPATH = "classpath:";

    /**
     * 允许引用图片资源的目录（只读这一个目录）
     */
    public static final String ALLOWED_IMAGE_DIR = "creative/eval/";

    /**
     * 图片快照大小上限（超过说明不是「一张待评测的图」，而是别的什么东西）
     */
    public static final long MAX_IMAGE_BYTES = 4L * 1024L * 1024L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeEvaluationSnapshots() {
    }

    /**
     * 解析文本快照（{@code inline:<json>}）。
     *
     * @param inputSnapshotRef 用例上的输入快照引用
     * @return 快照 JSON 对象
     */
    public static JsonNode readInlineJson(String inputSnapshotRef) {
        if (inputSnapshotRef == null || inputSnapshotRef.isBlank()) {
            throw new ServiceException("用例没有输入快照（input_snapshot_ref 为空）：评测必须在确定的输入上跑");
        }
        String ref = inputSnapshotRef.trim();
        if (!ref.startsWith(PREFIX_INLINE)) {
            throw new ServiceException("不支持的输入快照前缀：" + prefixOf(ref)
                + "。本执行器只支持 " + PREFIX_INLINE + "<json>（内联快照）；"
                + "对象存储/业务ID 形式的快照尚未实现——认不出的前缀不会被当成某种默认输入");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(ref.substring(PREFIX_INLINE.length()));
        } catch (Exception e) {
            throw new ServiceException("内联输入快照不是合法 JSON：" + e.getMessage());
        }
        if (node == null || !node.isObject()) {
            throw new ServiceException("内联输入快照必须是 JSON 对象");
        }
        return node;
    }

    /**
     * 读取图片快照（{@code classpath:<creative/eval/ 下的相对路径>}）。
     *
     * @param ref 资源引用
     * @return 图片字节
     */
    public static byte[] readImage(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new ServiceException("图片快照引用为空");
        }
        String trimmed = ref.trim();
        if (!trimmed.startsWith(PREFIX_CLASSPATH)) {
            throw new ServiceException("不支持的图片快照前缀：" + prefixOf(trimmed)
                + "。图片快照只支持 " + PREFIX_CLASSPATH + "<相对路径>（路径必须在 " + ALLOWED_IMAGE_DIR
                + " 下）；对象存储形式的快照尚未实现");
        }
        String path = trimmed.substring(PREFIX_CLASSPATH.length()).trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith(ALLOWED_IMAGE_DIR) || path.contains("..")) {
            throw new ServiceException("图片快照只允许 " + ALLOWED_IMAGE_DIR + " 下的资源"
                + "（用例不能读任意 classpath 资源）：" + path);
        }
        byte[] bytes;
        try (InputStream in = CreativeEvaluationSnapshots.class.getClassLoader()
            .getResourceAsStream(path)) {
            if (in == null) {
                throw new ServiceException("图片快照资源不存在：" + path
                    + "（快照随平台代码一起发布，路径是 " + ALLOWED_IMAGE_DIR + " 下的相对路径）");
            }
            bytes = in.readAllBytes();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("读取图片快照失败：" + path + "（" + e.getMessage() + "）");
        }
        if (bytes.length == 0) {
            throw new ServiceException("图片快照是空文件：" + path);
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new ServiceException("图片快照超过上限 " + MAX_IMAGE_BYTES + " 字节：" + path
                + "（实际 " + bytes.length + "）");
        }
        return bytes;
    }

    /**
     * 取快照里的文本字段（缺失或非文本即报错，不给出默认值）。
     *
     * @param snapshot 快照
     * @param key      字段
     * @param label    报错时的可读名称
     * @return 文本值
     */
    public static String requireText(JsonNode snapshot, String key, String label) {
        JsonNode node = snapshot.get(key);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new ServiceException("内联输入快照缺少 " + label + "（字段 " + key + "）");
        }
        return node.asText().trim();
    }

    /**
     * 取快照里的对象字段。
     *
     * @param snapshot 快照
     * @param key      字段
     * @param label    报错时的可读名称
     * @return 对象节点
     */
    public static JsonNode requireObject(JsonNode snapshot, String key, String label) {
        JsonNode node = snapshot.get(key);
        if (node == null || !node.isObject() || node.isEmpty()) {
            throw new ServiceException("内联输入快照缺少 " + label + "（字段 " + key + "，应为非空对象）");
        }
        return node;
    }

    /**
     * 取引用前缀（报错时告诉调用方平台看到了什么）。
     *
     * <p>只在冒号前那一段「像个 scheme」时才算前缀：否则一段没有前缀的 JSON
     * （{@code {"product_name":"x"}}）里的冒号会被误当成前缀分隔符，报错信息会把整段 JSON
     * 当成前缀回显——那既难看又误导。</p>
     *
     * @param ref 引用
     * @return 前缀（含冒号）；不像前缀时返回「（没有前缀）」
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

}
