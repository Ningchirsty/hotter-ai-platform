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

}
