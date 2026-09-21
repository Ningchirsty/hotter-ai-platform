package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 招聘渠道类型枚举。
 * <p>对应数据字典 {@code recruit_channel_type}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum ChannelTypeEnum {

    /**
     * 招聘网站
     */
    JOB_SITE("job_site", "招聘网站"),
    /**
     * 猎头
     */
    HEADHUNTER("headhunter", "猎头"),
    /**
     * 内部推荐
     */
    REFERRAL("referral", "内推"),
    /**
     * 社交媒体
     */
    SOCIAL_MEDIA("social_media", "社交媒体"),
    /**
     * 校园渠道
     */
    CAMPUS("campus", "校园"),
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
    public static ChannelTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ChannelTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
