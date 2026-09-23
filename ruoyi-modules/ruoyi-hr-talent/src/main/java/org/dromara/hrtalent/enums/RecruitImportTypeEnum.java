package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 招聘数据导入类型。
 *
 * <p>对应 {@code hr_recruit_import_batch.import_type}。新增导入类型时在这里加一项即可，
 * 导入服务按类型分发「模板列 → 解析校验 → 落库」三段。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum RecruitImportTypeEnum {

    /**
     * 招聘期限标准（hr_recruit_standard）
     */
    STANDARD("standard", "招聘期限标准"),

    /**
     * 公司月度招聘计划（hr_recruit_plan + hr_recruit_plan_item）
     */
    PLAN("plan", "月度招聘计划");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static RecruitImportTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (RecruitImportTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
