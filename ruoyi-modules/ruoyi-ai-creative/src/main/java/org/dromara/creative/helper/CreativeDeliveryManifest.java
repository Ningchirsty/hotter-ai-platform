package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 交付清单与交付包（V0.2 R30，文档 §26）。
 *
 * <p><b>这个类是纯函数</b>：清单怎么拼、校验和怎么算、ZIP 里放什么名字，全部不依赖容器——
 * 因为"交付包解出来是不是那几张图、名字对不对、能不能复现"是验收最该钉死的地方，
 * 而这类断言一旦需要起 Spring 就没法在单测里跑。</p>
 *
 * <p><b>为什么清单里要有 sha256</b>：交付是"最后一公里"，事后一定会有人问
 * "这包里那张图和当时选定的那张是同一张吗"。把每张图的 sha256 写进清单，
 * 并且清单自身的 checksum 也可复算，这个问题才有确定答案。</p>
 *
 * @author creative
 */
public final class CreativeDeliveryManifest {

    /** 清单 schema 版本 */
    public static final String SCHEMA = "delivery-manifest/1";

    /** 角色：长图 */
    public static final String ROLE_LONG_PAGE = "LONG_PAGE";
    /** 角色：逐屏交付图 */
    public static final String ROLE_SCREEN = "SCREEN_DELIVERY";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeDeliveryManifest() {
    }

    /**
     * 组装清单 JSON。
     *
     * @param renderer     渲染器编码（LONG_PAGE / MULTI_IMAGE …）
     * @param deliveryType 交付类型
     * @param templateKey  模板标识（形如 longpage@1.0.2；多图打包没有模板时为空）
     * @param renderedAt   渲染时间（ISO 文本，由调用方给，便于测试注入）
     * @param products     产物清单
     * @return 清单 JSON
     */
    public static String build(String renderer, String deliveryType, String templateKey,
                               String renderedAt, List<CreativeRenderer.Product> products) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("renderer", renderer);
        root.put("deliveryType", deliveryType);
        if (StringUtils.isNotBlank(templateKey)) {
            root.put("templateKey", templateKey);
        }
        root.put("renderedAt", renderedAt);
        root.put("imageCount", products.size());
        long totalBytes = 0L;
        for (CreativeRenderer.Product product : products) {
            totalBytes += product.bytes() == null ? 0L : product.bytes();
        }
        root.put("totalBytes", totalBytes);
        root.put("artifacts", CreativeRenderer.toRows(products));
        root.put("checksum", checksumOf(products));
        return toJson(root);
    }

    /**
     * 清单校验和：对"顺序 + 文件名 + 内容 sha256 + 附件ID"这四样求 sha256。
     *
     * <p>为什么用这四样：顺序与文件名决定"包长什么样"，内容 sha256 决定"图是不是那张"，
     * 附件ID 决定"能不能重新取到"。任何一样变了，校验和就该变——这正是"可复现"的定义。</p>
     *
     * @param products 产物
     * @return 十六进制 sha256（取前 16 位以外的完整值）
     */
    public static String checksumOf(List<CreativeRenderer.Product> products) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (CreativeRenderer.Product product : products) {
            index++;
            sb.append(index).append('|')
                .append(StringUtils.blankToDefault(product.fileName(), "")).append('|')
                .append(StringUtils.blankToDefault(product.sha256(), "")).append('|')
                .append(product.fileId() == null ? "" : product.fileId()).append('\n');
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节的 sha256 十六进制文本。
     *
     * @param bytes 字节（可空）
     * @return 十六进制；入参为空返回空串
     */
    public static String sha256(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 算法一定存在；真出错也不该让交付失败——如实返回空，页面显示"-"
            return "";
        }
    }

    /**
     * 交付包内该用哪个名字（清单顺序决定包内顺序）。
     *
     * @param product 产物
     * @return 文件名
     */
    public static String entryName(CreativeRenderer.Product product) {
        return StringUtils.blankToDefault(product.fileName(), "artifact.png");
    }

    /**
     * 从清单 JSON 里取产物附件ID列表（下载时按它现拼 ZIP，不重复存字节）。
     *
     * @param manifestJson 清单 JSON（可空）
     * @return 附件ID列表；解析不出返回空列表
     */
    public static List<Long> fileIdsOf(String manifestJson) {
        List<Long> ids = new java.util.ArrayList<>();
        if (StringUtils.isBlank(manifestJson)) {
            return ids;
        }
        try {
            Map<String, Object> root = MAPPER.readValue(manifestJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            Object artifacts = root.get("artifacts");
            if (!(artifacts instanceof List<?> list)) {
                return ids;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && row.get("fileId") != null) {
                    try {
                        ids.add(Long.valueOf(String.valueOf(row.get("fileId"))));
                    } catch (NumberFormatException ignored) {
                        // 非数字的 fileId 跳过：清单坏了也不该让下载整段失败，由服务层报"取不到"
                    }
                }
            }
        } catch (Exception e) {
            return ids;
        }
        return ids;
    }

    /**
     * 按清单现拼交付包（ZIP）。
     *
     * <p><b>为什么现拼而不是渲染时存一份 ZIP</b>：包里的每一张图本来就已经作为任务附件存过一份，
     * 再存一份 ZIP 等于交付一次就多占一份存储（PNG 已压缩，ZIP 也压不掉多少）。
     * 清单里记着附件ID与 sha256，需要包的时候现拼即可——**清单才是交付的可追溯物**。</p>
     *
     * <p>包里第一项固定是 {@code manifest.json}（自描述：每张图是什么屏、多大、sha256 多少），
     * 这样包离开系统之后仍然能自证。</p>
     *
     * @param products     产物清单（顺序即包内顺序）
     * @param manifestJson 清单 JSON
     * @param loader       按附件ID取字节（取不到会抛异常，由调用方转成可读错误）
     * @return ZIP 字节
     */
    public static byte[] zip(List<CreativeRenderer.Product> products, String manifestJson,
                             java.util.function.Function<Long, byte[]> loader) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            // PNG 已经是压缩格式，再花 CPU 压收益极小；用 BEST_SPEED 让下载更快
            zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
            zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
            zip.write(StringUtils.blankToDefault(manifestJson, "{}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (CreativeRenderer.Product product : products) {
                if (product.fileId() == null) {
                    continue;
                }
                byte[] bytes = loader.apply(product.fileId());
                if (bytes == null || bytes.length == 0) {
                    throw new IllegalStateException("产物取不到内容：" + entryName(product)
                        + "（附件 " + product.fileId() + " 可能已被清理）");
                }
                zip.putNextEntry(new java.util.zip.ZipEntry(entryName(product)));
                zip.write(bytes);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("拼装交付包失败：" + e.getMessage(), e);
        }
    }

    private static String toJson(Map<String, Object> root) {
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }
}
