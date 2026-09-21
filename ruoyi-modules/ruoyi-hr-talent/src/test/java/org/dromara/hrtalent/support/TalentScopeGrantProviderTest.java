package org.dromara.hrtalent.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.mapper.TalentScopeGrantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TalentScopeGrantProviderImpl} 单元测试（SPEC-P4 §2.4 必做项）。
 *
 * <p><b>覆盖重点</b>（授权有效性是 P3「显式授权」可见性分支与
 * {@code checkPermissionLevel} 能否生效的前提）：</p>
 * <ol>
 *     <li>未过期授权命中（含级别恰好等于、以及高于要求级别）；</li>
 *     <li>已过期（{@code valid_to <= now}）授权不命中；</li>
 *     <li>{@code valid_to} 为空视为长期有效（命中），{@code valid_from} 为空视为立即生效；</li>
 *     <li>{@code del_flag='1'} 与已撤销 {@code revoke_flag='1'} 不命中；</li>
 *     <li>权限级别不足不命中（summary 不能当 detail / attachment 用）；</li>
 *     <li><b>主体类型不同但 ID 相同不得互相命中</b>（role#5 不命中 user#5 的授权）；</li>
 *     <li><b>人才ID 不同不得命中</b>（A 人才的授权不得用来判定 B 人才可见，防跨人才越权）；</li>
 *     <li>未生效（{@code valid_from > now}）不命中；查询异常按拒绝处理。</li>
 * </ol>
 *
 * <p><b>替身说明</b>：不使用 Mockito（本机 JVM 禁止其自附加 Agent），
 * 用 JDK 动态代理手工实现 {@link TalentScopeGrantMapper}。替身把被测实现构造的
 * {@link QueryWrapper} <b>按条件语义求值</b>——真正按「人才ID、del_flag、revoke_flag、
 * 有效期、主体类型+ID 成对」过滤内存数据，并模拟 {@code @TableLogic} 的 {@code del_flag='0'}。
 * 因此一旦实现把这些条件写错（尤其是拆散主体对，退化成 {@code type OR id}），测试必然失败。</p>
 *
 * <p><b>不依赖 Spring 容器与 LoginHelper</b>：被测方法是可传入 {@code now} 与主体集合的纯逻辑，
 * 因此过期 / 级别不足 / 类型不匹配等边界用例都能真实执行。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentScopeGrantProviderTest {

    /**
     * 时间基准：2026-01-01 00:00:00。
     */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    /**
     * 匹配参数占位符。
     */
    private static final Pattern PARAM_REF = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    /**
     * 内存中的授权数据。
     */
    private final List<TalentScopeGrant> store = new ArrayList<>();

    /**
     * 被测实现。
     */
    private TalentScopeGrantProviderImpl provider;

    @BeforeEach
    void setUp() {
        store.clear();
        provider = new TalentScopeGrantProviderImpl(mapperStub());
    }

    /* ------------------------------------------------------------------ 1. 有效授权命中 ------------------------------------------------------------------ */

    @Test
    @DisplayName("未过期授权命中：级别恰好等于要求级别即通过")
    void shouldHitWhenGrantActiveAndLevelSufficient() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.SUMMARY,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertTrue(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("未过期授权命中：级别高于要求级别（detail 满足 summary）")
    void shouldHitWhenGrantLevelHigherThanRequired() {
        store.add(grant(1L, GrantSubject.TYPE_ROLE, 7L, TalentPermissionLevelEnum.DETAIL,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertTrue(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_ROLE, 7L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("多主体成对匹配：命中其中一个主体即通过，未命中主体不影响")
    void shouldHitWhenAnySubjectPairMatches() {
        store.add(grant(1L, GrantSubject.TYPE_ROLE, 9L, TalentPermissionLevelEnum.DETAIL, null, null, "0", "0"));

        Set<GrantSubject> subjects = new LinkedHashSet<>();
        subjects.add(new GrantSubject(GrantSubject.TYPE_USER, 5L));
        subjects.add(new GrantSubject(GrantSubject.TYPE_ROLE, 9L));
        subjects.add(new GrantSubject(GrantSubject.TYPE_DEPT, 3L));

        assertTrue(provider.hasActiveGrant(1L, subjects, TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 2. 已过期 / 未生效不命中 ------------------------------------------------------------------ */

    @Test
    @DisplayName("已过期授权不命中（valid_to < now）")
    void shouldMissWhenGrantExpired() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(10), NOW.minusSeconds(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("valid_to 恰好等于 now 视为已过期（严格大于才算有效）")
    void shouldMissWhenValidToEqualsNow() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.DETAIL,
            NOW.minusDays(1), NOW, "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("未生效授权不命中（valid_from > now）")
    void shouldMissWhenGrantNotStarted() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.DETAIL,
            NOW.plusDays(1), NOW.plusDays(10), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 3. 空值语义：长期有效 / 立即生效 ------------------------------------------------------------------ */

    @Test
    @DisplayName("valid_to 为空视为长期有效（命中）")
    void shouldHitWhenValidToIsNull() {
        store.add(grant(1L, GrantSubject.TYPE_COMPANY_DEPT, 3L, TalentPermissionLevelEnum.SUMMARY,
            NOW.minusDays(30), null, "0", "0"));

        assertTrue(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_COMPANY_DEPT, 3L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("valid_from 为空视为立即生效（命中）")
    void shouldHitWhenValidFromIsNull() {
        store.add(grant(1L, GrantSubject.TYPE_DEPT, 3L, TalentPermissionLevelEnum.SUMMARY, null, null, "0", "0"));

        assertTrue(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_DEPT, 3L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 4. del_flag / revoke_flag ------------------------------------------------------------------ */

    @Test
    @DisplayName("del_flag='1' 的授权不命中")
    void shouldMissWhenDeleted() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "1", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("已撤销（revoke_flag='1'）的授权不命中")
    void shouldMissWhenRevoked() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "1"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 5. 权限级别不足 ------------------------------------------------------------------ */

    @Test
    @DisplayName("权限级别不足不命中：summary 不能当 detail / attachment 使用")
    void shouldMissWhenLevelInsufficient() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.SUMMARY,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));
        Set<GrantSubject> subjects = Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L));

        assertTrue(provider.hasActiveGrant(1L, subjects, TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, subjects, TalentPermissionLevelEnum.DETAIL, NOW));
        assertFalse(provider.hasActiveGrant(1L, subjects, TalentPermissionLevelEnum.ATTACHMENT, NOW));
    }

    @Test
    @DisplayName("未知授权级别编码按不满足处理（fail-safe）")
    void shouldMissWhenLevelCodeUnknown() {
        TalentScopeGrant unknown = grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.SUMMARY,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0");
        unknown.setPermissionLevel("full");
        store.add(unknown);

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 6. 主体类型 + ID 成对（防越权） ------------------------------------------------------------------ */

    @Test
    @DisplayName("主体类型不同但 ID 相同不得互相命中（role#5 不等于 user#5）")
    void shouldMissWhenSubjectTypeDiffersWithSameId() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_ROLE, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_DEPT, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_COMPANY_DEPT, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertTrue(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("同一主体的 ID 不同不得命中（role#5 不命中 role#6 的授权）")
    void shouldMissWhenSubjectIdDiffers() {
        store.add(grant(1L, GrantSubject.TYPE_ROLE, 6L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_ROLE, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("授权只对目标人才生效：他人人才ID 不命中（防跨人才越权）")
    void shouldMissWhenTalentDiffers() {
        store.add(grant(2L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 7. 边界与异常 ------------------------------------------------------------------ */

    @Test
    @DisplayName("空主体、空级别、空人才ID、空时间一律不命中")
    void shouldMissOnEmptyArguments() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(null, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(), TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, null, TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)), null, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, null));
    }

    @Test
    @DisplayName("类型或 ID 缺失的无效主体被忽略，不得放大为命中")
    void shouldIgnoreInvalidSubjects() {
        store.add(grant(1L, GrantSubject.TYPE_USER, 5L, TalentPermissionLevelEnum.ATTACHMENT,
            NOW.minusDays(1), NOW.plusDays(1), "0", "0"));

        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, null)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject(null, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
        assertFalse(provider.hasActiveGrant(1L, Set.of(new GrantSubject("", 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    @Test
    @DisplayName("查询异常按拒绝处理，不向外抛异常")
    void shouldDenyOnMapperException() {
        TalentScopeGrantMapper failing = (TalentScopeGrantMapper) Proxy.newProxyInstance(
            TalentScopeGrantMapper.class.getClassLoader(),
            new Class<?>[]{TalentScopeGrantMapper.class},
            (proxy, method, args) -> {
                if ("selectList".equals(method.getName())) {
                    throw new IllegalStateException("模拟数据库不可用");
                }
                return defaultValue(method.getReturnType());
            });
        TalentScopeGrantProviderImpl failingProvider = new TalentScopeGrantProviderImpl(failing);

        assertFalse(failingProvider.hasActiveGrant(1L, Set.of(new GrantSubject(GrantSubject.TYPE_USER, 5L)),
            TalentPermissionLevelEnum.SUMMARY, NOW));
    }

    /* ------------------------------------------------------------------ 替身与夹具 ------------------------------------------------------------------ */

    /**
     * 构造授权 Mapper 的动态代理替身。
     *
     * <p>替身把被测实现构造的 {@link QueryWrapper} <b>按条件语义求值</b>：只提取并校验
     * 「人才ID / del_flag / revoke_flag / 有效期 / 主体（类型+ID 成对）」这几类条件
     * （不解析运算符细节，避免测试对 MyBatis-Plus 的 SQL 文本格式产生脆弱依赖），
     * 并模拟 {@code @TableLogic} 的 {@code del_flag='0'} 过滤。</p>
     *
     * @return Mapper 替身
     */
    @SuppressWarnings("unchecked")
    private TalentScopeGrantMapper mapperStub() {
        return (TalentScopeGrantMapper) Proxy.newProxyInstance(
            TalentScopeGrantMapper.class.getClassLoader(),
            new Class<?>[]{TalentScopeGrantMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList" -> selectByWrapper((QueryWrapper<TalentScopeGrant>) args[0]);
                case "toString" -> "TalentScopeGrantMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 按被测实现构造的条件语义筛选内存授权数据。
     *
     * @param wrapper 被测实现构造的条件
     * @return 命中的授权列
     */
    private List<TalentScopeGrant> selectByWrapper(QueryWrapper<TalentScopeGrant> wrapper) {
        String segment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        Set<String> subjectPairs = subjectPairs(segment, params);
        Object talentId = talentIdParam(segment, params);
        LocalDateTime validFrom = timeParam(segment, params, "valid_from");
        LocalDateTime validTo = timeParam(segment, params, "valid_to");
        boolean hasValidToNull = segment.contains("valid_to IS NULL");
        boolean hasValidFromNull = segment.contains("valid_from IS NULL");

        List<TalentScopeGrant> matched = new ArrayList<>();
        for (TalentScopeGrant grant : store) {
            // 模拟逻辑删除：@TableLogic 由框架自动追加，替身按实体的 del_flag 过滤
            if (!"0".equals(grant.getDelFlag())) {
                continue;
            }
            // 人才维度：写错（或漏写）即会跨人才命中
            if (talentId != null && !String.valueOf(talentId).equals(String.valueOf(grant.getTalentId()))) {
                continue;
            }
            // 撤销状态
            if (segment.contains("revoke_flag") && !"0".equals(grant.getRevokeFlag())) {
                continue;
            }
            // 有效期：数据库侧粗筛，精确判定由被测实现的 Java 逻辑完成
            if (validFrom != null && grant.getValidFrom() != null && grant.getValidFrom().isAfter(validFrom)) {
                continue;
            }
            if (validFrom != null && grant.getValidFrom() == null && !hasValidFromNull) {
                continue;
            }
            if (validTo != null) {
                boolean byRange = grant.getValidTo() != null && grant.getValidTo().isAfter(validTo);
                boolean byNull = hasValidToNull && grant.getValidTo() == null;
                if (!byRange && !byNull) {
                    continue;
                }
            }
            // 主体：类型 + ID 必须成对命中
            if (!subjectPairs.isEmpty()
                && !subjectPairs.contains(grant.getGranteeType() + ":" + grant.getGranteeId())) {
                continue;
            }
            matched.add(grant);
        }
        return matched;
    }

    /**
     * 从 SQL 片段与参数表中还原「主体类型 + ID」候选组合。
     *
     * <p>实现上兼容两种形态：{@code (t = ? AND i = ?)} 与 {@code (t = ?) ... (i = ?)}——
     * 后者是 MyBatis-Plus 对 nested 分组的展开方式。这里按「每个 grantee_type 与其后最近的
     * grantee_id 配对」，从而保证还原出的是<b>成对</b>主体，而不是把类型与 ID 混成两个集合。</p>
     *
     * @param segment SQL 片段
     * @param params  参数表
     * @return {@code type:id} 集合
     */
    private Set<String> subjectPairs(String segment, Map<String, Object> params) {
        List<String> types = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Matcher typeMatcher = Pattern.compile(
                "grantee_type\\s*=\\s*(?:'([^']*)'|#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)})")
            .matcher(segment);
        while (typeMatcher.find()) {
            types.add(typeMatcher.group(1) != null ? typeMatcher.group(1) : String.valueOf(params.get(typeMatcher.group(2))));
        }
        Matcher idMatcher = Pattern.compile(
                "grantee_id\\s*=\\s*(?:'(\\d+)'|#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)})")
            .matcher(segment);
        while (idMatcher.find()) {
            ids.add(idMatcher.group(1) != null ? idMatcher.group(1) : String.valueOf(params.get(idMatcher.group(2))));
        }
        Set<String> pairs = new LinkedHashSet<>();
        for (int i = 0; i < types.size() && i < ids.size(); i++) {
            pairs.add(types.get(i) + ":" + ids.get(i));
        }
        return pairs;
    }

    /**
     * 取 {@code talent_id} 条件绑定的参数值。
     *
     * @param segment SQL 片段
     * @param params  参数表
     * @return 人才ID参数，未找到返回 null
     */
    private Object talentIdParam(String segment, Map<String, Object> params) {
        int index = segment.indexOf("talent_id");
        if (index < 0) {
            return null;
        }
        Matcher matcher = PARAM_REF.matcher(segment.substring(index));
        return matcher.find() ? params.get(matcher.group(1)) : null;
    }

    /**
     * 取指定时间列条件绑定的参数值。
     *
     * @param segment SQL 片段
     * @param params  参数表
     * @param column  列名
     * @return 时间参数，未找到返回 null
     */
    private LocalDateTime timeParam(String segment, Map<String, Object> params, String column) {
        // 必须整列名匹配，避免 "valid_from" 里包含 "valid_to" 子串导致取错参数
        Matcher matcher = Pattern.compile(
                "\\b" + column + "\\s*(?:<=|>=|<>|!=|=|<|>)\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}")
            .matcher(segment);
        if (!matcher.find()) {
            return null;
        }
        Object value = params.get(matcher.group(1));
        return value instanceof LocalDateTime time ? time : null;
    }

    /**
     * 取返回类型的默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        return 0;
    }

    /**
     * 构造一条授权记录。
     *
     * @param talentId    人才ID
     * @param granteeType 主体类型
     * @param granteeId   主体ID
     * @param level       授权级别
     * @param validFrom   有效期起
     * @param validTo     有效期止
     * @param delFlag     逻辑删除标志
     * @param revokeFlag  撤销标志
     * @return 授权记录
     */
    private TalentScopeGrant grant(Long talentId, String granteeType, Long granteeId,
                                   TalentPermissionLevelEnum level, LocalDateTime validFrom,
                                   LocalDateTime validTo, String delFlag, String revokeFlag) {
        TalentScopeGrant grant = new TalentScopeGrant();
        grant.setGrantId((long) (store.size() + 1));
        grant.setTalentId(talentId);
        grant.setGranteeType(granteeType);
        grant.setGranteeId(granteeId);
        grant.setPermissionLevel(level.getCode());
        grant.setValidFrom(validFrom);
        grant.setValidTo(validTo);
        grant.setGrantReason("测试授权");
        grant.setGrantedBy(1L);
        grant.setGrantedTime(validFrom);
        grant.setRevokeFlag(revokeFlag);
        grant.setDelFlag(delFlag);
        return grant;
    }

}
