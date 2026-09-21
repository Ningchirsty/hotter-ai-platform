package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.hrtalent.support.HrTalentErrorCode;

/**
 * 人才重复预检匹配分级枚举（设计文档 §21.15「人才重复预检」、§7.6.3 入库规则）。
 *
 * <p>分级口径：</p>
 * <ul>
 *     <li>{@link #STRONG}：{@code phone_hash} 或 {@code email_hash} 命中；</li>
 *     <li>{@link #MEDIUM}：姓名命中，且（当前公司 或 学校 或 简历文件哈希）命中；</li>
 *     <li>{@link #WEAK}：姓名命中，且期望岗位命中。</li>
 * </ul>
 *
 * <p><b>硬约束</b>：不得以姓名作为唯一判断条件，因此本枚举不存在「仅姓名命中」的级别。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum DuplicateMatchLevelEnum {

    /**
     * 强匹配：电话或邮箱哈希命中。
     */
    STRONG("strong", "强匹配", HrTalentErrorCode.MSG_HR_TALENT_001),

    /**
     * 中匹配：姓名 +（公司 或 学校 或 简历哈希）。
     */
    MEDIUM("medium", "中匹配", HrTalentErrorCode.MSG_HR_TALENT_001),

    /**
     * 弱匹配：姓名 + 期望岗位。
     */
    WEAK("weak", "弱匹配", HrTalentErrorCode.MSG_HR_TALENT_001);

    /**
     * 级别编码（接口返回的稳定编码）。
     */
    private final String code;

    /**
     * 级别中文名称。
     */
    private final String desc;

    /**
     * 命中提示语（统一取错误码常量，禁止裸字符串）。
     */
    private final String message;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static DuplicateMatchLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DuplicateMatchLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
