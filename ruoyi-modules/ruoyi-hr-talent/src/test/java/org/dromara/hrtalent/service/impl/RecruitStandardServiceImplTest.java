package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardBo;
import org.dromara.hrtalent.domain.entity.RecruitStandard;
import org.dromara.hrtalent.mapper.RecruitStandardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招聘期限标准服务单元测试。
 *
 * <p><b>这个测试存在的唯一理由</b>：{@code findByKey} 的业务键里有两个<b>可空</b>列
 * （{@code company_dept_id} 表示「集团通用」、{@code effective_date} 表示「不限定生效日」）。
 * 可空列一旦用 {@code eq(列, null)} 去写，生成的是 {@code 列 = null}——在 SQL 里恒为假，
 * 后果不是报错，而是<b>静默匹配不上</b>：同一份文件反复导入会一条条插成重复数据，
 * 手工新增也拦不住重复。线上不报错、只是数据慢慢变脏，所以必须由单测把它钉死。</p>
 *
 * <p>测试直接检查生成 SQL 片段里出现的是 {@code IS NULL} 还是 {@code =}，
 * 而不依赖数据库：判别力就藏在这个断言本身，连库反而看不清。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class RecruitStandardServiceImplTest {

    /**
     * 最近一次传入 Mapper 的查询条件，供断言检查生成的 SQL。
     * <p>类型用 {@link AbstractWrapper} 而不是 {@code Wrapper}：绑定参数表
     * {@code getParamNameValuePairs()} 只在这一层暴露，而「null 值不该占位」正是要断言的点。</p>
     */
    private AbstractWrapper<RecruitStandard, ?, ?> captured;

    /**
     * 被测服务。
     */
    private RecruitStandardServiceImpl service;

    @BeforeEach
    void setUp() {
        MybatisTableInfoTestSupport.init(RecruitStandard.class);
        captured = null;
        service = new RecruitStandardServiceImpl(mapperStub());
    }

    /**
     * 构造招聘标准 Mapper 的动态代理替身。
     * <p>只做一件事：把查询条件截留下来，并回报「没查到」。这样服务层的
     * {@code findByKey} 会走到「未命中」分支，但 SQL 片段已经可以检查。</p>
     *
     * @return Mapper 替身
     */
    @SuppressWarnings("unchecked")
    private RecruitStandardMapper mapperStub() {
        return (RecruitStandardMapper) Proxy.newProxyInstance(
            RecruitStandardMapper.class.getClassLoader(),
            new Class<?>[]{RecruitStandardMapper.class},
            (proxy, method, args) -> {
                if (args != null && args.length == 1 && args[0] instanceof AbstractWrapper<?, ?, ?> wrapper) {
                    captured = (AbstractWrapper<RecruitStandard, ?, ?>) wrapper;
                }
                return switch (method.getName()) {
                    case "toString" -> "RecruitStandardMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
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
        if (long.class.equals(type)) {
            return 0L;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        return 0;
    }

    /**
     * 取最近一次查询条件生成的 SQL 片段。
     *
     * @return 小写化的 SQL 片段
     */
    private String sql() {
        assertTrue(captured != null, "服务层没有向 Mapper 发起查询，测试前提不成立");
        return captured.getTargetSql().toLowerCase();
    }

    /**
     * 取本次查询绑定的参数值。
     *
     * @return 参数值列表
     */
    private List<Object> params() {
        assertTrue(captured != null, "服务层没有向 Mapper 发起查询，测试前提不成立");
        return new ArrayList<>(captured.getParamNameValuePairs().values());
    }

    @Test
    @DisplayName("公司留空（集团通用）时用 IS NULL 匹配，而不是 = null")
    void shouldMatchGroupWideByIsNull() {
        assertNull(service.findByKey("前端开发工程师", null, LocalDate.of(2026, 8, 1)));

        String sql = sql();
        assertTrue(sql.contains("company_dept_id is null"),
            "集团通用标准必须用 IS NULL 匹配，否则永远匹配不到自己，重复导入会插出重复数据。实际 SQL=" + sql);
        // 生效日期有值 → 应当走 =
        assertTrue(sql.contains("effective_date ="),
            "生效日期有值时应使用等值匹配。实际 SQL=" + sql);
        // 只有岗位名称与生效日期两个绑定参数，company_dept_id 不占位
        assertEquals(2, params().size(), "company_dept_id 走 IS NULL 时不应产生绑定参数");
    }

    @Test
    @DisplayName("生效日期留空时用 IS NULL 匹配，而不是 = null")
    void shouldMatchNullEffectiveDateByIsNull() {
        assertNull(service.findByKey("产品经理", 1761000000000000102L, null));

        String sql = sql();
        assertTrue(sql.contains("effective_date is null"),
            "生效日期留空必须用 IS NULL 匹配。实际 SQL=" + sql);
        assertTrue(sql.contains("company_dept_id ="),
            "公司有值时应使用等值匹配。实际 SQL=" + sql);
        assertEquals(2, params().size(), "effective_date 走 IS NULL 时不应产生绑定参数");
    }

    @Test
    @DisplayName("岗位名称为空或全空白时短路返回，不发起查询")
    void shouldTreatBlankJobNameAsMissing() {
        assertNull(service.findByKey("   ", 1761000000000000102L, null));
        // 岗位名称为空属于非法调用，服务层直接短路，不应发起查询
        assertTrue(captured == null, "岗位名称为空时不应查询数据库");
    }

    @Test
    @DisplayName("新增时命中同键标准应拒绝（重复新增防护）")
    void shouldRejectDuplicateOnCreate() {
        // 让查询命中一条已有标准
        RecruitStandard exist = new RecruitStandard();
        exist.setStandardId(9L);
        exist.setJobName("前端开发工程师");
        service = new RecruitStandardServiceImpl(mapperStubReturning(exist));

        RecruitStandardBo bo = new RecruitStandardBo();
        bo.setJobName("前端开发工程师");
        bo.setStandardDays(30);

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
            org.dromara.common.core.exception.ServiceException.class, () -> service.create(bo));
        assertTrue(ex.getMessage().contains("已存在"), "重复新增应给出可读原因，实际=" + ex.getMessage());
    }

    /**
     * 构造一个「查得到一条数据」的 Mapper 替身，同时继续截留查询条件。
     *
     * @param exist 要返回的实体
     * @return Mapper 替身
     */
    @SuppressWarnings("unchecked")
    private RecruitStandardMapper mapperStubReturning(RecruitStandard exist) {
        return (RecruitStandardMapper) Proxy.newProxyInstance(
            RecruitStandardMapper.class.getClassLoader(),
            new Class<?>[]{RecruitStandardMapper.class},
            (proxy, method, args) -> {
                if (args != null && args.length == 1 && args[0] instanceof AbstractWrapper<?, ?, ?> wrapper) {
                    captured = (AbstractWrapper<RecruitStandard, ?, ?>) wrapper;
                }
                return switch (method.getName()) {
                    case "selectOne" -> exist;
                    case "toString" -> "RecruitStandardMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
            });
    }

}
