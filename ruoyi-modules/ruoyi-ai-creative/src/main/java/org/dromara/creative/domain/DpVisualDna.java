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
     * 这一版提示词的「措辞种子」（v1 人工测试反馈裁定 ⑤：「可以复现，但每次生成都要有差异化」）。
     *
     * <p>原文那一条是「不满足可『重新生成』，点了要出现新提示词」：提示词是基因的派生结果，
     * 若『重新生成』补出来的字段一样，提示词就逐字一样，人看到的就是"点了没变化"。
     * 这颗种子决定 {@code DnaPromptBuilder} 用哪一套措辞（顺序与引导语），
     * <b>只换说法、不换任何值</b>（色号/光线/留白/占比/场景一个字都不动）。</p>
     *
     * <p>为 {@code null} 表示「按改造前的原文案派生」——历史版本与老评测记录因此逐字不变；
     * 只有走过一次『重新生成』的新版本才会带上种子。</p>
     */
    private Long promptSeed;

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
