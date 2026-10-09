package org.dromara.aigov.enums;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.aigov.task.enums.AigTaskEventTypeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 契约 ↔ 实现 的枚举漂移守卫（Execution Contract v1）。
 *
 * <p><b>它守的是什么</b>：{@code docs/platform-v2/contract/} 下的 {@code error-codes.json} 与
 * {@code execution-event.schema.json} 是**机器可读的契约**，而 CI 里的
 * {@code contract/validate-examples.py} 只比对「json ↔ schema」，**从不比对 Java 枚举**。
 * 于是契约里明确标着 {@code "source": "v2-addition"} 的三个错误码与五个事件类型，
 * 在实现里长期缺失却没有任何一处会红——2026-10-09 实测：契约 13 个错误码，枚举只有 10 个。</p>
 *
 * <p>所以这里把这条链接锁死（三条都是**双向**断言，多一个少一个都失败）：</p>
 * <ol>
 *     <li>错误码集合一致，且每个码的四个处置位（{@code retryable / needsHuman / circuitBreak /
 *         worthFallback}）**逐个相等**——这四个位决定"重试还是转人工还是熔断"，
 *         不一致比"少一个码"更危险：它会让同一个错误在契约读者与实现之间得到相反处置；</li>
 *     <li>事件类型集合一致（逐节点/制品/策略事件是 V2 的红线词表，见契约"不发明第二套词表"）；</li>
 *     <li>任务状态集合一致。</li>
 * </ol>
 *
 * <p><b>为什么不直接在 CI 里跑</b>：这是 Java 侧的事实，放在单测里才能在本仓既有的
 * "模块测试必绿" 门禁下生效（CI 会跑 ruoyi-ai-gov 的全部单测）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigContractEnumDriftTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("★ 契约文件可定位（定位逻辑本身也要断言，否则整个守卫会静默空转）")
    void contractFilesAreLocatable() {
        Path contract = contractDir();
        assertTrue(Files.isRegularFile(contract.resolve("error-codes.json")),
            "缺 error-codes.json：" + contract);
        assertTrue(Files.isRegularFile(contract.resolve("execution-event.schema.json")),
            "缺 execution-event.schema.json：" + contract);
    }

    @Test
    @DisplayName("★ 错误码：契约与 AigErrorClassEnum 双向一致，且四个处置位逐个相等")
    void errorCodesMatchContract() throws Exception {
        JsonNode codes = MAPPER.readTree(contractDir().resolve("error-codes.json").toFile()).get("codes");
        Map<String, JsonNode> byCode = new LinkedHashMap<>();
        for (JsonNode node : codes) {
            byCode.put(node.get("code").asText(), node);
        }
        Set<String> contract = new LinkedHashSet<>(byCode.keySet());
        Set<String> impl = new LinkedHashSet<>();
        for (AigErrorClassEnum item : AigErrorClassEnum.values()) {
            impl.add(item.getCode());
        }
        assertEquals(contract, impl, "契约与 AigErrorClassEnum 的错误码集合不一致");

        for (AigErrorClassEnum item : AigErrorClassEnum.values()) {
            JsonNode node = byCode.get(item.getCode());
            assertAll(item.getCode(),
                () -> assertEquals(node.get("retryable").asBoolean(), item.isRetryable(), "retryable 不一致"),
                () -> assertEquals(node.get("needsHuman").asBoolean(), item.isNeedsHuman(), "needsHuman 不一致"),
                () -> assertEquals(node.get("circuitBreak").asBoolean(), item.isCircuitBreak(), "circuitBreak 不一致"),
                () -> assertEquals(node.get("worthFallback").asBoolean(), item.isWorthFallback(), "worthFallback 不一致"));
        }
    }

    @Test
    @DisplayName("★ 事件类型：契约与 AigTaskEventTypeEnum 双向一致")
    void eventTypesMatchContract() throws Exception {
        JsonNode schema = MAPPER.readTree(contractDir().resolve("execution-event.schema.json").toFile());
        Set<String> contract = new LinkedHashSet<>();
        schema.get("properties").get("eventType").get("enum").forEach(node -> contract.add(node.asText()));
        Set<String> impl = new LinkedHashSet<>();
        for (AigTaskEventTypeEnum item : AigTaskEventTypeEnum.values()) {
            impl.add(item.getCode());
        }
        assertEquals(contract, impl, "契约与 AigTaskEventTypeEnum 的事件类型集合不一致");
    }

    @Test
    @DisplayName("★ 任务状态：契约与 AigTaskStatusEnum 双向一致")
    void taskStatusesMatchContract() throws Exception {
        JsonNode schema = MAPPER.readTree(contractDir().resolve("execution-event.schema.json").toFile());
        Set<String> contract = new LinkedHashSet<>();
        schema.get("$defs").get("status").get("enum").forEach(node -> contract.add(node.asText()));
        Set<String> impl = new LinkedHashSet<>();
        for (AigTaskStatusEnum item : AigTaskStatusEnum.values()) {
            impl.add(item.getCode());
        }
        assertEquals(contract, impl, "契约与 AigTaskStatusEnum 的状态集合不一致");
    }

    @Test
    @DisplayName("★ 策略细因：契约 reasonCodes.recommended 与 AigPolicyReasonCodeEnum 双向一致")
    void policyReasonCodesMatchContract() throws Exception {
        JsonNode recommended = MAPPER.readTree(contractDir().resolve("error-codes.json").toFile())
            .get("reasonCodes").get("recommended");
        Set<String> contract = new LinkedHashSet<>();
        recommended.forEach(node -> contract.add(node.asText()));
        Set<String> impl = new LinkedHashSet<>();
        for (AigPolicyReasonCodeEnum item : AigPolicyReasonCodeEnum.values()) {
            impl.add(item.getCode());
        }
        assertEquals(contract, impl,
            "契约与 AigPolicyReasonCodeEnum 的细因集合不一致：细因是「为什么没成」的可聚合答案，"
                + "各处自造近义名（NO_POLICY / POLICY_MISSING…）会让按细因做的统计永远合不起来");
        assertTrue(contract.size() >= 10, "契约里的细因太少（" + contract.size() + "），可能读错了节点，别让这条守卫空转");
    }

    /**
     * 从运行目录往上找含 {@code docs/platform-v2/contract} 的仓库根。
     *
     * @return 契约目录
     */
    private static Path contractDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("docs/platform-v2/contract");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return fail("找不到 docs/platform-v2/contract，user.dir=" + System.getProperty("user.dir"));
    }

}
