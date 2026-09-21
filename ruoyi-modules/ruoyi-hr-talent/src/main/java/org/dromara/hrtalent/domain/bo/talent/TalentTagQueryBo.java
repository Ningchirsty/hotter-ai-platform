package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才标签检索业务对象（SPEC-P4 §2.3 {@code GET /talent/tags}）。
 *
 * <p>敏感标签（{@code sensitive_flag = '1'}）由服务层按当前用户身份过滤：
 * 非集团级人才管理员默认看不到敏感标签，避免敏感内容被普通用户检索（设计文档 §7.6.4）。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentTagQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签名称（模糊匹配）
     */
    private String tagName;

    /**
     * 标签编码（模糊匹配）
     */
    private String tagCode;

    /**
     * 标签分类（字典 talent_tag_category 编码，精确匹配）
     */
    private String tagCategory;

    /**
     * 是否敏感标签（0否 1是，精确匹配）
     */
    private String sensitiveFlag;

    /**
     * 状态（active/disabled 等稳定编码，精确匹配）
     */
    private String status;

    /**
     * 是否仅返回启用标签（默认 false，启用状态下返回全部有效标签）
     */
    private Boolean onlyEnabled;

}
