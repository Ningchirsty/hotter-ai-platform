package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才可见范围枚举。
 * <p>对应数据字典 {@code talent_visibility_type}（设计文档 §10），
 * 是设计文档 §21.14 人才数据权限查询规则的分支依据：
 * group 全集团共享、company 归属公司可见、department 归属部门可见、
 * owner 仅人才负责人可见、explicit 仅显式授权主体可见。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum TalentVisibilityTypeEnum {

    /**
     * 集团共享
     */
    GROUP("group", "集团共享"),
    /**
     * 归属公司
     */
    COMPANY("company", "本公司"),
    /**
     * 归属部门
     */
    DEPARTMENT("department", "本部门"),
    /**
     * 仅人才负责人
     */
    OWNER("owner", "仅负责人"),
    /**
     * 显式授权
     */
    EXPLICIT("explicit", "显式授权");

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
    public static TalentVisibilityTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentVisibilityTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
