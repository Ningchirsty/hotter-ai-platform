package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文案与要点块新增/编辑表单。
 *
 * <p>新增时 {@code sortNo} 可空（服务端取「同项目同类型最大 sortNo + 10」），
 * 编辑时 {@code blockType} 允许不改（为空表示沿用原值）——块类型决定这条文字在详情页里
 * 是卖点还是参数，改类型属于移动而不是编辑，页面走的是「删了重加」或显式传值。</p>
 *
 * @author creative
 */
@Data
public class CopyBlockBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 块ID（编辑时必填）
     */
    private Long id;

    /**
     * 块类型（SELLING_POINT/BODY_SECTION/SPEC_ROW/MUST_SHOW）
     */
    private String blockType;

    /**
     * 排序（可空＝追加到末尾）
     */
    private Integer sortNo;

    /**
     * 卖点标题/段落小标题/参数名
     */
    @Size(max = 255, message = "标题不能超过 255")
    private String title;

    /**
     * 卖点说明/段落正文/参数值
     */
    @Size(max = 2000, message = "内容不能超过 2000")
    private String content;

    /**
     * 来源（MANUAL/MODEL/FACT）；不传按 MANUAL 处理
     */
    private String source;

    /**
     * 来源引用（事实编码等）
     */
    @Size(max = 255, message = "来源引用不能超过 255")
    private String sourceRef;

    /**
     * 状态（DRAFT/CONFIRMED）
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过 500")
    private String remark;

}
