package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 候选人阶段枚举。
 * <p>对应数据字典 {@code recruit_candidate_stage}（设计文档 §10）。
 * 阶段顺序即 {@link #ordinal()} 顺序，用于校验阶段跳转是否合法。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum CandidateStageEnum {

    /**
     * 新简历
     */
    NEW("new", "新简历"),
    /**
     * 简历筛选
     */
    RESUME_REVIEW("resume_review", "简历筛选"),
    /**
     * 已邀约
     */
    INVITE("invite", "已邀约"),
    /**
     * 初试
     */
    FIRST_INTERVIEW("first_interview", "初试"),
    /**
     * 复试
     */
    SECOND_INTERVIEW("second_interview", "复试"),
    /**
     * 背景调查
     */
    BACKGROUND("background", "背调"),
    /**
     * 已发录用通知
     */
    OFFER("offer", "Offer"),
    /**
     * 待到岗
     */
    PENDING_ARRIVAL("pending_arrival", "待到岗"),
    /**
     * 已到岗
     */
    ARRIVED("arrived", "已到岗");

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
    public static CandidateStageEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (CandidateStageEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
