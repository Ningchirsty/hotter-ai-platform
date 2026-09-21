package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewCancelBo;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewFeedbackBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewBo;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.entity.RecruitInterviewer;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewVo;
import org.dromara.hrtalent.mapper.RecruitInterviewMapper;
import org.dromara.hrtalent.mapper.RecruitInterviewerMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面试域领域规则单元测试。
 *
 * <p>覆盖 SPEC-P3 面试域（§2.3/§3.3）的硬性规则：轮次从 1 开始、同一应聘记录同一轮次只允许一条有效面试、
 * 面试官必填且有上限、改期与取消的状态限制与操作留痕、反馈只接受字典编码且必须由本场面试官提交。
 * 这些规则都在访问数据库之前完成校验，因此无需 Spring 上下文。</p>
 *
 * <p><b>说明</b>：Mapper 替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。
 * 无登录态时 {@code LoginHelper.getUserId()} 返回 null，因此反馈/待办的「未登录」分支可被稳定断言。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitInterviewServiceImplTest {

    /**
     * 内存中的面试记录，按主键索引。
     */
    private final Map<Long, RecruitInterview> interviewStore = new HashMap<>();

    /**
     * 面试官插入次数（用于断言多人面试逐行落库）。
     */
    private final AtomicInteger interviewerInsertCount = new AtomicInteger();

    /**
     * 面试官更新次数（用于断言取消后待反馈置为无需反馈）。
     */
    private final AtomicInteger interviewerUpdateCount = new AtomicInteger();

    /**
     * 被测服务。
     */
    private RecruitInterviewServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 的实体元数据缓存。
     *
     * <p>{@code LambdaQueryWrapper} 依赖 {@code TableInfoHelper} 注册的 lambda 列缓存；
     * 单测不启动 Spring，因而需要手工注册两份实体的元数据，否则构造条件构造器时
     * 会抛出「can not find lambda cache for this entity」。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RecruitInterview.class);
        TableInfoHelper.initTableInfo(assistant, RecruitInterviewer.class);
    }

    @BeforeEach
    void setUp() {
        interviewStore.clear();
        interviewerInsertCount.set(0);
        interviewerUpdateCount.set(0);
        service = new RecruitInterviewServiceImpl(interviewMapperStub(), interviewerMapperStub(), event -> {
        });
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造面试记录 Mapper 的动态代理替身。
     *
     * @return Mapper 替身
     */
    private RecruitInterviewMapper interviewMapperStub() {
        return (RecruitInterviewMapper) Proxy.newProxyInstance(
            RecruitInterviewMapper.class.getClassLoader(),
            new Class<?>[]{RecruitInterviewMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> interviewStore.get((Long) args[0]);
                case "insert" -> {
                    RecruitInterview entity = (RecruitInterview) args[0];
                    if (entity.getInterviewId() == null) {
                        entity.setInterviewId(9001L);
                    }
                    interviewStore.put(entity.getInterviewId(), entity);
                    yield 1;
                }
                case "updateById" -> {
                    RecruitInterview entity = (RecruitInterview) args[0];
                    interviewStore.put(entity.getInterviewId(), entity);
                    yield 1;
                }
                case "selectCount" -> 0L;
                case "selectApplicationSummaries" -> summaries((Collection<Long>) args[0]);
                case "toString" -> "RecruitInterviewMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造面试官 Mapper 的动态代理替身。
     *
     * @return Mapper 替身
     */
    private RecruitInterviewerMapper interviewerMapperStub() {
        return (RecruitInterviewerMapper) Proxy.newProxyInstance(
            RecruitInterviewerMapper.class.getClassLoader(),
            new Class<?>[]{RecruitInterviewerMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    interviewerInsertCount.incrementAndGet();
                    yield 1;
                }
                case "update" -> {
                    interviewerUpdateCount.incrementAndGet();
                    yield 1;
                }
                case "updateById" -> {
                    interviewerUpdateCount.incrementAndGet();
                    yield 1;
                }
                case "selectList" -> new ArrayList<>();
                case "selectOne" -> null;
                case "toString" -> "RecruitInterviewerMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造只读应聘记录摘要替身：仅 respond 应聘记录ID 1001。
     *
     * @param applicationIds 应聘记录ID集合
     * @return 摘要列表
     */
    private List<RecruitInterviewVo> summaries(Collection<Long> applicationIds) {
        List<RecruitInterviewVo> result = new ArrayList<>();
        if (applicationIds == null) {
            return result;
        }
        for (Long id : applicationIds) {
            if (Long.valueOf(1001L).equals(id)) {
                RecruitInterviewVo summary = new RecruitInterviewVo();
                summary.setApplicationId(1001L);
                summary.setApplicationNo("APP20260921-001");
                summary.setCandidateName("张三");
                summary.setJobName("高级后端工程师");
                summary.setPlanItemId(2001L);
                summary.setCurrentStage("first_interview");
                result.add(summary);
            }
        }
        return result;
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
     * 构造一条最小可用的面试实体并放入内存库。
     *
     * @param status  面试状态
     * @param roundNo 面试轮次
     * @return 面试实体
     */
    private RecruitInterview interview(String status, int roundNo) {
        RecruitInterview entity = new RecruitInterview();
        entity.setInterviewId(5001L);
        entity.setApplicationId(1001L);
        entity.setRoundNo(roundNo);
        entity.setScheduleTime(LocalDateTime.now().plusDays(1));
        entity.setStatus(status);
        entity.setResult("pending");
        interviewStore.put(entity.getInterviewId(), entity);
        return entity;
    }

    /**
     * 构造一条最小可用的安排面试入参。
     *
     * @return 面试入参
     */
    private RecruitInterviewBo scheduleBo() {
        RecruitInterviewBo bo = new RecruitInterviewBo();
        bo.setApplicationId(1001L);
        bo.setRoundNo(1);
        bo.setScheduleTime(LocalDateTime.now().plusDays(1));
        bo.setInterviewerIds(new Long[]{7L, 8L});
        return bo;
    }

    /* ------------------------------------------------------------------ 安排面试 ------------------------------------------------------------------ */

    @Test
    @DisplayName("面试轮次小于 1 时拒绝安排")
    void shouldRejectInvalidRoundNo() {
        RecruitInterviewBo bo = scheduleBo();
        bo.setRoundNo(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.schedule(bo));
        assertEquals("面试轮次必须从 1 开始", ex.getMessage());
    }

    @Test
    @DisplayName("未指定面试官时拒绝安排")
    void shouldRejectEmptyInterviewers() {
        RecruitInterviewBo bo = scheduleBo();
        bo.setInterviewerIds(new Long[0]);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.schedule(bo));
        assertEquals("请至少指定一名面试官", ex.getMessage());
    }

    @Test
    @DisplayName("面试官超过 20 人时拒绝安排")
    void shouldRejectTooManyInterviewers() {
        RecruitInterviewBo bo = scheduleBo();
        Long[] ids = new Long[21];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (long) (i + 1);
        }
        bo.setInterviewerIds(ids);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.schedule(bo));
        assertEquals("面试官最多 20 人", ex.getMessage());
    }

    @Test
    @DisplayName("应聘记录不存在时拒绝安排")
    void shouldRejectUnknownApplication() {
        RecruitInterviewBo bo = scheduleBo();
        bo.setApplicationId(9999L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.schedule(bo));
        assertEquals("应聘记录不存在或已删除", ex.getMessage());
    }

    @Test
    @DisplayName("同一应聘记录同一轮次已存在有效面试时拒绝重复安排")
    void shouldRejectDuplicateRound() {
        RecruitInterviewMapper mapper = (RecruitInterviewMapper) Proxy.newProxyInstance(
            RecruitInterviewMapper.class.getClassLoader(),
            new Class<?>[]{RecruitInterviewMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectCount" -> 1L;
                case "selectApplicationSummaries" -> summaries((Collection<Long>) args[0]);
                case "toString" -> "DuplicateRoundStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
        RecruitInterviewServiceImpl duplicateService =
            new RecruitInterviewServiceImpl(mapper, interviewerMapperStub(), event -> {
            });

        ServiceException ex = assertThrows(ServiceException.class, () -> duplicateService.schedule(scheduleBo()));
        assertTrue(ex.getMessage().contains("已存在有效面试安排"));
    }

    @Test
    @DisplayName("安排面试成功：多人面试官逐行落库")
    void shouldScheduleInterviewWithInterviewers() {
        Long interviewId = service.schedule(scheduleBo());

        assertEquals(9001L, interviewId);
        RecruitInterview saved = interviewStore.get(9001L);
        assertNotNull(saved);
        assertEquals("scheduled", saved.getStatus());
        assertEquals("pending", saved.getResult());
        assertEquals(2, interviewerInsertCount.get());
    }

    /* ------------------------------------------------------------------ 改期与取消 ------------------------------------------------------------------ */

    @Test
    @DisplayName("已取消的面试不能改期")
    void shouldRejectRescheduleCancelled() {
        interview("cancelled", 1);

        RecruitInterviewBo bo = new RecruitInterviewBo();
        bo.setInterviewId(5001L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reschedule(bo));
        assertEquals("已取消的面试不能改期，请重新安排", ex.getMessage());
    }

    @Test
    @DisplayName("已完成的面试不能改期")
    void shouldRejectRescheduleFinished() {
        interview("finished", 1);

        RecruitInterviewBo bo = new RecruitInterviewBo();
        bo.setInterviewId(5001L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reschedule(bo));
        assertEquals("已完成的面试不能改期", ex.getMessage());
    }

    @Test
    @DisplayName("取消面试必须填写原因")
    void shouldRejectCancelWithoutReason() {
        interview("scheduled", 1);

        InterviewCancelBo bo = new InterviewCancelBo();
        bo.setInterviewId(5001L);
        bo.setCancelReason("   ");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(bo));
        assertEquals("取消面试必须填写原因", ex.getMessage());
    }

    @Test
    @DisplayName("重复取消面试被拒绝")
    void shouldRejectCancelAgain() {
        interview("cancelled", 1);

        InterviewCancelBo bo = new InterviewCancelBo();
        bo.setInterviewId(5001L);
        bo.setCancelReason("候选人临时有事");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(bo));
        assertEquals("该面试已取消，无需重复取消", ex.getMessage());
    }

    @Test
    @DisplayName("取消面试成功：状态置取消、原因留痕、待反馈面试官置无需反馈")
    void shouldCancelInterviewAndWaiveInterviewers() {
        interview("scheduled", 1);

        InterviewCancelBo bo = new InterviewCancelBo();
        bo.setInterviewId(5001L);
        bo.setCancelReason("候选人临时有事");

        service.cancel(bo);

        RecruitInterview saved = interviewStore.get(5001L);
        assertEquals("cancelled", saved.getStatus());
        assertEquals("候选人临时有事", saved.getCancelReason());
        assertTrue(saved.getRemark().contains("【取消】"));
        assertTrue(interviewerUpdateCount.get() >= 1);
    }

    /* ------------------------------------------------------------------ 面试反馈 ------------------------------------------------------------------ */

    @Test
    @DisplayName("面试结果只接受字典 recruit_interview_result 的编码")
    void shouldRejectUnknownFeedbackResult() {
        interview("scheduled", 1);

        InterviewFeedbackBo bo = new InterviewFeedbackBo();
        bo.setInterviewId(5001L);
        bo.setFeedback("技术基础扎实");
        bo.setResult("hired");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.feedback(bo));
        assertTrue(ex.getMessage().contains("面试结果不合法"));
    }

    @Test
    @DisplayName("已取消的面试不能提交反馈")
    void shouldRejectFeedbackOnCancelled() {
        interview("cancelled", 1);

        InterviewFeedbackBo bo = new InterviewFeedbackBo();
        bo.setInterviewId(5001L);
        bo.setFeedback("技术基础扎实");
        bo.setResult("pass");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.feedback(bo));
        assertEquals("已取消的面试不能提交反馈", ex.getMessage());
    }

    @Test
    @DisplayName("无登录态时不能提交反馈")
    void shouldRejectFeedbackWithoutLogin() {
        interview("scheduled", 1);

        InterviewFeedbackBo bo = new InterviewFeedbackBo();
        bo.setInterviewId(5001L);
        bo.setFeedback("技术基础扎实");
        bo.setResult("pass");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.feedback(bo));
        assertEquals("无法识别当前登录用户，不能提交面试反馈", ex.getMessage());
    }

    @Test
    @DisplayName("无登录态时不能查询面试待办")
    void shouldRejectMyTodosWithoutLogin() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.myTodos());
        assertEquals("无法识别当前登录用户，不能查询面试待办", ex.getMessage());
    }

    /* ------------------------------------------------------------------ 视图对象 ------------------------------------------------------------------ */

    @Test
    @DisplayName("多值面试官只读 getter 拼成逗号串并去重")
    void shouldJoinInterviewerIds() {
        RecruitInterviewVo vo = new RecruitInterviewVo();
        vo.setInterviewerIds(new Long[]{5L, 5L, 3L});
        assertEquals("5,3", vo.getInterviewerIdText());

        vo.setInterviewerIds(new Long[0]);
        assertEquals(null, vo.getInterviewerIdText());

        vo.setStatus("scheduled");
        assertEquals("已安排", vo.getStatusLabel());
        vo.setStatus("rescheduled");
        assertEquals("已改期", vo.getStatusLabel());
        vo.setStatus(null);
        assertEquals(null, vo.getStatusLabel());
    }

}
