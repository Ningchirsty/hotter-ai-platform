package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.hrtalent.domain.entity.TalentPoolMember;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才池成员业务对象 hr_talent_pool_member（SPEC-P4 §2.3 {@code POST /talent/pools/{poolId}/members}）。
 *
 * <p><b>加入语义</b>：加入人才池必须记录加入原因、推荐岗位、适配等级、加入人、加入时间与下次联系时间。
 * 加入人由服务端取登录态，不接受前端写入；重复加入按<b>幂等</b>处理，返回已有关系而不报错
 * （设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentPoolMember.class, reverseConvertGenerate = false)
public class TalentPoolMemberBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才池ID（由路径参数回填）
     */
    private Long poolId;

    /**
     * 人才主档ID
     */
    @NotNull(message = "人才ID不能为空")
    private Long talentId;

    /**
     * 适配等级（high/medium/low 等稳定编码）
     */
    @Size(max = 32, message = "适配等级长度不能超过 32")
    private String fitLevel;

    /**
     * 推荐岗位
     */
    @Size(max = 200, message = "推荐岗位长度不能超过 200")
    private String recommendedJob;

    /**
     * 加入原因
     */
    @Size(max = 500, message = "加入原因长度不能超过 500")
    private String joinReason;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextContactTime;

    /**
     * 成员状态（active/paused/removed/converted，字典 talent_pool_member_status）
     */
    @Size(max = 32, message = "成员状态长度不能超过 32")
    private String memberStatus;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
