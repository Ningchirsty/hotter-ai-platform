package org.dromara.hrtalent.service.impl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.talent.PhoneViewBo;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电话明文查看「被拒绝的敏感访问必须留痕」口径单元测试。
 *
 * <p><b>背景一（用途为空不留痕）</b>：{@code POST /recruit/candidates/{id}/phone-view} 早期在
 * {@code PhoneViewBo.purpose} 上加了 {@code @NotBlank} 并由控制器 {@code @Validated} 校验，
 * 参数校验框架先返回 400，服务层那条「purpose 为空 → 写 {@code denied} 审计后拒绝」的分支
 * <b>在 HTTP 路径上不可达</b>（违反 §15.2）。修复后用途的非空校验移到服务层。</p>
 *
 * <p><b>背景二（查询落空不留痕）</b>：候选人不存在（无应聘记录）与人才不存在 / 不可见时，
 * 服务层此前<b>直接抛错且不写审计</b>——一次被拒绝的电话明文访问往往就是越权探测，
 * 静默返回等于给探测开了不留痕的通道。修复后两类落空走<b>同一条分支</b>：
 * 写同一条 {@code denied} 审计，并抛同一个提示（有意不可区分，避免存在性探测旁路）。</p>
 *
 * <p>本测试锁定：① 入参不带 {@code @NotBlank}（链路可达性）；② 空用途留痕且不触碰业务数据；
 * ③ 落空留痕、两种落空不可区分；④ 审计写入真实失败（被记录器契约吞掉）不改变异常类型。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PhoneViewPurposeAuditTest {

    /**
     * 固定人才主档ID。
     */
    private static final Long TALENT_ID = 3001L;

    /**
     * 落空统一提示（与实现保持一致）。
     */
    private static final String MSG_NOT_FOUND = "候选人不存在或已删除";

    /**
     * 捕获的审计记录（格式：eventType|bizType|bizId|result）。
     */
    private final List<String> audits = new ArrayList<>();

    /**
     * 业务 Mapper 是否被误访问（一旦访问即置位）。
     */
    private boolean businessLookupTouched;

    /**
     * 应聘记录 Mapper 替身返回的记录数（0 表示候选人落空）。
     */
    private long applicationCount;

    /**
     * 人才主档服务替身：{@code getPhonePlain} 抛出的异常（模拟「人才不存在 / 不可见」）。
     */
    private ServiceException phonePlainFailure;

    /**
     * 被测候选人服务。
     */
    private CandidateServiceImpl candidateService;

    @BeforeEach
    void setUp() {
        audits.clear();
        businessLookupTouched = false;
        applicationCount = 0L;
        phonePlainFailure = null;
        candidateService = new CandidateServiceImpl(null, applicationMapperStub(), null,
            profileServiceStub(), null, null, auditRecorderStub());
    }

    /* ------------------------------------------------------------------ 入参口径 ------------------------------------------------------------------ */

    @Test
    @DisplayName("入参口径：purpose 不带 @NotBlank（避免校验框架拦截导致拒绝不留痕），但保留长度上限")
    void shouldKeepPurposeOutOfBeanValidation() throws Exception {
        Field purpose = PhoneViewBo.class.getDeclaredField("purpose");

        assertFalse(purpose.isAnnotationPresent(NotBlank.class),
            "purpose 不得使用 @NotBlank：参数校验先行拦截会让服务层的 denied 审计永远不可达");
        Size size = purpose.getAnnotation(Size.class);
        assertNotNull(size, "长度上限应保留");
        assertEquals(255, size.max());
    }

    /* ------------------------------------------------------------------ 用途为空 ------------------------------------------------------------------ */

    @Test
    @DisplayName("候选人侧：purpose 为空时先写 denied 审计再拒绝，且不做任何业务查询")
    void shouldAuditDeniedForBlankPurpose() {
        CandidateServiceImpl service = new CandidateServiceImpl(null, throwingApplicationMapper(), null,
            null, null, null, auditRecorderStub());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.viewPhone(TALENT_ID, "   "));

        assertEquals("查看电话明文必须填写用途", ex.getMessage());
        assertEquals(List.of(blankPurposeRecord()), audits, "被拒绝的敏感访问必须留痕");
        assertFalse(businessLookupTouched, "用途为空应在任何业务查询之前拒绝");
    }

    @Test
    @DisplayName("候选人侧：purpose 为 null 时同样写 denied 审计")
    void shouldAuditDeniedForNullPurpose() {
        CandidateServiceImpl service = new CandidateServiceImpl(null, throwingApplicationMapper(), null,
            null, null, null, auditRecorderStub());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.viewPhone(TALENT_ID, null));

        assertEquals("查看电话明文必须填写用途", ex.getMessage());
        assertEquals(List.of(blankPurposeRecord()), audits);
    }

    /* ------------------------------------------------------------------ 查询落空（本次新增口径） ------------------------------------------------------------------ */

    @Test
    @DisplayName("查询落空：无应聘记录时先写 denied 审计（reason=no_application），再抛既有提示")
    void shouldAuditDeniedWhenCandidateMissing() {
        applicationCount = 0L;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> candidateService.viewPhone(TALENT_ID, "人才库回访"));

        assertEquals(MSG_NOT_FOUND, ex.getMessage());
        assertEquals(List.of(deniedRecord(CandidateServiceImpl.DENY_REASON_NO_APPLICATION)), audits,
            "落空的敏感访问必须留痕，且带上可诊断的原因码");
    }

    @Test
    @DisplayName("查询落空：有应聘记录但人才不可见时，与「不存在」同一个提示、同一条审计主字段（有意不可区分）")
    void shouldNotDistinguishInvisibleFromMissing() {
        applicationCount = 1L;
        // 人才主档服务抛出「无权查看该人才」——对外必须与「候选人不存在」不可区分
        phonePlainFailure = new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_002);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> candidateService.viewPhone(TALENT_ID, "越权探测"));

        assertEquals(MSG_NOT_FOUND, ex.getMessage(), "不得泄露「存在但不可见」");
        assertEquals(List.of(deniedRecord(CandidateServiceImpl.DENY_REASON_OUT_OF_SCOPE_OR_ABSENT)), audits);
    }

    @Test
    @DisplayName("落空原因只进审计明细：两类落空的异常类型/文案/审计主字段完全一致，仅 detail 不同")
    void shouldRecordReasonOnlyInAuditDetail() {
        // A：无应聘记录
        applicationCount = 0L;
        phonePlainFailure = null;
        ServiceException missing = assertThrows(ServiceException.class,
            () -> candidateService.viewPhone(TALENT_ID, "回访"));
        String missingAudit = audits.get(0);

        // B：有应聘记录但不可见
        audits.clear();
        applicationCount = 1L;
        phonePlainFailure = new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_002);
        ServiceException invisible = assertThrows(ServiceException.class,
            () -> candidateService.viewPhone(TALENT_ID, "回访"));
        String invisibleAudit = audits.get(0);

        // 对外行为必须完全一致（异常类型 + 文案）
        assertEquals(missing.getClass(), invisible.getClass());
        assertEquals(missing.getMessage(), invisible.getMessage());
        // 审计的事件类型/业务对象/结果也必须完全一致，只允许明细不同
        assertEquals(missingAudit.substring(0, missingAudit.lastIndexOf('|')),
            invisibleAudit.substring(0, invisibleAudit.lastIndexOf('|')),
            "审计主字段必须一致，否则等于对外泄露差异");
        assertFalse(missingAudit.equals(invisibleAudit), "detailJson 必须能区分真实原因");
        assertTrue(missingAudit.endsWith("{\"reason\":\"no_application\"}"), missingAudit);
        assertTrue(invisibleAudit.endsWith("{\"reason\":\"out_of_scope_or_absent\"}"), invisibleAudit);
    }

    @Test
    @DisplayName("查询落空：审计写入真实失败（被记录器契约吞掉）时仍是原业务异常，不会变成 500")
    void shouldKeepBusinessExceptionWhenAuditWriteFails() {
        applicationCount = 0L;
        // 真实记录器 + null Mapper：record() 内部写入失败并被其「永不抛异常」契约吞掉
        CandidateServiceImpl service = new CandidateServiceImpl(null, applicationMapperStub(), null,
            profileServiceStub(), null, null, new SensitiveAuditRecorder(null));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.viewPhone(TALENT_ID, "审计不可用时查看"));

        assertEquals(MSG_NOT_FOUND, ex.getMessage(), "审计失败不得把业务拒绝变成 500");
    }

    /* ------------------------------------------------------------------ 替身与工具 ------------------------------------------------------------------ */

    /**
     * 构造 denied 审计断言串（含原因码明细）。
     *
     * @param reason 原因码
     * @return 断言串
     */
    private String deniedRecord(String reason) {
        return SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_DENIED
            + "|{\"reason\":\"" + reason + "\"}";
    }

    /**
     * 构造「用途为空」的 denied 审计断言串（无明细：服务层该分支走 5 参重载）。
     *
     * @return 断言串
     */
    private String blankPurposeRecord() {
        return SensitiveAuditRecorder.EVENT_PHONE_VIEW + "|" + SensitiveAuditRecorder.BIZ_TALENT
            + "|" + TALENT_ID + "|" + SensitiveAuditRecorder.RESULT_DENIED + "|null";
    }

    /**
     * 应聘记录 Mapper 替身：{@code selectCount} 返回可控条数。
     *
     * @return 替身
     */
    private RecruitApplicationMapper applicationMapperStub() {
        return (RecruitApplicationMapper) Proxy.newProxyInstance(
            RecruitApplicationMapper.class.getClassLoader(),
            new Class<?>[]{RecruitApplicationMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectCount" -> applicationCount;
                case "toString" -> "RecruitApplicationMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 应聘记录 Mapper 替身：任何调用都失败（用于证明拒绝路径不触碰业务数据）。
     *
     * @return 替身
     */
    private RecruitApplicationMapper throwingApplicationMapper() {
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
                businessLookupTouched = true;
                throw new IllegalStateException("用途为空时不应访问任何业务数据");
            });
    }

    /**
     * 人才主档服务替身：{@code getPhonePlain} 按需抛错或返回明文。
     *
     * @return 替身
     */
    private ITalentProfileService profileServiceStub() {
        return (ITalentProfileService) Proxy.newProxyInstance(
            ITalentProfileService.class.getClassLoader(),
            new Class<?>[]{ITalentProfileService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPhonePlain" -> {
                    if (phonePlainFailure != null) {
                        throw phonePlainFailure;
                    }
                    yield "13800001234";
                }
                case "toString" -> "TalentProfileServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
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
                audits.add(eventType + "|" + bizType + "|" + bizId + "|" + result + "|null");
            }

            @Override
            public void record(String eventType, String bizType, Long bizId, String purpose, String result,
                               String detailJson) {
                audits.add(eventType + "|" + bizType + "|" + bizId + "|" + result + "|" + detailJson);
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
