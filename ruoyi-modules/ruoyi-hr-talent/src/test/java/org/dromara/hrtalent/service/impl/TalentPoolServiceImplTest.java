package org.dromara.hrtalent.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolMemberBo;
import org.dromara.hrtalent.domain.entity.TalentPool;
import org.dromara.hrtalent.domain.entity.TalentPoolMember;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolMemberVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentPoolMemberStatusEnum;
import org.dromara.hrtalent.mapper.TalentPoolMapper;
import org.dromara.hrtalent.mapper.TalentPoolMemberMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.GrantSubject;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人才池领域规则单元测试（SPEC-P4 §2.3 C 线、§4 自检第 5 条）。
 *
 * <p>覆盖 C 线最容易回归的三条硬规则：</p>
 * <ol>
 *     <li><b>重复加入返回已有关系</b>（幂等，设计文档 §8.15）——不抛异常、不新增第二条关系；</li>
 *     <li><b>移出只结束成员关系，绝不删除人才主档</b>（设计文档 §8.15）；</li>
 *     <li><b>可见性与可维护性统一走领域服务</b>——不可见的人才池一律拒绝（设计文档 §11.1）。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper 与领域服务替身使用 JDK 动态代理/匿名子类手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * <p>测试刻意避开需要 Spring 容器的 {@code MapstructUtils} 分支（BO↔实体转换），
 * 只验证加入/移出/鉴权这些纯领域规则。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentPoolServiceImplTest {

    /**
     * 测试用人才池ID。
     */
    private static final Long POOL_ID = 1001L;

    /**
     * 测试用人才主档ID。
     */
    private static final Long TALENT_ID = 2001L;

    /**
     * 测试用成员关系ID。
     */
    private static final Long MEMBER_ID = 3001L;

    /**
     * 人才池内存库。
     */
    private final Map<Long, TalentPool> poolStore = new HashMap<>();

    /**
     * 人才池成员内存库。
     */
    private final Map<Long, TalentPoolMember> memberStore = new HashMap<>();

    /**
     * 是否发生过成员新增。
     */
    private boolean memberInserted;

    /**
     * 是否发生过成员更新。
     */
    private boolean memberUpdated;

    /**
     * 是否发生过成员物理删除（出现即为违反 §8.15）。
     */
    private boolean memberDeleted;

    /**
     * 是否发生过人才主档删除（出现即为违反 §8.15）。
     */
    private boolean talentDeleted;

    /**
     * 被测服务使用的替身可见范围判定结果。
     */
    private boolean scopeVisible = true;

    /**
     * 被测服务使用的替身集团级管理员标志。
     */
    private boolean groupLevelAdmin = true;

    /**
     * 被测服务使用的替身超级管理员标志。
     */
    private boolean superAdmin = true;

    /**
     * 被测服务。
     */
    private TalentPoolServiceImpl service;

    /**
     * 注册 MyBatis-Plus 实体元数据（Lambda 条件构造器需要 lambda 缓存）。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisTableInfoTestSupport.init(
            TalentPool.class, TalentPoolMember.class, TalentProfile.class);
    }

    @BeforeEach
    void setUp() {
        poolStore.clear();
        memberStore.clear();
        memberInserted = false;
        memberUpdated = false;
        memberDeleted = false;
        talentDeleted = false;
        scopeVisible = true;
        groupLevelAdmin = true;
        superAdmin = true;
        service = new TalentPoolServiceImpl(
            poolMapperStub(),
            memberMapperStub(),
            profileMapperStub(),
            profileServiceStub(),
            new ScopeStub(),
            new RecruitBusinessNoGenerator());
    }

    @Test
    @DisplayName("重复加入人才池返回已有关系且不新增第二条记录（幂等）")
    void shouldReturnExistingRelationWhenJoinTwice() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        memberStore.put(MEMBER_ID, member(TalentPoolMemberStatusEnum.ACTIVE.getCode()));

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(TALENT_ID);
        bo.setJoinReason("储备人才");
        TalentPoolMemberVo vo = service.addMember(POOL_ID, bo);

        assertNotNull(vo);
        assertEquals(MEMBER_ID, vo.getMemberId(), "重复加入必须返回已有成员关系");
        assertEquals(TALENT_ID, vo.getTalentId());
        assertFalse(memberInserted, "重复加入不得新增成员记录");
        assertEquals(1, memberStore.size());
    }

    @Test
    @DisplayName("历史已移出关系再次加入时恢复为在池而不是新增记录")
    void shouldReactivateRemovedRelation() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        memberStore.put(MEMBER_ID, member(TalentPoolMemberStatusEnum.REMOVED.getCode()));

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(TALENT_ID);
        service.addMember(POOL_ID, bo);

        assertTrue(memberUpdated, "已移出的关系应被恢复");
        assertFalse(memberInserted, "恢复关系不得新增记录");
        assertEquals(1, memberStore.size());
    }

    @Test
    @DisplayName("首次加入人才池记录加入人、加入时间与默认成员状态")
    void shouldRecordJoinInfoOnFirstJoin() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(TALENT_ID);
        bo.setRecommendedJob("高级后端工程师");
        bo.setJoinReason("面试通过但岗位暂缓");
        bo.setNextContactTime(LocalDateTime.now().plusDays(30));
        TalentPoolMemberVo vo = service.addMember(POOL_ID, bo);

        assertTrue(memberInserted);
        assertEquals(TalentPoolMemberStatusEnum.ACTIVE.getCode(), vo.getMemberStatus());
        assertNotNull(vo.getJoinedTime(), "加入时间必须由服务端记录");
        assertEquals(1, memberStore.size());
    }

    @Test
    @DisplayName("未知成员状态编码被拒绝")
    void shouldRejectUnknownMemberStatus() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(TALENT_ID);
        bo.setMemberStatus("waiting");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.addMember(POOL_ID, bo));
        assertEquals("未知的人才池成员状态：waiting", ex.getMessage());
    }

    @Test
    @DisplayName("人才不可见时拒绝加入人才池")
    void shouldRejectInvisibleTalent() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(9999L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.addMember(POOL_ID, bo));
        assertEquals("无权查看该人才", ex.getMessage());
        assertFalse(memberInserted);
    }

    @Test
    @DisplayName("人才池不可见时拒绝查看详情")
    void shouldRejectInvisiblePool() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        scopeVisible = false;

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getDetail(POOL_ID));
        assertEquals("无权查看该人才池", ex.getMessage());
    }

    @Test
    @DisplayName("移出人才池只结束成员关系，不删除人才主档与成员行")
    void shouldOnlyEndRelationWhenRemoveMember() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        memberStore.put(MEMBER_ID, member(TalentPoolMemberStatusEnum.ACTIVE.getCode()));

        service.removeMember(POOL_ID, MEMBER_ID, "不再关注");

        assertTrue(memberUpdated, "移出应更新成员状态");
        assertFalse(memberDeleted, "移出不得删除成员关系行");
        assertFalse(talentDeleted, "移出人才池绝不能删除人才主档");
        assertEquals(1, memberStore.size());
    }

    @Test
    @DisplayName("已移出的成员不允许重复移出")
    void shouldRejectRemoveRemovedMember() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        memberStore.put(MEMBER_ID, member(TalentPoolMemberStatusEnum.REMOVED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.removeMember(POOL_ID, MEMBER_ID, null));
        assertEquals("该成员已移出，无需重复操作", ex.getMessage());
    }

    @Test
    @DisplayName("非池管理员且非集团级管理员时拒绝维护人才池")
    void shouldRejectWriteForNonManager() {
        pool(TalentPoolMemberStatusEnum.ACTIVE.getCode());
        groupLevelAdmin = false;
        superAdmin = false;
        // 当前登录用户ID为 1，池管理员改为 99：既非管理员也非集团级管理员
        poolStore.get(POOL_ID).setManagerId(99L);

        TalentPoolMemberBo bo = new TalentPoolMemberBo();
        bo.setTalentId(TALENT_ID);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.addMember(POOL_ID, bo));
        assertTrue(ex.getMessage().contains("仅人才池管理员"));
    }

    @Test
    @DisplayName("人才池不存在时给出中文提示")
    void shouldRejectMissingPool() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getDetail(POOL_ID));
        assertEquals("人才池不存在", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造人才池实体并放入内存库。
     *
     * @param status 池状态
     */
    private void pool(String status) {
        TalentPool pool = new TalentPool();
        pool.setPoolId(POOL_ID);
        pool.setPoolCode("POOL20260921-001");
        pool.setPoolName("储备人才池");
        pool.setManagerId(1L);
        pool.setOwnerDeptId(10L);
        pool.setVisibilityType("group");
        pool.setStatus(status);
        pool.setMemberCount(1);
        pool.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        poolStore.put(POOL_ID, pool);
    }

    /**
     * 构造成员关系实体。
     *
     * @param status 成员状态
     * @return 成员关系
     */
    private TalentPoolMember member(String status) {
        TalentPoolMember member = new TalentPoolMember();
        member.setMemberId(MEMBER_ID);
        member.setPoolId(POOL_ID);
        member.setTalentId(TALENT_ID);
        member.setMemberStatus(status);
        member.setJoinedBy(1L);
        member.setJoinedTime(LocalDateTime.now().minusDays(1));
        member.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        return member;
    }

    /**
     * 构造人才池 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentPoolMapper poolMapperStub() {
        return (TalentPoolMapper) Proxy.newProxyInstance(
            TalentPoolMapper.class.getClassLoader(),
            new Class<?>[]{TalentPoolMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> poolStore.get((Long) args[0]);
                case "selectList" -> new ArrayList<>(poolStore.values());
                case "insert" -> {
                    TalentPool entity = (TalentPool) args[0];
                    entity.setPoolId(9001L);
                    poolStore.put(entity.getPoolId(), entity);
                    yield 1;
                }
                case "update" -> 1;
                case "updateById" -> 1;
                case "deleteById" -> {
                    poolStore.remove((Long) args[0]);
                    yield 1;
                }
                case "toString" -> "TalentPoolMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才池成员 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentPoolMemberMapper memberMapperStub() {
        return (TalentPoolMemberMapper) Proxy.newProxyInstance(
            TalentPoolMemberMapper.class.getClassLoader(),
            new Class<?>[]{TalentPoolMemberMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> memberStore.get((Long) args[0]);
                case "selectOne" -> memberStore.values().stream().findFirst().orElse(null);
                case "selectCount" -> (long) memberStore.size();
                case "insert" -> {
                    TalentPoolMember entity = (TalentPoolMember) args[0];
                    memberInserted = true;
                    entity.setMemberId(MEMBER_ID);
                    memberStore.put(MEMBER_ID, entity);
                    yield 1;
                }
                case "update" -> {
                    memberUpdated = true;
                    yield 1;
                }
                case "updateById" -> {
                    memberUpdated = true;
                    yield 1;
                }
                case "deleteById", "deleteByIds" -> {
                    memberDeleted = true;
                    yield 1;
                }
                case "toString" -> "TalentPoolMemberMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才主档 Mapper 替身（只读 + 可见ID解析）。
     *
     * @return Mapper 替身
     */
    private TalentProfileMapper profileMapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> profile();
                case "selectByIds" -> List.of(profile());
                case "selectVisibleTalentIds" -> List.of(TALENT_ID);
                case "deleteById", "deleteByIds" -> {
                    talentDeleted = true;
                    yield 1;
                }
                case "toString" -> "TalentProfileMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才主档服务替身：只放行测试人才，其余按「无权查看」拒绝。
     *
     * @return 服务替身
     */
    private ITalentProfileService profileServiceStub() {
        return (ITalentProfileService) Proxy.newProxyInstance(
            ITalentProfileService.class.getClassLoader(),
            new Class<?>[]{ITalentProfileService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "requireVisible" -> {
                    if (TALENT_ID.equals(args[0])) {
                        yield profile();
                    }
                    throw new ServiceException("无权查看该人才");
                }
                case "toString" -> "TalentProfileServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造测试用人才主档。
     *
     * @return 人才主档
     */
    private TalentProfile profile() {
        TalentProfile profile = new TalentProfile();
        profile.setTalentId(TALENT_ID);
        profile.setTalentNo("TAL20260921-001");
        profile.setName("张三");
        profile.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        return profile;
    }

    /**
     * 取返回类型默认值。
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
     * 可见范围领域服务替身：只暴露测试关心的三个方法，避免依赖登录态与 Spring 容器。
     *
     * @author hr-talent
     */
    private final class ScopeStub extends TalentScopeDomainService {

        /**
         * 构造替身（不注入授权扩展点）。
         */
        private ScopeStub() {
            super(null);
        }

        @Override
        public ScopeCondition currentScope() {
            Set<String> roleKeys = new LinkedHashSet<>();
            if (groupLevelAdmin) {
                roleKeys.add(HrTalentConstants.ROLE_TALENT_ADMIN_GROUP);
            }
            return new ScopeCondition(
                superAdmin,
                superAdmin,
                1L,
                10L,
                new LinkedHashSet<>(Set.of(10L)),
                new LinkedHashSet<>(Set.of(10L)),
                new LinkedHashSet<GrantSubject>(),
                roleKeys);
        }

        @Override
        public boolean visible(TalentScopeTarget target, ScopeCondition scope) {
            return scopeVisible;
        }

        @Override
        public boolean isGroupLevelAdmin() {
            return groupLevelAdmin;
        }
    }

}
