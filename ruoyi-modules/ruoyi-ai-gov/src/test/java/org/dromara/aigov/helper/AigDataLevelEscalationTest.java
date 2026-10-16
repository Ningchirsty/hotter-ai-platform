package org.dromara.aigov.helper;

import org.dromara.aigov.enums.AigDataLevelEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「能力声明的最低数据等级」合并口径（Q2C，纯函数）。
 *
 * <p><b>守的是"只抬不降"</b>：声明若能把调用方标高的等级降下来，它就从一个保护变成一个新的洞。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigDataLevelEscalationTest {

    @Test
    @DisplayName("★ 只抬不降：声明更高 → 取声明；调用方更高 → 保持调用方")
    void onlyEscalates() {
        assertEquals(AigDataLevelEnum.RESTRICTED,
            AigDataLevelEscalation.effective("RESTRICTED", AigDataLevelEnum.INTERNAL));
        // 调用方标得更高时不能被声明降下来（这正是"只抬不降"的意义）
        assertEquals(AigDataLevelEnum.STRICT,
            AigDataLevelEscalation.effective("RESTRICTED", AigDataLevelEnum.STRICT));
        assertEquals(AigDataLevelEnum.INTERNAL,
            AigDataLevelEscalation.effective("INTERNAL", AigDataLevelEnum.INTERNAL));
    }

    @Test
    @DisplayName("不声明（空/NULL）→ 调用方说了算（存量能力行为逐字不变）")
    void undeclaredKeepsRequested() {
        assertEquals(AigDataLevelEnum.INTERNAL,
            AigDataLevelEscalation.effective(null, AigDataLevelEnum.INTERNAL));
        assertEquals(AigDataLevelEnum.PUBLIC,
            AigDataLevelEscalation.effective("   ", AigDataLevelEnum.PUBLIC));
        assertNull(AigDataLevelEscalation.effective("RESTRICTED", null), "调用方等级为空时返回空");
        assertNull(AigDataLevelEscalation.explain(null, AigDataLevelEnum.INTERNAL), "不声明不产生噪音");
        assertNull(AigDataLevelEscalation.explain("  ", AigDataLevelEnum.INTERNAL));
    }

    @Test
    @DisplayName("说明文案：抬升时写清两边等级；声明与调用方一致时不产生噪音")
    void explainTexts() {
        String escalated = AigDataLevelEscalation.explain("RESTRICTED", AigDataLevelEnum.INTERNAL);
        assertTrue(escalated.contains("RESTRICTED"));
        assertTrue(escalated.contains("INTERNAL"));
        assertTrue(escalated.contains("只抬不降"));

        assertNull(AigDataLevelEscalation.explain("INTERNAL", AigDataLevelEnum.RESTRICTED),
            "声明低于调用方（说明没起作用）不该刷一条命中明细");

        // 声明值认不出：保留调用方等级，但要能被看见（否则等于锁没上而没人知道）
        String unrecognized = AigDataLevelEscalation.explain("TOPSECRET", AigDataLevelEnum.INTERNAL);
        assertTrue(unrecognized.contains("无法识别"));
        assertTrue(unrecognized.contains("INTERNAL"));
        assertEquals(AigDataLevelEnum.INTERNAL,
            AigDataLevelEscalation.effective("TOPSECRET", AigDataLevelEnum.INTERNAL));
    }

    @Test
    @DisplayName("大小写与空白容错；合法性判定用于服务端兜底")
    void tolerantAndLegal() {
        assertEquals(AigDataLevelEnum.STRICT,
            AigDataLevelEscalation.effective("  strict ", AigDataLevelEnum.PUBLIC));
        assertTrue(AigDataLevelEscalation.legalDeclaration(null));
        assertTrue(AigDataLevelEscalation.legalDeclaration(""));
        assertTrue(AigDataLevelEscalation.legalDeclaration("restricted"));
        assertFalse(AigDataLevelEscalation.legalDeclaration("TOPSECRET"));
        assertFalse(AigDataLevelEscalation.legalDeclaration("2"));
    }
}
