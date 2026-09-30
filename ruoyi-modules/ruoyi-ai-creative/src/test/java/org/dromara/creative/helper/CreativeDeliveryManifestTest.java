package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交付清单与交付包（V0.2 R30，文档 §26）。
 *
 * <p>钉的是交付这条链路上最容易被含糊过去的三件事：</p>
 * <ol>
 *   <li><b>可复现</b>：同一份产物 → 同一份清单校验和；任何一张图变了，校验和必须变；</li>
 *   <li><b>自描述</b>：交付包里第一项是 manifest.json，包离开系统后仍能自证每张图是什么；</li>
 *   <li><b>不重复存字节</b>：ZIP 是按清单现拼的（这里用假 loader 证明它只按清单取数）。</li>
 * </ol>
 */
class CreativeDeliveryManifestTest {

    private static CreativeRenderer.Product product(int index, String moduleCode, long fileId, String sha) {
        return new CreativeRenderer.Product(
            CreativeRenderer.fileNameOf(index, "S0" + index, moduleCode, "png"),
            CreativeDeliveryManifest.ROLE_SCREEN, "S0" + index, "MAIN_WHITE_BG", moduleCode,
            fileId, 100L + index, 800, 800, 700_000L, sha);
    }

    @Test
    @DisplayName("清单可复现：同产物 → 同 checksum；改一张图 → checksum 变；顺序变 → checksum 也变")
    void checksumIsReproducibleAndSensitive() {
        List<CreativeRenderer.Product> products = List.of(
            product(1, "MAIN_WHITE_BG", 11L, "aaa"),
            product(2, "MAIN_SCENE", 22L, "bbb"));

        String first = CreativeDeliveryManifest.checksumOf(products);
        String again = CreativeDeliveryManifest.checksumOf(List.of(
            product(1, "MAIN_WHITE_BG", 11L, "aaa"),
            product(2, "MAIN_SCENE", 22L, "bbb")));
        assertEquals(first, again, "同输入必须同校验和");

        String changedContent = CreativeDeliveryManifest.checksumOf(List.of(
            product(1, "MAIN_WHITE_BG", 11L, "ccc"),
            product(2, "MAIN_SCENE", 22L, "bbb")));
        assertNotEquals(first, changedContent, "图变了校验和必须变");

        String changedOrder = CreativeDeliveryManifest.checksumOf(List.of(
            product(2, "MAIN_SCENE", 22L, "bbb"),
            product(1, "MAIN_WHITE_BG", 11L, "aaa")));
        assertNotEquals(first, changedOrder, "顺序变了校验和必须变");
    }

    @Test
    @DisplayName("清单 JSON：自描述字段齐全，checksum 与单独算的一致")
    void manifestCarriesEverythingNeeded() {
        List<CreativeRenderer.Product> products = List.of(product(1, "MAIN_WHITE_BG", 11L, "aaa"));
        String json = CreativeDeliveryManifest.build("MULTI_IMAGE", "MAIN_IMAGE", null,
            "2026-09-30 12:00:00", products);

        assertTrue(json.contains("\"schema\":\"delivery-manifest/1\""), json);
        assertTrue(json.contains("\"renderer\":\"MULTI_IMAGE\""), json);
        assertTrue(json.contains("\"imageCount\":1"), json);
        assertTrue(json.contains("\"totalBytes\":700000"), json);
        assertTrue(json.contains("\"fileName\":\"01-S01-MAIN_WHITE_BG.png\""), json);
        assertTrue(json.contains("\"checksum\":\"" + CreativeDeliveryManifest.checksumOf(products) + "\""), json);
        assertEquals(List.of(11L), CreativeDeliveryManifest.fileIdsOf(json));
    }

    @Test
    @DisplayName("清单坏了不炸：解析不出产物时返回空列表（由服务层报「取不到」）")
    void brokenManifestIsHandledHonestly() {
        assertEquals(List.of(), CreativeDeliveryManifest.fileIdsOf(null));
        assertEquals(List.of(), CreativeDeliveryManifest.fileIdsOf(""));
        assertEquals(List.of(), CreativeDeliveryManifest.fileIdsOf("not-json"));
        assertEquals(List.of(), CreativeDeliveryManifest.fileIdsOf("{\"artifacts\":\"oops\"}"));
        // fileId 不是数字 → 跳过这一条，而不是整体失败
        assertEquals(List.of(), CreativeDeliveryManifest.fileIdsOf(
            "{\"artifacts\":[{\"fileId\":\"abc\"}]}"));
    }

    @Test
    @DisplayName("交付包：第一项是 manifest.json，其余按清单顺序；只按清单里的附件ID取数")
    void zipContainsManifestThenProductsInOrder() throws Exception {
        List<CreativeRenderer.Product> products = List.of(
            product(1, "MAIN_WHITE_BG", 11L, "aaa"),
            product(2, "MAIN_SCENE", 22L, "bbb"));
        Map<Long, byte[]> store = Map.of(
            11L, "IMG-11".getBytes(StandardCharsets.UTF_8),
            22L, "IMG-22".getBytes(StandardCharsets.UTF_8));
        List<Long> asked = new ArrayList<>();

        String manifest = CreativeDeliveryManifest.build("MULTI_IMAGE", "MAIN_IMAGE", null,
            "2026-09-30 12:00:00", products);
        byte[] zip = CreativeDeliveryManifest.zip(products, manifest, fileId -> {
            asked.add(fileId);
            return store.get(fileId);
        });

        List<String> names = new ArrayList<>();
        Map<String, String> contents = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
                contents.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(List.of("manifest.json", "01-S01-MAIN_WHITE_BG.png", "02-S02-MAIN_SCENE.png"), names);
        assertEquals("IMG-11", contents.get("01-S01-MAIN_WHITE_BG.png"));
        assertEquals("IMG-22", contents.get("02-S02-MAIN_SCENE.png"));
        assertTrue(contents.get("manifest.json").contains("delivery-manifest/1"));
        assertEquals(List.of(11L, 22L), asked, "只该按清单里的附件ID取数");
    }

    @Test
    @DisplayName("产物取不到 → 明确失败（不许悄悄出一个少图的包）")
    void missingProductFailsLoudly() {
        List<CreativeRenderer.Product> products = List.of(product(1, "MAIN_WHITE_BG", 11L, "aaa"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> CreativeDeliveryManifest.zip(products, "{}", fileId -> null));
        assertTrue(e.getMessage().contains("取不到内容"), e.getMessage());
        assertTrue(e.getMessage().contains("已被清理"), "要把可能的原因说出来：" + e.getMessage());
    }

    @Test
    @DisplayName("sha256：空入参给空串（不炸），同字节稳定")
    void sha256IsStable() {
        assertEquals("", CreativeDeliveryManifest.sha256(null));
        String a = CreativeDeliveryManifest.sha256("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(a, CreativeDeliveryManifest.sha256("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals(64, a.length());
        assertNotEquals(a, CreativeDeliveryManifest.sha256("abd".getBytes(StandardCharsets.UTF_8)));
        assertFalse(a.isEmpty());
    }
}
