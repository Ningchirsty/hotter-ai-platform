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
 * 人才关键字段变更历史对象 hr_talent_profile_change（设计文档 §9.2）。
 *
 * <p>人才主档的姓名、电话、邮箱、身份证类、状态、负责人等关键字段发生变更时，
 * 必须追加一条本表记录（{@code before_json} / {@code after_json} 保存结构化前后快照），
 * <b>只追加、不覆盖、不物理删除</b>。</p>
 *
 * <p><b>安全约束</b>：{@code before_json} / {@code after_json} 只允许保存脱敏后的字段快照，
 * 禁止写入电话/邮箱明文。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_profile_change")
public class TalentProfileChange extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更记录ID（主键）
     */
    @TableId(value = "change_id")
    private Long changeId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 变更类型（create/update/archive/merge/status 等稳定编码）
     */
    private String changeType;

    /**
     * 变更前快照（结构化 JSON）
     */
    private String beforeJson;

    /**
     * 变更后快照（结构化 JSON）
     */
    private String afterJson;

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
