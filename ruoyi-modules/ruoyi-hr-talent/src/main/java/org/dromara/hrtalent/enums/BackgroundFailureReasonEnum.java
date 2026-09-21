package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 背调未通过原因分类枚举。
 * <p>对应数据字典 {@code recruit_background_failure_reason}（设计文档 §10 无此组，
 * 按 §8.7「未通过原因分类」新增，用于背调结论为「不通过」时登记原因类别）。</p>
 *
 * <p><b>说明</b>：入库一律保存 {@link #getCode()} 稳定编码，中文名称仅用于服务端提示与页面兜底展示；
 * 编码值一旦投入使用不得随意变更。该字段属背调明细，为高敏感数据，列表与日志均不得展示。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum BackgroundFailureReasonEnum {

    /**
     * 信息不符：候选人提供的基础信息与核查结果不一致
     */
    INFO_MISMATCH("info_mismatch", "信息不符"),
    /**
     * 工作经历不一致：任职时间、单位或岗位与事实不符
     */
    WORK_EXPERIENCE("work_experience", "工作经历不一致"),
    /**
     * 学历或证书不一致：学历学位或资质证书无法核实
     */
    EDUCATION("education", "学历或证书不一致"),
    /**
     * 职位职责不一致：职位名称或职责范围与事实不符
     */
    POSITION_DUTY("position_duty", "职位职责不一致"),
    /**
     * 业绩表现不一致：业绩或绩效记录与事实不符
     */
    PERFORMANCE("performance", "业绩表现不一致"),
    /**
     * 法律或信用记录：存在法律纠纷、失信或不良信用记录
     */
    LEGAL_RECORD("legal_record", "法律或信用记录"),
    /**
     * 其他：不属于上述分类的其他未通过原因
     */
    OTHER("other", "其他");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，页面展示由字典转换）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static BackgroundFailureReasonEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (BackgroundFailureReasonEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
