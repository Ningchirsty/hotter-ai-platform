package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才跟进记录检索业务对象（SPEC-P4 §2.4 {@code GET /talent/profiles/{id}/follow-ups}）。
 *
 * <p>只承载检索条件。人才可见范围<b>不</b>由本对象决定：调用方必须先经
 * {@code TalentScopeDomainService} 校验该人才可见，再按 {@code talentId} 过滤。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentFollowUpQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（路径参数覆盖；服务层据此限定查询范围）
     */
    private Long talentId;

    /**
     * 联系方式（稳定编码，精确匹配）
     */
    private String contactMethod;

    /**
     * 联系结果（字典 talent_contact_result 编码，精确匹配）
     */
    private String contactResult;

    /**
     * 跟进人用户ID（精确匹配）
     */
    private Long followerId;

    /**
     * 联系时间起（含，{@code yyyy-MM-dd}）
     */
    private LocalDate contactDateBegin;

    /**
     * 联系时间止（含，{@code yyyy-MM-dd}）
     */
    private LocalDate contactDateEnd;

    /**
     * 下次联系时间起（含，{@code yyyy-MM-dd}）
     */
    private LocalDate nextContactDateBegin;

    /**
     * 下次联系时间止（含，{@code yyyy-MM-dd}）
     */
    private LocalDate nextContactDateEnd;

}
