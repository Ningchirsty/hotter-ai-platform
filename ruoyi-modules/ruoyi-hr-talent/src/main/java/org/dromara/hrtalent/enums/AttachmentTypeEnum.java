package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 附件类型枚举。
 * <p>对应数据字典 {@code recruit_attachment_type}（设计文档 §10）。
 * 附件表为 {@code hr_recruit_attachment} / {@code hr_talent_resume}，只保存 OSS 对象 ID。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum AttachmentTypeEnum {

    /**
     * 简历
     */
    RESUME("resume", "简历"),
    /**
     * 作品集
     */
    PORTFOLIO("portfolio", "作品集"),
    /**
     * 面试材料
     */
    INTERVIEW("interview", "面试材料"),
    /**
     * 背调材料
     */
    BACKGROUND("background", "背调材料"),
    /**
     * 录用通知
     */
    OFFER("offer", "Offer附件"),
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
    public static AttachmentTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AttachmentTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
