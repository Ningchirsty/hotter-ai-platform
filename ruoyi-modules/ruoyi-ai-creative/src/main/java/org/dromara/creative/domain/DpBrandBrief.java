package org.dromara.creative.domain;

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
 * 品牌 Brief dp_brand_brief（委托方的要求）。
 *
 * <p><b>为什么单独一张表、而不是塞进事实快照</b>：事实是「产品客观是什么」，
 * 由资料解析并经人确认；Brief 是「品牌方要求什么」（必显、禁用词、优先级、规范）。
 * 两者的作者、证据链与判定强度都不同：事实错了是解析错，要求错了是需求变更。
 * 混在一张表里，「这条是事实还是要求」就只能在备注里解释——闸门也就没法判定。</p>
 *
 * <p><b>与 {@code brand_tone} 事实并存</b>：事实里的品牌调性是「从资料里解析确认」的，
 * 这里的 {@code brandTone} 是「品牌方自己填的要求」，冲突时页面同时展示、由人裁定，
 * 不做自动合并（自动合并等于替人做决定）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_brand_brief")
public class DpBrandBrief extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花ID）
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id），一个项目一行
     */
    private Long taskId;

    /**
     * 品牌调性（品牌方填写的要求）
     */
    private String brandTone;

    /**
     * 必显信息，一行一条（品牌名/logo/口号/资质等，必须出现在成品里）
     */
    private String mustShow;

    /**
     * 禁用词与合规红线，一行一条（出图负向词与文案校验都用它）
     */
    private String forbiddenWords;

    /**
     * 目标人群
     */
    private String targetAudience;

    /**
     * 主推卖点与优先级，一行一条，行首数字即优先级（1 最高）
     */
    private String mainPush;

    /**
     * 尺寸/规范要求（画布比例、留白、字号、平台规范）
     */
    private String sizeSpecReq;

    /**
     * 参考风格（可写参考图/参考品牌/风格描述）
     */
    private String styleRef;

    /**
     * 状态（DRAFT草稿/CONFIRMED品牌方已确认）
     */
    private String status;

    /**
     * 确认人
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
