package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Visual DNA（视觉基因）dp_visual_dna
 *
 * <p>把「这条详情页长什么样」写成结构化规范：配色、光线、留白、产品占比、场景与禁忌。
 * {@code dna_json} 是权威内容（含证据链），其余列是<b>索引与展示用的镜像</b>——
 * 由服务层在写入时统一从 json 派生，禁止两处各写各的。</p>
 *
 * <p><b>版本语义</b>：草稿（DRAFT/REVIEW）可原地修改；**一旦 LOCKED 就不可改**，
 * 再改必须新建版本（version+1）。这样「这一版分镜/这批图是按哪版基因做的」永远可回溯。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_visual_dna")
public class DpVisualDna extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 编号 DNA-yyyyMMdd-xxxx
     */
    private String dnaNo;

    /**
     * 版本（每次重生成/改锁定版递增）
     */
    private Integer version;

    /**
     * 状态（DRAFT草稿/REVIEW待审/LOCKED已锁定）
     */
    private String status;

    /**
     * 风格关键词（逗号分隔）
     */
    private String styleKeywords;

    /**
     * 禁忌关键词
     */
    private String avoidKeywords;

    /**
     * 主色 #RRGGBB
     */
    private String colorPrimary;

    /**
     * 辅色
     */
    private String colorSecondary;

    /**
     * 点缀色
     */
    private String colorAccent;

    /**
     * 背景色
     */
    private String colorBg;

    /**
     * 饱和度档（LOW/MEDIUM/HIGH）
     */
    private String saturation;

    /**
     * 对比度档
     */
    private String contrastLevel;

    /**
     * 光线类型（SOFT/HARD/STUDIO/NATURAL）
     */
    private String lightingType;

    /**
     * 光位（FRONT/SIDE/TOP/BACK）
     */
    private String lightingDir;

    /**
     * 产品占画面最小比例（%）
     */
    private BigDecimal productRatioMin;

    /**
     * 产品占画面最大比例（%）
     */
    private BigDecimal productRatioMax;

    /**
     * 留白程度（LOW/MEDIUM/HIGH）
     */
    private String whitespaceLevel;

    /**
     * 字体风格描述
     */
    private String typographyStyle;

    /**
     * 场景类型
     */
    private String sceneType;

    /**
     * 完整视觉基因（权威内容，含证据链）
     */
    private String dnaJson;

    /**
     * 来源（AI生成/MANUAL人工/FACTS由已确认事实推导）
     */
    private String source;

    /**
     * 生成所用模型标识（治理台）
     */
    private String modelKey;

    /**
     * 治理层调用链ID
     */
    private String traceId;

    /**
     * 锁定人
     */
    private Long approvedBy;

    /**
     * 锁定时间
     */
    private java.time.LocalDateTime approvedAt;

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
