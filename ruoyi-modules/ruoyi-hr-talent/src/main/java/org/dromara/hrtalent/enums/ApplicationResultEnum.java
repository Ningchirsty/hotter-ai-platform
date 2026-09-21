package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应聘结果枚举。
 * <p>对应数据字典 {@code recruit_application_result}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum ApplicationResultEnum {

    /**
     * 流程中
     */
    PROCESSING("processing", "处理中"),
    /**
     * 已通过
     */
    PASSED("passed", "已通过"),
    /**
     * 已拒绝
     */
    REJECTED("rejected", "未通过"),
    /**
     * 已撤回
     */
    WITHDRAWN("withdrawn", "已撤回"),
    /**
     * 已暂停
     */
    PAUSED("paused", "已暂停"),
    /**
     * 转入人才库
     */
    TALENT_POOL("talent_pool", "转入人才池");

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
    public static ApplicationResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ApplicationResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
