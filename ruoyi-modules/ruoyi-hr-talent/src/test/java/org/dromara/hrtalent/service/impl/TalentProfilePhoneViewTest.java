package org.dromara.hrtalent.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 人才档案电话明文查看（{@code POST /talent/profiles/{id}/phone-view}）单元测试。
 *
 * <p>覆盖 SPEC-P4 前端反馈的缺失能力：{@code talent:profile:phone-view} 权限此前<b>没有对应端点</b>，
 * 候选人侧的 {@code phone-view} 又要求该人才必须有应聘记录，导致无应聘记录的人才看不了电话明文。</p>
 *
 * <p><b>断言要点</b>：</p>
 * <ul>
 *     <li>用途为空 → 拒绝并写 {@code denied} 审计，且不触发任何人才查询；</li>
 *     <li><b>无应聘记录的人才也能查看</b>：服务<b>不会</b>访问 {@code hr_recruit_application}
 *     （替身一旦被调用即抛错，从而证明本路径与应聘记录无关）；</li>
 *     <li>人才不可见 → 写 {@code denied} 审计后拒绝；</li>
 *     <li>成功路径 → 先鉴权再写 {@code success} 审计，最后返回明文。</li>
 * </ul>
 *
 * <p>明文解密由 {@code @EncryptField} 的 MyBatis 出参拦截器完成，纯单测里直接放入「已解密」值模拟。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentProfilePhoneViewTest {

    /**
     * 固定人才主档ID。
     */
    private static final Long TALENT_ID = 2001L;

    /**
     * 捕获的审计事件（格式：eventType|bizType|bizId|result）。
     */
    private final List<String> audits = new ArrayList<>();

    /**
     * 人才可见性：非空时 {@code checkTalentVisible} 抛出该异常。
     */
    private ServiceException visibilityFailure;

    /**
     * 当前人才主档快照。
     */
    private TalentProfile profile;

    /**
     * 应聘记录 Mapper 是否被误访问（一旦访问即置位，用于证明无应聘记录也能查看）。
     */
    private boolean applicationMapperTouched;

    /**
     * 被测服务。
     */
    private TalentProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        audits.clear();
        visibilityFailure = null;
        applicationMapperTouched = false;
        profile = new TalentProfile();
        profile.setTalentId(TALENT_ID);
        profile.setName("张三");
        profile.setVersion(0);
        profile.setTalentStatus("active");
        profile.setVisibilityType("group");
        profile.setDelFlag("0");
        // @EncryptField 由 MyBatis 出参拦截器解密；单测里直接放入已解密值
        profile.setPhoneCipher("13800001234");
        service = new TalentProfileServiceImpl(profileMapperStub(), null, applicationMapperStub(),
            null, scopeServiceStub(), null, null, auditRecorderStub());
    }

    @Test
    @DisplayName("电话明文：用途为空时拒绝并写 denied 审计")
    void shouldRejectBlankPurpose() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.viewPhone(TALENT_ID, "   "));

        assertEquals("查看电话明文必须填写用途", ex.getMessage());
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_DENIED), audits);
    }

    @Test
    @DisplayName("电话明文：无应聘记录的人才也能查看（不访问应聘记录表）")
    void shouldAllowViewForTalentWithoutApplication() {
        String plain = service.viewPhone(TALENT_ID, "人才档案联系候选人");

        assertEquals("13800001234", plain);
        assertFalse(applicationMapperTouched, "本接口不得依赖应聘记录存在");
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_SUCCESS), audits);
    }

    @Test
    @DisplayName("电话明文：人才不可见时写 denied 审计并拒绝")
    void shouldDenyWhenTalentInvisible() {
        visibilityFailure = new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_002);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.viewPhone(TALENT_ID, "越权尝试"));

        assertEquals(HrTalentErrorCode.MSG_HR_TALENT_002, ex.getMessage());
        assertEquals(List.of(SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_DENIED), audits);
    }

    @Test
    @DisplayName("电话明文：未登记电话时仍写审计并返回 null")
    void shouldReturnNullWhenPhoneAbsent() {
        profile.setPhoneCipher(null);

        assertNull(service.viewPhone(TALENT_ID, "人才档案查看"));

        assertEquals(SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_SUCCESS, audits.get(0));
    }

    /* ------------------------------------------------------------------ 测试替身 ------------------------------------------------------------------ */

    /**
     * 人才主档 Mapper 动态代理替身。
     *
     * @return 替身
     */
    private TalentProfileMapper profileMapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> TALENT_ID.equals(args[0]) ? profile : null;
                case "toString" -> "TalentProfileMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 应聘记录 Mapper 替身：任何调用都失败，用于证明电话明文查看不依赖应聘记录。
     *
     * @return 替身
     */
    private RecruitApplicationMapper applicationMapperStub() {
        return (RecruitApplicationMapper) Proxy.newProxyInstance(
            RecruitApplicationMapper.class.getClassLoader(),
            new Class<?>[]{RecruitApplicationMapper.class},
            (proxy, method, args) -> {
                if ("toString".equals(method.getName())) {
                    return "RecruitApplicationMapperStub";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                applicationMapperTouched = true;
                throw new IllegalStateException("电话明文查看不应访问应聘记录表");
            });
    }

    /**
     * 人才可见范围领域服务替身：只覆盖资源级鉴权入口。
     *
     * @return 替身
     */
    private TalentScopeDomainService scopeServiceStub() {
        return new TalentScopeDomainService(null) {
            @Override
            public void checkTalentVisible(TalentScopeTarget target) {
                if (visibilityFailure != null) {
                    throw visibilityFailure;
                }
            }
        };
    }

    /**
     * 审计记录器替身：捕获事件类型、业务对象与结果。
     *
     * @return 替身
     */
    private SensitiveAuditRecorder auditRecorderStub() {
        return new SensitiveAuditRecorder(null) {
            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result) {
                audits.add(eventType + "|" + bizType + "|" + bizId + "|" + result);
            }

            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result,
                               String detailJson) {
                audits.add(eventType + "|" + bizType + "|" + bizId + "|" + result);
            }
        };
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

}
