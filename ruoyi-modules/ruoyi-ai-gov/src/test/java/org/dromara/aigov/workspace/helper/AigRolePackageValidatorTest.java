package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位包静态校验测试（增量 1b）。
 *
 * <p>岗位包是"给人配的"：配错了不会报错，只会在某天表现为"员工点了没反应"或"点进去空白"。
 * 所以这里逐条钉住"什么样算配错"——包括那些看起来只是"风格问题"的（比如轻量能力指向页面）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRolePackageValidatorTest {

    /** 场景版本都存在（除非用例自己指定） */
    private static final AigRolePackageLookup SCENARIO_OK = (code, version) -> true;

    private static AigRolePackageDraft.ActionDraft action(String code) {
        return new AigRolePackageDraft.ActionDraft(code, "DETAIL_PAGE", "生成产品详情页",
            "FORM", "QUICK_CAPABILITY", "text_generation", null, "brandId,projectId", true);
    }

    private static AigRolePackageDraft valid() {
        return new AigRolePackageDraft(
            "GRAPHIC_DESIGNER_AI",
            "平面设计 AI 工作台",
            "1.0.0",
            List.of(new AigRolePackageDraft.CategoryDraft("DETAIL_PAGE", "详情页设计")),
            "DETAIL_PAGE",
            List.of(action("WRITE_DETAIL_COPY")),
            "ASSIGNED_ORG",
            "INTERNAL",
            "RESTRICTED");
    }

    private static List<String> problemsOf(AigRolePackageDraft draft) {
        return AigRolePackageValidator.validate(draft, SCENARIO_OK);
    }

    private static void assertRejected(AigRolePackageDraft draft, String expectedFragment) {
        List<String> problems = problemsOf(draft);
        assertTrue(problems.stream().anyMatch(item -> item.contains(expectedFragment)),
            "应当因为「" + expectedFragment + "」被拒绝，实际问题=" + problems);
    }

    private static AigRolePackageDraft withActions(AigRolePackageDraft.ActionDraft... actions) {
        AigRolePackageDraft base = valid();
        return new AigRolePackageDraft(base.roleCode(), base.roleName(), base.version(),
            base.categories(), base.defaultCategory(), List.of(actions), base.audienceScope(),
            base.defaultDataLevel(), base.maxDataLevel());
    }

    @Test
    @DisplayName("★ 一份完整的岗位包：没有任何问题")
    void validPackageHasNoProblems() {
        assertTrue(problemsOf(valid()).isEmpty(), "实际=" + problemsOf(valid()));
    }

    @Test
    @DisplayName("身份字段：编码必须稳定可读、版本必须 SemVer")
    void identityIsChecked() {
        AigRolePackageDraft noCode = new AigRolePackageDraft("", "x", "1.0.0", valid().categories(),
            null, valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(noCode, "roleCode");

        AigRolePackageDraft lowerCode = new AigRolePackageDraft("graphic_designer", "x", "1.0.0",
            valid().categories(), null, valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(lowerCode, "岗位编码");

        AigRolePackageDraft badVersion = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "v1",
            valid().categories(), null, valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(badVersion, "SemVer");
    }

    @Test
    @DisplayName("分类：至少一个、编码唯一、默认分类必须在本包内")
    void categoriesAreChecked() {
        AigRolePackageDraft none = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            List.of(), null, valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(none, "至少需要一个分类");

        AigRolePackageDraft dup = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            List.of(new AigRolePackageDraft.CategoryDraft("DETAIL_PAGE", "a"),
                new AigRolePackageDraft.CategoryDraft("DETAIL_PAGE", "b")),
            null, valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(dup, "分类编码重复");

        AigRolePackageDraft wrongDefault = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            valid().categories(), "NOT_EXIST", valid().actions(), "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        assertRejected(wrongDefault, "默认分类");
    }

    @Test
    @DisplayName("卡片：至少一张、编码唯一、标题与分类必填、分类必须在本包内")
    void actionsBasicsAreChecked() {
        AigRolePackageDraft none = withActions();
        assertRejected(none, "至少需要一张能力卡片");

        assertRejected(withActions(action("WRITE_DETAIL_COPY"), action("WRITE_DETAIL_COPY")), "卡片编码重复");

        AigRolePackageDraft wrongCategory = withActions(new AigRolePackageDraft.ActionDraft(
            "WRITE_DETAIL_COPY", "IMAGE_EDIT", "t", "FORM", "QUICK_CAPABILITY", "text_generation",
            null, null, true));
        assertRejected(wrongCategory, "的分类 IMAGE_EDIT 不在本包分类里");

        AigRolePackageDraft noTitle = withActions(new AigRolePackageDraft.ActionDraft(
            "WRITE_DETAIL_COPY", "DETAIL_PAGE", " ", "FORM", "QUICK_CAPABILITY", "text_generation",
            null, null, true));
        assertRejected(noTitle, "缺少标题");
    }

    @Test
    @DisplayName("★ 启动方式/目标类型必须是封闭枚举里的值")
    void enumsAreClosed() {
        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("WRITE_DETAIL_COPY", "DETAIL_PAGE",
            "t", "TALK", "QUICK_CAPABILITY", "text_generation", null, null, true)), "启动方式非法");
        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("WRITE_DETAIL_COPY", "DETAIL_PAGE",
            "t", "FORM", "SOMETHING_ELSE", "text_generation", null, null, true)), "目标类型非法");
    }

    @Test
    @DisplayName("★ 纯导航：routeKey 必须在白名单里（不允许岗位包自己发明跳转目标）")
    void navigationRouteKeyMustBeWhitelisted() {
        AigRolePackageDraft ok = withActions(new AigRolePackageDraft.ActionDraft("HISTORY", "DETAIL_PAGE",
            "历史成果", "NAVIGATION", "NAVIGATION", "CREATIVE_PROJECT", null, null, true));
        assertTrue(problemsOf(ok).isEmpty(), "实际=" + problemsOf(ok));

        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("HISTORY", "DETAIL_PAGE",
            "历史成果", "NAVIGATION", "NAVIGATION", "https://evil.example.com", null, null, true)),
            "不在白名单里");
    }

    @Test
    @DisplayName("★ SCENARIO：引用形态必须对，且场景版本必须真的存在")
    void scenarioRefMustResolve() {
        AigRolePackageDraft ok = withActions(new AigRolePackageDraft.ActionDraft("CREATE_DETAIL_PAGE",
            "DETAIL_PAGE", "生成详情页", "STUDIO", "SCENARIO",
            "scenario://COMMERCE_DETAIL_PAGE@1.0.0", "CREATIVE_PROJECT", "brandId,projectId", true));
        assertTrue(problemsOf(ok).isEmpty(), "实际=" + problemsOf(ok));

        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("CREATE_DETAIL_PAGE", "DETAIL_PAGE",
            "t", "FORM", "SCENARIO", "COMMERCE_DETAIL_PAGE", null, null, true)), "SCENARIO 引用必须是");

        List<String> missing = AigRolePackageValidator.validate(
            withActions(new AigRolePackageDraft.ActionDraft("CREATE_DETAIL_PAGE", "DETAIL_PAGE",
                "t", "FORM", "SCENARIO", "scenario://NOPE@9.9.9", null, null, true)),
            (code, version) -> false);
        assertTrue(missing.stream().anyMatch(item -> item.contains("场景版本不存在")), "实际=" + missing);
    }

    @Test
    @DisplayName("★ STUDIO：必须给 studioRouteKey 且必须命中白名单")
    void studioNeedsWhitelistedRouteKey() {
        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("CREATE_DETAIL_PAGE", "DETAIL_PAGE",
            "t", "STUDIO", "QUICK_CAPABILITY", "text_generation", null, null, true)),
            "必须给 studioRouteKey");

        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("CREATE_DETAIL_PAGE", "DETAIL_PAGE",
            "t", "STUDIO", "QUICK_CAPABILITY", "text_generation", "NOT_A_KEY", null, true)),
            "studioRouteKey NOT_A_KEY 不在白名单里");
    }

    @Test
    @DisplayName("★ 轻量能力不该指向页面（那是 NAVIGATION 的语义，混在一起会让启动方式失去意义）")
    void quickCapabilityMustNotBeARouteKey() {
        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("QUICK_COPY", "DETAIL_PAGE",
            "文案润色", "QUICK", "QUICK_CAPABILITY", "CREATIVE_PROJECT", null, null, true)),
            "别把两种语义混在同一张卡片上");
    }

    @Test
    @DisplayName("★ 上下文键必须来自白名单（不允许包要求约定外的字段，更不能借它覆盖身份）")
    void requiredContextIsWhitelisted() {
        AigRolePackageDraft ok = withActions(new AigRolePackageDraft.ActionDraft("WRITE_DETAIL_COPY",
            "DETAIL_PAGE", "t", "FORM", "QUICK_CAPABILITY", "text_generation", null,
            "brandId, projectId ,productId", true));
        assertTrue(problemsOf(ok).isEmpty(), "实际=" + problemsOf(ok));

        assertRejected(withActions(new AigRolePackageDraft.ActionDraft("WRITE_DETAIL_COPY",
            "DETAIL_PAGE", "t", "FORM", "QUICK_CAPABILITY", "text_generation", null,
            "brandId,userId", true)), "userId 不在白名单里");
    }

    @Test
    @DisplayName("★ 数据等级只能收紧：默认等级比最高等级还宽 → 自相矛盾的声明，当场拒绝")
    void dataLevelCanOnlyTighten() {
        AigRolePackageDraft contradictory = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            valid().categories(), "DETAIL_PAGE", valid().actions(), "ASSIGNED_ORG", "STRICT", "INTERNAL");
        assertRejected(contradictory, "只能收紧");

        AigRolePackageDraft equal = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            valid().categories(), "DETAIL_PAGE", valid().actions(), "ASSIGNED_ORG", "INTERNAL", "INTERNAL");
        assertTrue(problemsOf(equal).isEmpty(), "相等是允许的（不是放宽）：" + problemsOf(equal));

        AigRolePackageDraft badLevel = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            valid().categories(), "DETAIL_PAGE", valid().actions(), "ASSIGNED_ORG", "SECRET", "INTERNAL");
        assertRejected(badLevel, "默认数据等级非法");

        AigRolePackageDraft noScope = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            valid().categories(), "DETAIL_PAGE", valid().actions(), "  ", "INTERNAL", "RESTRICTED");
        assertRejected(noScope, "audienceScope");
    }

    @Test
    @DisplayName("★ 一次列全：多错并发时全部列出，而不是只报第一个")
    void allProblemsAreListedAtOnce() {
        AigRolePackageDraft bad = new AigRolePackageDraft("bad-code", "", "v1",
            List.of(new AigRolePackageDraft.CategoryDraft("bad", "")),
            "NOPE",
            List.of(new AigRolePackageDraft.ActionDraft("x", "OTHER", "", "TALK", "WRONG",
                "scenario://NOPE@1.0.0", "NOT_A_KEY", "userId", true)),
            "", "STRICT", "PUBLIC");

        List<String> problems = problemsOf(bad);
        String joined = String.join("\n", problems);
        assertTrue(problems.size() >= 8, "应一次列全，实际 " + problems.size() + " 条：" + joined);
        for (String fragment : List.of("岗位编码", "roleName", "SemVer", "分类", "默认分类", "标题",
            "分类 OTHER", "启动方式非法", "目标类型非法", "userId", "audienceScope", "只能收紧")) {
            assertTrue(joined.contains(fragment), "缺少关于「" + fragment + "」的问题：" + joined);
        }
    }

    @Test
    @DisplayName("空包：直接报「岗位包为空」，不抛异常")
    void nullDraftIsRejected() {
        List<String> problems = AigRolePackageValidator.validate(null, SCENARIO_OK);
        assertTrue(problems.size() == 1 && problems.get(0).contains("为空"), "实际=" + problems);
    }

    @Test
    @DisplayName("列表字段为 null 时按空处理（不抛 NPE）")
    void nullListsAreTolerated() {
        AigRolePackageDraft draft = new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "x", "1.0.0",
            null, null, null, "ASSIGNED_ORG", "INTERNAL", "RESTRICTED");
        List<String> problems = new ArrayList<>(problemsOf(draft));
        assertTrue(problems.size() >= 2, "分类与卡片都缺，应各报一条：" + problems);
    }

}
