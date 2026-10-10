package org.dromara.aigov.workspace.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 场景引用解析测试（增量 3）。
 *
 * <p>它是岗位包校验与启动链路**共用**的一处实现，所以两边的口径不会漂移；
 * 这里钉住"什么算合法引用"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioRefTest {

    @Test
    @DisplayName("合法引用解析出编码与版本，并能原样还原")
    void parsesValidRef() {
        AigScenarioRef ref = AigScenarioRef.parse("scenario://COMMERCE_DETAIL_PAGE@1.2.3");
        assertEquals("COMMERCE_DETAIL_PAGE", ref.code());
        assertEquals("1.2.3", ref.version());
        assertEquals("scenario://COMMERCE_DETAIL_PAGE@1.2.3", ref.toUri());
        // 前后空白容忍（配置里复制粘贴常带空格）
        assertEquals("1.0.0", AigScenarioRef.parse("  scenario://X@1.0.0  ").version());
    }

    @Test
    @DisplayName("形态不符一律返回 null（调用方据此报业务问题，不抛异常）")
    void rejectsMalformed() {
        assertNull(AigScenarioRef.parse(null));
        assertNull(AigScenarioRef.parse(""));
        assertNull(AigScenarioRef.parse("COMMERCE_DETAIL_PAGE"));
        assertNull(AigScenarioRef.parse("scenario://A@1.0"));
        assertNull(AigScenarioRef.parse("scenario://A@v1.0.0"));
        assertNull(AigScenarioRef.parse("http://A@1.0.0"));
        assertNull(AigScenarioRef.parse("scenario://A-B@1.0.0"), "编码里不允许中划线");
    }

}
