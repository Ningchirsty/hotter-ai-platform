package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentPool;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才池视图对象 hr_talent_pool（SPEC-P4 §2.3 C 线）。
 *
 * <p>只返回人才池自身的管理信息，不含任何人才联系方式；
 * 池内成员明细走 {@code GET /talent/pools/{poolId}/members}（叠加人才可见范围）。</p>
 *
 * <p><b>可见范围</b>：本 VO 的返回前提是服务层已用
 * {@code TalentScopeDomainService} 判定该池对当前用户可见（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentPool.class)
public class TalentPoolVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才池ID
     */
    private Long poolId;

    /**
     * 人才池编码（业务编号，唯一）
     */
    private String poolCode;

    /**
     * 人才池名称
     */
    private String poolName;

    /**
     * 人才池类型（reserve/position/talent 等稳定编码）
     */
    private String poolType;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 归属部门名称快照
     */
    private String ownerDeptName;

    /**
     * 池管理员用户ID
     */
    private Long managerId;

    /**
     * 池管理员昵称（由 {@link #managerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "managerId")
    private String managerName;

    /**
     * 可见范围（字典 talent_visibility_type 编码）
     */
    private String visibilityType;

    /**
     * 可见范围标签（字典 talent_visibility_type）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "visibilityType", other = "talent_visibility_type")
    private String visibilityTypeLabel;

    /**
     * 人才池说明
     */
    private String poolDesc;

    /**
     * 成员数量（冗余统计，服务层维护）
     */
    private Integer memberCount;

    /**
     * 状态（active/archived 等稳定编码）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
