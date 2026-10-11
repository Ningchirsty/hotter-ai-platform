package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.agent.domain.bo.AigAgentResourceBindingBo;
import org.dromara.aigov.agent.domain.bo.AigAgentResourceBindingQueryBo;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具/知识绑定接口的权限断言（附件 07 §5）。
 *
 * <p><b>为什么单独钉这个</b>：新增绑定的权限点是新的，而权限串只是个字符串——写错了编译期、
 * 运行期都不会报错，只会表现成"按钮点了没反应"（403）或更糟：没加注解 ⇒ 任何登录用户都能
 * 改某个版本的工具边界。装配烟测用 standalone MockMvc，不挂真实鉴权，所以这里用反射查注解。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentResourceBindingControllerTest {

    @Test
    @DisplayName("★ 新增绑定：必须带独立权限点 aig:agent:resource-binding（不能借「查看清单」的权限）")
    void addRequiresItsOwnPermission() throws Exception {
        assertPermission("addResourceBinding",
            new Class<?>[]{AigAgentResourceBindingBo.class},
            AigConstants.PERM_AGENT_RESOURCE_BINDING);

        Method method = AigAgentRegistryController.class.getMethod("addResourceBinding",
            AigAgentResourceBindingBo.class);
        assertNotNull(method.getAnnotation(RepeatSubmit.class),
            "新增绑定是写入口，必须带 @RepeatSubmit（否则网络重试会多绑一条）");
    }

    @Test
    @DisplayName("两个只读入口用清单权限；且新增的权限点与可见性绑定的不是同一个串")
    void readsUseListPermission() throws Exception {
        assertPermission("resourceBindingList",
            new Class<?>[]{AigAgentResourceBindingQueryBo.class,
                org.dromara.common.mybatis.core.page.PageQuery.class},
            AigConstants.PERM_AGENT_LIST);
        assertPermission("declaredResources", new Class<?>[]{Long.class},
            AigConstants.PERM_AGENT_LIST);

        assertTrue(!AigConstants.PERM_AGENT_RESOURCE_BINDING.equals(AigConstants.PERM_AGENT_BINDING),
            "「能用哪些工具/知识」与「对谁能看见」是两件事，不该共用一个权限串");
    }

    private static void assertPermission(String method, Class<?>[] params, String expected)
        throws Exception {
        Method m = AigAgentRegistryController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何登录用户都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(expected),
            method + " 的权限点应为 " + expected + "，实际=" + values);
    }

}
