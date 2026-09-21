package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试方式枚举。
 * <p>对应数据字典 {@code recruit_interview_method}（设计文档 §10 无此组，
 * 按 §9.2 {@code hr_recruit_interview.method} 建表注释
 * 「onsite现场/video视频/phone电话」补齐）。</p>
 *
 * <p><b>说明</b>：入库一律保存 {@link #getCode()} 稳定编码，中文名称仅用于服务端提示与页面兜底展示；
 * 编码值一旦投入使用不得随意变更。面试方式与「面试地点或线上链接」字段配合使用：
 * 现场方式填写地点，视频方式填写会议链接，电话方式可为空。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum InterviewMethodEnum {

    /**
     * 现场：线下面试，地点填写在 location
     */
    ONSITE("onsite", "现场"),
    /**
     * 视频：线上视频面试，会议链接填写在 location
     */
    VIDEO("video", "视频"),
    /**
     * 电话：电话面试，location 可为空
     */
    PHONE("phone", "电话");

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
    public static InterviewMethodEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (InterviewMethodEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
