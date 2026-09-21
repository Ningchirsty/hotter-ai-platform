package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.enums.BackgroundStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘背调记录视图对象 hr_recruit_background（列表与普通详情）。
 *
 * <p><b>安全约束</b>：本 VO <b>刻意不含</b> {@code detail_cipher} 字段，
 * 因此列表与普通详情在结构上就不可能返回背调敏感说明（设计文档 §8.7「背调详情必须使用独立权限控制」）。
 * 明细只能经 {@code GET /recruit/background-checks/{id}/detail}（权限
 * {@code recruit:background:view-sensitive}）并写审计后，用
 * {@link RecruitBackgroundDetailVo} 返回。</p>
 *
 * <p>字典标签与人员昵称统一用 {@code @Translation} 回填（全仓惯例）；背调状态在设计文档 §10
 * 未定义字典组，改由 {@link BackgroundStatusEnum} 兜底转换。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitBackground.class)
public class RecruitBackgroundVo implements Serializable {

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
     * <p>设计文档 §10 未定义该字典组（见交付说明的字典缺口），故此处不做标签翻译，只回编码。</p>
     */
    private String failureReasonCode;

    /**
     * 背调核查项清单
     */
    private String checkItems;

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
     * 背调状态中文标签。
     *
     * <p>设计文档 §10 未为背调状态定义字典类型，因此不使用 {@code @Translation} 引用不存在的字典组，
     * 而由 {@link BackgroundStatusEnum} 的稳定编码兜底转换，保证页面始终有中文可展示。</p>
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getStatusLabel() {
        return BackgroundStatusEnum.labelOf(status);
    }

}
