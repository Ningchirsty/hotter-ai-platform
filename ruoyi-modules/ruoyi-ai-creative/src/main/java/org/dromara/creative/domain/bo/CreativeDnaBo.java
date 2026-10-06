package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Visual DNA 编辑表单。
 *
 * <p>刻意用扁平字段（而不是直接收 json）：页面上的每个输入框对应一个明确字段，
 * 服务端再统一组装成权威 json——避免前端能塞进任何 json 把 schema 绕过去。</p>
 *
 * @author creative
 */
@Data
public class CreativeDnaBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要编辑的 DNA 主键（留空＝改最新一版）
     */
    private Long id;

    /**
     * 风格关键词
     */
    @Size(max = 12, message = "风格关键词最多 12 个")
    private List<String> styleKeywords;

    /**
     * 禁忌关键词
     */
    @Size(max = 20, message = "禁忌关键词最多 20 个")
    private List<String> avoidKeywords;

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
     * 留白档
     */
    private String whitespaceLevel;

    /**
     * 光线类型（SOFT/HARD/STUDIO/NATURAL）
     */
    private String lightingType;

    /**
     * 光位（FRONT/SIDE/TOP/BACK）
     */
    private String lightingDir;

    /**
     * 产品占比下限（%）
     */
    private Integer productRatioMin;

    /**
     * 产品占比上限（%）
     */
    private Integer productRatioMax;

    /**
     * 字体风格
     */
    private String typographyStyle;

    /**
     * 场景类型
     */
    private String sceneType;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /**
     * 人工改写的正向提示词（v1 裁定 ③：在基因页框里改提示词**算新一版基因**）。
     *
     * <p><b>这两个字段的三态是有意义的</b>：{@code null}＝这次保存不动提示词；
     * 空串＝清掉人工改写、回到"按基因派生"；有内容＝写进这一版基因的 {@code promptOverride}。
     * 若把"不动"和"清掉"混成同一个值，改别的字段时就会把人改过的提示词顺手抹掉。</p>
     */
    @Size(max = 1000, message = "正向提示词长度不能超过 1000")
    private String promptPositive;

    /**
     * 人工改写的负向提示词（口径同 {@link #promptPositive}；上限与出图框的负向提示词一致）
     */
    @Size(max = 500, message = "负向提示词长度不能超过 500")
    private String promptNegative;

}
