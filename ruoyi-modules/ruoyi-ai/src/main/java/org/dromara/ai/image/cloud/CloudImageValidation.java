package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 逐型号真实验收快照；未通过的能力在服务端关闭，浏览器不能覆写。
 *
 * <h3>为什么数据不在代码里（ADR-008）</h3>
 * <p>验收结论原本是写死在本类里的 {@code Map.of(...)} 常量。后果是
 * <b>改一次验收结论就要改代码、走一次发版</b>，而且它是一份<b>会过期的快照</b>，
 * 没有任何机制提醒它过期。ADR-008 要求把它迁出硬编码。</p>
 *
 * <h3>数据来源（按优先级）</h3>
 * <ol>
 *   <li>{@code ai.image.validation.external-file} 指向的外部 JSON —— 运维可不发版直接改；</li>
 *   <li>classpath 上的 {@code /cloud-image-validation.json} —— 随包发布的默认档；</li>
 *   <li>类内 <b>内置默认值</b> —— 兜底，保证单测与"文件缺失"时行为与迁移前一致。</li>
 * </ol>
 *
 * <p><b>为什么必须保留内置默认值</b>：本类被大量<b>纯单测</b>直接静态调用
 * （{@code CloudImageRequestTest} 等不启 Spring 容器）。若文件缺失就报错或返回空，
 * 等于把"配置缺失"放大成"所有云端图像能力不可提交"——那是比原问题严重得多的回归。
 * 因此解析失败一律<b>回落到内置默认</b>并打日志，绝不抛异常。</p>
 *
 * <p><b>与治理层的关系</b>（ADR-008）：本类只回答"这个型号×能力有没有被真实验证过"，
 * <b>不是路由开关</b>。能否被 aigov 路由由 {@code aig_model_governance.lifecycle_status} 决定；
 * 两者不一致时应告警，而不是各自静默生效。</p>
 */
@Component
public final class CloudImageValidation {

    /** 内置默认档位：与迁移前的硬编码常量逐项一致。 */
    private static final List<Entry> BUILT_IN = List.of(
        new Entry("flux-2-pro", "2026-10-08", 16, 10 * 1024 * 1024,
            Map.of("T2I", "HTTP_503")),
        new Entry("gpt-image-2.5-flare", "2026-10-08", 16, 20 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED",
                "MASK", "PASSED", "OUTPAINT", "PASSED", "TRANSPARENT", "PASSED")),
        new Entry("gpt-image-2.5-sunburst", "2026-10-08", 16, 20 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED",
                "MASK", "PASSED", "OUTPAINT", "PASSED", "TRANSPARENT", "PASSED")),
        new Entry("qwen-image-3.0", "2026-10-08", 3, 10 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED")),
        new Entry("qwen-image-3.0-pro", "2026-10-08", 3, 10 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED")),
        new Entry("wan2.7-image", "2026-10-08", 9, 10 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "HTTP_400")),
        new Entry("wan2.7-image-pro", "2026-10-08", 9, 10 * 1024 * 1024,
            Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED")));

    /** 型号 -> 档位。构造时即解析完成，之后只读。 */
    private static final AtomicReference<Map<String, Entry>> SNAPSHOT =
        new AtomicReference<>(index(BUILT_IN));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param externalFile 可选的外部覆盖文件路径；为空表示只用 classpath 与内置默认
     */
    public CloudImageValidation(
        @Value("${ai.image.validation.external-file:}") String externalFile) {
        SNAPSHOT.set(load(externalFile));
    }

    /**
     * 加载档位：外部文件 → classpath → 内置默认。
     *
     * @param externalFile 外部文件路径（可空）
     * @return 已建索引的档位表
     */
    static Map<String, Entry> load(String externalFile) {
        if (externalFile != null && !externalFile.isBlank()) {
            Path path = Path.of(externalFile.trim());
            if (Files.isReadable(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return index(parse(in));
                } catch (Exception e) {
                    System.err.println("[CloudImageValidation] 外部档位读取失败，回落到内置默认："
                        + path + " -> " + e);
                }
            } else {
                System.err.println("[CloudImageValidation] 外部档位不可读，回落到内置默认：" + path);
            }
        }
        try (InputStream in = CloudImageValidation.class
            .getResourceAsStream("/cloud-image-validation.json")) {
            if (in == null) {
                return index(BUILT_IN);
            }
            return index(parse(in));
        } catch (Exception e) {
            System.err.println("[CloudImageValidation] classpath 档位解析失败，回落到内置默认：" + e);
            return index(BUILT_IN);
        }
    }

    /**
     * 解析 JSON 数组。
     *
     * @param in 输入流
     * @return 档位清单；内容不可用时返回空列表
     */
    private static List<Entry> parse(InputStream in) throws Exception {
        JsonNode root = MAPPER.readTree(in);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        java.util.List<Entry> entries = new java.util.ArrayList<>();
        for (JsonNode node : root) {
            String model = node.path("model").asText(null);
            if (model == null || model.isBlank()) {
                continue;
            }
            Map<String, String> caps = new LinkedHashMap<>();
            JsonNode capsNode = node.path("capabilities");
            if (capsNode.isObject()) {
                capsNode.fields().forEachRemaining(f -> caps.put(f.getKey(), f.getValue().asText()));
            }
            entries.add(new Entry(model, node.path("testedAt").asText(""),
                node.path("maxReferenceImages").asInt(0),
                node.path("maxReferenceBytes").asInt(0),
                Map.copyOf(caps)));
        }
        return List.copyOf(entries);
    }

    /**
     * @param entries 档位清单
     * @return 型号 -> 档位（重复型号取首条）
     */
    private static Map<String, Entry> index(List<Entry> entries) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry e : entries) {
            map.putIfAbsent(e.model(), e);
        }
        return Map.copyOf(map);
    }

    /**
     * @return 最近一次加载的档位（型号 -&gt; 档位）
     */
    static Map<String, Entry> snapshot() {
        return SNAPSHOT.get();
    }

    /**
     * 某型号某项能力的验收状态。
     *
     * @param model      型号
     * @param capability 能力编码
     * @return 状态码；未知返回 {@code UNVERIFIED}
     */
    public static String status(String model, String capability) {
        if (model == null || capability == null) {
            return "UNVERIFIED";
        }
        Entry e = SNAPSHOT.get().get(model);
        return e == null ? "UNVERIFIED" : e.capabilities().getOrDefault(capability, "UNVERIFIED");
    }

    /**
     * 是否已通过真实验收。
     *
     * @param model      型号
     * @param capability 能力编码
     * @return 仅当状态为 {@code PASSED}
     */
    public static boolean verified(String model, String capability) {
        return "PASSED".equals(status(model, capability));
    }

    /**
     * 该型号已对接的能力（按统一能力顺序）。
     *
     * @param model 型号
     * @return 能力编码清单
     */
    public static List<String> capabilities(String model) {
        Entry e = model == null ? null : SNAPSHOT.get().get(model);
        if (e == null) {
            return List.of();
        }
        return CloudImageRequest.CAPABILITIES.stream().filter(e.capabilities()::containsKey).toList();
    }

    /**
     * 该型号的参考图张数上限。
     *
     * @param model 型号
     * @return 上限
     */
    public static int maxReferences(String model) {
        Entry e = model == null ? null : SNAPSHOT.get().get(model);
        return e == null ? 0 : e.maxReferenceImages();
    }

    /**
     * 该型号的单张参考图字节上限。
     *
     * @param model 型号
     * @return 上限
     */
    public static int maxReferenceBytes(String model) {
        Entry e = model == null ? null : SNAPSHOT.get().get(model);
        return e == null ? 0 : e.maxReferenceBytes();
    }

    /**
     * 该型号验收档位的测试日期（用于对外快照展示）。
     *
     * @param model 型号
     * @return 日期字符串；未知返回空串
     */
    public static String testedAt(String model) {
        Entry e = model == null ? null : SNAPSHOT.get().get(model);
        return e == null ? "" : e.testedAt();
    }

    /**
     * 单型号验收档位。
     *
     * @param model            型号
     * @param testedAt         验收日期
     * @param maxReferenceImages 参考图张数上限
     * @param maxReferenceBytes  单张参考图字节上限
     * @param capabilities     能力 -&gt; 状态
     */
    record Entry(String model, String testedAt, int maxReferenceImages, int maxReferenceBytes,
                 Map<String, String> capabilities) {
    }
}
