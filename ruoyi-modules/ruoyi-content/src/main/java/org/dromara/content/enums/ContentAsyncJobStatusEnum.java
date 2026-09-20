package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异步作业状态枚举。
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentAsyncJobStatusEnum {

    /**
     * 已入队
     */
    QUEUED("QUEUED", "已入队"),
    /**
     * 执行中
     */
    RUNNING("RUNNING", "执行中"),
    /**
     * 成功
     */
    SUCCESS("SUCCESS", "成功"),
    /**
     * 失败（须带可读原因）
     */
    FAILED("FAILED", "失败");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentAsyncJobStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentAsyncJobStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
