package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 敏感操作审计动作枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum AuditActionEnum {

    /**
     * 查看详情
     */
    VIEW_DETAIL("VIEW_DETAIL", "查看详情"),
    /**
     * 查看完整手机号
     */
    VIEW_FULL_PHONE("VIEW_FULL_PHONE", "查看完整手机号"),
    /**
     * 下载附件
     */
    DOWNLOAD("DOWNLOAD", "下载附件"),
    /**
     * 导出
     */
    EXPORT("EXPORT", "导出"),
    /**
     * 创建授权
     */
    CREATE_GRANT("CREATE_GRANT", "创建授权"),
    /**
     * 删除
     */
    DELETE("DELETE", "删除"),
    /**
     * 归档
     */
    ARCHIVE("ARCHIVE", "归档"),
    /**
     * 上传附件
     */
    UPLOAD("UPLOAD", "上传附件");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static AuditActionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AuditActionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
