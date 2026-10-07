package org.dromara.aigov.domain.bo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.dromara.common.core.validate.AddGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单次成本上限（{@code cost_limit_amount}）的入参校验测试。
 *
 * <p><b>为什么这两条校验必须钉住</b>：这一列会被路由当成金额比较，而两个方向的错误
 * 都不会报错、只会算错：</p>
 * <ul>
 *     <li><b>负数</b>：语义上「成本上限 -1 元」毫无意义，但比较逻辑会把它判成
 *         「永远在预算内」，于是本该拦下的模型被选中——写错的代价是多花钱；</li>
 *     <li><b>小数位超限</b>：列是 {@code decimal(18,8)}，超出的小数位会被数据库静默舍入。
 *         治理台显示 0.123456789、库里是 0.12345679，两边的数不一样，
 *         「为什么按这个值算出来不对」会变成一场没有结论的对账。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelCostLimitAmountBoTest {

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
     * 造一份其它字段都合法的「新增模型治理属性」入参。
     *
     * @param amount 单次成本上限
     * @return 治理属性业务对象
     */
    private static AigModelGovernanceBo governanceBo(BigDecimal amount) {
        AigModelGovernanceBo bo = new AigModelGovernanceBo();
        bo.setModelId(1L);
        bo.setDeploymentType("EXTERNAL_API");
        bo.setDataLevelMax("INTERNAL");
        bo.setLifecycleStatus("PRODUCTION");
        bo.setCostLimitAmount(amount);
        return bo;
    }

    /**
     * 造一份其它字段都合法的「新增模型」入参。
     *
     * @param amount 单次成本上限
     * @return 新增模型业务对象
     */
    private static AigModelCreateBo createBo(BigDecimal amount) {
        AigModelCreateBo bo = new AigModelCreateBo();
        bo.setProviderId(1L);
        bo.setModelName("flux-2-pro");
        bo.setModelKey("flux-2-pro");
        bo.setModelType("IMAGE");
        bo.setDeploymentType("EXTERNAL_API");
        bo.setDataLevelMax("INTERNAL");
        bo.setLifecycleStatus("PRODUCTION");
        bo.setCostLimitAmount(amount);
        return bo;
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
    @DisplayName("合法值通过：0、常规金额、正好 8 位小数")
    void acceptsValidAmounts() {
        assertFalse(hit(validator.validate(governanceBo(BigDecimal.ZERO), AddGroup.class), "costLimitAmount"),
            "0 是合法上限（等价于「这次调用不允许花钱」），不能当成未声明或非法");
        assertFalse(hit(validator.validate(governanceBo(new BigDecimal("0.5")), AddGroup.class), "costLimitAmount"),
            "常规金额应通过");
        assertFalse(hit(validator.validate(governanceBo(new BigDecimal("1.12345678")), AddGroup.class), "costLimitAmount"),
            "正好 8 位小数是列宽允许的上限，应通过");
        assertFalse(hit(validator.validate(createBo(new BigDecimal("2.5")), AddGroup.class), "costLimitAmount"),
            "新增模型的入口同样应通过");
    }

    @Test
    @DisplayName("未声明（null）合法：新列上线时既有模型全是 NULL，不能被判成非法")
    void acceptsUndeclaredAmount() {
        assertFalse(hit(validator.validate(governanceBo(null), AddGroup.class), "costLimitAmount"),
            "未声明必须合法，否则治理台一保存既有模型就会报错");
        assertFalse(hit(validator.validate(createBo(null), AddGroup.class), "costLimitAmount"),
            "新增时允许暂不声明，稍后补录");
    }

    @Test
    @DisplayName("负数被拒：否则比较逻辑会把「上限 -1」判成永远在预算内，本该拦下的模型被选中")
    void rejectsNegativeAmount() {
        assertTrue(hit(validator.validate(governanceBo(new BigDecimal("-1")), AddGroup.class), "costLimitAmount"),
            "负数上限必须拒绝");
        assertTrue(hit(validator.validate(createBo(new BigDecimal("-0.01")), AddGroup.class), "costLimitAmount"),
            "新增入口同样必须拒绝负数");
    }

    @Test
    @DisplayName("小数位超过 8 位被拒：超出会被数据库静默舍入，界面显示的与参与判定的值不一致")
    void rejectsTooManyFractionDigits() {
        assertTrue(hit(validator.validate(governanceBo(new BigDecimal("0.123456789")), AddGroup.class), "costLimitAmount"),
            "9 位小数会被 decimal(18,8) 舍入，必须在校验层就拦下");
    }

    @Test
    @DisplayName("整数位超过 10 位被拒：避免超出 decimal(18,8) 的整数部分容量")
    void rejectsTooManyIntegerDigits() {
        assertTrue(hit(validator.validate(governanceBo(new BigDecimal("12345678901")), AddGroup.class),
                "costLimitAmount"),
            "11 位整数超出 decimal(18,8) 可表达的整数范围，必须拦下");
    }

}
