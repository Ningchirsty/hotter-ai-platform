package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才数据来源类型枚举。
 * <p>对应数据字典 {@code talent_source_type}。</p>
 * <p>设计文档 §10 未定义，本次按 hr_talent_resume/education/work/project.source_type
 * 建表注释「人工/导入/解析等稳定编码」补齐。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentSourceTypeEnum {

    /**
     * 人工录入
     */
    MANUAL("manual", "人工录入"),
    /**
     * 导入
     */
    IMPORT("import", "导入"),
    /**
     * 简历解析
     */
    PARSE("parse", "简历解析"),
    /**
     * 系统生成
     */
    SYSTEM("system", "系统生成");

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
    public static TalentSourceTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentSourceTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
