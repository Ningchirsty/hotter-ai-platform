package org.dromara.creative.invoker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「输出模板注入」的回归测试。
 *
 * <p>钉住一个真实踩过的坑：治理台给视觉工厂能力登记的 schema 是
 * {@code {"fields":[],"note":"..."}}（意思是「只要求合法 JSON，字段由业务侧验收」）。
 * 早期实现把它整段当作「输出模板」塞进系统提示词，本地小模型就照着它输出
 * {@code {"fields":[],"note":...}}，完全不理会业务契约里的 directions/screens，
 * 表现为「模型调用成功但 0 条被采纳」——这种故障很难从结果反推，必须由单测拦住。</p>
 *
 * @author creative
 */
class CreativeLocalChatInvokerTest {

    @Test
    @DisplayName("未声明字段的 schema 一个字都不注入（否则模型会照抄模板）")
    void emptyFieldsSchemaIsNotInjected() {
        assertEquals("", CreativeLocalChatInvoker.schemaHint("{\"fields\":[],\"note\":\"只要求合法 JSON\"}"));
        assertEquals("", CreativeLocalChatInvoker.schemaHint("{\"note\":\"没有 fields\"}"));
        assertEquals("", CreativeLocalChatInvoker.schemaHint(null));
        assertEquals("", CreativeLocalChatInvoker.schemaHint(""));
    }

    @Test
    @DisplayName("声明了字段的 schema 才注入，且原样带上")
    void declaredFieldsAreInjected() {
        String schema = "{\"fields\":[{\"name\":\"answer\",\"type\":\"string\"}]}";
        String hint = CreativeLocalChatInvoker.schemaHint(schema);
        assertTrue(hint.contains("输出模板"), "应提示这是输出模板");
        assertTrue(hint.contains("answer"), "应包含声明的字段名");
    }

    @Test
    @DisplayName("坏 schema 不阻断调用（返回空片段而不是抛异常）")
    void brokenSchemaIsTolerated() {
        assertEquals("", CreativeLocalChatInvoker.schemaHint("{不是合法 json"));
    }

}
