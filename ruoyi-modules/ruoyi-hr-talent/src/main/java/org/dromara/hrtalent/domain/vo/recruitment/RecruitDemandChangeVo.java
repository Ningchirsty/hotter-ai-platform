package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitDemandChange;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘需求变更历史视图对象 hr_recruit_demand_change（SPEC-P2 §3.1）。
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitDemandChange.class)
public class RecruitDemandChangeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更记录ID
     */
    private Long changeId;

    /**
     * 招聘需求ID
     */
    private Long demandId;

    /**
     * 变更类型（create/update/submit/enter_recruiting/pause/resume/complete/close）
     */
    private String changeType;

    /**
     * 变更前快照（结构化 JSON 字符串）
     */
    private String beforeJson;

    /**
     * 变更后快照（结构化 JSON 字符串）
     */
    private String afterJson;

    /**
     * 变更原因
     */
    private String reason;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人昵称（用户翻译，仅出参）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
