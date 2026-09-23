package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入问题严重级别。
 *
 * <p>对应 {@code hr_recruit_import_error.severity}。分级的实际意义：{@code ERROR} 的行
 * <b>不会入库</b>，{@code WARNING} 的行<b>照常入库</b>但把提示带出来——
 * 例如「公司名称没填但公司ID填了」属于可继续，而「计划人数为 0」必须拦住。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum RecruitImportSeverityEnum {

    /**
     * 阻断：该行不会导入
     */
    ERROR("error", "错误"),

    /**
     * 提示：该行仍会导入
     */
    WARNING("warning", "提示");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

}
