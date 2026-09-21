package org.dromara.hrtalent.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobAssignBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobBo;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.enums.JobStatusEnum;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位执行项领域规则单元测试。
 *
 * <p>覆盖 SPEC-P2 岗位域的硬性规则：薪资低值不得大于高值、薪资周期必须明确、
 * 状态/招聘形式/紧急程度只接受稳定编码、状态机与分配接口的非法调用必须给出中文提示。
 * 这些规则都在访问数据库之前完成校验，因此无需 Spring 上下文。</p>
 *
 * <p><b>说明</b>：Mapper 替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitJobServiceImplTest {

    /**
     * 内存中的岗位数据，按主键索引。
     */
    private final Map<Long, RecruitJob> store = new HashMap<>();

    /**
     * 被测服务。
     */
    private RecruitJobServiceImpl service;

    @BeforeEach
    void setUp() {
        store.clear();
        service = new RecruitJobServiceImpl(mapperStub(), new RecruitBusinessNoGenerator());
    }

    /**
     * 构造岗位 Mapper 的动态代理替身。
     * <p>只实现测试需要的 {@code selectById}，其余方法返回类型默认值。</p>
     *
     * @return Mapper 替身
     */
    private RecruitJobMapper mapperStub() {
        return (RecruitJobMapper) Proxy.newProxyInstance(
            RecruitJobMapper.class.getClassLoader(),
            new Class<?>[]{RecruitJobMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> store.get((Long) args[0]);
                case "insert" -> {
                    RecruitJob entity = (RecruitJob) args[0];
                    store.put(entity.getJobId(), entity);
                    yield 1;
                }
                case "update" -> 1;
                case "updateById" -> 1;
                case "selectCount" -> 1L;
                case "deleteByIds" -> 1;
                case "toString" -> "RecruitJobMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
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
     * 构造一条最小可用的岗位实体并放入内存库。
     *
     * @param status 岗位状态
     * @return 岗位实体
     */
    private RecruitJob job(String status) {
        RecruitJob job = new RecruitJob();
        job.setJobId(1001L);
        job.setJobNo("JOB20260921-001");
        job.setJobName("高级后端工程师");
        job.setStatus(status);
        job.setVersion(0);
        store.put(job.getJobId(), job);
        return job;
    }

    /**
     * 构造一条最小可用的新增入参。
     *
     * @return 岗位入参
     */
    private RecruitJobBo bo() {
        RecruitJobBo bo = new RecruitJobBo();
        bo.setJobName("高级后端工程师");
        return bo;
    }

    @Test
    @DisplayName("薪资低值大于高值时拒绝新增")
    void shouldRejectSalaryMinGreaterThanMax() {
        RecruitJobBo bo = bo();
        bo.setSalaryMin(new BigDecimal("30000"));
        bo.setSalaryMax(new BigDecimal("20000"));
        bo.setSalaryPeriod("month");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("薪资低值不能大于薪资高值", ex.getMessage());
    }

    @Test
    @DisplayName("填写薪资但未明确薪资周期时拒绝新增")
    void shouldRejectSalaryWithoutPeriod() {
        RecruitJobBo bo = bo();
        bo.setSalaryMin(new BigDecimal("20000"));
        bo.setSalaryMax(new BigDecimal("30000"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("填写薪资时必须明确薪资周期（如 month/year/day）", ex.getMessage());
    }

    @Test
    @DisplayName("新增时不允许直接使用已暂停或已关闭状态")
    void shouldRejectClosedStatusOnCreate() {
        RecruitJobBo bo = bo();
        bo.setStatus(JobStatusEnum.CLOSED.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("新建岗位状态只能为草稿或招聘中", ex.getMessage());
    }

    @Test
    @DisplayName("未知岗位状态编码被拒绝")
    void shouldRejectUnknownStatusOnCreate() {
        RecruitJobBo bo = bo();
        bo.setStatus("recruiting");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertEquals("未知的岗位状态：recruiting", ex.getMessage());
    }

    @Test
    @DisplayName("招聘形式只接受 RecruitModeEnum 的稳定编码")
    void shouldRejectUnknownRecruitMode() {
        RecruitJobBo bo = bo();
        bo.setRecruitMode("社招");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("招聘形式不合法"));
    }

    @Test
    @DisplayName("紧急程度只接受 UrgencyEnum 的稳定编码")
    void shouldRejectUnknownUrgency() {
        RecruitJobBo bo = bo();
        bo.setUrgency("非常紧急");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("紧急程度不合法"));
    }

    @Test
    @DisplayName("不支持的动作被拒绝")
    void shouldRejectUnknownAction() {
        job(JobStatusEnum.OPEN.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.action(1001L, "publish"));
        assertEquals("不支持的岗位动作：publish", ex.getMessage());
    }

    @Test
    @DisplayName("已关闭岗位不能重复关闭")
    void shouldRejectCloseAgain() {
        job(JobStatusEnum.CLOSED.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.action(1001L, "close"));
        assertEquals("岗位已关闭，无需重复关闭", ex.getMessage());
    }

    @Test
    @DisplayName("仅已关闭岗位可以重新开放")
    void shouldRejectReopenWhenNotClosed() {
        job(JobStatusEnum.OPEN.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.action(1001L, "reopen"));
        assertEquals("仅已关闭的岗位可以重新开放", ex.getMessage());
    }

    @Test
    @DisplayName("分配接口未指定任何人员时拒绝")
    void shouldRejectEmptyAssign() {
        job(JobStatusEnum.OPEN.getCode());

        RecruitJobAssignBo assignBo = new RecruitJobAssignBo();
        assignBo.setJobId(1001L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assign(assignBo));
        assertEquals("请至少分配一项：招聘负责人、协助人、一面面试官或二面面试官", ex.getMessage());
    }

    @Test
    @DisplayName("已关闭岗位不能分配人员")
    void shouldRejectAssignOnClosedJob() {
        job(JobStatusEnum.CLOSED.getCode());

        RecruitJobAssignBo assignBo = new RecruitJobAssignBo();
        assignBo.setJobId(1001L);
        assignBo.setOwnerId(9L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assign(assignBo));
        assertEquals("已关闭的岗位不能分配人员", ex.getMessage());
    }

    @Test
    @DisplayName("岗位状态机只允许合法流转")
    void shouldCheckStatusTransfer() {
        assertTrue(JobStatusEnum.canTransfer(JobStatusEnum.DRAFT.getCode(), JobStatusEnum.OPEN.getCode()));
        assertTrue(JobStatusEnum.canTransfer(JobStatusEnum.OPEN.getCode(), JobStatusEnum.PAUSED.getCode()));
        assertTrue(JobStatusEnum.canTransfer(JobStatusEnum.PAUSED.getCode(), JobStatusEnum.OPEN.getCode()));
        assertTrue(JobStatusEnum.canTransfer(JobStatusEnum.CLOSED.getCode(), JobStatusEnum.OPEN.getCode()));
        assertFalse(JobStatusEnum.canTransfer(JobStatusEnum.DRAFT.getCode(), JobStatusEnum.PAUSED.getCode()));
        assertFalse(JobStatusEnum.canTransfer(JobStatusEnum.OPEN.getCode(), JobStatusEnum.OPEN.getCode()));
        assertFalse(JobStatusEnum.isValid("recruiting"));
        assertEquals("招聘中", JobStatusEnum.labelOf(JobStatusEnum.OPEN.getCode()));
    }

}
