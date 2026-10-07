package org.dromara.aigov.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据等级枚举的不变式测试。
 *
 * <p>{@code rank()} 是路由判定的比较依据（「模型允许的最高等级 ≥ 本次等级」），
 * 一旦顺序或取值被改动，表现是「等级不够的模型被放行」这类静默越权，
 * 因此把顺序与 STRICT 的独有语义钉死。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigDataLevelEnumTest {

    @Test
    @DisplayName("等级顺序：PUBLIC < INTERNAL < RESTRICTED < STRICT，rank 必须严格递增")
    void rankIsStrictlyAscending() {
        List<AigDataLevelEnum> ordered = List.of(
            AigDataLevelEnum.PUBLIC,
            AigDataLevelEnum.INTERNAL,
            AigDataLevelEnum.RESTRICTED,
            AigDataLevelEnum.STRICT);
        for (int i = 1; i < ordered.size(); i++) {
            int previous = ordered.get(i - 1).getRank();
            int current = ordered.get(i).getRank();
            assertTrue(current > previous,
                ordered.get(i - 1).getCode() + " 的 rank 应小于 " + ordered.get(i).getCode());
        }
    }

    @Test
    @DisplayName("四级的 rank 值必须与设计口径一致（0/1/2/3），且总数就是 4 个")
    void rankValuesMatchDesign() {
        assertEquals(4, AigDataLevelEnum.values().length, "新增等级会影响既有字典与策略，改这里必须同步改字典");
        assertEquals(0, AigDataLevelEnum.PUBLIC.getRank());
        assertEquals(1, AigDataLevelEnum.INTERNAL.getRank());
        assertEquals(2, AigDataLevelEnum.RESTRICTED.getRank());
        assertEquals(3, AigDataLevelEnum.STRICT.getRank());
    }

    @Test
    @DisplayName("只有 STRICT 禁止外发：RESTRICTED 仍可由策略显式放行，两者不能合并")
    void onlyStrictForbidsExternal() {
        assertTrue(AigDataLevelEnum.STRICT.externalForbidden());
        assertFalse(AigDataLevelEnum.RESTRICTED.externalForbidden(),
            "RESTRICTED 与 STRICT 语义不同：前者可由策略放行外发，合并会丢表达能力");
        assertFalse(AigDataLevelEnum.INTERNAL.externalForbidden());
        assertFalse(AigDataLevelEnum.PUBLIC.externalForbidden());
    }

    @Test
    @DisplayName("find：能按 code 命中 STRICT，未知 code 返回 null（不抛异常、不兜底放行）")
    void findResolvesStrictAndRejectsUnknown() {
        assertNotNull(AigDataLevelEnum.find("STRICT"));
        assertEquals(AigDataLevelEnum.STRICT, AigDataLevelEnum.find("STRICT"));
        assertNull(AigDataLevelEnum.find("SENSITIVE"),
            "设计文档的 SENSITIVE 映射到 RESTRICTED，不应作为独立 code 存在");
        assertNull(AigDataLevelEnum.find("unknown"));
        assertNull(AigDataLevelEnum.find(null));
    }

    @Test
    @DisplayName("每个等级都自带非空 code 与描述，避免页面出现空白选项")
    void everyLevelHasCodeAndDesc() {
        Arrays.stream(AigDataLevelEnum.values()).forEach(item -> {
            assertTrue(item.getCode() != null && !item.getCode().isBlank(), "code 不能为空");
            assertTrue(item.getDesc() != null && !item.getDesc().isBlank(), "描述不能为空");
        });
    }

}
