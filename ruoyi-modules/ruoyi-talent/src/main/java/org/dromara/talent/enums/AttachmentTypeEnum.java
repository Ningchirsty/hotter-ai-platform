package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才附件类型枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum AttachmentTypeEnum {

    /**
     * 简历
     */
    RESUME("RESUME", "简历"),
    /**
     * 身份证
     */
    ID_CARD("ID_CARD", "身份证"),
    /**
     * 学历证书
     */
    EDUCATION_CERT("EDUCATION_CERT", "学历证书"),
    /**
     * 其他
     */
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
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
