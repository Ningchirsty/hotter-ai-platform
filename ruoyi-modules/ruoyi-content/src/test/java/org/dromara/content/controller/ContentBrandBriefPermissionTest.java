package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.BrandBriefBo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 品牌 Brief 接口权限码的回归钉（内测 C1 · 职责分离）。
 *
 * <p><b>钉的是哪一类错</b>：不报错、但职责边界实际不存在的错。C1 之前三个接口都只要
 * {@code content:task:edit}，而该权限在设计账号手里（{@code dp_creative_menu.sql} 把整个
 * 1767 段视觉工厂菜单授予了内容角色）。于是"设计部能自己写一版品牌要求、再自己批一版"
 * 在权限层完全成立，审计里 {@code confirmed_by} 记的还是设计师——真机实测过。</p>
 *
 * <p><b>为什么用反射而不是起 Spring 上下文</b>：这里要钉的是"注解上写的是哪个权限码"，
 * 这是权限判定的唯一入口，且改动成本极低；起上下文会把这个断言淹没在环境依赖里
 * （本机就曾因构建产物问题导致登录接口直接 500）。</p>
 *
 * <p><b>为什么连字符串字面量一起钉</b>：权限码是代码与菜单 SQL 之间的契约——
 * {@code script/sql/cp_content_brief_perm.sql} 里的 {@code perms} 必须与这里逐字一致，
 * 只比较常量名会把"常量改了、SQL 没改"这种最危险的偏差漏掉（那会让品牌部整片按钮消失）。</p>
 *
 * @author content
 */
class ContentBrandBriefPermissionTest {

    /** 权限码的字面量：与 cp_content_brief_perm.sql 的 perms 列必须逐字一致 */
    private static final String BRIEF_EDIT_LITERAL = "content:brief:edit";
    private static final String BRIEF_CONFIRM_LITERAL = "content:brief:confirm";

    private static String permOf(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = ContentBrandBriefController.class.getMethod(methodName, paramTypes);
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation,
            methodName + " 必须显式声明 @SaCheckPermission，否则任何登录用户都能调用");
        String[] values = annotation.value();
        assertEquals(1, values.length, methodName + " 只应声明一个权限码");
        return values[0];
    }

    @Test
    @DisplayName("常量本身是 content:brief:edit / content:brief:confirm")
    void constantsMatchMenuSql() {
        assertEquals(BRIEF_EDIT_LITERAL, ContentConstants.PERM_BRIEF_EDIT);
        assertEquals(BRIEF_CONFIRM_LITERAL, ContentConstants.PERM_BRIEF_CONFIRM);
        assertNotEquals(ContentConstants.PERM_BRIEF_EDIT, ContentConstants.PERM_BRIEF_CONFIRM);
    }

    @Test
    @DisplayName("保存 Brief 用 content:brief:edit，且不是 content:task:edit")
    void saveUsesBriefEdit() throws NoSuchMethodException {
        String perm = permOf("saveBrandBrief", Long.class, BrandBriefBo.class);
        assertEquals(BRIEF_EDIT_LITERAL, perm);
        // 这一条是 C1 的核心：content:task:edit 的持有者包含设计账号
        assertFalse("content:task:edit".equals(perm),
            "保存 Brief 不能再用 content:task:edit——那是设计账号也持有的权限");
    }

    @Test
    @DisplayName("品牌方确认用 content:brief:confirm，且不是 content:task:edit")
    void confirmUsesBriefConfirm() throws NoSuchMethodException {
        String perm = permOf("confirmBrandBrief", Long.class);
        assertEquals(BRIEF_CONFIRM_LITERAL, perm);
        assertFalse("content:task:edit".equals(perm),
            "确认是品牌方的批准动作，不能与录入共用权限码（否则自己写自己批）");
    }

    @Test
    @DisplayName("读取 Brief 仍是 content:task:query（本次不改读权限）")
    void readKeepsTaskQuery() throws NoSuchMethodException {
        assertEquals(ContentConstants.PERM_TASK_QUERY, permOf("brandBrief", Long.class));
    }
}
