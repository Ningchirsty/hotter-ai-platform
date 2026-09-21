package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘需求变更历史对象 hr_recruit_demand_change（设计文档 §9.2）。
 *
 * <p>需求提交、状态流转与进入招聘中之后的关键字段修改均追加一条记录，
 * {@code before_json} / {@code after_json} 保存结构化快照（仅关键字段，不含敏感信息）。</p>
 *
 * <p><b>只追加不修改</b>：本表为审计型记录，业务上不提供更新入口，
 * 逻辑删除字段仅用于对齐通用字段约定。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_demand_change")
public class RecruitDemandChange extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更记录ID（主键）
     */
    @TableId(value = "change_id")
    private Long changeId;

    /**
     * 招聘需求ID
     */
    private Long demandId;

    /**
     * 变更类型（create/update/submit/enter_recruiting/pause/resume/complete/close 等稳定编码）
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
     * 变更原因（暂停、复开、关闭时必填）
     */
    private String reason;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

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
