package org.dromara.aigov.workspace.portal.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本选择测试（增量 2）。
 *
 * <p>守的是那个安静的错误：字符串比较会把 {@code 1.9.0} 当成比 {@code 1.10.0} 新，
 * 于是员工一直在用旧配置，而界面上看起来"我们明明发布过了"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigVersionPickTest {

    @Test
    @DisplayName("按数字逐段比较：1.10.0 比 1.9.0 新（字符串序会判反）")
    void numericSegmentsNotStringOrder() {
        assertTrue(AigVersionPick.compare("1.10.0", "1.9.0") > 0, "字符串序会说 1.9.0 更大");
        assertTrue(AigVersionPick.compare("2.0.0", "1.99.99") > 0);
        assertTrue(AigVersionPick.compare("1.0.10", "1.0.9") > 0);
        assertEquals(0, AigVersionPick.compare("1.0.0", "1.0.0"));
        assertTrue(AigVersionPick.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    @DisplayName("读不懂的形态回退字符串比较，不抛异常（列表不该因一条脏数据整页打不开）")
    void unparseableFallsBackToText() {
        assertEquals(0, AigVersionPick.compare("v1", "v1"));
        assertTrue(AigVersionPick.compare("v2", "v1") > 0);
        assertTrue(AigVersionPick.compare("1.0", "1.0") == 0);
        assertTrue(AigVersionPick.compare(null, "1.0.0") < 0, "null 当空串，排序仍稳定");
    }

    @Test
    @DisplayName("max 忽略空白项；空列表返回 null")
    void maxPicksHighest() {
        assertEquals("1.10.0", AigVersionPick.max(Arrays.asList("1.9.0", "1.10.0", "1.2.0")));
        assertEquals("1.0.0", AigVersionPick.max(List.of("1.0.0")));
        assertEquals("1.0.0", AigVersionPick.max(Arrays.asList(null, " ", "1.0.0")));
        assertEquals(null, AigVersionPick.max(List.of()));
        assertEquals(null, AigVersionPick.max(null));
    }

}
