package org.dromara.hrtalent.domainservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.support.GrantSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人才可见范围 SQL 生成的组合契约单元测试（SPEC-P3 §11.1 / 设计文档 §21.14）。
 *
 * <p>回归的是三个曾经真实存在的缺陷：</p>
 * <ol>
 *     <li>多次 {@code apply} 拼 OR 分支被 MyBatis-Plus 用 {@code AND} 连接，生成 {@code AND OR (...)}
 *     与悬空 {@code AND )}；</li>
 *     <li>片段带 {@code {0}} 占位符时会渲染成属于源 wrapper 的
 *     {@code #{ew.paramNameValuePairs.MPGENVALn}}，被搬进别的 wrapper 后参数错位；</li>
 *     <li>带参数 {@code apply(sql, values...)} 误用 {@code ?} 会直接抛
 *     {@code MybatisPlusException: sql not contains "{0}"}。</li>
 * </ol>
 *
 * <p>被测方法不访问数据库、不读登录态，因此可直接 new 出领域服务做纯单元测试
 * （{@code ObjectProvider} 传 null，只有授权级别判定才会用到它）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentScopeDomainServiceTest {

    /**
     * 被测领域服务。
     */
    private TalentScopeDomainService service;

    @BeforeEach
    void setUp() {
        service = new TalentScopeDomainService(null);
    }

    /**
     * 构造普通招聘专员的可见范围条件（非超管、非集团、单部门、带一个用户授权主体）。
     *
     * @return 可见范围条件
     */
    private TalentScopeDomainService.ScopeCondition normalScope() {
        Set<GrantSubject> grants = new LinkedHashSet<>();
        grants.add(new GrantSubject(GrantSubject.TYPE_USER, 1L));
        Set<Long> deptIds = new LinkedHashSet<>(List.of(2L));
        return new TalentScopeDomainService.ScopeCondition(false, false, 1L, 2L, deptIds, deptIds, grants,
            Set.of("hr_recruiter"));
    }

    /**
     * 计算 SQL 中圆括号的净深度。
     *
     * @param sql SQL 文本
     * @return 净深度，0 表示括号闭合
     */
    private int parenthesisDepth(String sql) {
        int depth = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return depth;
    }

    /**
     * 校验 SQL 圆括号自始至终闭合（不出现负深度、最终为 0）。
     *
     * @param sql SQL 文本
     * @return 是否合法
     */
    private boolean parenthesisBalanced(String sql) {
        int depth = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    @Test
    @DisplayName("普通用户的可见范围片段不再出现 AND OR / 悬空括号，且括号闭合")
    void shouldNotProduceMalformedOrGrouping() {
        String sql = service.visibleTalentWrapper(normalScope()).getCustomSqlSegment();

        assertFalse(sql.contains("AND OR"), "MyBatis-Plus 用 AND 连接多次 apply 会造成 AND OR 语法错误：" + sql);
        assertFalse(sql.contains("AND ( )"), "不应出现空括号：" + sql);
        assertFalse(sql.toLowerCase().contains("and )"), "不应出现悬空 AND )：" + sql);
        assertTrue(parenthesisBalanced(sql), "括号必须闭合且不出现负深度：" + sql);
        assertEquals(0, parenthesisDepth(sql), "括号净深度必须为 0：" + sql);
    }

    @Test
    @DisplayName("普通用户的可见范围片段把取值全部内联，不携带任何占位符参数")
    void shouldInlineAllLiteralsWithoutPlaceholders() {
        QueryWrapper<TalentProfile> wrapper = service.visibleTalentWrapper(normalScope());
        String sql = wrapper.getCustomSqlSegment();

        assertFalse(sql.contains("#{"), "片段不得含 #{...} 占位符：" + sql);
        assertFalse(sql.contains("?"), "片段不得含 ? 占位符：" + sql);
        assertFalse(sql.contains("{0}"), "片段不得含 {0} 占位符：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().isEmpty(),
            "取值全部内联后不应再绑定任何参数：" + wrapper.getParamNameValuePairs());
    }

    @Test
    @DisplayName("普通用户的可见范围片段包含前置谓词与全部五个可见性分支")
    void shouldContainAllVisibilityBranches() {
        String sql = service.visibleTalentWrapper(normalScope()).getCustomSqlSegment();

        assertTrue(sql.contains("p.del_flag = '0'"), sql);
        assertTrue(sql.contains("p.talent_status <> 'merged'"), sql);
        assertTrue(sql.contains("p.visibility_type = 'group'"), "缺少集团共享分支：" + sql);
        assertTrue(sql.contains("p.visibility_type = 'company'"), "缺少归属公司分支：" + sql);
        assertTrue(sql.contains("p.visibility_type = 'department'"), "缺少归属部门分支：" + sql);
        assertTrue(sql.contains("p.owner_id = 1"), "缺少仅负责人分支：" + sql);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM hr_talent_scope_grant g"), "缺少显式授权分支：" + sql);
        assertTrue(sql.contains("p.owner_dept_id IN (2)"), "部门ID应内联为字面量：" + sql);
        assertTrue(sql.contains("p.visibility_type = 'group' OR (p.visibility_type = 'company'"),
            "OR 分支之间必须用 OR 连接：" + sql);
    }

    @Test
    @DisplayName("可见范围片段可直接拼进只读 Mapper 的 ${ew.customSqlSegment} 查询")
    void shouldComposeIntoMapperSql() {
        String segment = service.visibleTalentWrapper(normalScope()).getCustomSqlSegment();
        String mapperSql = "SELECT p.talent_id FROM hr_talent_profile p " + segment;

        assertTrue(mapperSql.startsWith("SELECT p.talent_id FROM hr_talent_profile p WHERE "),
            "片段自带 WHERE，恰好供 ${ew.customSqlSegment} 使用：" + mapperSql);
        // 回归点：绝不能出现「片段搬家」造成的 WHERE 二次拼接
        assertFalse(mapperSql.toUpperCase().contains("AND WHERE"), "不得出现 AND WHERE：" + mapperSql);
        assertFalse(mapperSql.contains("WHERE WHERE"), "不得出现重复 WHERE：" + mapperSql);
        assertTrue(parenthesisBalanced(mapperSql), "拼接后的 SQL 括号必须闭合：" + mapperSql);
    }

    @Test
    @DisplayName("空范围返回恒假条件，调用方按空结果处理")
    void shouldReturnAlwaysFalseWhenScopeEmpty() {
        TalentScopeDomainService.ScopeCondition empty = new TalentScopeDomainService.ScopeCondition(
            false, false, null, null, Set.of(), Set.of(), Set.of(), Set.of());
        String sql = service.visibleTalentWrapper(empty).getCustomSqlSegment();

        assertTrue(sql.contains("1 = 0"), sql);
        assertFalse(sql.contains("p.visibility_type"), "空范围不应产出可见性分支：" + sql);
    }

    @Test
    @DisplayName("null 范围同样返回恒假条件")
    void shouldReturnAlwaysFalseWhenScopeNull() {
        String sql = service.visibleTalentWrapper(null).getCustomSqlSegment();
        assertTrue(sql.contains("1 = 0"), sql);
    }

    @Test
    @DisplayName("超级管理员不追加任何可见性分支")
    void shouldNotAppendBranchesForSuperAdmin() {
        Set<Long> deptIds = new LinkedHashSet<>(List.of(2L));
        TalentScopeDomainService.ScopeCondition superAdmin = new TalentScopeDomainService.ScopeCondition(
            true, true, 1L, 2L, deptIds, deptIds, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 1L)), Set.of());
        String sql = service.visibleTalentWrapper(superAdmin).getCustomSqlSegment();

        assertFalse(sql.contains("p.visibility_type"), sql);
        assertTrue(sql.contains("p.del_flag = '0'"), sql);
        assertTrue(sql.contains("p.talent_status <> 'merged'"), sql);
    }

    @Test
    @DisplayName("集团级管理员（unlimitedCompany）同样不追加可见性分支")
    void shouldNotAppendBranchesForGroupAdmin() {
        Set<Long> deptIds = new LinkedHashSet<>(List.of(2L));
        TalentScopeDomainService.ScopeCondition groupAdmin = new TalentScopeDomainService.ScopeCondition(
            false, true, 1L, 2L, deptIds, deptIds, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 1L)),
            Set.of("hr_recruit_admin_group"));
        String sql = service.visibleTalentWrapper(groupAdmin).getCustomSqlSegment();

        assertFalse(sql.contains("p.visibility_type"), sql);
        assertTrue(sql.contains("p.del_flag = '0'"), sql);
    }

    @Test
    @DisplayName("无授权主体时不产出 EXISTS 子查询；有多个主体时按类型+ID 成对展开")
    void shouldBuildGrantBranchByPairs() {
        Set<Long> deptIds = new LinkedHashSet<>(List.of(2L));
        TalentScopeDomainService.ScopeCondition noGrant = new TalentScopeDomainService.ScopeCondition(
            false, false, 1L, 2L, deptIds, deptIds, Set.of(), Set.of());
        assertFalse(service.visibleTalentWrapper(noGrant).getCustomSqlSegment()
            .contains("hr_talent_scope_grant"));

        Set<GrantSubject> grants = new LinkedHashSet<>();
        grants.add(new GrantSubject(GrantSubject.TYPE_USER, 11L));
        grants.add(new GrantSubject(GrantSubject.TYPE_ROLE, 22L));
        TalentScopeDomainService.ScopeCondition multi = new TalentScopeDomainService.ScopeCondition(
            false, false, 1L, 2L, deptIds, deptIds, grants, Set.of());
        String sql = service.visibleTalentWrapper(multi).getCustomSqlSegment();

        assertTrue(sql.contains("g.grantee_type = 'user' AND g.grantee_id = 11"), sql);
        assertTrue(sql.contains("g.grantee_type = 'role' AND g.grantee_id = 22"), sql);
        assertTrue(sql.contains(" OR "), sql);
    }

    @Test
    @DisplayName("单条可见性判定逻辑保持独立且可用（未被 SQL 改动影响）")
    void shouldKeepRowLevelVisibilityRuleIntact() {
        TalentScopeDomainService.ScopeCondition scope = normalScope();
        TalentScopeDomainService.TalentScopeTarget groupShared =
            new TalentScopeDomainService.TalentScopeTarget(9L, 999L, 999L, "group", "active", "0");
        TalentScopeDomainService.TalentScopeTarget otherDept =
            new TalentScopeDomainService.TalentScopeTarget(9L, 999L, 777L, "department", "active", "0");
        TalentScopeDomainService.TalentScopeTarget ownedByMe =
            new TalentScopeDomainService.TalentScopeTarget(9L, 1L, 777L, "owner", "active", "0");
        TalentScopeDomainService.TalentScopeTarget unknown =
            new TalentScopeDomainService.TalentScopeTarget(9L, 1L, 2L, null, "active", "0");

        assertTrue(service.visible(groupShared, scope));
        assertTrue(service.visible(ownedByMe, scope));
        assertFalse(service.visible(otherDept, scope));
        assertFalse(service.visible(unknown, scope), "未知可见性编码必须 fail-safe 拒绝");
        assertFalse(service.visible(null, scope));
        assertNotNull(scope);
    }

}
