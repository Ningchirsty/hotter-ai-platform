package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才池对象 hr_talent_pool（设计文档 §8.15、§9.2）。
 *
 * <p><b>用途</b>：表达组织级的人才运营范围（集团共享池、专业技术池、关键岗位储备池等），
 * 有管理员、可见范围与成员状态。</p>
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li>人才池的可见性判定<b>不在本实体或 Service 内自行实现</b>，统一由
 *     {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 的
 *     {@code visible(...)} 分支完成（设计文档 §11.1）；</li>
 *     <li>{@link #memberCount} 是冗余统计列，只由服务层在成员增减后重算，
 *     不接受前端写入；</li>
 *     <li>删除为逻辑删除（{@link #delFlag}），不做物理删除。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_pool")
public class TalentPool extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才池ID（主键）
     */
    @TableId(value = "pool_id")
    private Long poolId;

    /**
     * 人才池编码（业务编号，唯一，由服务端生成）
     */
    private String poolCode;

    /**
     * 人才池名称
     */
    private String poolName;

    /**
     * 人才池类型（reserve储备/position岗位定向/talent专项等稳定编码）
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
     * 池管理员用户ID（维护池成员与公共分组）
     */
    private Long managerId;

    /**
     * 可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）
     */
    private String visibilityType;

    /**
     * 人才池说明
     */
    private String poolDesc;

    /**
     * 成员数量（冗余统计，只由服务层重算）
     */
    private Integer memberCount;

    /**
     * 状态（active启用/archived归档等稳定编码）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
