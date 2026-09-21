package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才标签类别枚举。
 * <p>对应数据字典 {@code talent_tag_category}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentTagCategoryEnum {

    /**
     * 技能
     */
    SKILL("skill", "技能"),
    /**
     * 岗位方向
     */
    JOB_DIRECTION("job_direction", "岗位方向"),
    /**
     * 行业
     */
    INDUSTRY("industry", "行业"),
    /**
     * 经验
     */
    EXPERIENCE("experience", "经验"),
    /**
     * 语言
     */
    LANGUAGE("language", "语言"),
    /**
     * 证书
     */
    CERTIFICATE("certificate", "证书"),
    /**
     * 其他
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
    public static TalentTagCategoryEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentTagCategoryEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
