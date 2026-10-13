package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    @DisplayName("★ 删除绑定：用「新增绑定」同一个权限点，且**刻意不带** @RepeatSubmit")
    void removeRequiresBindingPermissionAndMustNotBeRepeatGuarded() throws Exception {
        assertPermission("removeResourceBinding", new Class<?>[]{Long.class},
            AigConstants.PERM_AGENT_RESOURCE_BINDING);

        Method method = AigAgentRegistryController.class.getMethod("removeResourceBinding", Long.class);
        assertNull(method.getAnnotation(RepeatSubmit.class),
            "删除绑定不能加 @RepeatSubmit。它存在的理由就是支撑「删掉绑错的那条、马上重建一条正确的」，"
                + "而防重键默认 5 秒、且只在第一次成功时保留 ⇒ 加了它就会把删完立刻重建挡掉，"
                + "等于把「范围改不了」这个坑换个地方又埋一次。"
                + "（按 id 的逻辑删除本身幂等：重复点第二次得到「不存在或已删除」。）");
    }

    @Test
    @DisplayName("★★ delFlag 必须是 @TableLogic：这是「删掉旧的那条再建」能成立的前提")
    void deleteIsLogicalSoSameScopeCanBeRebound() throws Exception {
        java.lang.reflect.Field delFlag = AigAgentResourceBinding.class.getDeclaredField("delFlag");
        assertNotNull(delFlag.getAnnotation(com.baomidou.mybatisplus.annotation.TableLogic.class),
            "@TableLogic 掉了，这条链就静默断掉：逻辑删除仍会写 del_flag，但查询不再自动过滤已删除的行 ⇒ "
                + "重复检查会看见刚被删掉的那条，于是服务层回「同一条绑定已存在」——"
                + "而拒绝文案却让人「删掉旧的那条再建」，用户会卡在一个死循环里。这个断言就是钉住那句话。");
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
