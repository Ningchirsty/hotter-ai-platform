package org.dromara.aigov.domain.bo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.common.core.validate.AddGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「数据等级 STRICT 必须能从每一个入口进来」的一致性测试。
 *
 * <p><b>为什么需要它</b>：STRICT 是后加的第 4 个等级（{@code AigDataLevelEnum.STRICT}），
 * 而校验正则散落在多个 BO 上。加等级时漏改其中一处，症状是<b>运行期才发现</b>的、
 * 且方向正好相反的两类故障：</p>
 * <ul>
 *     <li>{@code AigInvokeBo} 漏改：持有严格级数据的业务域无法调用，只能把等级标低再调
 *         ——这正是 STRICT 要防的事，而且从请求上看不出发生过；</li>
 *     <li>{@code AigRoutePolicyBo} 漏改：建不出严格级策略行，而路由是「无策略即拒绝」，
 *         于是所有严格级调用一律被拒。</li>
 * </ul>
 * <p>两者都不会报「STRICT 不被支持」，只会表现为「按流程用不了」，排查成本极高。
 * 因此这里把「每个入口都接受 STRICT」变成一条可执行的断言。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStrictDataLevelEntryPointTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    /**
     * 判断校验结果里是否命中指定字段。
     *
     * @param violations 违规集合
     * @param property   字段名
     * @return 是否命中
     */
    private static boolean hit(Set<? extends ConstraintViolation<?>> violations, String property) {
        return violations.stream()
            .anyMatch(violation -> property.equals(violation.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("调用入参：STRICT 必须被接受（否则业务域只能把严格级标低再调）")
    void invokeBoAcceptsStrict() {
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode("image_generation");
        bo.setDataLevel(AigDataLevelEnum.STRICT.getCode());

        assertFalse(hit(validator.validate(bo), "dataLevel"),
            "STRICT 必须能从调用入口进来");
    }

    @Test
    @DisplayName("路由策略：STRICT 必须被接受（否则建不出严格级策略行，而路由无策略即拒绝）")
    void routePolicyBoAcceptsStrict() {
        AigRoutePolicyBo bo = new AigRoutePolicyBo();
        bo.setCapabilityCode("image_generation");
        bo.setDataLevel(AigDataLevelEnum.STRICT.getCode());
        bo.setAllowExternal("N");
        bo.setFallbackToManual("Y");

        assertFalse(hit(validator.validate(bo, AddGroup.class), "dataLevel"),
            "STRICT 策略行必须建得出来，否则严格级调用会因「未配置策略」一律被拒");
    }

    @Test
    @DisplayName("模型治理：dataLevelMax 必须接受 STRICT（否则模型永远够不上严格级数据）")
    void governanceBoAcceptsStrictAsMax() {
        AigModelGovernanceBo bo = new AigModelGovernanceBo();
        bo.setModelId(1L);
        bo.setDeploymentType("LOCAL");
        bo.setDataLevelMax(AigDataLevelEnum.STRICT.getCode());
        bo.setLifecycleStatus("PRODUCTION");

        assertFalse(hit(validator.validate(bo, AddGroup.class), "dataLevelMax"),
            "模型要能声明「可达严格级」，否则严格级数据在本地也没有候选");
    }

    @Test
    @DisplayName("三处入口都要拒绝不存在的等级（避免「接受了任意字符串」式的假放行）")
    void allEntryPointsRejectUnknownLevel() {
        AigInvokeBo invoke = new AigInvokeBo();
        invoke.setCapabilityCode("image_generation");
        invoke.setDataLevel("SECRET");
        assertTrue(hit(validator.validate(invoke), "dataLevel"), "调用入口应拒绝未知等级");

        AigRoutePolicyBo policy = new AigRoutePolicyBo();
        policy.setCapabilityCode("image_generation");
        policy.setDataLevel("SECRET");
        policy.setAllowExternal("N");
        policy.setFallbackToManual("Y");
        assertTrue(hit(validator.validate(policy, AddGroup.class), "dataLevel"), "策略入口应拒绝未知等级");

        AigModelGovernanceBo governance = new AigModelGovernanceBo();
        governance.setModelId(1L);
        governance.setDeploymentType("LOCAL");
        governance.setDataLevelMax("SECRET");
        governance.setLifecycleStatus("PRODUCTION");
        assertTrue(hit(validator.validate(governance, AddGroup.class), "dataLevelMax"),
            "治理入口应拒绝未知等级");
    }

}
