package org.dromara.hrtalent.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.TalentGroupBo;
import org.dromara.hrtalent.domain.entity.TalentGroup;
import org.dromara.hrtalent.domain.entity.TalentGroupMember;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupMemberVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.mapper.TalentGroupMapper;
import org.dromara.hrtalent.mapper.TalentGroupMemberMapper;
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
 * 人才分组领域规则单元测试（SPEC-P4 §2.3 C 线、设计文档 §8.15）。
 *
 * <p>覆盖两条关键口径：</p>
 * <ol>
 *     <li><b>个人收藏仅本人可见可维护</b>，且个人收藏不改变人才数据权限；</li>
 *     <li><b>公共分组由人才池管理员维护</b>，普通用户不得自行创建；</li>
 *     <li><b>重复加入按幂等处理</b>：{@code (group_id, talent_id)} 已存在时返回已有关系。</li>
 * </ol>
 *
 * <p><b>说明</b>：替身使用 JDK 动态代理/匿名子类构造，不依赖 Mockito；
 * 测试路径避开需要 Spring 容器的 {@code MapstructUtils} 分支。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentGroupServiceImplTest {

    /**
     * 测试用分组ID。
     */
    private static final Long GROUP_ID = 6001L;

    /**
     * 测试用人才主档ID。
     */
    private static final Long TALENT_ID = 2001L;

    /**
     * 测试用分组成员ID。
     */
    private static final Long MEMBER_ID = 7001L;

    /**
     * 分组内存库。
     */
    private final Map<Long, TalentGroup> groupStore = new HashMap<>();

    /**
     * 分组成员内存库。
     */
    private final Map<Long, TalentGroupMember> memberStore = new HashMap<>();

    /**
     * 是否发生过成员新增。
     */
    private boolean memberInserted;

    /**
     * 是否发生过人才主档删除（出现即违反 §8.15）。
     */
    private boolean talentDeleted;

    /**
     * 替身集团级管理员标志。
     */
    private boolean groupLevelAdmin;

    /**
     * 被测服务。
     */
    private TalentGroupServiceImpl service;

    /**
     * 注册 MyBatis-Plus 实体元数据（Lambda 条件构造器需要 lambda 缓存）。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisTableInfoTestSupport.init(
            TalentGroup.class, TalentGroupMember.class, TalentProfile.class);
    }

    @BeforeEach
    void setUp() {
        groupStore.clear();
        memberStore.clear();
        memberInserted = false;
        talentDeleted = false;
        groupLevelAdmin = false;
        service = new TalentGroupServiceImpl(
            groupMapperStub(),
            groupMemberMapperStub(),
            profileMapperStub(),
            profileServiceStub(),
            new ScopeStub(),
            new RecruitBusinessNoGenerator());
    }

    @Test
    @DisplayName("重复加入分组返回已有关系且不新增记录（幂等）")
    void shouldReturnExistingMemberWhenJoinTwice() {
        personalGroup(1L);
        memberStore.put(MEMBER_ID, member());

        TalentGroupMemberVo vo = service.addMember(GROUP_ID, TALENT_ID);

        assertNotNull(vo);
        assertEquals(MEMBER_ID, vo.getMemberId(), "重复加入必须返回已有成员关系");
        assertFalse(memberInserted, "重复加入不得新增成员记录");
    }

    @Test
    @DisplayName("首次加入分组写入成员关系")
    void shouldInsertMemberOnFirstJoin() {
        personalGroup(1L);

        TalentGroupMemberVo vo = service.addMember(GROUP_ID, TALENT_ID);

        assertTrue(memberInserted);
        assertNotNull(vo);
        assertEquals(TALENT_ID, vo.getTalentId());
    }

    @Test
    @DisplayName("个人收藏仅本人可维护")
    void shouldRejectForeignPersonalGroup() {
        personalGroup(99L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.addMember(GROUP_ID, TALENT_ID));
        assertEquals("个人收藏仅本人可维护", ex.getMessage());
        assertFalse(memberInserted);
    }

    @Test
    @DisplayName("移出分组成员不删除人才主档")
    void shouldNotDeleteTalentWhenRemoveMember() {
        personalGroup(1L);
        memberStore.put(MEMBER_ID, member());

        service.removeMember(GROUP_ID, MEMBER_ID);

        assertFalse(talentDeleted, "移出分组绝不能删除人才主档");
    }

    @Test
    @DisplayName("普通用户不得创建公共分组")
    void shouldRejectPublicGroupForOrdinaryUser() {
        TalentGroupBo bo = new TalentGroupBo();
        bo.setGroupName("重点候选人");
        bo.setGroupType("public");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("公共分组由人才池管理员维护，无权创建", ex.getMessage());
    }

    @Test
    @DisplayName("分组类型只接受 public 或 personal")
    void shouldRejectUnknownGroupType() {
        TalentGroupBo bo = new TalentGroupBo();
        bo.setGroupName("我的收藏");
        bo.setGroupType("shared");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("分组类型不合法，只能为 public（公共分组）或 personal（个人收藏）", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造个人收藏分组实体。
     *
     * @param ownerId 收藏人用户ID
     */
    private void personalGroup(Long ownerId) {
        TalentGroup group = new TalentGroup();
        group.setGroupId(GROUP_ID);
        group.setGroupCode("GRP20260921-001");
        group.setGroupName("我的收藏");
        group.setGroupType("personal");
        group.setOwnerId(ownerId);
        group.setOwnerDeptId(10L);
        group.setVisibilityType("owner");
        group.setStatus("active");
        group.setTalentCount(0);
        group.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        groupStore.put(GROUP_ID, group);
    }

    /**
     * 构造分组成员实体。
     *
     * @return 分组成员
     */
    private TalentGroupMember member() {
        TalentGroupMember member = new TalentGroupMember();
        member.setMemberId(MEMBER_ID);
        member.setGroupId(GROUP_ID);
        member.setTalentId(TALENT_ID);
        member.setAddedBy(1L);
        member.setAddedTime(LocalDateTime.now().minusDays(1));
        member.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        return member;
    }

    /**
     * 构造分组 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentGroupMapper groupMapperStub() {
        return (TalentGroupMapper) Proxy.newProxyInstance(
            TalentGroupMapper.class.getClassLoader(),
            new Class<?>[]{TalentGroupMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> groupStore.get((Long) args[0]);
                case "selectList" -> new ArrayList<>(groupStore.values());
                case "insert" -> {
                    TalentGroup entity = (TalentGroup) args[0];
                    entity.setGroupId(9001L);
                    groupStore.put(entity.getGroupId(), entity);
                    yield 1;
                }
                case "update" -> 1;
                case "updateById" -> 1;
                case "deleteById" -> {
                    groupStore.remove((Long) args[0]);
                    yield 1;
                }
                case "toString" -> "TalentGroupMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造分组成员 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentGroupMemberMapper groupMemberMapperStub() {
        return (TalentGroupMemberMapper) Proxy.newProxyInstance(
            TalentGroupMemberMapper.class.getClassLoader(),
            new Class<?>[]{TalentGroupMemberMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> memberStore.get((Long) args[0]);
                case "selectOne" -> memberStore.values().stream().findFirst().orElse(null);
                case "selectCount" -> (long) memberStore.size();
                case "insert" -> {
                    TalentGroupMember entity = (TalentGroupMember) args[0];
                    memberInserted = true;
                    entity.setMemberId(MEMBER_ID);
                    memberStore.put(MEMBER_ID, entity);
                    yield 1;
                }
                case "deleteById" -> {
                    memberStore.remove((Long) args[0]);
                    yield 1;
                }
                case "restoreMember" -> 1;
                case "toString" -> "TalentGroupMemberMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才主档 Mapper 替身。
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
     * 构造人才主档服务替身。
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
     * 可见范围领域服务替身（当前登录用户固定为 1）。
     *
     * @author hr-talent
     */
    private final class ScopeStub extends TalentScopeDomainService {

        /**
         * 构造替身。
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
                false,
                false,
                1L,
                10L,
                new LinkedHashSet<>(Set.of(10L)),
                new LinkedHashSet<>(Set.of(10L)),
                new LinkedHashSet<GrantSubject>(),
                roleKeys);
        }

        @Override
        public boolean visible(TalentScopeTarget target, ScopeCondition scope) {
            return true;
        }

        @Override
        public boolean isGroupLevelAdmin() {
            return groupLevelAdmin;
        }
    }

}
