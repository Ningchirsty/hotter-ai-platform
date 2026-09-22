package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异步作业类型枚举。
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentAsyncJobTypeEnum {

    /**
     * 资料解析（经 aigov 能力 document_parse）
     */
    PARSE("PARSE", "资料解析"),
    /**
     * 资料预检（冲突与缺失检测，经 aigov 能力 brief_precheck）
     */
    PRECHECK("PRECHECK", "资料预检"),
    /**
     * 开工包生成
     */
    PACKAGE("PACKAGE", "开工包生成"),
    /**
     * 成品一致性检查（生成结果 vs 原参考图，经 aigov 能力 deliverable_consistency）
     */
    OUTPUT_CHECK("OUTPUT_CHECK", "成品一致性检查");

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
    public static ContentAsyncJobTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentAsyncJobTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
