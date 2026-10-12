package org.dromara.aigov.controller;

import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 治理层写入口的「防重复提交」覆盖率检查。
 *
 * <p><b>这条检查防的是什么</b>：{@code @RepeatSubmit} 是逐方法加的注解，漏一个不会有任何编译期
 * 或运行期信号——只会在生产上表现成"双击/网络重试多了一条账本行、多花了一次钱、或多推进了一次状态"。
 * 本仓已经踩过一次同类的坑：菜单权限行漏种（{@code aig:agent:resource-binding} 永远 403），
 * 也是"逐条声明、漏了没人知道"。所以这里用同一套办法：<b>把覆盖率做成构建期能红的检查</b>。</p>
 *
 * <p><b>它到底断言什么</b>：{@code org.dromara.aigov} 下每个 {@code @RestController} 的
 * <b>写方法</b>（{@code @PostMapping} / {@code @PutMapping} / {@code @DeleteMapping} /
 * {@code @PatchMapping}，或 {@code @RequestMapping} 且 method 含 POST/PUT/DELETE/PATCH）
 * <b>必须</b>带 {@code @RepeatSubmit}，<b>除非</b>它被显式列入 {@link #EXEMPT_WITH_REASON}
 * 并写清理由。</p>
 *
 * <p><b>为什么用"豁免清单 + 理由"而不是直接全加</b>：有些写入口已经自带更强的幂等
 * （如回调端点的签名 + 唯一键、保存草稿的内容哈希无变化守卫、只读式校验/探测）。
 * 给它们再套一层 {@code @RepeatSubmit} 不是"更安全"，而是会改变语义
 * （例如回调的重复投递本就该被识别并记录，而不是被防重键吞掉）。
 * 但"不加"必须是一个<b>被写下来的决定</b>，不能是"忘了"——这正是本检查存在的意义。</p>
 *
 * <p><b>⚠️ 一个容易误判的运行时语义（读 {@code RepeatSubmitAspect} 得来）</b>：防重键
 * <b>只在第一次请求成功（{@code R.code=200}）时保留</b>；第一次失败时 {@code AfterReturning}
 * 会删键，抛异常时 {@code AfterThrowing} 也删键（默认间隔 5000ms）。
 * 所以"连发两次**无效**请求看有没有重复提交提示"是<b>看不到</b>的——那个测试设计是错的。
 * 该注解防的是"成功之后又被点一次"，而这恰好是会造成重复账本/重复计费/重复推进的那种。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRepeatSubmitCoverageTest {

    /**
     * 扫描根包。
     */
    private static final String BASE_PACKAGE = "org.dromara.aigov";

    /**
     * 显式豁免的写入口：{@code 控制器简单类名#方法名} → 为什么它可以不防重。
     *
     * <p>加入这里的每个都必须是"已经用别的方式幂等"或"重复执行没有额外后果"，
     * 不能是"暂时没加"。</p>
     */
    private static final Map<String, String> EXEMPT_WITH_REASON = Map.ofEntries(
        Map.entry("AigTaskCallbackController#receive",
            "自带更强的幂等：入参签名 + (providerCode,eventId) 幂等键；加 @RepeatSubmit 会把"
                + "「重复投递」这种需要被识别与记录的情况直接吞掉，反而降低可观测性"),
        Map.entry("AigRolePackageController#update",
            "自带更强的乐观锁：覆盖草稿必须带 expectedManifestSha256，重复提交会被 CAS 判为"
                + "「草稿已被他人修改」而拒绝——比时间窗防重更精确"),
        Map.entry("AigStudioDraftController#update",
            "内容哈希守卫已保证语义幂等：内容没变就不产生新修订（同一条 no-change 判断）"),
        Map.entry("AigStudioDraftController#rollback",
            "目标态幂等：回滚到指定修订号，重复执行得到同一个目标态"),
        Map.entry("AigStudioDraftController#archive",
            "目标态幂等：归档是状态收敛（已归档再归档不产生新修订）"),
        Map.entry("AigUserBrandController#revoke",
            "软停用（不物理删除、可重新登记恢复），重复执行得到同一个终止态"),
        Map.entry("AigInvokeController#dryRun",
            "只做策略预演与路由推演，不落账本、不调模型，重复执行无额外后果"),
        Map.entry("AigModelController#testConnection",
            "连通性探测，无副作用（不写账本、不改治理状态），重复执行等价"),
        Map.entry("AigModelController#runHealthProbe",
            "健康巡检，重复执行等价（只更新健康观测字段）"),
        Map.entry("AigStudioDraftController#validate",
            "只读式预检（跑一遍校验规则），不写任何表"),
        Map.entry("AigRolePackageController#validate",
            "只读式校验入参，不落库"),
        Map.entry("AigRolePackageController#validateStored",
            "只读式校验已存版本，不落库"),
        Map.entry("AigPortalController#refreshMyAssets",
            "幂等重建当前用户的资产索引，重复执行等价"),
        Map.entry("AigPortalController#suggest",
            "只读式建议，无副作用")
    );

    @Test
    @DisplayName("★ 每个治理层写接口都必须带 @RepeatSubmit，或在豁免清单里写明理由"
        + "（漏了 = 双击/重试会多一条账本、多花一次钱、多推进一次状态）")
    void everyWriteEndpointIsProtectedOrExplicitlyExempt() {
        List<String> unprotected = new ArrayList<>();
        Set<String> allowList = new TreeSet<>();
        int scanned = 0;

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isWriteMethod(method)) {
                    continue;
                }
                scanned++;
                String key = controller.getSimpleName() + "#" + method.getName();
                if (method.getAnnotation(RepeatSubmit.class) != null) {
                    continue;
                }
                if (EXEMPT_WITH_REASON.containsKey(key)) {
                    allowList.add(key);
                    continue;
                }
                unprotected.add(key + "（" + describe(method) + "）");
            }
        }

        assertTrue(scanned > 0,
            "没扫到任何写方法：扫描逻辑失效了，这个守卫不能静默通过");
        assertTrue(unprotected.isEmpty(),
            "以下治理层写接口既没有 @RepeatSubmit、也不在豁免清单里：" + unprotected
                + "。@RepeatSubmit 是逐方法声明的，漏了不会有任何编译期/运行期信号，"
                + "只会在生产上表现为「双击多一条账本行 / 多花一次钱 / 多推进一次状态」。"
                + "请二选一：①加 @RepeatSubmit；②若它已用别的方式幂等（或重复执行没有额外后果），"
                + "把它加进 AigRepeatSubmitCoverageTest#EXEMPT_WITH_REASON 并写清理由。");
        assertTrue(!allowList.isEmpty(),
            "豁免清单里的方法一个都没匹配上：控制器被改名/删除了，条目会静默失效，请同步清理");
    }

    @Test
    @DisplayName("豁免清单没有过期条目（方法改名或删除后，这条会提醒你同步）")
    void exemptListHasNoStaleEntry() {
        Set<String> present = new TreeSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (isWriteMethod(method)) {
                    present.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        List<String> stale = EXEMPT_WITH_REASON.keySet().stream()
            .filter(k -> !present.contains(k))
            .sorted()
            .toList();
        assertTrue(stale.isEmpty(),
            "豁免清单里这些条目已经不存在于任何写接口上（方法改名/删除/已被加上注解）：" + stale
                + "。过期条目会让清单逐渐失真，请删除它们。");
    }

    /**
     * 是否为写方法（POST / PUT / DELETE / PATCH）。
     *
     * @param method 控制器方法
     * @return 是写方法则 true
     */
    private static boolean isWriteMethod(Method method) {
        if (method.getAnnotation(PostMapping.class) != null
            || method.getAnnotation(PutMapping.class) != null
            || method.getAnnotation(DeleteMapping.class) != null
            || method.getAnnotation(PatchMapping.class) != null) {
            return true;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (RequestMethod candidate : mapping.method()) {
            switch (candidate) {
                case POST, PUT, DELETE, PATCH -> {
                    return true;
                }
                default -> {
                    // 读方法，跳过
                }
            }
        }
        return false;
    }

    /**
     * 拼出人类可读的映射描述（用于报错定位）。
     *
     * @param method 控制器方法
     * @return 形如 {@code POST /run/record}
     */
    private static String describe(Method method) {
        List<String> parts = new ArrayList<>();
        PostMapping post = method.getAnnotation(PostMapping.class);
        PutMapping put = method.getAnnotation(PutMapping.class);
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (post != null) {
            parts.add("POST " + Arrays.toString(post.value()));
        } else if (put != null) {
            parts.add("PUT " + Arrays.toString(put.value()));
        } else if (delete != null) {
            parts.add("DELETE " + Arrays.toString(delete.value()));
        } else if (patch != null) {
            parts.add("PATCH " + Arrays.toString(patch.value()));
        } else if (mapping != null) {
            parts.add(Arrays.toString(mapping.method()) + " " + Arrays.toString(mapping.value()));
        }
        return String.join(" ", parts);
    }

    /**
     * 扫出 {@code org.dromara.aigov} 下所有带 {@code @RestController} 的类。
     *
     * @return 控制器类集合（按类名排序，保证报错稳定）
     */
    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> names = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            names.add(definition.getBeanClassName());
        }
        List<Class<?>> classes = new ArrayList<>();
        for (String name : names) {
            try {
                classes.add(Class.forName(name));
            } catch (ClassNotFoundException e) {
                fail("扫到的控制器类加载不了：" + name + "：" + e);
            }
        }
        if (classes.isEmpty()) {
            fail("在 " + BASE_PACKAGE + " 下没扫到任何 @RestController：扫描失效，守卫不能静默通过");
        }
        classes.sort((a, b) -> a.getName().compareTo(b.getName()));
        return classes;
    }

}
