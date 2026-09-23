package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 招聘数据导入批次状态。
 *
 * <p>对应 {@code hr_recruit_import_batch.status}。刻意保留 {@code pending}（已预检待确认）
 * 与 {@code confirming}（确认导入执行中）两个中间态：导入是「先看清错误再落库」的两段式，
 * 只有一个「成功/失败」终态就无法区分「还没确认」和「确认后失败了」。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum RecruitImportBatchStatusEnum {

    /**
     * 预检通过，等待人工确认导入
     */
    PENDING("pending", "待确认"),

    /**
     * 正在执行确认导入
     */
    CONFIRMING("confirming", "导入中"),

    /**
     * 全部导入成功
     */
    SUCCESS("success", "全部成功"),

    /**
     * 部分行失败（有效行已导入）
     */
    PARTIAL_FAILED("partial_failed", "部分失败"),

    /**
     * 全部失败
     */
    FAILED("failed", "失败"),

    /**
     * 人工取消，未导入任何数据
     */
    CANCELLED("cancelled", "已取消");

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
    public static RecruitImportBatchStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (RecruitImportBatchStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
