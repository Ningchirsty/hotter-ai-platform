package org.dromara.hrtalent.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagBo;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentProfileTag;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.domain.vo.talent.TalentTagVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.mapper.TalentProfileTagMapper;
import org.dromara.hrtalent.mapper.TalentTagMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.GrantSubject;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人才标签领域规则单元测试（SPEC-P4 §2.3 C 线）。
 *
 * <p>聚焦设计文档 §8.15 与 §7.6.4 明文要求的敏感标签约束：</p>
 * <ol>
 *     <li><b>禁止随意创建敏感或歧视性标签</b>：标签名称命中敏感/歧视词库一律拒绝；</li>
 *     <li><b>敏感标签受控</b>：{@code sensitive_flag = '1'} 只允许集团级人才管理员维护，
 *     普通用户在标签字典与人才标签列表中看不到敏感标签；</li>
 *     <li><b>背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签</b>：
 *     解析/系统来源的标签关系不得挂敏感标签。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper 与领域服务替身使用 JDK 动态代理/匿名子类手工构造，不依赖 Mockito。
 * 测试路径刻意避开需要 Spring 容器的 {@code MapstructUtils} 分支（实体→VO 转换），
 * 只验证校验与关系维护这类纯领域规则。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentTagServiceImplTest {

    /**
     * 测试用人才主档ID。
     */
    private static final Long TALENT_ID = 2001L;

    /**
     * 测试用标签ID。
     */
    private static final Long TAG_ID = 4001L;

    /**
     * 标签内存库。
     */
    private final List<TalentTag> tagStore = new ArrayList<>();

    /**
     * 标签关系内存库。
     */
    private final List<TalentProfileTag> relationStore = new ArrayList<>();

    /**
     * 是否发生过标签关系新增。
     */
    private boolean relationInserted;

    /**
     * 是否发生过已删除关系的恢复。
     */
    private boolean relationRestored;

    /**
     * 替身集团级管理员标志。
     */
    private boolean groupLevelAdmin;

    /**
     * 替身超级管理员标志。
     */
    private boolean superAdmin;

    /**
     * 被测服务。
     */
    private TalentTagServiceImpl service;

    @BeforeEach
    void setUp() {
        tagStore.clear();
        relationStore.clear();
        relationInserted = false;
        relationRestored = false;
        groupLevelAdmin = false;
        superAdmin = false;
        service = new TalentTagServiceImpl(
            tagMapperStub(),
            profileTagMapperStub(),
            profileServiceStub(),
            new ScopeStub(),
            new RecruitBusinessNoGenerator());
    }

    @Test
    @DisplayName("禁止创建「背调失败」类敏感标签")
    void shouldRejectBackgroundCheckFailureTag() {
        TalentTagBo bo = tag("背调失败", "other");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("禁止创建敏感或歧视性标签"), ex.getMessage());
    }

    @Test
    @DisplayName("禁止创建「年龄 35 岁以下」类歧视性标签")
    void shouldRejectAgeDiscriminatoryTag() {
        TalentTagBo bo = tag("年龄35岁以下", "other");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("禁止创建敏感或歧视性标签"), ex.getMessage());
    }

    @Test
    @DisplayName("禁止创建「已婚已育」类家庭状况标签")
    void shouldRejectFamilyStatusTag() {
        TalentTagBo bo = tag("已婚已育", "other");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("禁止创建敏感或歧视性标签"), ex.getMessage());
    }

    @Test
    @DisplayName("普通用户不得标记敏感标签")
    void shouldRejectSensitiveFlagForOrdinaryUser() {
        TalentTagBo bo = tag("长期出差", "experience");
        bo.setSensitiveFlag("1");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("敏感标签仅集团级人才管理员可维护"), ex.getMessage());
    }

    @Test
    @DisplayName("标签分类必须是 talent_tag_category 的稳定编码")
    void shouldRejectUnknownCategory() {
        TalentTagBo bo = tag("微服务", "tech");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("标签分类不合法"), ex.getMessage());
    }

    @Test
    @DisplayName("解析或系统来源不得挂敏感标签（§7.6.4）")
    void shouldRejectSensitiveTagFromAutoSource() {
        tagStore.add(sensitiveTag());
        TalentProfileTagBo bo = new TalentProfileTagBo();
        bo.setTagIds(new Long[]{TAG_ID});
        bo.setSourceType("resume");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateProfileTags(TALENT_ID, bo));
        assertTrue(ex.getMessage().contains("敏感标签不允许由解析或系统自动生成"), ex.getMessage());
        assertTrue(!relationInserted, "敏感标签不得被自动写入标签关系");
    }

    @Test
    @DisplayName("普通用户不得给人才挂敏感标签")
    void shouldRejectSensitiveTagForOrdinaryUser() {
        tagStore.add(sensitiveTag());
        TalentProfileTagBo bo = new TalentProfileTagBo();
        bo.setTagIds(new Long[]{TAG_ID});
        bo.setSourceType("manual");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateProfileTags(TALENT_ID, bo));
        assertTrue(ex.getMessage().contains("敏感标签仅集团级人才管理员可挂载"), ex.getMessage());
    }

    @Test
    @DisplayName("已停用标签不允许挂到人才档案")
    void shouldRejectDisabledTag() {
        TalentTag disabled = normalTag();
        disabled.setStatus("disabled");
        tagStore.add(disabled);
        TalentProfileTagBo bo = new TalentProfileTagBo();
        bo.setTagIds(new Long[]{TAG_ID});

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateProfileTags(TALENT_ID, bo));
        assertTrue(ex.getMessage().contains("标签已停用"), ex.getMessage());
    }

    @Test
    @DisplayName("人工挂载普通标签成功写入关系")
    void shouldAttachManualNormalTag() {
        tagStore.add(normalTag());
        TalentProfileTagBo bo = new TalentProfileTagBo();
        bo.setTagIds(new Long[]{TAG_ID});
        bo.setSourceType("manual");

        service.updateProfileTags(TALENT_ID, bo);

        assertTrue(relationInserted, "人工挂载普通标签应写入标签关系");
    }

    @Test
    @DisplayName("普通用户查询人才标签时敏感标签整体隐藏")
    void shouldHideSensitiveTagsFromOrdinaryUser() {
        tagStore.add(sensitiveTag());
        relationStore.add(relation());
        List<TalentTagVo> rows = service.listProfileTags(TALENT_ID);

        assertTrue(rows.isEmpty(), "敏感标签不得出现在普通用户的标签列表");
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造标签入参。
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类
     * @return 标签入参
     */
    private TalentTagBo tag(String tagName, String tagCategory) {
        TalentTagBo bo = new TalentTagBo();
        bo.setTagName(tagName);
        bo.setTagCategory(tagCategory);
        return bo;
    }

    /**
     * 构造敏感标签实体。
     *
     * @return 敏感标签
     */
    private TalentTag sensitiveTag() {
        TalentTag tag = normalTag();
        tag.setTagName("需要长期出差");
        tag.setSensitiveFlag("1");
        return tag;
    }

    /**
     * 构造普通标签实体。
     *
     * @return 普通标签
     */
    private TalentTag normalTag() {
        TalentTag tag = new TalentTag();
        tag.setTagId(TAG_ID);
        tag.setTagCode("TAG20260921-001");
        tag.setTagName("Java");
        tag.setTagCategory("skill");
        tag.setSensitiveFlag("0");
        tag.setSortNo(0);
        tag.setStatus("active");
        tag.setDelFlag(HrTalentConstants.DEL_FLAG_NORMAL);
        return tag;
    }

    /**
     * 构造标签字典 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentTagMapper tagMapperStub() {
        return (TalentTagMapper) Proxy.newProxyInstance(
            TalentTagMapper.class.getClassLoader(),
            new Class<?>[]{TalentTagMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> tagStore.stream()
                    .filter(t -> t.getTagId().equals(args[0]))
                    .findFirst().orElse(null);
                case "selectByIds" -> new ArrayList<>(tagStore);
                case "insert" -> {
                    tagStore.add((TalentTag) args[0]);
                    yield 1;
                }
                case "updateById" -> 1;
                case "deleteById" -> 1;
                case "toString" -> "TalentTagMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才标签关系 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentProfileTagMapper profileTagMapperStub() {
        return (TalentProfileTagMapper) Proxy.newProxyInstance(
            TalentProfileTagMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileTagMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList" -> new ArrayList<>(relationStore);
                case "insert" -> {
                    relationInserted = true;
                    yield 1;
                }
                case "delete" -> 1;
                case "restoreRelation" -> {
                    relationRestored = true;
                    yield 1;
                }
                case "toString" -> "TalentProfileTagMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造标签关系实体。
     *
     * @return 标签关系
     */
    private TalentProfileTag relation() {
        TalentProfileTag relation = new TalentProfileTag();
        relation.setRelId(5001L);
        relation.setTalentId(TALENT_ID);
        relation.setTagId(TAG_ID);
        relation.setSourceType("manual");
        return relation;
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
                        TalentProfile profile = new TalentProfile();
                        profile.setTalentId(TALENT_ID);
                        yield profile;
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
     * 可见范围领域服务替身。
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
        public boolean isGroupLevelAdmin() {
            return groupLevelAdmin;
        }
    }

}
