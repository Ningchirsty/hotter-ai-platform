package org.dromara.aigov.workspace.portal.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作台收藏编解码测试（增量 5）。
 *
 * <p>守两件事：①**坏数据不挡路**（收藏是偏好，坏掉就按"没有收藏"处理，不能让人打不开工作台）；
 * ②**上限行为明确**（满了不记录新的，但**不动**已有收藏——静默丢弃会让用户以为点了没反应）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigWorkspaceFavoritesTest {

    @Test
    @DisplayName("往返：保序去重去空白")
    void roundTripKeepsOrderAndDedupes() {
        List<String> decoded = AigWorkspaceFavorites.decode("[\"B\",\"A\",\"B\",\"  \",\"C\"]");
        assertEquals(List.of("B", "A", "C"), decoded);
        assertEquals(List.of("B", "A"), AigWorkspaceFavorites.decode(AigWorkspaceFavorites.encode(List.of("B", "A"))));
    }

    @Test
    @DisplayName("坏数据按空处理，不抛异常（偏好坏了不该让工作台打不开）")
    void brokenDataIsTreatedAsEmpty() {
        assertTrue(AigWorkspaceFavorites.decode(null).isEmpty());
        assertTrue(AigWorkspaceFavorites.decode("").isEmpty());
        assertTrue(AigWorkspaceFavorites.decode("{not json").isEmpty());
        assertTrue(AigWorkspaceFavorites.decode("{\"a\":1}").isEmpty());
        assertTrue(AigWorkspaceFavorites.decode("[1,{\"x\":2},[3]]").isEmpty());
    }

    @Test
    @DisplayName("收藏/取消：重复收藏与取消不存在都不出错")
    void toggleIsIdempotent() {
        List<String> once = AigWorkspaceFavorites.toggle(List.of(), "R1", true);
        assertEquals(List.of("R1"), once);
        assertEquals(List.of("R1"), AigWorkspaceFavorites.toggle(once, "R1", true), "重复收藏不产生重复项");
        assertEquals(List.of(), AigWorkspaceFavorites.toggle(once, "R1", false));
        assertEquals(List.of(), AigWorkspaceFavorites.toggle(List.of(), "R1", false), "取消没收藏过的不出错");
        assertEquals(List.of("A", "C"), AigWorkspaceFavorites.toggle(List.of("A", "B", "C"), "B", false));
        assertEquals(List.of("A"), AigWorkspaceFavorites.toggle(List.of("A"), "  ", true), "空白编码原样返回（什么都不做）");
    }

    @Test
    @DisplayName("到上限后不记录新的，但已有收藏一字不动（不能静默丢弃）")
    void capStopsNewButKeepsExisting() {
        List<String> full = new ArrayList<>();
        for (int i = 0; i < AigWorkspaceFavorites.MAX_FAVORITES; i++) {
            full.add("R" + i);
        }
        List<String> after = AigWorkspaceFavorites.toggle(full, "NEW_ONE", true);
        assertEquals(AigWorkspaceFavorites.MAX_FAVORITES, after.size());
        assertTrue(!after.contains("NEW_ONE"));
        assertEquals("R0", after.get(0), "已有收藏顺序与内容都不变");
    }

}
