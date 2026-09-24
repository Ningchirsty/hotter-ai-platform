package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 模型产出「逐字段验收」的执行点测试。
 *
 * <p>为什么值得单独钉住：这是「把模型说的话写进交付物」之前唯一的闸门。
 * 它一旦宽松，页面就会出现「null」「N/A」这种占位符，或者整段散文塞进卡片，
 * 而这恰恰是这套系统一直拒绝做的事（如实、不编造、不糊过去）。</p>
 *
 * @author creative
 */
class CreativeDraftBrainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode node(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("合法文本原样采纳（去首尾空白）")
    void acceptsValidText() {
        ObjectNode json = node("{\"name\":\"  克制影棚 · 柔光  \"}");
        assertEquals("克制影棚 · 柔光", CreativeDraftBrain.text(json, "name"));
    }

    @Test
    @DisplayName("字段缺失 / 非字符串 / 空白 → 一律不采纳")
    void rejectsMissingAndBlank() {
        ObjectNode json = node("{\"name\":\"有值\",\"blank\":\"   \",\"num\":123,\"obj\":{}}");
        assertNull(CreativeDraftBrain.text(json, "notExist"), "缺失字段不能编造");
        assertNull(CreativeDraftBrain.text(json, "blank"), "空白串视为没有");
        assertNull(CreativeDraftBrain.text(json, "num"), "非字符串不采纳");
        assertNull(CreativeDraftBrain.text(json, "obj"), "对象不采纳");
    }

    @Test
    @DisplayName("占位符 null / N/A / None → 视为没有，不能当成文案写进交付物")
    void rejectsPlaceholders() {
        ObjectNode json = node("{\"a\":\"null\",\"b\":\"N/A\",\"c\":\"None\",\"d\":\"NULL\"}");
        assertNull(CreativeDraftBrain.text(json, "a"));
        assertNull(CreativeDraftBrain.text(json, "b"));
        assertNull(CreativeDraftBrain.text(json, "c"));
        assertNull(CreativeDraftBrain.text(json, "d"));
    }

    @Test
    @DisplayName("超长文本丢弃（避免把散文塞进卡片）")
    void rejectsOverLength() {
        String longText = "很".repeat(41);
        ObjectNode json = node("{\"name\":\"" + longText + "\"}");
        assertNull(CreativeDraftBrain.text(json, "name", 40), "超过上限应丢弃");
        assertEquals(longText, CreativeDraftBrain.text(json, "name", 400), "在更大的上限内应采纳");
    }

    @Test
    @DisplayName("null 节点不炸")
    void toleratesNullNode() {
        assertNull(CreativeDraftBrain.text(null, "name"));
    }

}
