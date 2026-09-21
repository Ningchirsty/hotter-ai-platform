package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历解析状态枚举。
 * <p>对应数据字典 {@code talent_resume_parse_status}（设计文档 §10）。
 * 解析结果必须经人工复核确认后才可写入正式人才字段。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum ResumeParseStatusEnum {

    /**
     * 待解析
     */
    PENDING("pending", "待解析"),
    /**
     * 解析中
     */
    PROCESSING("processing", "解析中"),
    /**
     * 解析成功
     */
    SUCCEEDED("succeeded", "解析成功"),
    /**
     * 解析失败
     */
    FAILED("failed", "解析失败"),
    /**
     * 待人工复核
     */
    REVIEWING("reviewing", "待复核"),
    /**
     * 已确认
     */
    CONFIRMED("confirmed", "已确认");

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
    public static ResumeParseStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ResumeParseStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
