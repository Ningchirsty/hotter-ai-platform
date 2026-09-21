package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 疑似重复人才处理状态枚举。
 * <p>对应数据字典 {@code talent_duplicate_status}（设计文档 §10）。
 * 弱匹配记录不自动合并，必须人工确认。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum DuplicateStatusEnum {

    /**
     * 待确认
     */
    PENDING("pending", "待处理"),
    /**
     * 已合并
     */
    MERGED("merged", "已合并"),
    /**
     * 非同一人
     */
    NOT_SAME("not_same", "非同一人"),
    /**
     * 已忽略
     */
    IGNORED("ignored", "已忽略"),
    /**
     * 已确认待合并（人工确认确为同一人但尚未执行合并，区别于 {@link #PENDING} 待人工判定，
     * 不计入「待处理」工作队列）
     */
    CONFIRMED("confirmed", "已确认待合并");

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
    public static DuplicateStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DuplicateStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
