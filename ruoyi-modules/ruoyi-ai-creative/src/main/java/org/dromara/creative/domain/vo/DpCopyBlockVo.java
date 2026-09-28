package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文案与要点块展示对象。
 *
 * <p>字段名即 R7 冻结契约，前端按这些名字取数；{@code blockType} 与 {@code source} 都是编码，
 * 页面自行映射中文（模块内其余 VO 也是这个做法，保持两端口径一致）。</p>
 *
 * @author creative
 */
@Data
public class DpCopyBlockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 块ID
     */
    private Long id;

    /**
     * 视觉项目ID
     */
    private Long taskId;

    /**
     * 块类型（SELLING_POINT/BODY_SECTION/SPEC_ROW/MUST_SHOW）
     */
    private String blockType;

    /**
     * 排序（即优先级/阅读顺序）
     */
    private Integer sortNo;

    /**
     * 卖点标题/段落小标题/参数名
     */
    private String title;

    /**
     * 卖点说明/段落正文/参数值
     */
    private String content;

    /**
     * 来源（MANUAL/MODEL/FACT）
     */
    private String source;

    /**
     * 来源引用（事实编码等）
     */
    private String sourceRef;

    /**
     * 状态（DRAFT/CONFIRMED）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
