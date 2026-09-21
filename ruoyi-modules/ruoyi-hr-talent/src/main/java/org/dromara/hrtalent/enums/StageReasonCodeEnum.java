package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 阶段变更原因分类枚举。
 * <p>对应数据字典 {@code recruit_stage_reason_code}，与 {@code hr_recruit_stage_log.reason_code}
 * 的稳定编码一致（建表注释「原因编码（字典编码，不存中文）」）。</p>
 *
 * <p><b>依据</b>：设计文档 §8.5「原因分类」要求阶段变化可归因，但文档<b>未枚举</b>具体编码；
 * 下列 8 个编码为本次新定义（已在 {@code hr_talent_menu.sql} / {@code hr_talent_migration.sql}
 * 的字典注释中写明）。字典值只存英文编码，中文仅出现在 {@code dict_label} 与本枚举描述中。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum StageReasonCodeEnum {

    /**
     * 技能不匹配
     */
    SKILL_MISMATCH("skill_mismatch", "技能不匹配"),
    /**
     * 经验年限不符
     */
    EXPERIENCE_MISMATCH("experience_mismatch", "经验年限不符"),
    /**
     * 薪资不符
     */
    SALARY_MISMATCH("salary_mismatch", "薪资不符"),
    /**
     * 学历不符
     */
    EDUCATION_MISMATCH("education_mismatch", "学历不符"),
    /**
     * 沟通表现不符
     */
    COMMUNICATION("communication", "沟通表现不符"),
    /**
     * 候选人主动放弃
     */
    CANDIDATE_DECLINED("candidate_declined", "候选人主动放弃"),
    /**
     * 岗位已关闭
     */
    POSITION_CLOSED("position_closed", "岗位已关闭"),
    /**
     * 其他
     */
    OTHER("other", "其他");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，仅用于提示与兜底展示）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static StageReasonCodeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (StageReasonCodeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断编码是否合法。
     *
     * @param code 编码
     * @return 合法返回 true
     */
    public static boolean isValid(String code) {
        return find(code) != null;
    }

    /**
     * 取编码对应的中文标签。
     *
     * @param code 编码
     * @return 中文标签，未知编码返回 null
     */
    public static String labelOf(String code) {
        StageReasonCodeEnum item = find(code);
        return item == null ? null : item.getDesc();
    }

}
