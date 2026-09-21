package org.dromara.hrtalent.domain.vo.recruitment;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.enums.BackgroundStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 背调敏感明细视图对象（仅 {@code GET /recruit/background-checks/{id}/detail} 使用）。
 *
 * <p><b>权限与审计硬要求</b>（设计文档 §8.7、§15.1、§15.2；SPEC-P3 §3.4/§3.6）：</p>
 * <ul>
 *     <li>只有持有 {@code recruit:background:view-sensitive} 的调用方能访问该视图；</li>
 *     <li>服务端必须<b>先</b>调用 {@code SensitiveAuditRecorder.record(background_view, background, id, purpose, ...)}
 *     再返回本对象，且用途为空时拒绝并记 {@code denied} 审计；</li>
 *     <li>{@link #detailCipher} 落库为密文，由 {@code ruoyi-common-encrypt} 自动解密后返回，
 *     <b>禁止</b>进入日志与审计明细。</li>
 * </ul>
 *
 * <p>本类与 {@link RecruitBackgroundVo} 刻意不构成继承关系：翻译处理器按「实际类声明的字段」回填标签，
 * 继承会让翻译静默失效（SPEC-P3 §1），因此这里显式声明全部字段。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitBackgroundDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 背调记录ID
     */
    private Long backgroundId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 是否已获得候选人授权（0否 1是）
     */
    private String authorizedFlag;

    /**
     * 授权时间
     */
    private LocalDateTime authorizeTime;

    /**
     * 背调负责人用户ID
     */
    private Long checkerId;

    /**
     * 背调负责人昵称（由 {@link #checkerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "checkerId")
    private String checkerName;

    /**
     * 背调完成时间
     */
    private LocalDateTime checkTime;

    /**
     * 背调开始日期
     */
    private LocalDate checkStartDate;

    /**
     * 背调结束日期
     */
    private LocalDate checkEndDate;

    /**
     * 背调结论（字典 recruit_background_result 编码）
     */
    private String result;

    /**
     * 背调结论标签（字典 recruit_background_result）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "result", other = "recruit_background_result")
    private String resultLabel;

    /**
     * 未通过原因编码（字典编码，不存中文）
     */
    private String failureReasonCode;

    /**
     * 背调核查项清单
     */
    private String checkItems;

    /**
     * 背调敏感说明明文（来源为库内密文，服务端解密后返回；禁止写日志与审计明细）
     */
    private String detailCipher;

    /**
     * 免背调授权原因（result=waived 时必填）
     */
    private String waiveReason;

    /**
     * 背调状态（draft/checking/finished/cancelled）
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

    /**
     * 背调状态中文标签（由 {@link BackgroundStatusEnum} 兜底转换）。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getStatusLabel() {
        return BackgroundStatusEnum.labelOf(status);
    }

}
