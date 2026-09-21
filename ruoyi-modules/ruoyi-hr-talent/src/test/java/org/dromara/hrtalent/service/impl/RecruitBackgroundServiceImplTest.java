package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.Update;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundBo;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundDetailVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundVo;
import org.dromara.hrtalent.enums.BackgroundResultEnum;
import org.dromara.hrtalent.enums.BackgroundStatusEnum;
import org.dromara.hrtalent.event.BackgroundResultChangedEvent;
import org.dromara.hrtalent.mapper.RecruitBackgroundMapper;
import org.dromara.hrtalent.mapper.RecruitSensitiveAuditMapper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 背调域领域规则单元测试（SPEC-P3 §3.4、§3.6，设计文档 §8.7、§15.1、§15.2、§21.9）。
 *
 * <p>覆盖三类硬要求：</p>
 * <ol>
 *     <li>结论/状态/原因分类等编码规则与必填规则（含「不存中文」）；</li>
 *     <li>敏感字段独立权限：普通视图结构上不含 {@code detailCipher}，
 *     明文明细只能在「有用途 + 写审计」之后返回，无用途查看必须记 {@code denied} 审计并拒绝；</li>
 *     <li>一个应聘记录只保留一条当前有效背调（新建时旧记录失效）与结论变化事件。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper 与事件发布器替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitBackgroundServiceImplTest {

    /**
     * 内存中的背调数据，按主键索引。
     */
    private final Map<Long, RecruitBackground> store = new HashMap<>();

    /**
     * 捕获到的敏感审计记录。
     */
    private final List<RecruitSensitiveAudit> audits = new ArrayList<>();

    /**
     * 捕获到的领域事件。
     */
    private final List<Object> events = new ArrayList<>();

    /**
     * 最近一次更新的 SET 片段（用于校验显式清空字段）。
     */
    private String lastUpdateSqlSet;

    /**
     * 最近一次提交给 Mapper 的更新补丁。
     */
    private RecruitBackground lastUpdatePatch;

    /**
     * 主键生成器（模拟 MyBatis-Plus 的 ASSIGN_ID 回填）。
     */
    private final AtomicLong idGenerator = new AtomicLong(3000L);

    /**
     * 被测服务。
     */
    private RecruitBackgroundServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 的实体列缓存。
     *
     * <p>单元测试没有 Spring/MyBatis 上下文，而 {@code LambdaUpdateWrapper#set} 需要 TableInfo
     * 才能把方法引用解析成列名，因此这里显式注册一次实体元信息（不依赖 Mockito）。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), RecruitBackground.class);
    }

    @BeforeEach
    void setUp() {
        store.clear();
        audits.clear();
        events.clear();
        lastUpdateSqlSet = null;
        lastUpdatePatch = null;
        service = new RecruitBackgroundServiceImpl(
            backgroundMapperStub(),
            new SensitiveAuditRecorder(auditMapperStub()),
            eventPublisherStub());
    }

    /* ------------------------------------------------------------------ 结论与编码规则 ------------------------------------------------------------------ */

    @Test
    @DisplayName("未知背调结论编码被拒绝")
    void shouldRejectUnknownResult() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setResult("通过");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("背调结论不合法"));
    }

    @Test
    @DisplayName("未通过原因分类必须是字典编码，传中文被拒绝")
    void shouldRejectChineseFailureReasonCode() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setResult(BackgroundResultEnum.FAIL.getCode());
        bo.setFailureReasonCode("工作经历造假");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("未通过原因分类必须使用字典编码，不能填写中文", ex.getMessage());
    }

    @Test
    @DisplayName("结论为不通过但未填原因分类时拒绝")
    void shouldRejectFailWithoutReasonCode() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setResult(BackgroundResultEnum.FAIL.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("背调结论为不通过（fail）时必须填写未通过原因分类", ex.getMessage());
    }

    @Test
    @DisplayName("结论为已豁免但未填免背调原因时拒绝")
    void shouldRejectWaivedWithoutReason() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setResult(BackgroundResultEnum.WAIVED.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("背调结论为已豁免（waived）时必须填写免背调原因", ex.getMessage());
    }

    @Test
    @DisplayName("授权标志只接受 0 或 1")
    void shouldRejectIllegalAuthorizedFlag() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setAuthorizedFlag("Y");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("是否已获得候选人授权只能为 0 或 1", ex.getMessage());
    }

    @Test
    @DisplayName("未知背调状态编码被拒绝")
    void shouldRejectUnknownStatus() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setStatus("doing");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("未知的背调状态"));
    }

    @Test
    @DisplayName("新建背调状态不能为已取消")
    void shouldRejectCancelledStatusOnCreate() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setStatus(BackgroundStatusEnum.CANCELLED.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("新建背调记录状态不能为已取消", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 当前有效背调唯一 ------------------------------------------------------------------ */

    @Test
    @DisplayName("同一应聘记录新建背调时旧的有效背调被置为失效")
    void shouldSupersedePreviousActiveBackground() {
        RecruitBackground old = background(2001L, 100L, BackgroundStatusEnum.CHECKING.getCode());
        old.setResult(BackgroundResultEnum.PENDING.getCode());

        RecruitBackgroundBo bo = bo(100L);
        bo.setCheckerId(9L);

        Long newId = service.create(bo);

        assertNotNull(newId);
        assertEquals(BackgroundStatusEnum.CANCELLED.getCode(), store.get(2001L).getStatus());
        assertEquals(BackgroundStatusEnum.DRAFT.getCode(), store.get(newId).getStatus());
        long active = store.values().stream()
            .filter(item -> !BackgroundStatusEnum.CANCELLED.getCode().equals(item.getStatus()))
            .count();
        assertEquals(1L, active);
    }

    @Test
    @DisplayName("已失效的旧背调不会被重复处理")
    void shouldKeepAlreadyCancelledRecord() {
        RecruitBackground cancelled = background(2002L, 100L, BackgroundStatusEnum.CANCELLED.getCode());
        cancelled.setRemark("人工取消");

        service.create(bo(100L));

        assertEquals("人工取消", store.get(2002L).getRemark());
    }

    /* ------------------------------------------------------------------ 结论变化事件 ------------------------------------------------------------------ */

    @Test
    @DisplayName("新增时带回执结论发布背调结论变化事件")
    void shouldPublishEventWhenConclusiveOnCreate() {
        RecruitBackgroundBo bo = bo(100L);
        bo.setResult(BackgroundResultEnum.PASS.getCode());

        Long id = service.create(bo);

        assertEquals(1, events.size());
        BackgroundResultChangedEvent event = (BackgroundResultChangedEvent) events.get(0);
        assertEquals(id, event.backgroundId());
        assertEquals(100L, event.applicationId());
        assertEquals(BackgroundResultEnum.PASS.getCode(), event.result());
    }

    @Test
    @DisplayName("待背调结论不发布事件")
    void shouldNotPublishEventWhenPending() {
        service.create(bo(100L));

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("存在事务同步时结论变化事件在事务提交后才发布")
    void shouldPublishEventAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            RecruitBackgroundBo bo = bo(100L);
            bo.setResult(BackgroundResultEnum.PASS.getCode());

            service.create(bo);

            assertTrue(events.isEmpty(), "事务提交前不得发布事件，避免消费者读到未提交数据");
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            assertEquals(1, events.size());
            assertEquals(BackgroundResultEnum.PASS.getCode(),
                ((BackgroundResultChangedEvent) events.get(0)).result());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("更新后结论发生变化时发布事件，且结论非不通过时清空未通过原因")
    void shouldPublishEventAndClearFailureReasonOnUpdate() {
        RecruitBackground existing = background(2003L, 100L, BackgroundStatusEnum.FINISHED.getCode());
        existing.setResult(BackgroundResultEnum.FAIL.getCode());
        existing.setFailureReasonCode("work_history_mismatch");

        RecruitBackgroundBo bo = new RecruitBackgroundBo();
        bo.setBackgroundId(2003L);
        bo.setResult(BackgroundResultEnum.PASS.getCode());

        service.update(bo);

        assertEquals(1, events.size());
        assertEquals(BackgroundResultEnum.PASS.getCode(),
            ((BackgroundResultChangedEvent) events.get(0)).result());
        assertNotNull(lastUpdatePatch);
        assertEquals(BackgroundResultEnum.PASS.getCode(), lastUpdatePatch.getResult());
        // 所属应聘记录不可修改：更新补丁不得携带 application_id
        assertNull(lastUpdatePatch.getApplicationId());
        // 显式清空：实体更新默认忽略 null，必须通过 SET 片段把原因置空，避免与新结论矛盾
        assertNotNull(lastUpdateSqlSet);
        assertTrue(lastUpdateSqlSet.contains("failure_reason_code"));
    }

    @Test
    @DisplayName("已取消或被替代的背调记录不可修改")
    void shouldRejectUpdateOnCancelledRecord() {
        background(2004L, 100L, BackgroundStatusEnum.CANCELLED.getCode());

        RecruitBackgroundBo bo = new RecruitBackgroundBo();
        bo.setBackgroundId(2004L);
        bo.setResult(BackgroundResultEnum.PASS.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(bo));
        assertEquals("已取消或被替代的背调记录不可修改，请更新当前有效背调", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 敏感字段独立权限 ------------------------------------------------------------------ */

    @Test
    @DisplayName("列表与普通详情 VO 结构上不含背调明细字段")
    void shouldNotDeclareDetailCipherInNormalVo() {
        List<String> normalVoFields = Arrays.stream(RecruitBackgroundVo.class.getDeclaredFields())
            .map(Field::getName).toList();
        assertFalse(normalVoFields.contains("detailCipher"),
            "普通 VO 不得声明 detailCipher，否则列表与普通详情会泄露明细");

        List<String> detailVoFields = Arrays.stream(RecruitBackgroundDetailVo.class.getDeclaredFields())
            .map(Field::getName).toList();
        assertTrue(detailVoFields.contains("detailCipher"));
    }

    @Test
    @DisplayName("普通详情不返回明细")
    void shouldNotReturnDetailInNormalDetail() {
        RecruitBackground entity = background(2005L, 100L, BackgroundStatusEnum.FINISHED.getCode());
        entity.setDetailCipher("候选人存在重大不一致");

        RecruitBackgroundVo vo = service.getDetail(2005L);

        assertEquals(2005L, vo.getBackgroundId());
        assertFalse(Arrays.stream(vo.getClass().getDeclaredFields()).anyMatch(f -> "detailCipher".equals(f.getName())));
    }

    @Test
    @DisplayName("无用途查看明细被拒绝并写入 denied 审计")
    void shouldRejectDetailViewWithoutPurpose() {
        RecruitBackground entity = background(2006L, 100L, BackgroundStatusEnum.FINISHED.getCode());
        entity.setDetailCipher("候选人存在重大不一致");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.viewDetail(2006L, "  "));
        assertEquals("查看背调明细必须填写用途（purpose）", ex.getMessage());
        assertEquals(1, audits.size());
        assertEquals(SensitiveAuditRecorder.EVENT_BACKGROUND_VIEW, audits.get(0).getEventType());
        assertEquals(SensitiveAuditRecorder.BIZ_BACKGROUND, audits.get(0).getBizType());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("有用途查看明细先写 success 审计再返回明文，且审计不含明细")
    void shouldAuditThenReturnDetail() {
        RecruitBackground entity = background(2007L, 100L, BackgroundStatusEnum.FINISHED.getCode());
        entity.setDetailCipher("候选人存在重大不一致");

        RecruitBackgroundDetailVo vo = service.viewDetail(2007L, "录用前合规复核");

        assertEquals("候选人存在重大不一致", vo.getDetailCipher());
        assertEquals(1, audits.size());
        RecruitSensitiveAudit audit = audits.get(0);
        assertEquals(SensitiveAuditRecorder.RESULT_SUCCESS, audit.getResult());
        assertEquals("录用前合规复核", audit.getPurpose());
        assertEquals(2007L, audit.getBizId());
        // 审计明细不得记录背调明文
        assertNull(audit.getDetailJson());
    }

    @Test
    @DisplayName("查看不存在的背调明细写 denied 审计并提示记录不存在")
    void shouldAuditAndRejectUnknownDetailView() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.viewDetail(9999L, "合规复核"));

        assertEquals("背调记录不存在或已删除", ex.getMessage());
        assertEquals(1, audits.size());
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).getResult());
    }

    @Test
    @DisplayName("普通详情查询不存在的记录给出中文提示")
    void shouldRejectUnknownNormalDetail() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getDetail(9999L));
        assertEquals("背调记录不存在或已删除", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造背调 Mapper 的动态代理替身。
     * <p>模拟内存库：插入时回填主键，更新时把非空字段合并回内存记录，并捕获 SET 片段。</p>
     *
     * @return Mapper 替身
     */
    private RecruitBackgroundMapper backgroundMapperStub() {
        return (RecruitBackgroundMapper) Proxy.newProxyInstance(
            RecruitBackgroundMapper.class.getClassLoader(),
            new Class<?>[]{RecruitBackgroundMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> store.get((Long) args[0]);
                case "selectList" -> new ArrayList<>(store.values());
                case "insert" -> {
                    RecruitBackground entity = (RecruitBackground) args[0];
                    if (entity.getBackgroundId() == null) {
                        entity.setBackgroundId(idGenerator.incrementAndGet());
                    }
                    store.put(entity.getBackgroundId(), entity);
                    yield 1;
                }
                case "updateById" -> {
                    RecruitBackground entity = (RecruitBackground) args[0];
                    store.put(entity.getBackgroundId(), entity);
                    yield 1;
                }
                case "update" -> {
                    if (args[0] instanceof RecruitBackground patch) {
                        lastUpdatePatch = patch;
                    }
                    if (args.length > 1 && args[1] instanceof Update<?, ?> updateWrapper) {
                        lastUpdateSqlSet = updateWrapper.getSqlSet();
                    }
                    yield 1;
                }
                case "selectCount" -> (long) store.size();
                case "deleteByIds" -> 1;
                case "toString" -> "RecruitBackgroundMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造敏感审计 Mapper 的动态代理替身。
     *
     * @return Mapper 替身
     */
    private RecruitSensitiveAuditMapper auditMapperStub() {
        return (RecruitSensitiveAuditMapper) Proxy.newProxyInstance(
            RecruitSensitiveAuditMapper.class.getClassLoader(),
            new Class<?>[]{RecruitSensitiveAuditMapper.class},
            (proxy, method, args) -> {
                if ("insert".equals(method.getName())) {
                    audits.add((RecruitSensitiveAudit) args[0]);
                    return 1;
                }
                if ("toString".equals(method.getName())) {
                    return "RecruitSensitiveAuditMapperStub";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                return defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造事件发布器替身。
     *
     * @return 发布器替身
     */
    private ApplicationEventPublisher eventPublisherStub() {
        return (ApplicationEventPublisher) Proxy.newProxyInstance(
            ApplicationEventPublisher.class.getClassLoader(),
            new Class<?>[]{ApplicationEventPublisher.class},
            (proxy, method, args) -> {
                if ("publishEvent".equals(method.getName())) {
                    events.add(args[0]);
                    return null;
                }
                if ("toString".equals(method.getName())) {
                    return "ApplicationEventPublisherStub";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                return defaultValue(method.getReturnType());
            });
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
     * 构造最小可用的新增入参。
     *
     * @param applicationId 应聘记录ID
     * @return 背调入参
     */
    private RecruitBackgroundBo bo(Long applicationId) {
        RecruitBackgroundBo bo = new RecruitBackgroundBo();
        bo.setApplicationId(applicationId);
        return bo;
    }

    /**
     * 构造一条最小可用的背调记录并放入内存库。
     *
     * @param backgroundId  背调记录ID
     * @param applicationId 应聘记录ID
     * @param status        背调状态
     * @return 背调记录
     */
    private RecruitBackground background(Long backgroundId, Long applicationId, String status) {
        RecruitBackground entity = new RecruitBackground();
        entity.setBackgroundId(backgroundId);
        entity.setApplicationId(applicationId);
        entity.setStatus(status);
        entity.setAuthorizedFlag("1");
        entity.setResult(BackgroundResultEnum.PENDING.getCode());
        store.put(backgroundId, entity);
        return entity;
    }

}
